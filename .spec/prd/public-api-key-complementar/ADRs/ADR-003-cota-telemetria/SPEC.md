# SPEC — ADR-003: Cota própria da key e telemetria preservada

> Escopo: dar à key um limite de chat próprio e provar que headers e trace permanecem coerentes. Nenhuma mudança no caminho JWT.

## 1. ADRs

### ADR-003 (este ciclo)

- **Decision (fechada 2026-11-24):** V4 adiciona `key_chat_requests_per_minute` (default 100, CHECK 1..10000, backfill copiando a cota do tenant); `AuthenticatedKey` ganha `keyId`/`keyChatRequestsPerMinute`, o filtro seta `publicapi.keyId`/`publicapi.keylim` só no ramo key; `PublicChatServiceImpl` usa `acquireKey` para key e `acquireTenant` para JWT; namespace Redis `hive:public:quota-key:{keyId}`; headers e telemetria inalterados.
- **Context:** PRD §7 exige cota própria da key. Hoje a key usa `plim.r` do tenant e compartilharia o contador `quota-tenant:{tenantId}` com o JWT. V1/V2/V3 ocupam o histórico; `QuotaDecision` já carrega o limite aplicado e o controller já emite os headers.
- **Trade-offs:** mais uma migration, dois campos na porta e dois atributos de request versus cota própria sem tocar `plim`/emissor/gateway.

ADRs afetados: ADR-001 e ADR-002 deste PRD (ambas merged). ADR-004 do PRD arquivado (cota por tenant) permanece intocada.

## 2. Domain Context, Tenant Strategy & Provisioning

- **Bounded Context:** `publicapi` dono da key, da cota da key e do guard; `security` apenas propaga os atributos no filtro; `history`/`assistant` não participam.
- **Tenant:** continua vindo da linha; a key é ligada ao client. Cada key tem contador Redis próprio por `keyId`, distinto do contador do tenant.
- **Provisionamento:** limite persistido na linha da key (default 100); keys existentes recebem o valor da cota do tenant via backfill, com fallback 100 se o tenant não tiver linha em V2.

## 3. Identity & Access Management

- `client_credentials` e JWT inalterados. A key não muda autenticação, apenas o limite aplicado.

## 4. Security, Key Management & Disaster Recovery

- Nenhuma credencial em log; coluna não expõe segredo. Fail-open preservado: `measured=false` ⇒ atributos `publicapi.quota.*` não gravados e headers omitidos, sem valores inventados.
- `publicapi.keyId`/`publicapi.keylim` existem somente no ramo `sk-`; o ramo JWT continua com os atributos atuais.

## 5. Data Model & Concurrency

| Item | Definição |
|---|---|
| Migration | **V4__public_api_client_key_chat_rpm.sql** em `publicapi_db` (próxima livre após V1/V2/V3) |
| Coluna | `key_chat_requests_per_minute INT NOT NULL DEFAULT 100`, `CHECK BETWEEN 1 AND 10000` |
| Backfill | `COALESCE((SELECT chat_requests_per_minute FROM public_api_tenant_quota WHERE tenant_id = k.tenant_id), 100)` — preserva o limite efetivo atual |
| Nomes | explícitos `key_*`; vedado `limit`/`quota`/`rate_limit` |
| Histórico | `baselineOnMigrate(true)` e **sem `repair()`**; sem `DROP`; V1/V2/V3 intactas |
| Redis | **`hive:public:quota-key:{keyId}`** (`keyKey`), distinto de `hive:public:quota-tenant:{tenantId}`; Lua, sufixo e fail-open inalterados |

## 6. Event Model, Integrations & Messaging Isolation

- Nenhum evento ou integração nova.

## 7. Invariants & Property-Based Testing

