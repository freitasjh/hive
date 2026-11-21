---
prd_id: langchain4j-ai-services
research_version: 1
type: refactor-study
title: "Pesquisa — centralização de regras e adoção de Ai Services/ChatMemory (LangChain4j 1.19.0) no backend do hive"
dono_da_decisao: architect (RESEARCH apenas fornece insumo)
dono_do_valor: product-manager
agente: engineer (md-only)
data: 2026-11-21
escopo: "Leitura A — todo o backend (backend/**); planejamento, sem implementação"
insumos:
  - ".spec/prd/langchain4j-ai-services/PRD.md (§16 aprovado em 2026-11-21)"
  - ".spec/prd/langchain4j-ai-services/TECH-BRIEF.md (A1–A15, alternativas A–D, K1–K13)"
esteira: "PRD (aprovado) → RESEARCH (este arquivo) → ADR-INDEX → ADR → branch → SDD"
status: insumo-entregue
---

# RESEARCH — Ai Services no backend do hive

> **Natureza.** Insumo técnico. **Não decide arquitetura** e não autoriza implementação. Quem decide é `architect`, depois do `ADR-INDEX` (`.agents/rules/prd-adr-pipeline.md:15-16`; `PRD.md:204-218`).
> **Este lote.** Artefato único: este arquivo. Nenhum `ADR-INDEX.md`, `ADRs/**`, `SPEC.md`, `TASKS.md`, `VERIFY.md`, `workflow-state.json`, código, config ou teste criado/alterado por este agente. Alterações pré-existentes de outros agentes no `git status` (frontend, `backend/dependencies/.flattened-pom.xml`) não pertencem a este lote.
> **Convenção.** Afirmação de API = `Afirmação — URL — data da consulta (2026-11-21)`. Fontes `raw.githubusercontent.com/langchain4j/langchain4j/1.19.0/...` são fixadas na tag; páginas `docs.langchain4j.dev` são **não versionadas** e rotuladas como tal. Onde não há fonte, o texto diz explicitamente "inferência (sem fonte consultada)".

---

## 1. Pergunta de pesquisa e critérios de decisão

**Pergunta.** Como centralizar as regras do turno de chat (montagem/ordem do `SystemMessage`, conversão de histórico, retry, classificação de erro, limites, isolamento de tenant, telemetria) e adotar Ai Services/ChatMemory do LangChain4j 1.19.0 no backend do hive, **sem** perder o histórico persistido em JPA, a telemetria medida, o isolamento de tenant nem a ordem atual do system message — e qual das alternativas A–D minimiza custo e acoplamento com o stack atual?

**Critérios explícitos** (mesmos cinco do `TECH-BRIEF.md:230`, aqui operacionalizados para permitir comparação; a **ponderação** entre eles é decisão do `architect`, não deste insumo):

| Critério | Pergunta operacional | Evidência de apoio |
|---|---|---|
| **Custo de mudança** | Quantos módulos/classes mudam; quantas ITs de caracterização são necessárias? | `TECH-BRIEF.md:105-188` (R1–R7), `:281-291` (5 ADRs) |
| **Acoplamento de módulo** | A alternativa cria aresta nova entre módulos ou muda ciclo de beans? | `TECH-BRIEF.md:97` (grafo), `:87` (aresta `publicapi/impl → agent/impl`) |
| **Testabilidade** | Dá para capturar prompt, ordem de mensagens e números de token em teste? | `TECH-BRIEF.md:234,235` (testes atuais), A7/A8 |
| **Segurança** | Preserva rejeição de `role=system`, ordem do system message e isolamento de tenant? | K4, K9, K2; `TECH-BRIEF.md:167-172` |
| **Aderência ao stack** | Compatível com Java 25 preview, Spring Boot 4.1.0, LangChain4j 1.19.0, `provided` em quase todo módulo? | `backend/pom.xml:36-39`, `backend/dependencies/pom.xml:8-10,37`, `TECH-BRIEF.md:193-196` |

**Restrição de fronteira (não negociável neste estudo):** histórico integral permanece no repositório JPA do hive — LangChain4j oferece "memory", não "histórico" (A12; `https://docs.langchain4j.dev/tutorials/chat-memory` — 2026-11-21, **não versionada**). Nenhuma alternativa propõe remover `chat_message`/`ChatSession` (`ChatSession.java:11-26`, `ChatMessageServiceImpl.java:68-74`).

---

## 2. Fontes e deepsearch adicional obrigatório

### 2.1 Fontes já fixadas no PRD (reutilizadas)

