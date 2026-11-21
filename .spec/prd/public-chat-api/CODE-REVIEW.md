# CODE-REVIEW — ADR-001 (contrato tipado e OpenAPI)

**Data:** 2026-11-21 · **Branch:** `adr/public-chat-api/01-contrato-openapi` · **Árvore:** sem commit no momento do veredito.

## Veredito

**APPROVED** — 0 🔴 · 0 🟠 · 0 🟡 · 0 🔵 · `must_fix_before_done: []`.

Duas rodadas: revisão inicial (3 🟡) e re-review após aplicação das 3 melhorias. `reviewer_session`: `ses_dffa34effffeZMrCoNwL2t4pqv`.

## Escopo revisado

`backend/dependencies/pom.xml` (springdoc 3.2.0), `SecurityConfig.java`, `backend/publicapi/**` (controllers, DTOs, service, erro), `backend/bootstrap/**` (springdoc config/alias), `OpenApiSpecIT`, ITs de contrato e guia da ADR (`ADR.md`, `SPEC.md`, `TASKS.md`).

## Guardas verificadas

| # | Guarda | Resultado |
|---|---|---|
| 1 | Decisão 7 — nenhum `@ExceptionHandler(QuotaExceededException)` em `PublicAuthControllerV1`; cota no token → 400 `invalid_request` sem `Retry-After`; 429 do chat intacto | ✅ |
| 2 | Decisão 6 — apenas `/v3/**`, `/swagger-resources/**`, `/scalar/**` removidos do `permitAll`; 401 anônimo e 200 autenticado | ✅ |
| 3 | springdoc 3.2.0 com deepsearch (URL + data); path canônico `/v3/api-docs/public` e alias `?group=public` cobertos | ✅ |
| 4 | DTOs tipados sem drift de serialização: envelope `{message,type,param,code}`, `param:null`, `choices`, `usage`, `metadata.hive`, snake_case, duplo `/v1`, `model`, `stream`, `role:system` | ✅ |
| 5 | Duplicata `ChatCompletionRequestDTO` removida; `OpenAiErrorResponse` local sem refs | ✅ |
| 6 | Caracterização antes/depois, ITs reais, nenhum teste desabilitado, guard de reflexão e group alias | ✅ |
| 7 | Higiene: zero diff em `Mfa*`, `workflow-state.json`, `.flattened-pom.xml`; nenhuma credencial nova; `git diff --check` limpo | ✅ |

## Melhorias aplicadas entre as rodadas

1. `OpenApiSpecIT` — teste do path canônico `/v3/api-docs/public` com sessão humana (200 + 2 paths + schemas) e sem sessão (401).
2. `OpenApiSpecIT` — baseline com asserções reais em `pathNames` e `schemas`, não apenas log.
3. `PublicChatServiceImpl` — remoção do alias redundante `req`; parâmetro tipado `request` usado em todas as referências.

## Evidência declarada (implementador) e conferida estaticamente pelo reviewer

`npm test` exit 0 (378 front + 520 backend); `mvn verify -pl integration-test` 119 ITs exit 0; `OpenApiSpecIT` 9/9; `mvn install -DskipTests` exit 0; `git diff --check` limpo. O reviewer não reexecutou suíte por restrição de papel; a reexecução independente foi feita pelo `qa-engineer` em `harness/public-chat-api-adr-001.json`.

## Bloqueio de âncora

O bloqueio real não é o parecer, é a âncora B do `gate.js` lendo `code_review_status` no estado. Este artefato + o registro escopado em `workflow-state.json.code_reviews_by_adr.ADR-001` tornam o veredito verificável sem alterar `current_phase`.
