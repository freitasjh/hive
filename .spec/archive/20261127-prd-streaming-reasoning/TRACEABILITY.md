# TRACEABILITY — streaming-reasoning (2026-11-27)

| ADR | RF/NFR na SPEC | Tickets | Tickets concluídos | VERIFY |
|---|---|---|---|---|
| ADR-001 | 34 | 16 (PS-01..SEC-16) | 16/16 | `ADRs/ADR-001-provider-streaming/VERIFY.md` — APPROVED |
| ADR-002 | 34 | 16 (SSE-01..CNT-16) | 16/16 | `ADRs/ADR-002-sse-endpoints/VERIFY.md` — APPROVED |

- Cada RF/NFR aparece mapeado na §13 da sua SPEC; cada ticket tem seção `### ID` e cenários BDD de sucesso/falha/borda com teste antes da implementação.
- Scope markers conferidos: `<!-- scope: adr/ADR-001 -->` e `<!-- scope: adr/ADR-002 -->` nos TASKS.
- Evidência de execução: ADR-001 698 testes unit + 17 ITs + security recheck 9/9; ADR-002 699 testes unit + 248 ITs + security recheck 19/19; `npm test` 421/421 em ambos os fechamentos.
- Code review: ambos `APPROVED` com zero 🔴 (cada um após 1 iteração de correção).
- Cobertura rastreável: 68/68 RF/NFR (100%) com implementação e teste correspondentes.

Dívidas registradas: H3 do MFA (override prévio), SEC-P4-E/F do ADR-002, `X-RateLimit-Remaining/Reset` do assistant, merge humano/PR das 2 branches.
