# ADR-INDEX — public-chat-api

| order | id | title | status | depends_on | dir | branch | merge | unit |
|---|---|---|---|---|---|---|---|---|
| 1 | ADR-001 | Contrato tipado e especificação OpenAPI da API pública | in-progress | — | ADRs/ADR-001-contrato-openapi/ | adr/public-chat-api/01-contrato-openapi | — | adr |

`prd_version: 2` (§16 do `PRD.md`, aprovado em 2026-11-21). Unidade de entrega = ADR: 1 ADR = 1 branch = 1 PR = 1 revisão, merge humano. Com `unit: adr`, a linha `in-progress` exige `ADR.md` + `SPEC.md` + `TASKS.md`; `VERIFY.md` entra só após merge.

## Backlog (candidatos ainda não abertos)

Não são linhas do índice enquanto não há diretório correspondente — o contrato é 1:1 com o disco (`.agents/rules/prd-adr-pipeline.md:20-27`).

1. **ADR-002** — Centralizar conversão de histórico, precedência do `SystemMessage` e telemetria (K6/K11). Depende de ADR-001.
2. **ADR-003** — Gestão de credenciais com UI admin sobre `client_credentials`. Depende de ADR-001.
3. **ADR-004** — Cota por tenant (100 req/min compartilhado) e headers de rate limit. Depende de ADR-001, paralelo ao ADR-003.
4. **ADR-005** — Fronteira `agent/api` × `agent/impl` × `publicapi/impl` (K3). Depende de ADR-002; estrutural e adiável.

Teto de 5 ADRs por PRD respeitado. Branch reservado para o ADR 1: `adr/public-chat-api/01-contrato-openapi` — criação por operador (sem tool de shell nesta sessão).

## Fora deste PRD

K12 (custo de ocupação), SEC-02, gate externo ERP (VALIDATION §4), `application-dev.properties`, crédito/budget, Ai Services/`ChatMemoryStore`. Ver `ARCHITECT-REVIEW.md` e `PRD.md` §14.