S1 `https://raw.githubusercontent.com/langchain4j/langchain4j/1.19.0/langchain4j/src/main/java/dev/langchain4j/service/AiServices.java` — 2026-11-21 (tag 1.19.0)
S2 `https://raw.githubusercontent.com/langchain4j/langchain4j/1.19.0/langchain4j/src/main/java/dev/langchain4j/service/memory/ChatMemoryAccess.java` — 2026-11-21
S3 `https://raw.githubusercontent.com/langchain4j/langchain4j/1.19.0/langchain4j-core/src/main/java/dev/langchain4j/store/memory/chat/ChatMemoryStore.java` — 2026-11-21
S4 `https://raw.githubusercontent.com/langchain4j/langchain4j/1.19.0/langchain4j/src/main/java/dev/langchain4j/memory/chat/MessageWindowChatMemory.java` — 2026-11-21
S5 `https://docs.langchain4j.dev/tutorials/chat-memory` — 2026-11-21 (**não versionada**)
S6 `https://docs.langchain4j.dev/tutorials/ai-services` — 2026-11-21 (**não versionada**)

### 2.2 Fontes novas deste deepsearch (consultadas em 2026-11-21)

| ID | Fonte | Tipo / ressalva |
|---|---|---|
| S7 | `https://github.com/langchain4j/langchain4j/issues/6236` — 2026-11-21 (issue aberta 2026-08-31, fechada como `completed` em 2026-09-08; resposta do maintainer `dliubarskyi`) | issue oficial; relato de teste do maintainer, não matriz de suporte formal |
| S8 | `https://docs.langchain4j.dev/tutorials/spring-boot-integration/` — 2026-11-21 | documentação oficial **não versionada**; exemplos hoje em `1.24.0-beta34` |
| S9 | `https://central.sonatype.com/artifact/dev.langchain4j/langchain4j-spring-boot4-starter/1.19.0-beta29` — 2026-11-21 (POM publicado) | repositório de artefatos; POM efetivo do starter SB4 |
| S10 | `https://raw.githubusercontent.com/langchain4j/langchain4j/1.19.0/langchain4j/src/main/java/dev/langchain4j/service/MemoryId.java` — 2026-11-21 | tag 1.19.0 |
| S11 | `https://raw.githubusercontent.com/langchain4j/langchain4j/1.19.0/langchain4j/src/main/java/dev/langchain4j/service/AiServices.java` — 2026-11-21 (re-leitura integral do builder; 1294 linhas) | tag 1.19.0 |
| S12 | `https://github.com/langchain4j/langchain4j/blob/main/docs/docs/integrations/language-models/ollama.md` — 2026-11-21 | branch `main`, **não fixada na 1.19.0**; usada só como indicador de onde `maxRetries` mora |

### 2.3 D1 — starter × Spring Boot 4.1.0 × LangChain4j 1.19.0 (K10 / A15)

**Antes (TECH-BRIEF:222, 224, 237):** compatibilidade **NÃO verificada** → inferência.

**Depois (fontes S7–S9, 2026-11-21):**

- D1.1 — Convenção oficial: `-spring-boot4-starter` para Spring Boot 4; `-spring-boot-starter` para Spring Boot 3. Integração suporta **Spring Boot 4 (4.0+)** e Spring Boot 3 (3.5+); famílias liberadas juntas, mesmo número de versão — `https://docs.langchain4j.dev/tutorials/spring-boot-integration/` — 2026-11-21 (**não versionada**).
- D1.2 — Causa do `NoClassDefFoundError: ...ClientHttpRequestFactorySettings` relatado: uso do starter **SB3** (`langchain4j-ollama-spring-boot-starter:1.19.0-beta29`) em Spring Boot 4.1.1. O maintainer afirma que as linhas SB4 existem desde `1.13.0-beta23` (2026-04-09), incluindo `1.19.0-beta29` — `https://github.com/langchain4j/langchain4j/issues/6236` — 2026-11-21.
- D1.3 — Teste relatado pelo maintainer em SB 4.1.1 / Java 25: `-spring-boot-starter:1.19.0-beta29` reproduz o erro; `-spring-boot4-starter:1.19.0-beta29` inicia e cria o bean `OllamaChatModel`; `-spring-boot4-starter:1.20.0-beta30` também inicia. Ele afirma não haver gap específico de 4.1 — `https://github.com/langchain4j/langchain4j/issues/6236` — 2026-11-21.
- D1.4 — O POM de `langchain4j-spring-boot4-starter:1.19.0-beta29` declara `dev.langchain4j:langchain4j:1.19.0` e `org.springframework.boot:spring-boot-starter:4.0.5` — `https://central.sonatype.com/artifact/dev.langchain4j/langchain4j-spring-boot4-starter/1.19.0-beta29` — 2026-11-21. Ou seja: starter **beta29**, core **1.19.0 GA**, build contra SB **4.0.5** (não 4.1.0).
- D1.5 — O starter cria beans por autoconfiguração e os injeta em interfaces `@AiService`; se houver múltiplos beans do mesmo tipo sem `@Primary`, a aplicação **falha ao iniciar**; há modo `wiringMode = EXPLICIT` para nomear beans — `https://docs.langchain4j.dev/tutorials/spring-boot-integration/` — 2026-11-21 (**não versionada**). Hoje o hive não declara beans `ChatModel` e constrói clientes por turno (`TECH-BRIEF.md:196-197`; `LangChain4jClientFactory.java:75-105`), com 5 providers na fábrica (`TECH-BRIEF.md:71`).

