---
prd_id: langchain4j-ai-services
prd_version: 2
type: refactor-study
title: "Estudo de centralização de regras e adoção de Ai Services (LangChain4j 1.19.0) no backend do hive"
dono_da_aprovacao: product-manager (com parecer técnico de architect)
status: draft
data: 2026-11-21
escopo: "Leitura A — todo o backend (backend/**)"
insumo: ".spec/prd/langchain4j-ai-services/TECH-BRIEF.md"
esteira: "PRD → RESEARCH → ADR-INDEX → ADR → branch → SDD"
---

# PRD — LangChain4j Ai Services no backend do hive (estudo)

> **Mapa das 16 seções**: §1, §2, §5, §6, §7, §9 e §16 são obrigatórias. §16 = aprovação humana, em branco e bloqueante: nenhum `ADRs/**` nasce antes dela (`.agents/rules/prd-adr-pipeline.md:9-16`).
> **Natureza**: produto entregue agora = estudo. Implantação fica para ADRs futuros, após aprovação.

## 1. Problema [obrigatória]

Regras do turno de chat — montagem/ordem do `SystemMessage`, conversão de histórico, retry, classificação de erro, limites, isolamento de tenant e telemetria — vivem espalhadas e duplicadas em `agent`, `publicapi`, `provider`, `tenant` e `commons`. Já há divergências observáveis no código: conversão pública sem ramo `SYSTEM` e sem descarte de vazio (`TECH-BRIEF.md:109,121,257`), javadoc de retry contradiz comportamento (`:133`), linha de trace pública imprime `turnId=null clientId=null` (`:184`). Sem centralização, cada mudança de prompt/segurança exige editar vários módulos, aumentando custo, risco de regressão e resposta inconsistente entre chat humano e API pública. O potencial de Ai Services/ChatMemory do LangChain4j 1.19.0 nunca foi avaliado com fonte primária (`:196-198`).
> ❓ Validação: A dor existe hoje e dói o bastante para justificar a mudança? **sim** — divergências e débitos citados em `TECH-BRIEF.md:101-188,243-258`.

## 2. Objetivo [obrigatória]

Entregar, sem escrever código, um estudo que: (a) inventarie todo o backend; (b) mapeie regras candidatas a centralizar separando "regra atual" de "regra proposta"; (c) produza decisão de AiService/ChatMemory com citações de fonte oficial; (d) proponha até 5 ADRs numerados com dependências; (e) registre riscos e débitos. Resultado observável e mensurável pelo aceite de §5.
> ❓ Validação: O objetivo é mensurável e não é uma solução? **sim** — medido por contagens e pela ausência de diff de código.

## 3. Contexto e evidências

**Inventário — cobertura do backend** (`backend/pom.xml:16-27`; `find backend -name pom.xml`, 2026-11-21):

| Módulo 1º nível | poms | Papel / acoplamento ao turno (evidência em `TECH-BRIEF.md:22-97`) |
|---|---|---|
| `backend` (raiz) | 1 | agregador; Java 25 preview atinge todo o backend (`:26`) |
| `dependencies` | 1 | BOM; fixa LangChain4j **1.19.0** (`:27`; `backend/dependencies/pom.xml:37`) |
| `bootstrap` | 1 | único com artefatos `dev.langchain4j` em `compile` (`:28`) |
| `commons` | 4 | isolamento de tenant, HTTP pinado, envelope de erro (`:30-36`) |
| `tenant` | 7 | origem do provider/modelo padrão do turno (`:38-45`) |
| `identity` | 7 | usuário/MFA — **sem acoplamento direto ao turno** (`:47-56`) |
| `security` | 6 | 2 filtros abrem `TenantContext` (`:58-64`) |
| `provider` | 3 | `LlmClientFactory` + fábrica LangChain4j (`:66-71`) |
| `agent` | 7 | domínio do turno, gateway, JPA, telemetria (`:73-80`) |
| `publicapi` | 7 | 2º chamador da porta; aresta para `agent/impl` (`:82-89`) |
| `integration-test` | 1 | ITs de caracterização da fronteira (`:91-95`) |

