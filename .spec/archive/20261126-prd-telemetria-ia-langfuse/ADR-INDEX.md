---
prd_id: telemetria-ia-langfuse
prd_version: 2
type: adr-index
title: "Índice de ADRs — telemetria de IA no Langfuse self-host v4"
status: merged
data: 2026-11-25
atualizado: 2026-11-26
esteira: "PRD §16 APROVADO em 2026-11-25 → RESEARCH → ADR-INDEX → ADR → branch → SDD"
---

# ADR-INDEX — telemetria-ia-langfuse

> Ledger 1:1 com o disco e com as 5 linhas do PRD §13. Uma linha por ADR; `order` = ordem de merge.
> **Estado:** 5 ADRs `merged` em `main` (PRs #15–#19). Cada diretório contém `ADR.md` + `SPEC.md` + `TASKS.md` + `VERIFY.md`, todos com `VERIFY STATUS: APPROVED ✅`.
> `merge` = SHA do merge commit · PR (confirmados por `gh pr view N --json state,mergeCommit,headRefOid` e por `git log --merges`, 2026-11-26). SHA do code (`headRefOid`): 001 `c9769fd` · 002 `fc16d3b` · 003 `8b5be7e` · 004 `a9632a0` · 005 `b5aee24`.
> `unit: adr` em todas as linhas (PRD `prd_version: 2`). `ADR-01` e `ADR-02` PASS.

| order | id | title | status | depends_on | dir | branch | merge | unit |
|---|---|---|---|---|---|---|---|---|
| 1 | ADR-001 | Contrato de telemetria: modelo de spans turno/provider, atributos de correlação e fronteira com `ChatTraceRecorder` | merged | — | ADRs/ADR-001-contrato-spans/ | adr/telemetria-ia-langfuse/01-contrato-spans | f380216 · PR #15 | adr |
| 2 | ADR-002 | Ingestão OTLP/HTTP no Langfuse self-host v4 (endpoint, Basic `pk:sk`, header v4, isolamento do exporter) | merged | ADR-001 | ADRs/ADR-002-ingestao-otlp/ | adr/telemetria-ia-langfuse/02-ingestao-otlp | d92c4e9 · PR #16 | adr |
| 3 | ADR-003 | Redação na origem e privacidade/LGPD do texto de prompt/resposta em produção | merged | ADR-001 | ADRs/ADR-003-redacao-origem/ | adr/telemetria-ia-langfuse/03-redacao-origem | 1c09ac1 · PR #17 | adr |
| 4 | ADR-004 | Controle de acesso no reverse proxy e retenção externa de 30 dias (Community) | merged | ADR-002, ADR-003 | ADRs/ADR-004-acesso-retencao/ | adr/telemetria-ia-langfuse/04-acesso-retencao | 9f91256 · PR #18 | adr |
| 5 | ADR-005 | Métricas operacionais Micrometer/Prometheus e custo derivado no Langfuse | merged | ADR-002 | ADRs/ADR-005-metricas-custo/ | adr/telemetria-ia-langfuse/05-metricas-custo | 63baf69 · PR #19 | adr |

> Contrato das colunas (`.agents/rules/prd-adr-pipeline.md:18-39`; implementação em
> `.opencode/plugins/lib/prd-adr-pipeline/checks.mjs:61-88,199-252,443-532`):
> `order` inteiro ≥1 e sem pulo · `id` único · `status` ∈ todo|in-progress|merged|dropped ·
> `depends_on` declarado (`—` quando não há) · `dir` = `ADRs/ADR-00N-{slug}/` ·
> `branch` = `adr/{prd-id}/{nn}-{slug}` · `merge` preenchido quando `status=merged` ·
> `unit` ∈ `adr`|`legacy` (enum estrito em minúsculas). Teto de 5 ADRs por PRD respeitado
> (`.agents/rules/prd-adr-pipeline.md:46`).
>
> **Layout por ADR em execução:** `unit: adr` exige `ADR.md` + `SPEC.md` +
> `TASKS.md` no diretório, com `<!-- scope: adr/ADR-00N -->` na primeira linha do `TASKS.md`,
> e `VERIFY.md` após `merged`. `ADR.md` traz `depende-de` e `Interface congelada`.
> Os 5 ADRs satisfazem esse layout; o índice é 1:1 com o disco. Arquivamento pendente —
> compete ao `orchestrator` mover o PRD para `.spec/archive/` após o fechamento.
