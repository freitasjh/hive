---
prd_id: langchain4j-ai-services
prd_version: 2
type: refactor-study
title: "Índice de ADRs — centralização de regras e adoção de Ai Services (LangChain4j 1.19.0)"
status: decision-only
data: 2026-11-21
esteira: "PRD (§16 aprovado em 2026-11-21) → RESEARCH → ADR-INDEX → ADR (decision-only) → branch → SDD"
---

# ADR-INDEX — langchain4j-ai-services

> Ledger 1:1 com `ADRs/**`: uma linha por ADR; `order` = ordem prevista de merge.
> **Modo deste lote: decision-only.** Toda linha `status: todo`, toda linha `unit: legacy`
> (`.agents/rules/prd-adr-pipeline.md:51-54` — ADR decision-only = `unit: legacy` +
> `status: todo`), `merge` vazio (`—` = não preenchido; merge é humano, sem `gh`). Não há
> branch real, push nem URL de compare — apenas os nomes declarados abaixo.
> **Sem `SPEC.md`/`TASKS.md`/`VERIFY.md`:** `unit: legacy` exige só `ADR.md` — o checador
> ADR-01 exige `SPEC.md`+`TASKS.md` apenas para linha `unit: adr`
> (`.opencode/plugins/lib/prd-adr-pipeline/checks.mjs:403-408`). `unit: adr` é obrigatório
> somente em linha `in-progress`/`merged` de PRD v2 (ADR-02; `todo` é isento). Se o ADR
> entrar em execução, converta a linha para `unit: adr` e crie SPEC/TASKS/VERIFY conforme
> o portão. Nenhuma implementação autorizada.

| order | id | title | status | depends_on | dir | branch | merge | unit |
|---|---|---|---|---|---|---|---|---|
| 1 | ADR-001 | Centralizar conversão de histórico e precedência do system message | todo | — | ADRs/ADR-001-conversao-system-message/ | adr/langchain4j-ai-services/01-conversao-system-message | — | legacy |
| 2 | ADR-002 | Integrar Ai Services de forma manual e stateless dentro da porta | todo | ADR-001 | ADRs/ADR-002-integracao-ai-services/ | adr/langchain4j-ai-services/02-integracao-ai-services | — | legacy |
| 3 | ADR-003 | Manter histórico JPA; nenhuma ChatMemory e nenhum @MemoryId | todo | ADR-002 | ADRs/ADR-003-memoria-historico-memoryid/ | adr/langchain4j-ai-services/03-memoria-historico-memoryid | — | legacy |
| 4 | ADR-004 | Preservar telemetria com Result e corrigir turnId/clientId da trace | todo | ADR-001 | ADRs/ADR-004-retry-telemetria/ | adr/langchain4j-ai-services/04-retry-telemetria | — | legacy |
| 5 | ADR-005 | Congelar fronteira de módulos, teto de maxTokens e débito da aresta | todo | ADR-001, ADR-002 | ADRs/ADR-005-fronteira-limites/ | adr/langchain4j-ai-services/05-fronteira-limites | — | legacy |

> Contrato das colunas (`.agents/rules/prd-adr-pipeline.md:18-39`): `order` inteiro ≥1 e
> sem pulo · `id` único · `status` ∈ todo|in-progress|merged|dropped · `depends_on`
> declarado (`—` quando não há) · `dir` = `ADRs/ADR-00N-{slug}/` · `branch` =
> `adr/{prd-id}/{nn}-{slug}` · `merge` preenchido quando `status=merged` · `unit` ∈
> `adr`|`legacy` (enum estrito em minúsculas). Teto de 5 ADRs por PRD respeitado
> (`.agents/rules/prd-adr-pipeline.md:46`).

## Vereditos das 10 perguntas (PRD §14:183-194)

Encaminhamento humano: arquitetura (#1–#6, #9) ao `architect`; produto/ambas (#7, #8, #10)
empurradas para os ADRs (`PRD.md:215-217`). Respostas dadas nos ADRs, com fonte.

- **#1** (AiService substitui todo o gateway ou só montagem/parsing?) → **ADR-002**: só montagem/parsing; porta e políticas ficam no gateway.
- **#2** (`ChatTurnGateway` permanece?) → **ADR-002 + ADR-005**: sim, em `agent/api`, com 2 chamadores.
- **#3** (memória × histórico; `@MemoryId` = sessionId ou turnId?) → **ADR-003**: nenhuma memória; histórico JPA permanece; se reaberto, `sessionId` (UUID), nunca `turnId`.
- **#4** (preservar `TokenUsage`/`FinishReason`/`promptChars` com `Result<T>`?) → **ADR-004**: `Result<T>` no proxy + números medidos no gateway; listeners não são fonte de verdade sem IT de paridade.
- **#5** (concorrência por sessão, dado A8?) → **ADR-003**: stateless por construção (sem `@MemoryId` compartilhado); serialização por sessão só se C for reaberto.
- **#6** (adotar starter SB4?) → **ADR-002**: não; builder manual. Divergência beta29/GA/4.0.5×4.1.0 registrada como risco, não como fato.
- **#7** (teto único de `maxTokens`) → **ADR-005**: sim, no gateway (`min(requested, 4096)`); validação `[256,4096]` permanece no DTO.
- **#8** (`turnId`/`clientId` da trace) → **ADR-004**: pré-condição absorvida no ADR de telemetria; não vira 6º ADR (teto 5).
- **#9** (aresta `publicapi/impl → agent/impl`) → **ADR-005**: débito separado, fora deste PRD; nenhum ADR deste lote a aprofunda.
- **#10** (restrição MFA) → **confirmado em todos os ADRs** e reafirmado no ADR-005.

## Confirmação MFA (pergunta #10)

Confirmado: nenhuma decisão deste índice toca `security/**/Mfa*` nem `identity/**/Mfa*`.
Restrição registrada em `PRD.md:118,177,194`, `TECH-BRIEF.md:56` e `workflow-state.json:2-6`.
Verificação de implementação futura: `git diff` com zero linhas nesses paths (gate H6).