44 poms sob os 10 módulos de primeiro nível **+ 1 agregador raiz = 45 arquivos** no `find`. `TECH-BRIEF.md:20` rotula "44 pom.xml" — interpretação aqui preservada como "44 sub-poms"; a rótulo é reconciliação menor, não fato a herdar (ver K13 em §8).

**Fatos de API** — nenhum aceito de memória; lidos em 2026-11-21. Legendas de fonte (URL + data):

- **S1** `https://raw.githubusercontent.com/langchain4j/langchain4j/1.19.0/langchain4j/src/main/java/dev/langchain4j/service/AiServices.java` — 2026-11-21 (tag 1.19.0)
- **S2** `https://raw.githubusercontent.com/langchain4j/langchain4j/1.19.0/langchain4j/src/main/java/dev/langchain4j/service/memory/ChatMemoryAccess.java` — 2026-11-21
- **S3** `https://raw.githubusercontent.com/langchain4j/langchain4j/1.19.0/langchain4j-core/src/main/java/dev/langchain4j/store/memory/chat/ChatMemoryStore.java` — 2026-11-21
- **S4** `https://raw.githubusercontent.com/langchain4j/langchain4j/1.19.0/langchain4j/src/main/java/dev/langchain4j/memory/chat/MessageWindowChatMemory.java` — 2026-11-21
- **S5** `https://docs.langchain4j.dev/tutorials/chat-memory` — 2026-11-21 (**não versionada**)
- **S6** `https://docs.langchain4j.dev/tutorials/ai-services` — 2026-11-21 (**não versionada**)

| # | Afirmação de API | Fonte | Data |
|---|---|---|---|
| A1 | `AiServices.builder(Class<T>)` → `AiServices<T>`; `AiServices.create(...)` é atalho | S1 | 2026-11-21 |
| A2 | `.chatModel(...)` e `.streamingChatModel(...)` mutuamente exclusivos | S1 | 2026-11-21 |
| A3 | `.chatMemory(...)` e `.chatMemoryProvider(...)` existem, não simultâneos; provider cria memória por `@MemoryId` | S1 | 2026-11-21 |
| A4 | `.systemMessage(String)` / `.systemMessageProvider(Function<Object,String>)`; `@SystemMessage` tem precedência | S1 | 2026-11-21 |
| A5 | `.chatRequestTransformer(UnaryOperator<ChatRequest>)` e sobrecarga `BiFunction<ChatRequest,Object,ChatRequest>` | S1 | 2026-11-21 |
| A6 | `.tools(...)` e `.contentRetriever(ContentRetriever)` existem | S1 | 2026-11-21 |
| A7 | `Result<T>` acessa `TokenUsage`, `FinishReason`, `ChatResponse` final | S1 + S6 | 2026-11-21 |
| A8 | AI Service **não** é seguro para chamada concorrente no mesmo `@MemoryId` (corrompe `ChatMemory`); sem proteção nativa | S1 + S6 | 2026-11-21 |
| A9 | `ChatMemoryAccess.getChatMemory(Object)` / `evictChatMemory(Object)` | S2 | 2026-11-21 |
| A10 | `ChatMemoryStore`: `getMessages(Object)`, `updateMessages(Object,List<ChatMessage>)`, `deleteMessages(Object)` | S3 | 2026-11-21 |
| A11 | `MessageWindowChatMemory`: system retido, um só, conteúdo diferente **vai para o fim** salvo `alwaysKeepSystemMessageFirst(true)`; store padrão `SingleSlotChatMemoryStore` | S4 | 2026-11-21 |
| A12 | Memory ≠ histórico; histórico integral deve ser mantido manualmente | S5 | 2026-11-21 |
| A13 | `SystemMessage` tem tratamento especial; mensagens evictadas saem também do store | S5 | 2026-11-21 |
| A14 | Ai Services = proxies de interface; `@SystemMessage`, `@UserMessage`, `@MemoryId`, `@V`, `ChatRequestParameters` por chamada | S6 + S1 | 2026-11-21 |
| A15 | Autoconfig Spring depende do starter; compatibilidade com Spring Boot 4.1.0 (`dependencies/pom.xml:8-10`) × 1.19.0 **NÃO verificada** → inferência | S6 + inferência rotulada | 2026-11-21 |

