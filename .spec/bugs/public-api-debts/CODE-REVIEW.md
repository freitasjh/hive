# CODE-REVIEW — Bugfix débitos 1–3 da API pública

**Data:** 2026-11-23 · **Branch:** `fix/public-api-debts` (de `main` @ `9989e16`) · **Árvore:** sem commit no momento do veredito.

## Veredito

**APPROVED** — 0 🔴 · 0 🟠 · 0 🟡 · 0 🔵 · `must_fix_before_done: []`. `reviewer_session`: `ses_dffa34effffeZMrCoNwL2t4pqv`.

## Itens revisados

| Item | Mudança | Resultado |
|---|---|---|
| 1 — N+1 | `findByClientRefIn` + agrupamento por `clientRef` em `PublicApiClientAdminServiceImpl.list()`; `fill()` sobrecarregado sem regra duplicada | ✅ 42/202 → 4 queries (size 20/100), contrato e ordem preservados |
| 2 — fail-open | IT novo `PublicApiQuotaFailOpenIT` | ✅ sucesso e negação sem `X-RateLimit-*`; `retry-after=7` preservado |
| 3 — staleness | IT novo `PublicApiPlimStalenessIT` | ✅ JWT real via issuer, DB real; antigo `250` após DB `1`, novo `1` |

## Guardas

- R1 documentada antes da edição, com números medidos e arquivo:linha; nenhuma task marcada como reproduzida sem medição.
- Itens 2–3 sem mudança de produção — somente ITs; nenhum `@Disabled`, `assumeTrue` ou mock que esconda o caminho HTTP observado (mock apenas na fronteira do guard, cujo fail-open é provado em `RedisQuotaGuardTest`).
- H1/H2: pré 411 front + 526 backend + 133 ITs; pós 411 + 526 + 138 ITs (+5); build 0; `git diff --check` limpo.
- Zero diff em `.spec/archive/**`, ADRs, PRD, MFA, `workflow-state.json`, AGENTS.md, skills, configs, frontend; nenhuma dependência nova; nenhum teste apagado; `.flattened-pom.xml` limpos.
- R2: portas 8080/5432/5433/6379 encerradas por PID, sem `pkill -f`. R4: `brain_store` `public-api-debts-itens1-3-bugfix-2026-11-23`.

## QA independente

`harness/public-chat-api-debts-1-3.json`: H1 ✅ · H2 ✅ · H3 N/A · H4 ✅ · H5 ✅ · **H6 ✅**. Novos ITs 2/2 + 2/2 + 1/1.

## Risco residual

Não foi exercitada queda física do Redis no IT de fail-open (decisão `measured=false` induzida por `@MockitoBean`); o caminho HTTP é real e o guard é coberto em unit. `findByClientRefIn` com página vazia gera `IN ()` — Hibernate/Postgres resolve como `1=0`, mas não exercitado.

## Ónus de governança

Este parecer é parecer; o bloqueio é a âncora B do `gate.js` lendo `code_review_status`. Registro escopado a ser gravado no `workflow-state.json` no fechamento do bugfix, sem alterar o registro da Fase 4 MFA.
