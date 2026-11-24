# ADR-INDEX — public-api-key-complementar

| order | id | title | status | depends_on | dir | branch | merge | unit |
|---|---|---|---|---|---|---|---|---|
| 1 | ADR-001 | Formato Bearer `sk-` e contrato OpenAPI da key complementar | in-progress | — | ADRs/ADR-001-formato-bearer/ | adr/public-api-key-complementar/01-formato-bearer | — | adr |

`prd_version: 2` (§16 aprovado em 2026-11-23). Unidade de entrega = ADR: 1 ADR = 1 branch = 1 PR = 1 revisão, merge humano. Com `unit: adr`, a linha `in-progress` exige `ADR.md` + `SPEC.md` + `TASKS.md`; `VERIFY.md` entra só após merge.

## Backlog (candidatos ainda não abertos)

Não são linhas do índice enquanto não há diretório correspondente — o contrato é 1:1 com o disco (`.agents/rules/prd-adr-pipeline.md:20-27`).

1. **ADR-002** — Emissão, hash, rotação e revogação da key. Depende de ADR-001.
2. **ADR-003** — Cota própria da key e telemetria preservada. Depende de ADR-001 e ADR-002.

Teto de 5 ADRs do PRD respeitado (3 candidatos).

## Fora deste PRD

`client_credentials` atual; `x-goog-api-key`; aceitação de ambos os headers; UI admin da ADR-003; crédito/budget; SEC-02; K12; bugfixes pendentes; ADRs arquivadas do PRD anterior.

## Próximo passo

Parecer do `architect` sobre o rascunho da ADR-001 → branch `adr/public-api-key-complementar/01-formato-bearer` → implementação → review → QA → merge → `VERIFY.md`.