Estado real no repo: **`langchain4j-spring-boot-starter` ausente**; busca por `AiServices`, `ChatMemory`, `@MemoryId`, `Result<` deu zero ocorrências (`TECH-BRIEF.md:196-198`). Hoje o gateway chama `client.chat(prompt)` direto (`:197`; `LangChain4jTurnGateway.java:83`).

## 4. Público e impacto

- **product-manager / architect** — decisores de valor e de estrutura; único impacto é o estudo e a esteira de ADRs.
- **Engenharia backend** — consumidores do inventário e das regras centralizáveis.
- **Usuários de chat humano e API pública** — impactados só depois de ADRs aprovados; risco de ordem do system message (K4) e de telemetria (K5/K6).
- **Operação/segurança** — isolamento de tenant (K9), rastreabilidade da trace (K6), restrição MFA (§7).

## 5. Métricas de sucesso [obrigatória]

| Métrica | Como medir | Meta |
|---|---|---|
| Cobertura do inventário | contar módulos/poms em `TECH-BRIEF.md:18-98` contra `find backend -name pom.xml` | 10/10 módulos, 44 sub-poms + raiz |
| Regras mapeadas | contar R1–R7 em `TECH-BRIEF.md:101-188`, cada uma com "atual" e "proposta" | 7/7 |
| Fatos de API rastreáveis | checar A1–A15 por URL + data | 15/15 |
| Riscos registrados | contar K1–K12 em `TECH-BRIEF.md:243-258` (+K13 em §8) | 12/12 + reconciliação |
| ADRs candidatos | contar tabela §6 com número, título e dependência | 5 (teto), 0 diretórios criados |
| Pureza do estudo | `git status`/`git diff` — nenhum arquivo fora `.spec/` alterado | zero diff |
| Restrição MFA | busca por diff em `security/**/Mfa*`, `identity/**/Mfa*` | zero |
| Bloqueio de esteira | `§16` vazio e nenhum `RESEARCH/ADR-INDEX/ADRs/**` criado | aprovado na separação |

> ❓ Validação: Ao menos uma métrica tem "como medir" preenchida? **sim** — todas.

## 6. Escopo [obrigatória]

Entra: Leitura A — todo o backend; inventário dos 10 módulos; regras R1–R7; fatos A1–A15; riscos K1–K12; alternativas A–D (`TECH-BRIEF.md:228-239`); 10 perguntas classificadas (§14); 5 ADRs candidatos abaixo. Artefato único deste lote: este PRD (co-assinado pelo insumo do `engineer`).

| # | ADR candidato (`TECH-BRIEF.md:281-291`) | Depende de |
|---|---|---|
| 1 | Centralizar conversão de histórico e precedência do system message (R1/R2, K4, K11) | — |
| 2 | Escolher forma de integração com Ai Services (A/B/C/D; manter `AiServices.builder` manual) | 1 |
| 3 | Definir relação memória × histórico e política de `@MemoryId`/concorrência (K1/K2) | 2 |
| 4 | Unificar retry, classificação de transporte e telemetria de turno (R3/R4/R7, K5/K6/K7) | 1 |
| 5 | Resolver fronteira `agent/api` × `agent/impl` × `publicapi/impl` e limites de requisição (K3/K8/K12) | 1, 2 |

> ❓ Validação: O escopo é fechado o bastante para caber em um ADR? **sim** — decomposto em 5 ADRs, teto respeitado (`.agents/rules/prd-adr-pipeline.md:46`).

