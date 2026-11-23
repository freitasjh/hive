# Traceability Report — public-chat-api

**Data:** 2026-11-23 · **Régua:** `.agents/skills/sdd-compiler/asserts/traceability-gate.md` · **Escopo:** PRD inteiro, suas 5 ADRs `merged`.

## PRD → ADR

| RF do PRD | ADR | Tarefas | Evidência |
|---|---|---|---|
| RF1 — contrato tipado | ADR-001 | CT03 | VERIFY ADR-001 §1/§2 |
| RF2 — spec OpenAPI | ADR-001 | CT01, CT02, CT04 | VERIFY ADR-001 §2 (27 paths/46 schemas) |
| RF3 — ordem do SystemMessage e tokens | ADR-002 | CZ01, CZ03 | VERIFY ADR-002 §2 (120/340/460/STOP) |
| RF4 — turnId/clientId na trace | ADR-002 | CZ04 | VERIFY ADR-002 §2 |
| RF5 — UI admin de credenciais | ADR-003 | ADM01, ADM02, ADM03, ADM04 | VERIFY ADR-003 §2/§4 (H3 verde) |
| RF6 — cota por tenant | ADR-004 | QT01, QT02, QT03 | VERIFY ADR-004 §2 (migration + competição) |
| RF7 — 429 e headers de rate limit | ADR-004 | QT04, QT05 | VERIFY ADR-004 §2 |

7/7 RFs do PRD com ADR, tarefa e código. Zero órfãos.

## Spec → Code por ADR

| ADR | RFs | Tasks | RF→Task | Task→Code | Orphaned specs | Orphaned code |
|---|---|---|---|---|---|---|
| 001 | 5 | 5 (CT01..CT05) | 5/5 | 5/5 | 0 | 0 |
| 002 | 4 | 5 (CZ01..CZ05) | 4/4 | 5/5 | 0 | 0 |
| 003 | 6 | 6 (ADM01..ADM06) | 6/6 | 6/6 | 0 | 0 |
| 004 | 5 | 6 (QT01..QT06) | 5/5 | 6/6 | 0 | 0 |
| 005 | 3 | 5 (MOD01..MOD05) | 3/3 | 5/5 | 0 | 0 |
| **Total** | **23** | **27** | **23/23** | **27/27** | **0** | **0** |

Toda task possui seção 1:1 em `TASKS.md`, cenários de sucesso/falha/borda e evidência de execução no `harness/*.json` correspondente.

## Code → Spec (módulos principais)

| Módulo/área | RF | Status |
|---|---|---|
| `publicapi/web/internal/v1` + DTOs | RF1, RF2, RF5 | ✅ |
| `publicapi/api` + `PublicClientTokenIssuer` | RF6, RF7 | ✅ |
| `publicapi/impl` (serviço, cota, gestão) | RF5, RF6, RF7 | ✅ |
| `publicapi/dbupdate` (V1/V2) | RF5, RF6 | ✅ |
| `assistant/api` + `assistant/impl` + `history/api` | RF3, RF4 | ✅ |
| `frontend/src/modules/hive/admin/public-clients/` | RF5 | ✅ |
| `frontend/scripts/h3-mock-api.mjs` | RF5 (fixture de H3) | ✅ infra (ok) |
| `backend/security/**/Mfa*`, configs de harness | — | ⚠️ infra (ok) |

## Score

- RF→Task: **100%** (23/23)
- Task→Code: **100%** (27/27)
- Orphaned specs: **0**
- Orphaned code: **0** (apenas infra/comum listada)
- **Overall: 100% ✅ PASS** — acima do mínimo de 80%; archive liberado.

## Gaps

Nenhum dentro do PRD. Fora de escopo, registrados: K12, SEC-02, crédito/budget, `chat-model-override/PROPOSAL.md` órfão pre-existente, dívida N+1 da ADR-003 e 2 débitos de cobertura da ADR-004.