**Resultado D1:** premissa "NÃO verificada" de A15/K10 **confirmada com ressalvas** — existe fonte oficial de que o artefato SB4 correto funciona em SB 4.1.x; **não** existe fonte consultada de que a combinação exata `starter 1.19.0-beta29 + core 1.19.0 GA + SB 4.1.0` seja suportada como matriz formal. Ressalvas (beta vs GA, build contra 4.0.5, docs já em 1.24.0-beta34, colisão de beans) ficam como insumo, não como conclusão de adoção.

### 2.4 D2 — `@MemoryId` com `UUID` do domínio

- D2.1 — Fonte fixada na tag: parâmetro anotado com `@MemoryId` **pode ser de qualquer tipo, desde que implemente corretamente `equals()` e `hashCode()`**; quando `@MemoryId` é usado, `ChatMemoryProvider` **deve** ser configurado — `https://raw.githubusercontent.com/langchain4j/langchain4j/1.19.0/langchain4j/src/main/java/dev/langchain4j/service/MemoryId.java` — 2026-11-21.
- D2.2 — `ChatMemoryProvider.get(Object id)` é chamado para cada memoryId **ainda não visto**; a instância retornada é retida e gerenciada pelo AiService — `https://raw.githubusercontent.com/langchain4j/langchain4j/1.19.0/langchain4j/src/main/java/dev/langchain4j/service/AiServices.java` — 2026-11-21. Consequência (inferência a partir de D2.1 + D2.2): estabilidade do identificador entre chamadas determina continuidade da memória; `turnId` mudaria a cada turno, `sessionId` não. **Não é decisão** — é insumo para a pergunta #3.
- D2.3 — `java.util.UUID` satisfaz o requisito de `equals`/`hashCode` (contrato padrão do tipo). Já passar a entidade `ChatSession` diretamente como `@MemoryId` dependeria da implementação de `equals`/`hashCode` dessa entidade — **inferência (sem fonte consultada); verificar em `ChatSession.java:11-26` antes de qualquer implementação**.
- D2.4 — Sem parâmetro `@MemoryId`, o valor do memoryId é a string `"default"` (javadoc de `systemMessageProvider`) — S1/S11 — 2026-11-21. Relevante para a alternativa B (stateless).
- D2.5 — Aviso de concorrência: AI Service **não** deve ser chamado simultaneamente para o mesmo `@MemoryId` (corrompe `ChatMemory`); não há mecanismo nativo de proteção — S1/S11 — 2026-11-21. Reforça K2.

**Resultado D2:** compatibilidade de tipo confirmada (UUID é elegível); identidade estável e semântica de `equals` da entidade ficam como verificação pendente.

### 2.5 D3 — builder do AiServices 1.19.0 expõe config de retry?

- D3.1 — Re-leitura integral do builder em S1/S11 (2026-11-21) lista, entre outros: `create`, `builder`, `chatModel`, `streamingChatModel`, `systemMessage`, `systemMessageProvider`, `systemMessageProviderWithContext`, `systemMessageTransformer`, `userMessage`, `userMessageProvider`, `chatMemory`, `chatMemoryProvider`, `chatRequestTransformer` (2 sobrecargas), `moderationModel`, `tools`, `toolProvider(s)`, `executeToolsConcurrently`, `maxToolCallingRoundTrips`, callbacks de tool, `toolSearchStrategy`, `contentRetriever`, `retrievalAugmentor`, `registerListener(s)`, `unregisterListener(s)`, `inputGuardrailsConfig`, `outputGuardrailsConfig`, `inputGuardrailClasses`, `inputGuardrails`, `outputGuardrails`. **Não há método de configuração de retry de transporte** (`maxRetries`, `retryPolicy` ou equivalente) no builder 1.19.0 — `https://raw.githubusercontent.com/langchain4j/langchain4j/1.19.0/langchain4j/src/main/java/dev/langchain4j/service/AiServices.java` — 2026-11-21.
- D3.2 — O que pode confundir: `maxRetries` aparece em config de **guardrails** (re-validação/re-prompt de output) e `maxToolCallingRoundTrips(int)` (padrão 100) conta **round-trips de tool calling** — ambos em S1/S11 — 2026-11-21. **Nenhum dos dois é retry de transporte HTTP.**
- D3.3 — Retry de transporte continua responsabilidade do `ChatModel`: parâmetro `maxRetries` existe em builders de modelos — `https://github.com/langchain4j/langchain4j/blob/main/docs/docs/integrations/language-models/ollama.md` — 2026-11-21 (branch `main`, **não fixada na 1.19.0**). No hive, a política já está na fábrica (`MAX_RETRIES = 0` em `LangChain4jClientFactory.java:41`, replicado nos 5 builders em `:115,135,153,169,185`) e no wrapper `LlmRetryPolicy.java:29-146` (`TECH-BRIEF.md:130-136`).