## 7. NÃO entra [obrigatória]

- Implementação, diff de código/config/teste fora de `.spec/` — nenhum.
- `RESEARCH.md`, `ADR-INDEX.md`, `ADRs/**`, `SPEC.md`, `TASKS.md`, `VERIFY.md`, `workflow-state.json`.
- Ciclo MFA em andamento: nenhuma proposta toca `security/**/Mfa*` nem `identity/**/Mfa*` (`workflow-state.json:2-6`; `TECH-BRIEF.md:56`).
- Frontend, desenvolvimento/código, migração de dados, adoção de starter sem deepsearch de compatibilidade (K10/A15).
- Respostas às 10 perguntas de §14 — ficam para `architect`/`product-manager`.
> ❓ Validação: O que não entra está declarado, não inferido? **sim**.

## 8. Riscos e mitigação

Riscos de produto derivados de K1–K12 (`TECH-BRIEF.md:243-258`), não copiados cego:

| Risco (valor/custo/contrato/telemetria/recurso) | Impacto de produto | Mitigação no estudo |
|---|---|---|
| K1 memória ≠ histórico | perda do que a UI mostra e da base das ITs | PRD declara persistência JPA permanece (§7) |
| K2 concorrência por `@MemoryId` (A8) | corrupção de conversa | registrar como pré-condição do ADR 3 |
| K3 aresta `publicapi/impl → agent/impl` | custo de mudança, contrato de erro divergente | relegar a decisão ao ADR 5 |
| K4 ordem do system message | mudança de regra de segurança (rejeição de `system`) | IT de caracterização da ordem no ADR 1 |
| K5 perda de `TokenUsage`/`FinishReason` | telemetria e faturamento inconsistentes | exigir `Result<T>`/`finalResponse()` (A7) no ADR 4 |
| K6 trace não atribuível | impossibilidade de auditar API pública | ADR próprio ou pré-condição (pergunta 8) |
| K7 javadoc vs jitter | documento enganoso | corrigir junto do ADR 4 |
| K8 clamp de `maxTokens` inexistente | expectativa de teto não cumprida | decidir na pergunta 7; não herdar parecer antigo |
| K9 `ScopedValue` não propaga | leitura cross-tenant silenciosa em callback | fronteira explícita de tenant no ADR 3/5 |
| K10 starter não verificado | auto-config duplicando beans | deepsearch de compatibilidade antes de propor D |
| K11 conversão divergente | resposta inconsistente entre chamadores | argumento central do ADR 1 |
| K12 retry 2× + timeout 120 s vs cota 60/min | ocupação de recurso sem cobertura de cota | registrar como débito de produto, não resolver por palpite |
| K13 reconciliação 44 vs 45 poms (§3) | rótulo de inventário impreciso | corrigir redação no RESEARCH; sem impacto de código |

Maior risco: **K1+K2+K9** — substituir histórico persistido por `ChatMemory` e combinar com `@MemoryId` concorrente e `ScopedValue` não propagado pode gerar perda de dados e vazamento cross-tenant. Redução: preferir A→B (stateless), tratar C como decisão separada e D como bloqueada (K10).

## 9. Requisitos funcionais [obrigatória]

- **RF01** Entregar inventário cobrindo os 10 módulos de primeiro nível e 44 sub-poms + raiz, com papel e acoplamento por módulo.
- **RF02** Listar R1–R7 com "regra atual" (arquivo:linha) e "regra proposta" separadas.
- **RF03** Cada afirmação de API A1–A15 trazer URL + data 2026-11-21; distinguer tag 1.19.0 de docs não versionadas.
- **RF04** Registrar estado real do repo: versão 1.19.0, ausência de starter e de `AiServices`/`ChatMemory`.
- **RF05** Comparar alternativas A–D com custo, acoplamento, testabilidade, segurança e aderência.
- **RF06** Propor exatamente 5 ADRs candidatos, numerados, com título e `depende-de`.
- **RF07** Registrar K1–K12 (+K13) com evidência e impacto de produto.
- **RF08** Classificar as 10 perguntas de `TECH-BRIEF.md:262-273` sem respondê-las.
- **RF09** Declarar explicitamente: sem código, sem MFA, sem ADRs/RESEARCH/ADR-INDEX, §16 em branco.
- **RF10** Incluir co-assinatura técnica do `engineer` com ressalva de não decidir arquitetura.

