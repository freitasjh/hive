# ADR-003 — Gestão de credenciais com UI admin sobre `client_credentials`

**Status:** in-progress · **Data:** 2026-11-22 · **PRD:** `public-chat-api` · **Branch:** `adr/public-chat-api/03-credenciais-admin`

**depende-de:** ADR-001 (merged — contrato público tipado e spec OpenAPI estáveis). Paralela à ADR-004.

## Contexto

A API pública já emite tokens OAuth `client_credentials` (`PublicClientTokenIssuer`) e persiste credenciais em `public_api_client`, `public_api_client_grant` e `public_api_client_prompt` (`V1__public_api_client.sql`). O provisionamento hoje é feito por CLI (`PublicApiProvisioningRunner`), sem interface para operadores. O schema já suporta rotação com janela (`previous_secret_hash` + `previous_secret_expires_at`), revogação (`revoked_at`), escopo (`permission`), vínculo de prompts e `pin_mode`, e limites de requisição (`max_messages`, `max_message_chars`, `max_total_chars`). O PRD fixou que o modelo `hk_` do ROADMAP Phase 3 não será adotado.

Fatos verificados em 2026-11-22 (revisão contra código): `client_id` é globalmente único (`V1:28`); `UNIQUE(tenant_id,label)` (`V1:29`); o tenant vem da linha, nunca do request; credenciais no corpo do token endpoint são legadas, Basic é preferido (RFC 6749 §2.3.1); o limite de chat por tenant e o bugfix do 429 pertencem à ADR-004, não a esta.

Achados que fecham os pontos antes deixados em aberto:

- **Não existe permissão humana de administração de API pública.** `SecurityConstants.java:17-31` só tem `PROMPT_MANAGE`, `PROMPT_ADMIN`, `AGENT_MANAGE`, `AGENT_ADMIN`, `CHAT_SEND`, `CHAT_SESSION_MANAGE`. `PUBLIC_CHAT_COMPLETE` é **grant de client** (`PublicApiProvisioningRunner.java:28`), não permissão de usuário. O precedente implementado para gestão de credenciais é `CredentialControllerV1.java:48,59,70` → `@HasPermission(SecurityConstants.AGENT_MANAGE)`.
- **Path:** controllers seguem `@RequestMapping(RestPath.V1 + "/...")` (ex.: `CredentialControllerV1.java:34`); o front prefixa `/hive/v1` (`frontend/src/services/api.ts:46`). A chain pública casa só `/v1/public/**` (`SecurityConfig.java:45,76`); `/manager/**` é `permitAll` (`:85`) — ambos devem ser evitados.
- **Serviço existente é parcial.** `PublicApiClientServiceImpl` já faz `provision`, `verifySecret`/`hashSecret`, `revoke` (DB + Redis) e grava a janela de secret anterior (`:47-108`). **Não faz listagem, get, grants/prompt-write nem retorna o secret na rotação** — `rotateSecret` é `void` e descarta o plaintext (`:73-85`, comentário `:84`). `PublicClientTokenIssuer` já honra a janela (`:77-82`) e a revogação imediata Redis+DB (`:182-191`). `PublicApiProvisioningRunner` já escreve grants/prompts (`:104-124`) — essa regra precisa ser compartilhada, não duplicada.
- **UI:** `frontend/src/modules/hive/admin/credentials/` é de **credenciais de provider LLM**, não de clients públicos; a tela nova precisa de área própria (`public-clients/`) com service/tipos em `frontend/src/services/`. H3 é gate aplicável (diferente das ADRs 001/002, sem UI).

## Decisão

1. **Manter `client_credentials`.** Nenhuma migração para API keys estáticas `hk_`; nenhuma mudança em `PublicClientTokenIssuer`, no JWT `aud=hive.public` ou na chave separada `api.security.public.token.secret`.
2. **Serviço de gestão em `publicapi/impl` — reuso + lacunas pontuais.** Reuso: `PublicApiClientServiceImpl.provision`/`verifySecret`/`hashSecret`/`revoke`, a janela gravada em `:80-81`, os 3 repositórios e o schema V1, e a leitura de grants/prompts pelo `PublicClientTokenIssuer`. **Novo nesta ADR:** (a) `rotateSecret` passa a **devolver o secret plaintext** — hoje é `void` e descarta o valor (`:73-85`), o que torna impossível cumprir "plaintext só em create/rotate" sem mudar `PublicApiClientService`; (b) listagem paginada por tenant, get, escrita de grants e de prompts com `pin_mode`/`pinned_version` (os repositórios só têm `findByClientId`/`findByClientRef`); (c) extrair a regra de escrita de grant/prompt hoje inline no CLI (`PublicApiProvisioningRunner:104-124`) para método compartilhado, para que UI e CLI não divirjam. Inalteradas: unicidade global de `client_id`, tenant imutável da linha, janela do secret anterior e revogação imediata via Redis + DB (já implementadas e cobertas por IT).
3. **Superfície HTTP na chain humana — decidida.** Controller `@RestController @RequestMapping(RestPath.V1 + "/public-api/clients")`, ou seja `/hive/v1/public-api/clients` no front. `public-api` é segmento distinto de `public`: não casa `securityMatcher("/v1/public/**")` nem o `denyAll()` da chain humana (`SecurityConfig.java:45,76`). Também não usar `/manager/**` — está `permitAll` (`:85`). Métodos: `GET /` (lista paginada), `GET /{clientId}`, `POST /`, `POST /{clientId}/rotate`, `POST /{clientId}/revoke`, `PUT /{clientId}/grants`, `PUT /{clientId}/prompts`. Todos com `@HasPermission(SecurityConstants.AGENT_MANAGE)` — reuso deliberado do precedente de `CredentialControllerV1`, sem criar permissão nova nesta ADR (ver Alternativas).
4. **UI admin em `frontend/src/modules/hive/admin/public-clients/`** (área nova — não reutilizar `credentials/`, que é de providers LLM). Lista, criação, rotação e revogação; service em `frontend/src/services/public-api-admin.service.ts`, tipos em `admin.types.ts`, i18n em `admin/i18n.ts`, export em `admin/index.ts`; View + Container + stories + teste. Secret em plaintext **somente** na resposta de `POST`/`rotate`, exibido uma vez em diálogo com copiar/dispensar, nunca reconsultado; listagem/get não contêm `secret`, `secret_hash` nem `previous_secret_hash`.
5. **Campos expostos.** `clientId`, `label`, `tenantId` (somente leitura), grants, `prompt_code` + `pin_mode`/`pinned_version`, `token_ttl_seconds`, `max_messages`/`max_message_chars`/`max_total_chars`, `revokedAt`, `expiresAt` e `previousSecretExpiresAt` (timestamp da janela, sem hash). `rate_limit_per_minute` por client **não** aparece — a cota é do tenant (ADR-004). `client_id` é **sempre gerado pelo servidor** (`UUID.randomUUID()` em `PublicApiClientServiceImpl:51`), nunca aceito do cliente, e imutável. Tenant vem da linha/sessão humana e é imutável; UI não troca tenant.
6. **Paginação e estado da rotação/revogação.** Lista paginada com `?page=0&size=20` (máx. `size=100`) via `Pageable` — sem nova migration, usando `JpaRepository`. A UI mostra o secret anterior como "válido até `previousSecretExpiresAt`" (janela de 10 min, `PublicApiClientServiceImpl:81`) e, após expirar, o badge desaparece; revogação aparece como badge `revokedAt` com ações desabilitadas (o issuer já devolve 401 via Redis+DB). Nenhum hash ou plaintext é exibido em nenhum desses estados.

