# SPEC — ADR-001: Formato Bearer `sk-` e contrato OpenAPI

> Escopo: somente formato e documentação da key complementar. Emissão/hash/rotação/revogação ficam na ADR-002; cota própria e telemetria na ADR-003.

## 1. ADRs

### ADR-001 (este ciclo)

- **Decision (fechada 2026-11-23):** aceitar `Authorization: Bearer sk-...` na chain M2M, discriminando por prefixo exato **`sk-` sem fallback**, e documentar `bearer-key` (JWT, já publicado) + `apiKeyAuth` (novo) na operação de chat. A porta `PublicApiKeyAuthenticator` em `publicapi/api` fornece ao filtro os mesmos dados de principal que o JWT; a implementação real vem na ADR-002.
- **Context:** onboarding OpenAI-style sem quebrar `client_credentials`. Filtro hoje lê `tenant-id`/`permissions`/`clientId`/`pcode`/`plock`/`plim` só do JWT (`PublicApiSecurityFilter:60-93`); key não tem claim `tenant-id` ainda.
- **Trade-offs:** um ramo novo no filtro, uma porta opcional e um esquema extra na spec versus compatibilidade de SDK e contrato de cliente gerado intacto.

ADRs afetados: nenhuma dentro deste PRD. ADRs do PRD anterior estão arquivadas e não são tocadas.

## 2. Domain Context, Tenant Strategy & Provisioning

- **Bounded Context:** `publicapi` continua dono da superfície externa e da porta `PublicApiKeyAuthenticator` (`publicapi/api`); `security` mantém a chain M2M e consome a porta via `ObjectProvider` (aresta `security/impl → publicapi/api` declarada explicitamente).
- **Tenant:** vem da linha do client, nunca da key — invariante do PRD §7. No caminho de key, o tenant é devolvido pelo autenticador e passa por `TenantContext.runWithTenant`, exatamente como no JWT.
- **Provisionamento:** a emissão/verificação da key é da ADR-002; aqui só o formato de apresentação e a costura no filtro.

## 3. Identity & Access Management

- `client_credentials` e o JWT `aud=hive.public` permanecem. A key é credencial alternativa da **mesma identidade tenant/client** (PRD §7).
- Precedência: `sk-` exato (case-sensitive) ⇒ somente caminho de key; demais valores ⇒ somente JWT; falha em qualquer caminho ⇒ 401 sem fallback. `sk-` vazio e JWT iniciado por `sk-` ⇒ 401.
- O caminho de key preenche `publicapi.clientId`/`pcode`/`plock`/`plim`, authorities (`PUBLIC_CHAT_COMPLETE`) e `TenantContext` com a mesma semântica do JWT — necessário para `@HasPermission` em `PublicChatControllerV1:60` e para `PublicChatServiceImpl:88-122`.
- Nenhuma mudança em MFA, RBAC humano ou no endpoint de token (`shouldNotFilter` em `:40-43` já o exclui).

## 4. Security, Key Management & Disaster Recovery

- Plaintext de key nunca em log; emissão one-shot é requisito da ADR-002.
- `x-goog-api-key` e aceitação de ambos os headers não são implementados nem lidos.
- Sem bean `PublicApiKeyAuthenticator`, `sk-` → 401 (fail-closed); não há credencial de produção nesta ADR.
- RTO/RPO: N/A.

## 5. Data Model & Concurrency

- Nenhuma entidade ou migration nesta ADR. Filtro stateless.

## 6. Event Model, Integrations & Messaging Isolation

- Nenhum evento ou integração nova.

## 7. Invariants & Property-Based Testing

| Tipo | Invariante | Verificação |
|---|---|---|
| Formato | `sk-` exato ⇒ key; demais ⇒ JWT; ambos com 200 nos respectivos caminhos | IT com JWT e com stub de key |
| Falha | `sk-` vazio, JWT iniciado por `sk-`, formato desconhecido e key sem bean → 401 sem fallback | ITs de 401 |
| Principal | key produz tenantId/clientId/authorities/`pcode`/`plock`/`plim` equivalentes ao JWT | IT de chat com `@HasPermission` e trace |
| Contrato | envelope OpenAI, status e duplo `/v1` inalterados | ITs de contrato anteriores reexecutados |
| Spec | `bearer-key` inalterado + `apiKeyAuth`, ambos Bearer, só na operação de chat | `/v3/api-docs` + `OpenApiSpecIT` |
| Escopo | `x-goog-api-key` e ambos não aparecem no código | grep/inspeção |

## 8. Requisitos Funcionais

- **RF1** — aceitar `Bearer sk-...` no chat via porta `PublicApiKeyAuthenticator`, sem quebrar o JWT atual.
- **RF2** — rejeitar com 401, sem fallback: `sk-` vazio/sem bean, JWT iniciado por `sk-` e formato desconhecido.
- **RF3** — documentar `bearer-key` (existente) e `apiKeyAuth` (novo) na operação de chat, sem renomear o esquema publicado.

## 9. Requisitos Não-Funcionais

- **NFR1** — suíte completa sem regressão; nenhum teste apagado ou desabilitado.
- **NFR2** — zero diff em ADRs arquivadas, MFA, `workflow-state.json`, UI e configs.
- **NFR3** — nenhuma credencial em log.
- **NFR4** — afirmação de API externa com URL + data.
- **NFR5** — nenhuma lógica de emissão, hash, rotação, revogação, cota própria ou telemetria nesta ADR; nenhuma chave/JWT novo.

## 10. Critérios de Aceite

- IT: 200 no chat com JWT válido e com `Bearer sk-...` usando stub da porta (sem emissão real).
- IT: 401 para `sk-` sem bean, `sk-` vazio, JWT iniciado por `sk-` e formato desconhecido — sem fallback.
- Chat com key satisfaz `@HasPermission("PUBLIC_CHAT_COMPLETE")` e grava `ChatTrace` com `turnId`/`clientId`.
- `/v3/api-docs` com `bearer-key` e `apiKeyAuth` na operação de chat, token endpoint e paths inalterados.
- Suíte 0 falhas; `code-reviewer` sem 🔴.

## 11. Riscos

| Risco | Mitigação |
|---|---|
| Prefixo `sk-` colidir com JWT futuro | JWT compacto começa com `eyJ`; regra explícita sem fallback + teste com `sk-eyJ...` → 401 |
| Renomear esquema quebrar clientes gerados | `bearer-key` permanece; só se soma `apiKeyAuth` |
| Key sem `tenant-id`/claims quebrar o chat | porta devolve os mesmos dados de principal; filtro preenche os mesmos atributos/authorities/TenantContext |
| Sem bean da porta, startup falhar | `ObjectProvider`: ausência ⇒ 401 fail-closed; ADR-002 registra o bean |
| Ramo novo no filtro alterar chain humana | ITs de auth existentes reexecutados; `/v1/public/auth/**` continua fora do filtro |

## 12. Fontes

Externa: OpenAI Authentication — https://platform.openai.com/docs/api-reference/authentication — 2026-11-23. Internas (revisão 2026-11-23): `RESEARCH.md` §4/§5; `PRD.md` §7/§16 (`prd_version: 2`); `PublicApiSecurityFilter.java:40-43,60-93,123-135`; `OpenApiConfig.java:44-50`; `PublicApiOpenApiConfig.java:17-21`; `PublicChatControllerV1.java:60`; `PublicChatServiceImpl.java:88-122`; `security/impl/pom.xml:56-64`; contrato arquivado da ADR-001 anterior.