> ❓ Validação: Cada requisito é verificável por um teste? **sim** — por contagem/inspeção de arquivos (§5).

## 10. Histórias de usuário

- Dado o inventário publicado, Quando o `architect` revisa, Então identifica cada módulo do backend e seu acoplamento ao turno sem ler código.
- Dado o conjunto R1–R7, Quando o time discute centralização, Então distingue regra vigente de candidato sem confundir as duas.
- Dados A1–A15 com fonte, Quando o time avalia AiService/ChatMemory, Então decide sem assumir assinatura de memória.
- Dados os 5 ADRs e riscos, Quando o `product-manager` prioriza, Então sequencia A→B com C/D condicionados e vê o bloqueio de §16.

## 11. UX / experiência

Estudo não entrega tela. Impacto futuro considerado: (a) ordem do `SystemMessage` altera comportamento de segurança visível ao usuário (K4); (b) telemetria/auditoria da API pública depende de `turnId`/`clientId` (K6). Recomenda-se parecer de `@ux-designer` apenas quando os ADRs 1 e 4 virarem mudança observável; nenhuma decisão visual é tomada aqui.

## 12. Dependências

Insumo obrigatório: `.spec/prd/langchain4j-ai-services/TECH-BRIEF.md` (309 linhas, `engineer`, 2026-11-21). Regras: `.agents/rules/prd-adr-pipeline.md:7-16,46`; `.agents/rules/brain-context.md:9-11`. Brain: `arquitetura/projetos/hive/arquitetura/api-publica-chat/parecer-consolidado-2026-09-27`, `.../fechamento-d5-d7-d3`, `arquitetura/projetos/hive/decisoes/api-publica-chat`. Fonte primária: S1–S6 (§3). Pré-condição de esteira: §16 aprovado antes de RESEARCH/ADR-INDEX/ADR.

## 13. Segurança

Isolamento de tenant depende de `TenantContext` = `ScopedValue`, que não propaga para thread pool, e `TenantAspect` segue com filtro desligado quando não encontra tenant (K9; `TECH-BRIEF.md:167-172`). Rejeição de `role=system` do cliente é regra de segurança cuja ordem deve ser preservada (K4). Telemetria deve evitar expor texto do provider — `TurnReply.toString()` não imprime (`TECH-BRIEF.md:181`). Nenhuma mudança toca MFA (§7). Dado sensível: nenhum novo; credenciais e grants permanecem em `publicapi` (`:88`).

## 14. Perguntas abertas

CLASSIFICADAS, NÃO RESPONDIDAS (origem `TECH-BRIEF.md:262-273`; classes: `produto` / `arquitetura` / `ambas`):

| # | Pergunta | Classe |
|---|---|---|
| 1 | AiService substitui todo o `ChatTurnGateway` ou só montagem/parsing? | arquitetura |
| 2 | `ChatTurnGateway` permanece como porta entre `agent/api` e os 2 chamadores? | arquitetura |
| 3 | `ChatMemory` em memória, store sobre JPA ou nenhuma? `@MemoryId` = `sessionId` ou `turnId`? (orig. arquitetura + segurança) | ambas |
| 4 | Como preservar `promptMessages`, `promptChars`, `TokenUsage`, `FinishReason` com `Result<T>`? (K5) | arquitetura |
| 5 | Política de concorrência por sessão, dado A8? | arquitetura |
| 6 | Adotar `langchain4j-spring-boot-starter`? Requer deepsearch SB 4.1.0 × 1.19.0 (K10) | arquitetura |
| 7 | `maxTokens` tem teto único e onde mora — DTO, gateway ou fábrica? (K8) | produto |
| 8 | Corrigir `turnId`/`clientId` da trace é pré-condição do ADR de telemetria ou ADR próprio? (K6) | ambas |
| 9 | Aresta `publicapi/impl → agent/impl` entra neste PRD ou fica como débito separado? (K3) | arquitetura |
| 10 | Confirmar que o PRD registra a restrição de não tocar o ciclo MFA | produto |