**Decisões do parecer (2026-11-22):** permissão = `AGENT_MANAGE` (reuso); path = `/v1/public-api/clients`; paginação = `page`/`size` (20 padrão, teto 100); `client_id` = gerado pelo servidor, imutável.

## Alternativas rejeitadas

| Alternativa | Motivo |
|---|---|
| Migrar para API keys `hk_` do ROADMAP Phase 3 | Quebra clientes; perde expiração/escopo nativos; ROADMAP é legado em relação ao código |
| Manter só provisionamento por CLI | Não atende ao pedido de gestão via UI |
| Expor endpoints admin sob `/v1/public/**` | Mistura superfície M2M com painel humano; a chain pública não tem sessão |
| Exibir `secret_hash` ou permitir recuperação do secret | Hash BCrypt não é recuperável; plaintext só no ato de criação/rotação |
| Criar permissão humana nova (ex.: `PUBLIC_API_MANAGE`) nesta ADR | Exige editar `SecurityConstants`, `SecurityTokenServiceImpl`, `RoleControllerV1` e `LoginIT`; como a lista de claims é hardcoded e igual para todo usuário (`:175-182`), não traria isolamento real sem redesenho de RBAC — dívida de segurança separada |
| Usar `/v1/public/**` ou `/manager/**` para o painel | Primeiro casa a chain M2M (sem sessão); segundo é `permitAll` em `SecurityConfig:85` |
| Reutilizar `admin/credentials/` para clients públicos | Aquela área gere credenciais de provider LLM; misturar domínios quebraria service/tipos/i18n |

## Interface congelada

- `POST /v1/public/auth/token` e `POST /v1/public/v1/chat/completions` — inalterados (ADR-001).
- Claims do JWT (`tenant-id`, `permissions`, `pcode`, `plock`, `plim`) e `aud=hive.public` — inalterados.
- Schema `publicapi_db` V1 — sem alteração nesta ADR; V2 pertence à ADR-004.
- `client_id` globalmente único, gerado pelo servidor e imutável; tenant derivado da linha e imutável.
- Envelope de erro OpenAI e contrato da ADR-001 — inalterados.
- Surface admin fixa: `/v1/public-api/clients` (e `/v1/public-api/clients/{clientId}`) na chain humana com `@HasPermission(AGENT_MANAGE)`; lista com `page`/`size` (20/100). `/v1/public/**` e `/manager/**` não são usados.
- Nenhuma credencial em listagem/get; plaintext apenas na resposta de create/rotate, exibido uma vez na UI; nenhum hash em field algum da API admin.

## Consequências

- **Positivas:** operadores autônomos para CRUD/rotação/revogação; menos dependência de CLI; rotação e revogação já suportadas pelo domínio.
- **Negativas:** nova superfície HTTP e nova tela, com risco de vazamento de secret se a resposta de create/rotate for mal tratada; mudança da assinatura de `PublicApiClientService.rotateSecret`; exige H3 e revisão de segurança. Uso de `AGENT_MANAGE` é mais amplo que o necessário até existir RBAC por capability — registrado como dívida, não como bloqueio.
- **Neutras:** contrato público, emissão de token e cota não mudam; nenhuma migration, nenhuma chave nova.

## Evidência exigida

ITs de CRUD/rotação/revogação, de que `rotateSecret` devolve o plaintext e de que secret/hash nunca aparecem em listagem/get; IT de 401 anônimo e de `AGENT_MANAGE` negado; IT de token com secret anterior dentro/fora da janela e de revogação imediata Redis+DB; H3 375/1280 com console/network limpos e screenshots; Vitest da nova View/Container; suíte completa sem regressão; zero diff em `Mfa*`, `workflow-state.json` e migrations; `code-reviewer` sem 🔴.
