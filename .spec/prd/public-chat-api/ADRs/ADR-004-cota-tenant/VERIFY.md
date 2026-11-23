# VERIFY — ADR-004: Cota por tenant e bugfix do 429 no token

| Campo | Valor |
|---|---|
| **PRD** | `public-chat-api` (`prd_version: 2`, §16 aprovado em 2026-11-21) |
| **ADR** | `ADR-004` — `ADRs/ADR-004-cota-tenant/` |
| **Branch** | `adr/public-chat-api/04-cota-tenant` |
| **Abertura da ADR** | `147a3efe9b40f477d6e4087be2cd6439b0c2fb9c` |
| **Commit de implementação** | `c15fbabade055a842b63f95daddf6806aee795a6` |
| **Merge** | `37f0cab4b5f597286f5241c5bf2eed866837a32b` — PR #7 em `origin/main` (2026-11-23) |
| **Data verificação** | 2026-11-23 |
| **Verificador** | sdd-orquestrador + `qa-engineer` (`ses_dff9a9429ffexOyBnXOoT0feUw`) |
| **Evidência harness** | `harness/public-chat-api-adr-004.json` (`h6.status: PASS`, `mergeVerification: MERGE_CONFIRMADO`) |
| **Code review** | APPROVED · 0 🔴 / 0 🟠 / 0 🟡 — `CODE-REVIEW.md` neste diretório |

---

## 1. Completude

QT01..QT06 concluídas. 17 arquivos no commit de implementação (14 backend + CODE-REVIEW + harness + estado), nenhum alheio.

| Tarefa | Entrega real | Status |
|---|---|---|
| QT01 | migration V2 com tabela de tenant, default 100, CHECK e backfill | ✅ |
| QT02 | `plim.r` no JWT com fallback 100 | ✅ |
| QT03 | `checkTenant` no chat, competição e isolamento por tenant | ✅ |
| QT04 | contrato do guard, headers `X-RateLimit-*`, 429 local no token | ✅ |
| QT05 | fail-open sem valores inventados e Lua intacto | ✅ |
| QT06 | evidência H1–H6 e Brain | ✅ |

**Veredito completude: PASS.**

## 2. Corretude

| Verificação | Resultado |
|---|---|
| `npm test` (suiteFull do `harness.config.json`) | exit 0 · 411 front + 526 backend · 0 falhas/erros/skips |
| `mvn verify -pl integration-test` (cwd `backend/`) | exit 0 · 133 ITs · `PublicApiTenantQuotaIT` 4/4 · `PublicAuthQuotaContractIT` 2/2 |
| `mvn install -DskipTests` | exit 0 · BUILD SUCCESS |
| `npm --prefix frontend run build` | exit 0 |
| `git diff --check` | limpo |
| Code review | APPROVED · 0 🔴 / 0 🟠 / 0 🟡 |

Gates funcionais medidos: Flyway `version=2 success=true`, tabela com PK `tenant_id`, `DEFAULT 100` e `CHECK 1..10000`; `plim.r=250` com linha e fallback 100 sem linha; dois clients do mesmo tenant com limite 1 → 1º 200, 2º 429 com `Retry-After` e headers; tenants isolados; handler local do token devolve 429 com envelope `quota_exceeded`; headers medidos no sucesso/429 e omitidos em fail-open; bloco Lua com 1265 bytes e SHA `7f02dd6f433abdb0` idêntico entre HEAD e worktree.

**Veredito corretude: PASS.**

## 3. Coerência

- `ADR-INDEX.md` 1:1 com o diretório; `order 4` sem pulo (001/002/003 `merged`); `depends_on: ADR-001`; `unit: adr`.
- Marcador `<!-- scope: adr/ADR-004 -->` na linha 1 de `TASKS.md`; `ADR.md` com `depende-de` e `interface congelada`; `SPEC.md` e `TASKS.md` presentes.
- PRD-01/ADR-01/ADR-02 PASS; único achado global é o órfão pre-existente `chat-model-override/PROPOSAL.md`, fora de escopo.
- Superscrição do trecho da ADR-001 (400 → 429 no token) registrada explicitamente sem editar a ADR-001; `VERIFY.md` da ADR-001 permanece válido.
- Rastreabilidade RF1–RF5 ↔ QT01..QT06 ↔ ITs ↔ `harness/public-chat-api-adr-004.json`.
- Merge provado por git: `merge-base --is-ancestor c15fbab… origin/main` exit 0; artefatos presentes em `origin/main`.

**Veredito coerência: PASS.**

## 4. H3

N/A — sem UI nesta ADR; nenhuma mudança na UI da ADR-003.

## 5. Security Verify

Nenhuma chave ou fluxo de autenticação alterado; `plim.r` é claim de política, não segredo. Fail-open preservado. Zero diff em `Mfa*`. Nenhuma credencial nova em fixtures.

## 6. Débitos registrados

- Falta IT de omissão dos `X-RateLimit-*` no HTTP quando Redis cai (unitário já prova `measured=false`).
- Falta IT de staleness do `plim.r` (comportamento aceito pela ADR).
- Crédito/budget e custo de ocupação (K12) permanecem fora do PRD.
- Órfão pre-existente `chat-model-override/PROPOSAL.md`.

## 7. Veredito final

**APPROVED — H1 ✅ · H2 ✅ · H3 N/A · H4 ✅ · H5 ✅ · H6 ✅.** ADR-004 pronta para `merged`. PRD permanece aberto: ADR-005 pendente.

## 8. Resumo 10 pontos

1. Decisão do operador: cota por tenant compartilhado, default 100 req/min.
2. Migration V2 em `publicapi_db`, sem `repair()`, sem `DROP` da coluna antiga.
3. `plim.r` é snapshot no JWT, com fallback 100 para tokens antigos.
4. Chat trocou `checkClient` por `checkTenant`; Lua e fail-open intactos.
5. Headers `X-RateLimit-*` medidos; omitidos em fail-open.
6. Bugfix do 429 no token com envelope OpenAI, superando o 400 da ADR-001 sem editá-la.
7. 411 front + 526 backend + 133 ITs, build limpo.
8. Review APPROVED 0/0/0; dois débitos de cobertura registrados.
9. Merge PR #7 em `37f0cab`, provado em `origin/main`.
10. Próximo e último: ADR-005 (fronteira `ai/api` × `assistant/impl` × `publicapi/impl`).