**Resultado D3:** confirmado com fonte — adotar Ai Services **não** remove nem substitui a política de retry do hive. R3/K7/K12 permanecem intactos e devem ser tratados no ADR 4.

### 2.6 Fatos de API adicionais encontrados (ampliam A1–A15)

| # | Afirmação — fonte — data | Uso previsto |
|---|---|---|
| A16 | `systemMessageTransformer(UnaryOperator<String>)` e `systemMessageTransformer(BiFunction<String,InvocationContext,String>)` existem desde 1.12.0; transformam o **conteúdo** do system message depois de `@SystemMessage`/provider e antes de `chatRequestTransformer` — S1/S11 — 2026-11-21 | insumo para R1 (não resolve sozinho a **ordem** de mensagens em memória — K4) |
| A17 | `registerListener(AiServiceListener<I>)`, `registerListeners(...)` e `unregisterListener(s)` existem no builder 1.19.0 — S1/S11 — 2026-11-21 | insumo para R7/K5 (telemetria); **inferência (sem fonte consultada)** sobre equivalência de fidelidade com `Result<T>`/`ChatResponse` — verificar no ADR 4 |
| A18 | Consulta paralela em docs oficiais mostra `ChatModelListener` beans e `langchain4j.executor.use-spring-task-executor=true` como mecanismo do starter para carregar tracing/MDC/security context em invocação assíncrona — `https://docs.langchain4j.dev/tutorials/spring-boot-integration/` — 2026-11-21 (**não versionada**) | insumo para K9; **inferência (sem fonte consultada)** de que isso propague `ScopedValue<UUID>` do `TenantContext` — verificar antes de usar como mitigação |

---

## 3. Alternativas reavaliadas com fonte

Critérios para todas: custo, acoplamento, testabilidade, segurança, aderência (§1). "Risco K" refere-se a `TECH-BRIEF.md:245-258` + K13 (`PRD.md:141,218`). "ADR" refere-se aos 5 candidatos de `PRD.md:104-110`.

### Alternativa A — centralizar regras mantendo `client.chat(List<ChatMessage>)`

**Descrição.** Manter a chamada direta atual (`LangChain4jTurnGateway.java:83` — `retryPolicy.call(() -> client.chat(prompt))`), mas extrair as 7 regras de `TECH-BRIEF.md:101-188` para funções/portas compartilhadas entre `agent/impl` e `publicapi/impl`. Nenhuma API nova do LangChain4j entra.

**Fontes.** Código do repo: `LangChain4jTurnGateway.java:66-69,120-129`; `ChatServiceImpl.java:194,210-219`; `PublicChatServiceImpl.java:151-167`; `agent/api/.../ProviderMessages.java:48-63`; `LlmRetryPolicy.java:29-146`; `TECH-BRIEF.md:105-188,234`.

| Critério | Avaliação de insumo |
|---|---|
| Custo | Baixo — nenhuma troca de mecanismo; apenas extração (inferência a partir das contagens de ocorrência em `TECH-BRIEF.md:105-188`) |
| Acoplamento | Nenhuma aresta nova; pode até reduzir a duplicação entre `agent/api` e `publicapi/impl` (K11) |
| Testabilidade | Alta — testes atuais já capturam prompt em `LangChain4jTurnGatewayTest.java:97` e `ChatServiceImplTest.java:315` (`TECH-BRIEF.md:234`) |
| Segurança | Preserva a ordem atual "template primeiro" (`LangChain4jTurnGateway.java:121-128`; reforço em D3 do parecer `fechamento-d5-d7-d3`) e a rejeição de `role=system` (`PublicChatServiceImpl.java:69-73`) |
| Aderência | Máxima — não muda versões, escopo `provided` nem ciclo de beans (`TECH-BRIEF.md:193-196`) |

