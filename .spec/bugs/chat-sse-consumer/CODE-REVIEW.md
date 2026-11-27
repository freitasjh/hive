# CODE-REVIEW — chat-sse-consumer (2026-11-27)

- **approval_status:** APPROVED
- **status:** passed
- **critical_issues_count:** 0
- **reviewer_session:** `ses_de1d7df57ffeyl8T7YTBgpr18s`
- **iterações:** 3/3

## Escopo

Branch `fix/chat-sse-consumer` (partiu de `origin/main` = `0b60b1c`): `frontend/src/**`, `ai/api` (`TurnStreamEvent.Done`), `assistant/impl`, `assistant/web/internal/v1`, ITs.

## Correções aplicadas nas 3 iterações

- I1: 🔴 `done` sem `sessionId` → Emenda 3 dos ADRs + `Done(FinishReason, TokenUsage, UUID)` com construtor de 2 args; `AssistantSseSerializer.done` emite `sessionId` quando não nulo; `DoneSessionIdIT` 4/4.
- I1: 401 do stream passou a espelhar a regra multi-aba (`sentToken` congelado; `current!==sentToken` repete sem refresh).
- I1: `patch({sending:false})` protegido por `!stale()`; fallback que reenviava turno removido; `TIMEOUT` com chave própria.
- I2/I3: `streamed`/`failed` hoistados; `catch` com `if (streamed)` → erro seguro e placeholder removido; fallback só em `!streamed && (NETWORK_ERROR|STREAM_UNAVAILABLE)`.

## Evidência

- Frontend: 38 arquivos / 444 testes, build EXIT=0.
- Backend: 699/699; ITs do bugfix 32/32 (inclui `DoneSessionIdIT` 4/4).
- Segurança: `pass` — zero token em URL, zero `EventSource`, `safeStreamCode` + redação preservados.
- Checks Pós: ADR-01/ADR-02 PASS após Emenda 3.

## Aceite do operador

**Validado ao vivo em 2026-11-27:** pensamento aparece durante a geração, texto cresce por partes e a conversa vincula na sidebar no primeiro turno ("funcionando correto agora").

## Observações

- Se o modelo não emitir thinking, o canal é omitido por design (degradação silenciosa aprovada no PRD).
- `harness/streaming-reasoning.json` segue com `frontend_evidence` N/A e override EVIDENCE registrado; se este bugfix entrar no mesmo ciclo de auditoria, gerar stories/screenshots/a11y reais e restaurar `evidence=enforce`.