## 15. PICCO (prompt da demanda)

<task>Entregar estudo de melhoria de código e projeto de todo o backend do hive, com proposta de centralização de regras e adoção de ai-services, sem implementar nada.</task>
<context>Leitura A — todo o backend (`backend/**`); LangChain4j 1.19.0; gateway monta mensagens e chama `client.chat` direto; ChatSession é entidade JPA convertida manualmente; parecer prévio de API pública no Brain. PICCO aprovado 6/6, porta fechada.</context>
<constraints>Cobrir os 10 módulos de primeiro nível e 44 sub-poms + raiz; citar arquivo:linha ou URL+data; usar docs oficiais 1.19.0; separar regra atual de proposta; registrar esteira PRD→RESEARCH→ADR-INDEX→ADR; apontar riscos. Sem código/config/teste, sem API sem fonte, sem tocar MFA, sem virar diff de implementação.</constraints>
<acceptance>Estudo em `.spec/**`: inventário, mapa de regras, decisão AiService/ChatMemory com fontes, 5 ADRs, riscos. Zero diff fora `.spec/`. Deepsearch com URL+data.</acceptance>
<open_questions></open_questions>

## Co-assinatura técnica (engineer)

Insumo: `.spec/prd/langchain4j-ai-services/TECH-BRIEF.md` (2026-11-21). 3 descobertas principais: (1) duas conversões de histórico divergentes — `agent/api` descarta vazio e trata `SYSTEM`, `publicapi/impl` não (`:109,121,257`); (2) `ChatTrace` tem `turnId`/`clientId`, mas `TurnOutcomeMapper` nunca os popula (`:184`); (3) nenhum starter nem `AiServices` no repo, e `MessageWindowChatMemory` 1.19.0 põe system novo no fim (`:196-198`; A11/S4). Ressalva: `engineer` apenas fornece insumo — **não decide arquitetura**; decisão é do `architect` após §16 (`.agents/rules/prd-adr-pipeline.md:15-16`).

## 16. Aprovação [obrigatória]

- **Veredito**: Aprovado (aprovação humana explícita no turno de 2026-11-21)
- **Data**: 2026-11-21
- **Portão**: P0 → P1 — PRD aprovado; RESEARCH liberado; ADRs ainda condicionados a RESEARCH + ADR-INDEX
> ❓ Validação: O owner aprovou (veredito + data) antes de qualquer ADR? **sim** — §16 preenchido nesta data, antes de RESEARCH/ADR-INDEX/ADR (`.agents/rules/prd-adr-pipeline.md:15-16`).

**Deliberação humana registrada no turno de aprovação**:
- Perguntas de **produto** (§14 #7 `maxTokens`, #8 trace `turnId/clientId`, #10 restrição MFA): **empurradas para os ADRs**. Não respondidas aqui; devem constar como decisão ou pré-condição no ADR que as tocar.
- Perguntas de **arquitetura** (§14 #1–#6, #9): **encaminhadas ao `architect`** junto com o RESEARCH.
- K13 (44 sub-poms + 1 raiz = 45) confirmado por contagem em 2026-11-21.

**Pendências restantes**: RESEARCH (≥2 alternativas com fonte, trade-off e recomendação) → ADR-INDEX → ADRs, nesta ordem. Nenhuma implementação autorizada.
