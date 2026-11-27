# VERIFY — ADR-002 (2026-11-27)

Branch: `adr/streaming-reasoning/02-sse-endpoints` (empilhada sobre ADR-001, ainda não mesclado; sem commit/PR).

## Dimensão 1 — Completeness ✅

- 16 tickets SSE-01..CNT-16 com checkboxes marcados (25 checkboxes); 34 RF/NFR da SPEC rastreados em §13; nenhuma task N/A sem justificativa.
- Correção do review na iteração 1/3: posse do `StreamAdmission`, terminal no teto de frames, sync sem clamp, canário/SDK reais, persistência do assistant, header de cota, `handleBase`.

## Dimensão 2 — Correctness ✅

- `npm test` EXIT=0: frontend 421/421; `mvn -f backend/pom.xml test -q` EXIT=0: 699/699.
- ITs completos: 248/248, 0 falhas/erros/skips; security verify reexecutou 19/19 em 7 classes (canário, compat SDK real, concorrência, transporte/frame ceiling, persistência, disconnect, error frame).
- Code review: `APPROVED`, zero 🔴 (iteração 2/3).
- Security P4: `passed_with_findings`, zero 🔴, zero bloqueios; F1/F2/F5 fechados.
- Contrato medido: `SseEmitter(120_000L)` nos 2 controllers; `" [DONE]"` gera `data: [DONE]` literal; SDK OpenAI real (1.19.0) parseia `reasoning_content`/`content`/usage/`[DONE]` contra `HttpServer` local.
- Greps: zero `completeWithError`/`Flux`/reactor/`Failed.cause` em renderização; 2 `SseEmitter`; zero migration; zero diff de `TurnReply`.

## Dimensão 3 — Coherence ✅

- A1 `SseEmitter` (A2 não acionado); erro in-stream `OpenAiError` com `message=código`, `type=server_error`, `param=null`, `[DONE]` e `complete()`.
- Preflight único sync×SSE antes do 200; cota/concorrência por `tenantId+clientId`, `ChatRateLimiter`, teto de 2000 frames; `stream_options` aceito.
- `reasoning_content`/`event:thinking` separados de `content`/`event:text`; sem frames vazios quando não há thinking.
- Wire em `bootstrap/StreamingConfig`: `TurnPromptPolicy`, scheduler e `StreamingMetrics` como beans; fail-fast.
- Resposta do stream do assistant persistida (`saveAssistantAndTouch`) com trace, sem pensamento em JPA.

## Resultado

```text
VERIFY STATUS: APPROVED ✅ (com dívidas 🟡 registradas)

Observações (não bloqueantes):
- SEC-P4-E: PublicChatControllerV1:169 — turnStreamGateway.stream() sem try/catch; token de admissão vaza se lançar antes do handle (explorabilidade baixa/não demonstrada).
- SEC-P4-F: frame_limit fora do conjunto fechado de FailureClassifier e sem incremento em stream.failed — lacuna de telemetria.
- X-RateLimit-Remaining/Reset do assistant não expostos (commons/impl fora do escopo); apenas Limit derivado de ChatRateLimits.MAX_CALLS.
- Merge humano/PR ainda não realizado: PRD não pode ser movido para archive antes disso; branch 02 depende do merge de ADR-001.
```
