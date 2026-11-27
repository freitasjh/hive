---
bug_id: chat-sse-consumer
type: bugfix
phase: BugAnalysis
date: 2026-11-27
severity: medium
scope: frontend/src/modules/hive/chat + frontend/src/services
---

# Análise — chat não consome SSE (thinking/texto por partes)

## R1 — Reprodução

Com backend UP (`/hive/actuator/health` 200) e frontend em `:5173`, enviar mensagem no chat: aparece apenas a resposta completa, de uma vez; nenhum bloco de pensamento é exibido. `curl -X POST http://127.0.0.1:8080/hive/v1/chat/stream` sem auth devolve 401 (endpoint existe; não é 404). `curl` em `/hive/v1/chat` também 401 — mesma proteção.

## R2 — Portas e estado

- Backend: `POST /v1/chat/stream` em `assistant/web/internal/v1/.../ChatControllerV1.java:71-74`, `produces=text/event-stream`, eventos nomeados `thinking`/`text`/`done`/`error`, `SseEmitter(120_000L)`. Mergeado em `0b60b1c` (PR #21).
- Backend não é a causa: ITs 248/248, `OpenAiSdkStreamingCompatIT` valida o wire com SDK real; health 200 após `mvn install -DskipTests` (jar `~/.m2` estava stale —lição registrada no Brain).
- Frontend: `chat.service.ts:62` usa `apiRequest("/chat", {method:"POST"})` e retorna `unwrapData<ChatResponseDTO>`; `use-chat-controller.ts:288` faz `await chatService.sendMessage(...)` e só então monta a mensagem assistant com `answer.message` completo.
- `grep` em `frontend/src`: zero `getReader(`, `EventSource`, `text/event-stream`, `reasoning_content`. Ou seja, consumidor SSE inexistente.
- UI já tem `Reasoning` (`ai-elements.tsx:250`) e `ai-workspace-view.tsx:918` usa `message.reasoning` — não é preciso criar componente novo.
- Causa raiz: F7/débito do PRD `streaming-reasoning` — frontend ficou explicitamente fora do escopo ("outra sessão"). Não é regressão de código; é entrega incompleta.

## R3 — Correção planejada

1. `api.ts`: helper de streaming com `fetch`, Bearer/`X-Tenant-ID`/cookie via mesma política de refresh single-flight, `AbortSignal` e parse de frames SSE (`event:` + `data:`).
2. `chat.service.ts`: `streamMessage(input, defaults, handlers)` → eventos `thinking`, `text`, `done`, `error`; manter `sendMessage` como fallback.
3. `use-chat-controller.ts`: criar placeholder assistant, anexar `text` incrementalmente, alimentar `reasoning` do placeholder, finalizar no `done` com `sessionId`/`timestamp`/`provider`/`model`.
4. Cancelamento: `AbortController` ligado ao desmontar/troca de turno; backend cancela via `onCompletion`/`onError`/`onTimeout`.
5. Nenhum token em URL; `EventSource` proibido; sem dependência nova.

## R4 — Aceite e brain

- Thinking visível durante a geração; texto cresce por chunks; `done` finaliza; `error` sem `Throwable`.
- Testes: parser SSE (unidade), hook incremental, UI 375/1280, console/network limpos.
- Brain: `regras/projetos/projeto/hive/streaming-reasoning-evidence-override-2026-11-27` (dívida F7 original).
