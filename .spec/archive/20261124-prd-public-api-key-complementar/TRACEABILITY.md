# Traceability Report — public-api-key-complementar

**Data:** 2026-11-24 · **Régua:** `.agents/skills/sdd-compiler/asserts/traceability-gate.md` · **Escopo:** PRD inteiro, 3 ADRs `merged`.

## ADR → RF → Task → Código

| ADR | RFs | Tasks | Status |
|---|---|---|---|
| ADR-001 — formato Bearer | RF1–RF3 | KEY01–KEY04 | merged (`3a891e7`) |
| ADR-002 — emissão/rotação | RF1–RF4 | KEY2-01–KEY2-05 | merged (`aae4379`) |
| ADR-003 — cota/telemetria | RF1–RF4 | COT01–COT05 | merged (`6118a3a`) |

| RF | ADR | Task | Código | Status |
|---|---|---|---|---|
| ADR1-RF1 — aceitar `sk-` | 001 | KEY02 | `PublicApiSecurityFilter` | ✅ |
| ADR1-RF2 — 401 formato desconhecido | 001 | KEY02 | `PublicApiKeyAuthenticator`/filtro | ✅ |
| ADR1-RF3 — spec com dois esquemas | 001 | KEY03 | `OpenApiConfig`/`PublicChatControllerV1` | ✅ |
| ADR2-RF1 — bean da porta | 002 | KEY2-02 | `PublicApiKeyAuthenticatorImpl` | ✅ |
| ADR2-RF2 — emissão one-shot | 002 | KEY2-03 | `PublicApiKeyAdminServiceImpl`/controller | ✅ |
| ADR2-RF3 — rotação com janela | 002 | KEY2-04 | `PublicApiKeyAdminServiceImpl` + V3 | ✅ |
| ADR2-RF4 — revogação imediata | 002 | KEY2-04 | Redis `hive:public:revoked-keys` | ✅ |
| ADR3-RF1 — limite por key | 003 | COT02 | V4 + entidade da key | ✅ |
| ADR3-RF2 — propagação do limite | 003 | COT03 | `AuthenticatedKey`/filtro/`PublicChatServiceImpl` | ✅ |
| ADR3-RF3 — headers coerentes | 003 | COT03 | `QuotaDecision`/controller | ✅ |
| ADR3-RF4 — telemetria preservada | 003 | COT04 | `PublicChatServiceImpl`/`TurnOutcomeMapper` (sem diff) | ✅ |

11/11 RFs com task e código. 14/14 tasks com código e evidência em `harness/*.json`.

## Code → Spec

| Módulo/área | RF | Status |
|---|---|---|
| `PublicApiSecurityFilter` + porta | ADR1-RF1/2, ADR3-RF2 | ✅ |
| `OpenApiConfig` + controllers públicos | ADR1-RF3 | ✅ |
| `PublicApiKeyAuthenticatorImpl` + repositório + V3 | ADR2-RF1 | ✅ |
| Admin de keys + DTOs | ADR2-RF2/3/4 | ✅ |
| V4 + `RedisQuotaGuard.acquireKey` + `PublicChatServiceImpl` | ADR3-RF1/2/3 | ✅ |
| `ChatTrace`/telemetria (sem diff, comportamento verificado) | ADR3-RF4 | ✅ |
| Fixtures/ITs de infra | — | ⚠️ infra (ok) |

## Score

- RF→Task: **100%** (11/11)
- Task→Code: **100%** (14/14)
- Orphaned specs: **0**
- Orphaned code: **0** (apenas infra/comum)
- **Overall: 100% ✅ PASS** — acima do mínimo de 80%; archive liberado.

## Gaps

Nenhum dentro do PRD. Fora de escopo, registrados: `.gitignore:32`, EXPLAIN do `OR`, cobertura do backfill no instante da migration, K12/crédito, SEC-02, `chat-model-override`.