| Tipo | Invariante | Verificação |
|---|---|---|
| Cota | key X e JWT Y no mesmo tenant aplicam limites e contadores distintos | IT comparativo |
| Namespace | `quota-key:{keyId}` ≠ `quota-tenant:{tenantId}`; JWT não é afetado | IT de isolamento + `grep` da chave |
| Headers | `X-RateLimit-*` refletem o limite aplicado (`QuotaDecision.limit()`) | IT de headers |
| Fail-open | `measured=false` omite atributos e headers, sem valores inventados | IT existente reexecutado |
| Telemetria | `turnId`/`clientId` preenchidos nos dois caminhos | `ChatCallerParityIT` + IT de trace da key |
| JWT | `plim` m/c/t/r e `aud` inalterados; emissor sem diff | IT de contrato do emissor |
| Grafo | `ChatTurnGateway`/`TurnRequest`/`TurnReply`, `TurnOutcomeMapper` e regras de prompt sem diff | `git diff --name-only` |

## 8. Requisitos Funcionais

- **RF1** — V4 com `key_chat_requests_per_minute`, CHECK 1..10000 e backfill copiando a cota do tenant (fallback 100).
- **RF2** — propagar `keyId`/limite via `AuthenticatedKey` e atributos `publicapi.keyId`/`publicapi.keylim` só no ramo key; `acquireKey` para key, `acquireTenant` para JWT.
- **RF3** — headers `X-RateLimit-*` coerentes com `QuotaDecision`, omitidos em fail-open.
- **RF4** — telemetria `turnId`/`clientId` preservada no caminho da key, sem tocar o gateway.

## 9. Requisitos Não-Funcionais

- **NFR1** — suíte 0 falhas, nenhum teste apagado/`@Disabled`.
- **NFR2** — zero diff em ADR-001/002, PRD/RESEARCH, arquivadas, MFA, `workflow-state.json`, UI, cota por tenant e prompt.
- **NFR3** — nenhuma dependência nova; comandos de `harness.config.json`/`AGENTS.md`.
- **NFR4** — portas por PID (lsof+kill), nunca `pkill -f`.
- **NFR5** — nenhuma aresta de módulo/dependência nova; nenhuma mudança em `plim`, emissor de JWT, gateway, trace, prompt, cota por tenant (V2) ou UI.

## 10. Critérios de Aceite

- Migration V4 aplicada em Postgres real: `key_chat_requests_per_minute`, default 100, CHECK 1..10000, backfill copiando V2 com fallback 100; sem `repair()`/`DROP`.
- IT: key X e JWT Y no mesmo tenant → contadores Redis distintos (`quota-key` vs `quota-tenant`).
- IT: headers refletem `QuotaDecision.limit()`; fail-open (`measured=false`) omite headers.
- IT: `turnId`/`clientId` preenchidos no caminho da key; `ChatCallerParityIT` verde; `plim` e emissor sem diff.
- Suíte 0 falhas; review sem 🔴; H6 verde.

## 11. Riscos

| Risco | Mitigação |
|---|---|
| Colisão Redis tenant/key | namespaces distintos: `quota-key:{keyId}` vs `quota-tenant:{tenantId}`; IT no mesmo tenant |
| Default para keys antigas | backfill copia V2 com fallback 100; IT de migração em banco real |
| Divergência entre claim e coluna | `AuthenticatedKey` é a única fonte da key; `plim` permanece só no JWT |
| Atributo vazando para o ramo JWT | filtro seta `keyId`/`keylim` apenas em `:67-94`; IT de JWT com `plim` inalterado |
| Fail-open inventando valores | `QuotaDecision.measured=false` não grava `publicapi.quota.*`; IT existente |

## 12. Fontes

Internas (revisão 2026-11-24): `PRD.md` §7, `RESEARCH.md` §5/§6, `ADR-001-formato-bearer`, `ADR-002-emissao-rotacao` (ambas merged); `PublicChatServiceImpl.java:112-146,180-189`; `PublicApiSecurityFilter.java:67-94`; `PublicApiKeyAuthenticator.java`; `PublicApiKeyAuthenticatorImpl.java:45-89`; `RedisQuotaGuard.java:130-190`; `QuotaDecision.java`; `PublicChatControllerV1.java:155-170`; `TurnOutcomeMapper.java`; `V2__public_api_tenant_quota.sql`, `V3__public_api_client_key.sql`; `PublicApiDbUpdateConfig.java:17-21`. Nenhuma fonte externa — decisão interna de fronteira.
