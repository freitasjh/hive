# VERIFY — ADR-001 (2026-11-27)

Branch: `adr/streaming-reasoning/01-provider-streaming` (sem commit/PR; merge humano pendente).

## Dimensão 1 — Completeness ✅

- 16 tickets PS-01..SEC-16 com checkboxes marcados (35 checkboxes); 34 RF/NFR da SPEC rastreados em §13; nenhuma task N/A sem justificativa.
- Testes escritos antes da implementação em todos os tickets (evidência do handoff, com compile-fail/falha comportamental prévia).
- Correções do review: CR-01, CR-02, AT-01..AT-04 aplicadas na iteração 1/3.

## Dimensão 2 — Correctness ✅

- `npm test` EXIT=0: frontend 421/421; `mvn -f backend/pom.xml test -q` EXIT=0: 698/698, 0 erros/skips.
- ITs reais (`-Pintegration`, PostgreSQL 16 + Redis 7 via Testcontainers): 17/17 nas 8 classes; security verify reexecutou 9/9 (canário, cancel, evicção, métricas, persistência).
- Code review: `APPROVED`, zero 🔴 (iteração 2/3).
- Security P4: `passed`, zero 🔴 (reavaliado em 2026-11-27).
- Verificações estáticas: `lockFor`=0; `Semaphore(1, true)`=1; `gate.release()` só em `releaseGateOnce()`; `ReentrantLock` novo=0; `getMessage()`/`printStackTrace()` no caminho SSE=0; `Flux`/reactor/`reasoning_tokens`=0; diff de `dependencies/pom.xml`=0; zero migration.

## Dimensão 3 — Coherence ✅

- Interface congelada do ADR §4 + Emendas 1/2 presente: `LlmStreamingClientFactory`, `AssistantStreamDefault`, `TurnStreamEvent`, `TurnStreamGateway`, `StreamingTurnHandle`, `SessionGateRegistry`, `TurnPromptPolicy`.
- `ai/**` folha (zero imports de `assistant|publicapi|history`); sem aresta `impl→impl`; `TurnReply` sem texto de resposta.
- R1 degradado retém gate até terminal/janela de 5 s; suportado libera na hora (S24); evicção com refcount atômico.
- `TurnPromptPolicy` implementada por `AssistantTurnGateway` reusando `PROMPT`/`systemFor` — sem duplicação em `ai/**`.

## Resultado

```text
VERIFY STATUS: APPROVED ✅

Observações (não bloqueantes):
- Wire de produção do gateway/scheduler/metrics fechado no ADR-002.
- F2/F5/F1-residual transferidos como requisitos do ADR-002 e lá verificados.
- Merge humano/PR ainda não realizado: pré-condição para status merged no ADR-INDEX.
```
