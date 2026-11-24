# ADR-001 — Formato Bearer `sk-` e contrato OpenAPI da key complementar

**Status:** merged (merge `3a891e7`, PR #9) · **Data:** 2026-11-23 · **PRD:** `public-api-key-complementar` · **Branch:** `adr/public-api-key-complementar/01-formato-bearer`

**depende-de:** nenhuma ADR. Pré-condição: PRD §16 aprovado em 2026-11-23 (`prd_version: 2`).

## Contexto

Integradores OpenAI-style enviam `Authorization: Bearer sk-...`. O Hive já aceita `Authorization: Bearer <JWT>` na chain M2M (`PublicApiSecurityFilter`), onde o JWT vem de `client_id` + `client_secret`. Acrescentar a key ao mesmo header exige distinguir as duas credenciais sem ambiguidade e documentar os dois esquemas no OpenAPI.

`x-goog-api-key` e a aceitação de ambos os headers foram avaliados no `RESEARCH.md` e **ficaram fora do escopo** por decisão do operador. Esta ADR não muda emissão, hash, rotação, revogação, cota ou telemetria — esses temas pertencem às ADRs 002 e 003.

Fonte externa: OpenAI aceita API keys via `Authorization: Bearer <credencial>` — https://platform.openai.com/docs/api-reference/authentication — 2026-11-23. Fonte interna: `RESEARCH.md` §4/§5; `PublicApiSecurityFilter.java`; `OpenApiConfig.java:44-50`; `PublicApiOpenApiConfig.java:17-21`; `PublicChatControllerV1.java:60`; contrato arquivado da ADR-001 anterior.

Achados da revisão de 2026-11-23 que fecham a decisão:

- `PublicApiSecurityFilter.recoverToken` (`:123-129`) aceita qualquer `Bearer <valor>` e `decodeAndVerify` (`:60`) é incondicional. **Todo o principal vem do JWT**: `tenant-id` (`:71-72`), `permissions`→authorities (`:74-79`), `subject`→`clientId` (`:80,83`) e os atributos de request `publicapi.pcode`/`plock`/`plim` (`:84-88`). A key ainda não tem claim `tenant-id` — este é o acoplamento invisível que esta ADR precisa endereçar, sem implementar a emissão.
- O esquema publicado hoje se chama **`bearer-key`** (`OpenApiConfig.java:44-50`), com `SecurityRequirement` global. Renomeá-lo para `bearerAuth`, como sugeria o rascunho, mudaria o nome do método de autenticação em clientes gerados — contrato observável.

## Decisão

1. **Formato único:** `Authorization: Bearer sk-...` no mesmo endpoint público de chat (`POST /v1/public/v1/chat/completions`). Nenhum header adicional; `x-goog-api-key` não é lido em nenhum caminho, mesmo se presente.
2. **Discriminação no filtro — regra fechada, sem fallback.** Após `recoverToken`: (a) se o valor começa com `sk-` (minúsculo exato), segue **somente** o caminho de key; (b) caso contrário, segue **somente** o caminho JWT atual. Falha em qualquer caminho ⇒ 401 imediato, sem tentar o outro. `sk-` vazio/só prefixo ⇒ 401. Como JWT compacto começa com `eyJ` (base64url de `{"`), um JWT iniciado por `sk-` não é emitido por `PublicClientTokenIssuer`; se aparecer, é tratado como key e rejeitado com 401 — risco aceito e coberto por teste. Prefixo é case-sensitive.
3. **Costura da key (sem emissão/cota/telemetria nesta ADR).** Declarar a porta `PublicApiKeyAuthenticator` em **`publicapi/api`**, com `authenticate(rawKey)` devolvendo tenantId, clientId, permissions, `pcode`, `plock` e `plim` — exatamente os dados que o filtro hoje lê do JWT. `PublicApiSecurityFilter` usa `ObjectProvider<PublicApiKeyAuthenticator>`: sem bean (estado até a ADR-002), qualquer `sk-` é 401 fail-closed; a ADR-002 registra a implementação sem mudar o filtro. A adição explícita de `hive-publicapi-api` ao pom de `security/impl` formaliza aresta já transitiva via `publicapi/impl`. No caminho de key o filtro preenche os mesmos atributos de request, authorities e `TenantContext.runWithTenant` do caminho JWT — sem nova lógica de cota ou telemetria.
4. **OpenAPI — nomes fechados.** Manter **`bearer-key`** (JWT, nome já publicado) e acrescentar **`apiKeyAuth`** (HTTP bearer, `sk-`). Não renomear `bearer-key` para `bearerAuth`: quebraria clientes gerados. Declarar ambos como alternativas **somente na operação de chat** via `@SecurityRequirements({@SecurityRequirement(name="bearer-key"), @SecurityRequirement(name="apiKeyAuth")})`; o token endpoint continua com a configuração atual, pois key não emite token. Group `public` e paths inalterados; nenhum `x-*` novo.
5. **Contrato inalterado:** envelope de erro `{message,type,param,code}`, status codes, duplo `/v1`, limites `m/c/t`, claim `plim` do JWT e contrato de admin da ADR-003 anterior permanecem como estão.
6. **Fora desta ADR:** emissão/hash/rotação/revogação (ADR-002); cota própria e telemetria (ADR-003); migração de integradores; mudança no token endpoint.

## Alternativas rejeitadas

| Alternativa | Motivo |
|---|---|
| `x-goog-api-key` | Fora do escopo por decisão do operador; não é o formato dos SDKs OpenAI |
| Ambos os headers | Superfície maior e precedência ambígua; fora do escopo |
| Substituir o JWT por key | Quebra contrato publicado e descarta expiração/escopo; fora do PRD |
| Renomear `bearer-key` para `bearerAuth` | Muda o nome do método nos clientes gerados sem ganho funcional; manter o esquema publicado e somar `apiKeyAuth` |
| Anunciar `apiKeyAuth` no token endpoint | Key não emite token (`shouldNotFilter` já exclui `/v1/public/auth/`); documentação enganosa |
| Fallback key→JWT (ou JWT→key) na falha | Duas validações sobre a mesma credencial e diagnóstico ambíguo; 401 imediato |

## Interface congelada

- `POST /v1/public/v1/chat/completions` e `POST /v1/public/auth/token` — paths e métodos inalterados.
- Envelope OpenAI e códigos de erro da ADR-001 anterior, incluindo `429` do token endpoint corrigido na ADR-004 anterior.
- JWT `aud=hive.public`, claims `tenant-id`, `permissions`, `pcode`, `plock`, `plim` inalterados no caminho JWT.
- Caminho de key produz **os mesmos** atributos de request (`publicapi.clientId`/`pcode`/`plock`/`plim`), authorities (`PUBLIC_CHAT_COMPLETE`) e `TenantContext` — sem duplicação em `PublicChatServiceImpl`.
- Esquemas OpenAPI: `bearer-key` (existente, inalterado) + `apiKeyAuth` (novo), ambos HTTP bearer, apenas na operação de chat; token endpoint e group `public` inalterados.
- `ChatTurnGateway`/`TurnRequest`/`TurnReply` inalterados; `ChatTrace` e `turnId`/`clientId` preservados.
- Nenhuma credencial em log; plaintext de key só no ato de emissão (ADR-002).

## Consequências

- **Positivas:** integradores OpenAI-style conseguem conectar sem adaptar header; dois esquemas documentados sem renomear o publicado; risco de ambiguidade reduzido por prefixo explícito e falha fechada.
- **Negativas:** filtro M2M ganha um ramo e uma porta opcional; até a ADR-002, todo `sk-` devolve 401 por ausência de bean — comportamento intencional e fail-closed.
- **Neutras:** nada muda para clients que já usam `client_credentials`; emissão, cota e telemetria continuam nas ADRs 002/003.

## Evidência exigida

IT de 200 no chat com JWT e com `Bearer sk-...` usando **stub de teste** da porta (sem emissão real, que é da ADR-002); IT de 401 para `sk-` sem bean, `sk-` vazio, JWT iniciado por `sk-` e formato desconhecido — todos sem fallback; spec OpenAPI com `bearer-key` e `apiKeyAuth` na operação de chat, token endpoint inalterado; suíte completa sem regressão; zero diff em ADRs arquivadas, PRD, MFA e `workflow-state.json`; `code-reviewer` sem 🔴.
