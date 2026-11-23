# CODE-REVIEW — ADR-004 (cota por tenant e bugfix 429)

**Data:** 2026-11-22 · **Branch:** `adr/public-chat-api/04-cota-tenant` · **Árvore:** sem commit no momento do veredito.

## Veredito

**APPROVED** — 0 🔴 · 0 🟠 · 0 🟡 · 0 🔵 · `must_fix_before_done: []`. `reviewer_session`: `ses_dffa34effffeZMrCoNwL2t4pqv`.

## Guardas verificadas

| # | Guarda | Resultado |
|---|---|---|
| 1 | V2: `tenant_id PK`, `DEFAULT 100`, `CHECK 1..10000`, backfill `DISTINCT ... ON CONFLICT DO NOTHING`, sem `DROP`; `PublicApiDbUpdateConfig` sem diff (baselineOnMigrate, sem repair); V1 intocada; IT em banco real | ✅ |
| 2 | Lua byte-a-byte idêntico ao HEAD (SHA `7f02dd6f433abdb0d0b9`, 1265 bytes); sufixo aleatório preservado; mudança restrita ao contrato interno do guard para devolver decisão | ✅ |
| 3 | `plim.r` com fallback 100; `m/c/t`, `aud`, `tenant-id` inalterados; tokens antigos sem `r` caem no fallback | ✅ |
| 4 | Dois clients do mesmo tenant competem (1º 200, 2º 429); tenants isolados; `checkClient` fora do chat; `checkTokenEndpoint` com 30/120/30 inalterado | ✅ |
| 5 | Headers `X-RateLimit-Limit/Remaining/Reset-Requests` no 429 e no sucesso quando medidos; omitidos em fail-open; `Retry-After` preservado; handler 429 local no token com envelope OpenAI e precedência sobre advice; chat handler intacto | ✅ |
| 6 | Zero diff em poms, ADR-001/002/003, UI da ADR-003, `Mfa*`, `workflow-state.json`, V1, `PublicApiDbUpdateConfig`, `.flattened-pom.xml`; nenhum teste apagado ou `@Disabled`; evidência coerente (411 front, 523→526 backend, 129→133 ITs, build 0) | ✅ |

## Mudança de contrato registrada

Os 2 testes de `PublicAuthQuotaContractIT` foram invertidos de 400 → 429 (inclusive guard de reflexão). Trata-se da correção prevista na Decisão 7 da ADR-001 e formalizada na ADR-004 — não é remoção de teste para esconder falha. A ADR-001 não foi editada; seu `VERIFY.md` permanece válido.

## Débitos registrados (não bloqueantes)

1. IT de omissão dos `X-RateLimit-*` no HTTP quando Redis cai — o unitário `RedisQuotaGuardTest` já prova `measured=false`, mas falta a asserção na resposta.
2. IT de staleness do `plim.r`: token emitido antes de alterar `chat_requests_per_minute` mantém o valor antigo até reemissão (comportamento aceito pela ADR, sem teste de regressão).

## Bloqueio de âncora

O bloqueio real é a âncora B do `gate.js` lendo `code_review_status`. Este artefato e o registro escopado `workflow-state.json.code_reviews_by_adr.ADR-004` tornam o veredito verificável sem alterar `current_phase` nem o registro da Fase 4 MFA.
