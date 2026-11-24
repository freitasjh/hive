# VERIFY — ADR-003: Cota própria da key e telemetria preservada

| Campo | Valor |
|---|---|
| **PRD** | `public-api-key-complementar` (`prd_version: 2`, §16 aprovado em 2026-11-23) |
| **ADR** | `ADR-003` — `ADRs/ADR-003-cota-telemetria/` |
| **Branch** | `adr/public-api-key-complementar/03-cota-telemetria` |
| **Abertura da ADR** | `84d764064b5a6de7ee5a277df038ab02efa71f37` |
| **Commit de implementação** | `01d28e9412cbbcccfd15edbf10fdc01279c6214a` |
| **Merge** | `6118a3a3b5caece92c90fc59a35e3bedfdadf9ed` — PR #13 em `origin/main` (2026-11-24) |
| **Data verificação** | 2026-11-24 |
| **Verificador** | sdd-orquestrador + `qa-engineer` (`ses_dff9a9429ffexOyBnXOoT0feUw`) |
| **Evidência harness** | `harness/public-api-key-complementar-adr-003.json` (`h6.status: PASS`, `mergeVerification: MERGE_CONFIRMADO`) |
| **Code review** | APPROVED · 0 🔴 / 0 🟠 / 0 🟡 — `CODE-REVIEW.md` neste diretório |

---

## 1. Completude

COT01..COT05 concluídas. 12 arquivos no commit (9 backend + CODE-REVIEW + harness + estado), nenhum alheio.

| Tarefa | Entrega real | Status |
|---|---|---|
| COT01 | baseline de cota e telemetria registrado | ✅ |
| COT02 | migration V4 com default 100, CHECK e backfill da cota do tenant | ✅ |
| COT03 | `acquireKey`, namespace `quota-key`, atributos só no ramo `sk-`, headers coerentes | ✅ |
| COT04 | `turnId`/`clientId` assertados nos dois caminhos | ✅ |
| COT05 | evidência H1–H6 e Brain | ✅ |

## 2. Corretude

| Verificação | Resultado |
|---|---|
| `npm test` (suiteFull do `harness.config.json`) | exit 0 · 411 front + 530 backend · 0/0/0 |
| `mvn verify -pl integration-test` (cwd `backend/`) | exit 0 · 152 ITs · `PublicApiKeyQuotaIT` 2/2 · `ChatCallerParityIT` 1/1 |
| `mvn install -DskipTests` | exit 0 · BUILD SUCCESS |
| `npm --prefix frontend run build` | exit 0 |
| `git diff --check` | limpo |
| Code review | APPROVED · 0 🔴 / 0 🟠 / 0 🟡 |

Gates: V4 em Postgres real; `keyKey = hive:public:quota-key:{keyId}` distinto de `quota-tenant`; Lua SHA `7f02dd6f433abdb0` idêntico; JWT limite 1 (2º request 429) e key limite 2 (3º request 429) no mesmo tenant; headers 1 e 2; `turnId`/`clientId` preenchidos; fail-open sem headers; `plim`/emissor/gateway/prompt/V2 sem diff.

**Veredito corretude: PASS.**

## 3. Coerência

- `ADR-INDEX.md` 1:1 com o diretório; `order 3` sem pulo; ADR-001 e ADR-002 `merged`; `depends_on: ADR-001, ADR-002`; `unit: adr`.
- Marcador `<!-- scope: adr/ADR-003 -->` na linha 1 de `TASKS.md`; `ADR.md` com `depende-de` e `interface congelada`; `SPEC.md`/`TASKS.md`/`CODE-REVIEW.md` presentes.
- PRD-01/ADR-01/ADR-02 PASS; órfão `chat-model-override` fora de escopo.
- Rastreabilidade RF1–RF4 ↔ COT01..COT05 ↔ ITs ↔ `harness/public-api-key-complementar-adr-003.json`.
- Merge provado por git: `merge-base --is-ancestor 01d28e9… origin/main` exit 0; artefatos presentes em `origin/main`.

**Veredito coerência: PASS.**

## 4. H3

N/A — sem UI nesta ADR; nenhuma mudança nas telas de admin da ADR-003 do PRD anterior.

## 5. Security Verify

Nenhuma chave/fluxo de autenticação alterado; namespace de cota isolado por key; `plim` do JWT intacto. Zero diff em `Mfa*`. Nenhuma credencial em log.

## 6. Riscos e débitos

- IT de backfill reaplica `UPDATE` manualmente em vez de provar a V4 populando linha pré-existente no instante da migration; SQL inspecionado correto.
- `EXPLAIN` do `OR`/índice não medido.
- `.gitignore:32` (`*credentials*`) — dívida pré-existente de repo.
- K12, crédito/budget, SEC-02 e `chat-model-override` fora do PRD.

## 7. Veredito final

**APPROVED — H1 ✅ · H2 ✅ · H3 N/A · H4 ✅ · H5 ✅ · H6 ✅.** Última ADR do PRD; habilita P6 (archive) após traceability ≥80%.

## 8. Resumo 10 pontos

1. Decisão: cota própria da key com telemetria preservada.
2. V4 com `key_chat_requests_per_minute` default 100 e backfill da cota do tenant.
3. Namespace Redis `quota-key:{keyId}` — sem colisão com JWT do mesmo tenant.
4. `AuthenticatedKey` com `keyId`/limite; atributos só no ramo `sk-`.
5. `acquireKey` para key, `acquireTenant` para JWT; `plim` intacto.
6. Headers derivados de `QuotaDecision`; fail-open omite.
7. `turnId`/`clientId` preenchidos nos dois caminhos.
8. 411 front + 530 backend + 152 ITs; build 0; review limpo.
9. Merge PR #13 em `6118a3a`, provado em `origin/main`.
10. Próximo: traceability e arquivamento do PRD `public-api-key-complementar`.
