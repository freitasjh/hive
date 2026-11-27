# Arquivamento — PRD streaming-reasoning

- **Data:** 2026-11-27.
- **Duração:** 2026-11-26 → 2026-11-27.
- **Motivo:** 2 ADRs implementados, revisados e verificados; rastreabilidade 68/68 RF/NFR (100%) em `TRACEABILITY.md`.
- **Artefatos:** `PRD.md`, `RESEARCH.md`, `ADR-INDEX.md`, `TRACEABILITY.md`, e por ADR `ADR.md`/`SPEC.md`/`TASKS.md`/`VERIFY.md` (ambos APPROVED).
- **Código:** ADR-001 em `adr/streaming-reasoning/01-provider-streaming`; ADR-002 em `adr/streaming-reasoning/02-sse-endpoints` (empilhada sobre ADR-001). Ambos sem commit/PR.
- **Status de merge:** **PENDENTE — ato humano.** Nenhum commit, PR ou merge foi criado por agente; `ADR-INDEX.md` mantém `merge: —` e status real (`in-progress`/`todo`). `current_phase` não foi promovido a `Completed` por este motivo.
- **Dívidas registradas:** H3 real do MFA (override prévio); SEC-P4-E (token de admissão se `stream()` lançar) e SEC-P4-F (`frame_limit` sem `stream.failed`); `X-RateLimit-Remaining/Reset` do assistant; frontend consumidor do SSE é outra sessão; `A2 StreamingResponseBody` segue como fallback não acionado.
- **Commit sugerido (não executado):**

```text
feat: archive prd-streaming-reasoning — SSE + reasoning no chat público e assistant

Spec: .spec/archive/20261127-prd-streaming-reasoning/
Branches: adr/streaming-reasoning/01-provider-streaming, adr/streaming-reasoning/02-sse-endpoints
```