**Prós.** Risco quase zero; ataca diretamente K11/K4; serve de pré-condição para qualquer ADR posterior.
**Contras.** Não explora Ai Services; não resolve sozinho telemetria (K5/K6), retry (K7/K12) nem aresta `publicapi/impl → agent/impl` (K3).
**Riscos K tratados:** K4, K11 (mitigação direta); K1/K2/K9 (intocados — sem memória nova); K5/K6/K7/K8/K12 (intocados); K3/K10/K13 (intocados).
**Impacto nos 5 ADRs:** ADR 1 = núcleo; ADR 4 = pré-condição para unificar retry/telemetria; ADR 5 = pré-condição para fronteira; ADR 2 e ADR 3 = neutros.

### Alternativa B — `AiServices.builder` manual, stateless (sem `ChatMemory`)

**Descrição.** Substituir a montagem de prompt/parsing dentro do gateway por um proxy de interface criado com `AiServices.builder(Classe).chatModel(model)...build()`, sem `chatMemory`/`chatMemoryProvider`. Nenhum starter (`langchain4j-spring-boot-starter` ausente — `TECH-BRIEF.md:196`).

**Fontes.** A1, A2, A4, A5, A7, A8, A14 (PRD §3:60-74; S1/S6 — 2026-11-21); A16/A17 (S11 — 2026-11-21); D3.1 (S11 — 2026-11-21); `TECH-BRIEF.md:196-198`.

