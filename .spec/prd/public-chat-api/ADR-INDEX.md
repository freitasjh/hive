# ADR-INDEX — public-chat-api

| order | id | title | status | depends_on | dir | branch | merge | unit |
|---|---|---|---|---|---|---|---|---|
| 1 | ADR-001 | Contrato tipado e especificação OpenAPI da API pública | merged | — | ADRs/ADR-001-contrato-openapi/ | adr/public-chat-api/01-contrato-openapi | d0d3890 · PR #4 | adr |
| 2 | ADR-002 | Centralizar conversação, precedência do system message e telemetria | merged | ADR-001 | ADRs/ADR-002-centralizacao-chat/ | adr/public-chat-api/02-centralizacao-chat | c71081e · PR #5 | adr |
| 3 | ADR-003 | Gestão de credenciais com UI admin sobre `client_credentials` | merged | ADR-001 | ADRs/ADR-003-credenciais-admin/ | adr/public-chat-api/03-credenciais-admin | 431150e · PR #6 | adr |
| 4 | ADR-004 | Cota por tenant (100 req/min compartilhado) e bugfix do 429 no token | merged | ADR-001 | ADRs/ADR-004-cota-tenant/ | adr/public-chat-api/04-cota-tenant | 37f0cab · PR #7 | adr |

`prd_version: 2` (§16 do `PRD.md`, aprovado em 2026-11-21). Unidade de entrega = ADR: 1 ADR = 1 branch = 1 PR = 1 revisão, merge humano. Com `unit: adr`, a linha `in-progress` exige `ADR.md` + `SPEC.md` + `TASKS.md`; `VERIFY.md` entra só após merge.

## Backlog (candidatos ainda não abertos)

Não são linhas do índice enquanto não há diretório correspondente — o contrato é 1:1 com o disco (`.agents/rules/prd-adr-pipeline.md:20-27`).

1. **ADR-005** — Fronteira `ai/api` × `assistant/impl` × `publicapi/impl` (K3). Depende de ADR-002; estrutural e adiável.

Teto de 5 ADRs por PRD respeitado. Branch reservado para o ADR 1: `adr/public-chat-api/01-contrato-openapi` — criação por operador (sem tool de shell nesta sessão).

## Fora deste PRD

K12 (custo de ocupação), SEC-02, gate externo ERP (VALIDATION §4), `application-dev.properties`, crédito/budget, Ai Services/`ChatMemoryStore`. Ver `ARCHITECT-REVIEW.md` e `PRD.md` §14.
