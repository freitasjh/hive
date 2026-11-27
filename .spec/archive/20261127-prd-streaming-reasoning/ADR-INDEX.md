# ADR-INDEX — streaming-reasoning

| order | id | title | status | depends_on | dir | branch | merge | unit |
|---|---|---|---|---|---|---|---|---|
| 1 | ADR-001 | Provider de streaming e gateway de turno com thinking em `ai` | in-progress | — | ADRs/ADR-001-provider-streaming/ | adr/streaming-reasoning/01-provider-streaming | — | adr |
| 2 | ADR-002 | Endpoints SSE público e assistant com `reasoning_content` separado | todo | ADR-001 | ADRs/ADR-002-sse-endpoints/ | adr/streaming-reasoning/02-sse-endpoints | — | adr |

`prd_version: 2` (§16 do `PRD.md`, aprovado em 2026-11-26). Unidade de entrega = ADR: 1 ADR = 1 branch = 1 PR = 1 revisão, merge humano. Linhas `todo` usam `unit: legacy` até a decisão ser tomada; ao entrar em `in-progress`, trocam para `unit: adr` e ganham `SPEC.md` + `TASKS.md`; `VERIFY.md` entra só após merge. Contrato 1:1 com o disco.

Teto de 5 ADRs por PRD: 2 reservados.

## Fora deste PRD

H3 do MFA (override registrado), frontend (outra sessão), bump do langchain4j, `langchain4j-reactor`, `reasoning_tokens` do `TokenUsage` 1.19.0, persistência de thinking. Ver `PRD.md` §14.