| Critério | Avaliação de insumo |
|---|---|
| Custo | Médio — gateway passa a construir proxy; fábrica e retry atuais permanecem (D3.3) |
| Acoplamento | `agent/impl` passa a depender da API de serviço; `agent/api` mantém a porta `ChatTurnGateway` (`ChatTurnGateway.java:29-36`) — inferência de que a porta pode ficar, decisão do architect (pergunta #2) |
| Testabilidade | Média-alta — proxy é interface (A14); ITs de caracterização existem (`TECH-BRIEF.md:95`) |
| Segurança | Sem `ChatMemory`, o mecanismo de reordenação de A11 não entra; **mas** a ordem efetiva `SystemMessage` × histórico precisa de IT de caracterização (inferência; verificar) |
| Aderência | Alta — API confirmada na tag 1.19.0 (S1), sem starter novo, sem mudança de `provided` |

**Prós.** Usa API confirmada com fonte primária; `Result<T>` dá acesso a `TokenUsage`/`FinishReason` (A7) para telemetria; nenhum risco de store/concorrência de C.
**Contras.** Telemetria precisa provar paridade de `promptMessages`/`promptChars` e números medidos (K5); sem `@MemoryId`, memoryId default é `"default"` (D2.4) — irrelevante se não houver memória, mas a distinção precisa ficar explícita no ADR.
**Riscos K tratados:** K1/K2/K9 (evitados — sem memória); K4 (mitigado por IT, não por construção); K5 (exige `Result<T>`); K3, K6, K7, K8, K10, K11, K12, K13 (intocados).
**Impacto nos 5 ADRs:** ADR 2 = núcleo; ADR 1 = pré-condição; ADR 4 = telemetria com `Result<T>`; ADR 5 = fronteira; ADR 3 = só se o architect decidir evoluir para C.

### Alternativa C — B + `ChatMemoryProvider`/`ChatMemoryStore` sobre JPA (`@MemoryId = sessionId`)

**Descrição.** Acrescentar memória por sessão com store adaptando as tabelas JPA existentes, usando o `UUID` da sessão como `@MemoryId`.

**Fontes.** A3, A9, A10, A11, A12, A13 (PRD §3:62-73; S2/S3/S4/S5 — 2026-11-21); D2.1–D2.5 (S10/S11 — 2026-11-21); `ChatSession.java:11-26`; `ChatMessageServiceImpl.java:68-74`.

| Critério | Avaliação de insumo |
|---|---|
| Custo | Alto — novo adaptador de store, política de evict, testes de corrida |
| Acoplamento | Adiciona dependência de store em `agent/impl` ou módulo novo; `@MemoryId` acopla assinatura do proxy ao identificador de domínio |
| Testabilidade | Média — A8/D2.5 exigem teste de concorrência por memoryId |
| Segurança | **Maior risco**: memory ≠ history (A12), evict remove mensagens do store (A13), chamada concorrente corrompe memória (A8), `ScopedValue` não propaga em callback (K9/R6) |
| Aderência | Média — JPA + `ScopedValue` do hive não casam naturalmente com store assíncrono (`TECH-BRIEF.md:174`) |

**Prós.** Semântica de memória por conversa pronta; isolamento por `sessionId` é compatível com o tipo `UUID` (D2.1/D2.3).
**Contras.** Não substitui o histórico persistido (A12) — risco de duplicidade de fontes de verdade; evict destrutivo (A13); concorrência sem proteção nativa (A8).
**Riscos K tratados:** K2 e K9 (agravados — exigem política explícita); K1 e K4 (agravados se memória virar fonte da UI); K5/K6/K7/K8/K12 (intocados); K11 (piora se houver duas conversões + duas memórias).
**Impacto nos 5 ADRs:** ADR 3 = núcleo; ADR 2 = pré-condição; ADR 1 e ADR 5 = fronteira/histórico; ADR 4 = telemetria.

### Alternativa D — `langchain4j-spring-boot-starter` (autoconfiguração)

**Descrição.** Adotar o starter com criação de beans de `ChatModel`/`ChatMemory`/Ai Service por autoconfiguração e anotação `@AiService`.

**Fontes reavaliadas.** S7, S8, S9 (2026-11-21) + A15 (`PRD.md:74`), `TECH-BRIEF.md:220,237`; ausência de starter no repo em `TECH-BRIEF.md:196`.

| Critério | Avaliação de insumo |
|---|---|
| Custo | Alto — muda forma de construção de beans; exige `wiringMode = EXPLICIT` ou `@Primary` se houver múltiplos beans do mesmo tipo (D1.5) |
| Acoplamento | Alto — autoconfig cruza módulos e injeta componentes em interfaces anotadas (D1.5); risco de colidir com a fábrica de 5 providers (`TECH-BRIEF.md:71`) |
| Testabilidade | Média — contexto Spring mais pesado; sintaxe anotacional do starter confirmada em S8 (**não versionada**) |
| Segurança | Mesmos riscos de B/C, mais superfície de config; A18 (**inferência**) não prova propagação de `ScopedValue` |
| Aderência | **Reavaliada**: compatibilidade SB4 **verificada com ressalvas** (D1.1–D1.4) — deixa de ser "baixa até verificação"; permanece a divergência beta29 (starter) × GA (core) × SB 4.0.5 (build) × SB 4.1.0 (projeto) e o fato de os docs já exemplificarem 1.24.0-beta34 |

**Prós.** Menos código de montagem; integração com observabilidade/HTTP client do Spring (S8 — 2026-11-21); compatibilidade SB4 provada pelo maintainer (D1.3).
**Contras.** A15 era inferência; agora há fonte, mas a combinação exata do projeto não é matriz formal (D1.4); colisão de beans (D1.5); versão beta do starter vs core GA; muda ciclo de beans sem necessidade demonstrada para as 7 regras.
**Riscos K tratados:** K10 (parcialmente resolvido por D1); K3 e K9 (agravados); K2/K4/K5 (iguais a B/C); K13 (intocado).
**Impacto nos 5 ADRs:** ADR 2 = núcleo; ADR 5 = fronteira/ciclo de beans; ADR 3 = se memória via beans; ADR 1 e ADR 4 = indiretos.

---

## 4. Recomendação de insumo (não decisão arquitetural)

**Sequência sugerida pelo insumo:** **A → B**, **C condicionado**, **D condicionado**.

- **A primeiro** — ataca K11/K4 com risco quase zero e cria a base de regras centralizadas que qualquer ADR posterior precisa (`TECH-BRIEF.md:105-188`).
- **B depois** — usa API confirmada na tag 1.19.0 (S1/S11) sem starter e sem memória; evita K1/K2/K9. Telemetria depende de provar paridade com `Result<T>` (A7/K5) — verificação, não afirmação.
- **C condicionado** — só após ADR 3 decidir relação memória × histórico, política de `@MemoryId` e concorrência (A8/D2.5/K2), com persistência JPA preservada (A12/K1).
- **D** — recomendação original do TECH-BRIEF era "bloqueado até verificação" (K10/A15). **O deepsearch desta data realizou a verificação** (D1.1–D1.4): existe fonte oficial de que o artefato `-spring-boot4-starter` correto inicia em SB 4.1.x. Portanto D **deixa de estar bloqueado por ausência de fonte**, mas **permanece sem recomendação de adoção neste insumo**, agora por trade-offs de acoplamento/versão (D1.4/D1.5) e porque a combinação exata do projeto não é matriz formal. **Cabe ao `architect` decidir** se esses trade-offs justificam mudança do ciclo de beans.

**Nada aqui autoriza implementação.** A recomendação é insumo para os ADRs; o veredito é do `architect` (`.agents/rules/prd-adr-pipeline.md:15-16`; `PRD.md:215-218`).

---

## 5. Perguntas de arquitetura encaminhadas ao `architect`

Origem: `PRD.md:183-193`, classificadas como `arquitetura`/`ambas` e encaminhadas na deliberação de 2026-11-21 (`PRD.md:217`). **Insumo = fontes e trade-offs. Nenhuma é respondida aqui.**

| # | Pergunta (PRD §14) | Insumo que o RESEARCH traz |
|---|---|---|
| 1 | AiService substitui todo o `ChatTurnGateway` ou só montagem/parsing? | A1/A5/A14 (S1/S6 — 2026-11-21) e A16/A17 (S11 — 2026-11-21) mostram que o proxy cobre desde mensagens até parsing/telemetria; alternativas A vs B em §3 com custo/acoplamento; K3/K5. Sem resposta. |
| 2 | `ChatTurnGateway` permanece como porta entre `agent/api` e os 2 chamadores? | Porta em `ChatTurnGateway.java:29-36`; dois chamadores em `TECH-BRIEF.md:78,87,97`; aresta `publicapi/impl → agent/impl` (K3); efeito de B/D na forma do proxy. Sem resposta. |
| 3 | `ChatMemory` em memória, store sobre JPA ou nenhuma? `@MemoryId` = `sessionId` ou `turnId`? | A3/A9/A10/A11/A12/A13 (S2–S5 — 2026-11-21); D2.1–D2.5 (S10/S11 — 2026-11-21): tipo UUID é elegível, estabilidade do ID define continuidade (inferência de D2.2), sem proteção de concorrência (A8); K1/K2/K4/K9. Sem resposta. |
| 4 | Como preservar `promptMessages`, `promptChars`, `TokenUsage`, `FinishReason` com `Result<T>`? | A7 (S1/S6 — 2026-11-21); A17 (S11 — 2026-11-21) oferece listeners como via alternativa, com fidelidade **não verificada** (inferência); K5 e débito K6; baseline em `TurnReply.java:45-113`, `TurnOutcomeMapper.java:174-193`. Sem resposta. |
| 5 | Política de concorrência por sessão, dado A8? | A8 (S1/S6 — 2026-11-21) e D2.5 (S11 — 2026-11-21): corrupção sem mecanismo nativo; `ChatMemoryAccess.evictChatMemory` (A9); K2/K9. Sem resposta. |
| 6 | Adotar `langchain4j-spring-boot-starter`? | D1.1–D1.5 (S7/S8/S9 — 2026-11-21): compatibilidade SB4 verificada com ressalvas; divergência beta29/GA/4.0.5; colisão de beans e `wiringMode = EXPLICIT`; alternativa D em §3; K10. Sem resposta. |
| 9 | Aresta `publicapi/impl → agent/impl` entra neste PRD ou fica como débito separado? | `publicapi/impl/pom.xml:33-37`, `PublicChatServiceImpl.java:9-10` (K3); parecer `parecer-consolidado-2026-09-27` e `fechamento-d5-d7-d3` (D5); impacto em ADR 5; alternativas A/B/C/D não eliminam a aresta por si mesmas (inferência a partir de `TECH-BRIEF.md:97,249`). Sem resposta. |

---

## 6. Produto empurrado para os ADRs — não respondido

Deliberação humana de 2026-11-21 (`PRD.md:215-216`): perguntas de produto/ambas #7, #8 e #10 ficam para decisão nos ADRs. Este RESEARCH **não as responde**.

| # | Pergunta | Destino registrado | Insumo factual (sem veredito) |
|---|---|---|---|
| 7 | `maxTokens` tem teto único e onde mora — DTO, gateway ou fábrica? (K8) | ADR (decisão de produto) | `InferenceParamsDTO.java:9-19` valida `[256,4096]`; `LangChain4jTurnGateway.java:66-82` repassa sem clamp; `LangChain4jClientFactory.java:116-193` aplica por provider; parecer antigo dizia que o gateway clamparia — divergência histórica (`TECH-BRIEF.md:156`) |
| 8 | Corrigir `turnId`/`clientId` da trace é pré-condição do ADR de telemetria ou ADR próprio? (K6) | ADR (decisão de produto + arquitetura) | `ChatTrace.java:47-49` tem os campos; `TurnOutcomeMapper.java:181-189` não os popula; `LoggingChatTraceRecorder.java:38-39` imprime `null`; `PublicChatServiceImpl.java:192,200,212` tem os valores (`TECH-BRIEF.md:184,252`) |
| 10 | Confirmar que o PRD registra a restrição de não tocar o ciclo MFA | ADR (confirmação de produto) | `PRD.md:118,177,194`; `TECH-BRIEF.md:56`; `workflow-state.json:2-6` — nenhuma alternativa deste RESEARCH propõe tocar `security/**/Mfa*` ou `identity/**/Mfa*` |

---

## 7. Matriz de rastreabilidade

### 7.1 Alternativa → ADR candidato

| Alternativa | ADR 1 (conversão/system) | ADR 2 (integração Ai Service) | ADR 3 (memória/@MemoryId) | ADR 4 (retry/telemetria) | ADR 5 (fronteira/limites) |
|---|---|---|---|---|---|
| A — centralizar mantendo `client.chat` | **núcleo** | — | — | pré-condição | pré-condição |
| B — `AiServices.builder` stateless | pré-condição | **núcleo** | — (se stateless) | `Result<T>` (A7) | fronteira |
| C — B + store JPA | pré-condição | pré-condição | **núcleo** | telemetria | fronteira |
| D — starter | indireto | **núcleo** (se memória via beans) | indireto | indireto | **ciclo de beans** |

### 7.2 Risco K → tratamento

| Risco | Onde é tratado neste RESEARCH |
|---|---|
| K1 memória ≠ histórico | §2.1 (A12), §1 (restrição), §3 C, §5 #3; persistência JPA permanece |
| K2 concorrência `@MemoryId` | D2.5 (S11), §3 B/C/D, §5 #5 |
| K3 aresta `publicapi/impl → agent/impl` | §3 A/D, §5 #2 e #9, §7.1 ADR 5 |
| K4 ordem do system message | §3 A/B/C, A11/A16, §5 #3, §7.1 ADR 1 |
| K5 perda de telemetria | A7/A17, §3 B, §5 #4, §6 #8, §7.1 ADR 4 |
| K6 trace não atribuível | §6 #8, §5 #4 (débito pré-existente) |
| K7 javadoc vs jitter | D3.3, §3 A, §7.1 ADR 4 |
| K8 clamp de `maxTokens` | §6 #7, §3 B (parâmetros por chamada em A5), §7.1 ADR 5 |
| K9 `ScopedValue` não propaga | §3 C/D, A18 (**inferência**), §5 #5, §7.1 ADR 3/5 |
| K10 starter não verificado | **D1.1–D1.5 (resolvido com ressalvas)**, §3 D, §5 #6, §4 |
| K11 conversão divergente | §3 A (núcleo), §7.1 ADR 1 |
| K12 retry 2× + timeout vs cota | D3.3, §6 #7, §7.1 ADR 4/5 — débito de produto, não resolvido aqui |
| K13 44 sub-poms + 1 raiz = 45 | `PRD.md:47,218`; rótulo "44 pom.xml" em `TECH-BRIEF.md:20` interpretado como 44 sub-poms; reconciliação menor, confirmada em 2026-11-21 |

---

## 8. Premissas não validadas, riscos do próprio estudo e encerramento

**Premissas não validadas (levadas ao architect/product-manager, não resolvidas por palpite):**

1. Combinação exata `langchain4j-spring-boot4-starter:1.19.0-beta29` + core `1.19.0` GA + **Spring Boot 4.1.0** (projeto) não é matriz formal publicada; fonte oficial cobre 4.1.1 em teste de maintainer e POM build 4.0.5 (D1.3/D1.4).
2. `equals`/`hashCode` da entidade `ChatSession` — **inferência (sem fonte consultada); verificar em `ChatSession.java:11-26`** antes de qualquer `@MemoryId` com entidade.
3. Equivalência de fidelidade entre `Result<T>` e `AiServiceListener`/`ChatModelListener` para `promptMessages`/`promptChars`/`TokenUsage`/`FinishReason` — **inferência (sem fonte consultada); verificar no ADR 4**.
4. Propagação de `ScopedValue<UUID>` pelo executor do starter — **inferência (sem fonte consultada); verificar antes de usar A18 como mitigação de K9**.
5. Ordem efetiva `SystemMessage` × histórico na alternativa B — **inferência; exige IT de caracterização** (a regra atual está em `LangChain4jTurnGateway.java:121-128`).

**Riscos do estudo em si:** nenhum artefato de implementação nasce aqui; toda conclusão depende de verificação futura nos ADRs. Nenhuma mudança toca MFA (`PRD.md:118,194`; `TECH-BRIEF.md:56`). Nenhuma assinatura de API foi aceita de memória: as afirmações vêm de S1–S12 com URL + data de 2026-11-21; onde faltou fonte, o texto diz "inferência".

**Co-assinatura técnica (engineer).** Insumo entregue: este `RESEARCH.md`. Contribuição nova sobre o `TECH-BRIEF.md`: (1) K10/A15 verificado com fonte oficial (D1) — incompatibilidade era uso de starter SB3 em SB4, não ausência de linha SB4; (2) `@MemoryId` aceita qualquer tipo com `equals`/`hashCode` na tag 1.19.0 (D2); (3) builder do AiServices 1.19.0 **não** expõe retry de transporte (D3) — retry permanece na fábrica/`LlmRetryPolicy`. **Ressalva:** `engineer` apenas fornece insumo — **não decide arquitetura**; decisão é do `architect` na esteira ADR-INDEX → ADR (`.agents/rules/prd-adr-pipeline.md:15-16`).

**Próximo passo da esteira (não executado neste lote):** `ADR-INDEX.md` → `ADRs/ADR-00N-{slug}/ADR.md`, nesta ordem, por `architect`. Nenhuma implementação autorizada.
