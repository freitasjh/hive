---
prd_id: telemetria-ia-langfuse
prd_version: 2
type: adr-index
title: "Índice de ADRs — telemetria de IA no Langfuse self-host v4"
status: declarado
data: 2026-11-25
esteira: "PRD §16 APROVADO em 2026-11-25 → RESEARCH → ADR-INDEX → ADR → branch → SDD"
---

# ADR-INDEX — telemetria-ia-langfuse

> Ledger **declarativo**, 1:1 com as 5 linhas do PRD §13. Uma linha por ADR candidato; `order` = ordem prevista de merge.
> **Estado:** nenhum `ADRs/**`, branch, push, merge, código ou `workflow-state.json` foi criado — restrição expressa do insumo.
> `status: todo` em toda linha; `merge` vazio (`—` = não preenchido; merge é humano, sem `gh`).
> `unit: adr` declarado em todas as linhas (PRD `prd_version: 2`; o checador isenta `todo`, mas a declaração antecipa a execução).
> **Efeito mecânico conhecido:** `checks.mjs:353-356` exige que cada `dir` da tabela exista no disco. Com 5 linhas declaradas e zero diretórios, `ADR-01` reporta 5 linhas sem dir correspondente; `ADR-02` permanece conforme (ordem 1..5 sem pulo, branches no regex, `status` no enum, `merge` vazio). O `architect` cria cada `ADRs/ADR-00N-{slug}/` junto com seu ADR; o ledger passa a ser 1:1 com o disco nesse momento. Ver `RESEARCH.md` §8.

| order | id | title | status | depends_on | dir | branch | merge | unit |
|---|---|---|---|---|---|---|---|---|
| 1 | ADR-001 | Contrato de telemetria: modelo de spans turno/provider, atributos de correlação e fronteira com `ChatTraceRecorder` | todo | — | ADRs/ADR-001-contrato-spans/ | adr/telemetria-ia-langfuse/01-contrato-spans | — | adr |
| 2 | ADR-002 | Ingestão OTLP/HTTP no Langfuse self-host v4 (endpoint, Basic `pk:sk`, header v4, isolamento do exporter) | todo | ADR-001 | ADRs/ADR-002-ingestao-otlp/ | adr/telemetria-ia-langfuse/02-ingestao-otlp | — | adr |
| 3 | ADR-003 | Redação na origem e privacidade/LGPD do texto de prompt/resposta em produção | todo | ADR-001 | ADRs/ADR-003-redacao-origem/ | adr/telemetria-ia-langfuse/03-redacao-origem | — | adr |
| 4 | ADR-004 | Controle de acesso no reverse proxy e retenção externa de 30 dias (Community) | todo | ADR-002, ADR-003 | ADRs/ADR-004-acesso-retencao/ | adr/telemetria-ia-langfuse/04-acesso-retencao | — | adr |
| 5 | ADR-005 | Métricas operacionais Micrometer/Prometheus e custo derivado no Langfuse | todo | ADR-002 | ADRs/ADR-005-metricas-custo/ | adr/telemetria-ia-langfuse/05-metricas-custo | — | adr |

> Contrato das colunas (`.agents/rules/prd-adr-pipeline.md:18-39`; implementação em
> `.opencode/plugins/lib/prd-adr-pipeline/checks.mjs:61-88,199-252,443-532`):
> `order` inteiro ≥1 e sem pulo · `id` único · `status` ∈ todo|in-progress|merged|dropped ·
> `depends_on` declarado (`—` quando não há) · `dir` = `ADRs/ADR-00N-{slug}/` ·
> `branch` = `adr/{prd-id}/{nn}-{slug}` · `merge` preenchido quando `status=merged` ·
> `unit` ∈ `adr`|`legacy` (enum estrito em minúsculas). Teto de 5 ADRs por PRD respeitado
> (`.agents/rules/prd-adr-pipeline.md:46`).
>
> **Layout por ADR quando entrar em execução:** `unit: adr` exige `ADR.md` + `SPEC.md` +
> `TASKS.md` no diretório, com `<!-- scope: adr/ADR-00N -->` na primeira linha do `TASKS.md`,
> e `VERIFY.md` após `merged`. `ADR.md` traz `depende-de` e `Interface congelada`.
> Nada disso existe agora — o índice é só o contrato do que virá.
