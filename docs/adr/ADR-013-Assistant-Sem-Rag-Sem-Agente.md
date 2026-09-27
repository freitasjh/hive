# ADR-013 — Escopo do produto: Assistant (chat simples), sem base de conhecimento e sem agente

- **Status:** Aceito (decisão de escopo 2026-09-26 · decisão de migrations 2026-09-27 — executada, não proposta)
- **Data:** 2026-09-27
- **Contexto:** Hive AI (Java 21 + Spring Boot 4.1.0 + Vue 3.5 + PostgreSQL 16)
- **Decisores:** usuário (decisão de escopo e de migrations) + architect
- **Supersede:** ADR-002, ADR-003 (parcialmente), ADR-007 (parcialmente), ADR-008, ADR-010
- **Supersede parcialmente:** ADR-009 (a decisão de LangChain4j como framework único continua em pé; morreram o catálogo de providers como domínio de agente e o path do probe de validação) e ADR-012 (a decisão da porta `ChatTurnGateway` continua em pé; morrem as opções C, D e E)
- **Intactos:** ADR-011 (provider custom — decisão em pé, falta construção) e `ADR_Prompt_Versioning` (idêntico: `Proposed`, nunca construído)
- **Fonte da verdade desta decisão:** este arquivo. A nota `arquitetura/projetos/hive/decisoes/scope-assistant-sem-rag` do Brain MCP é **registro secundário e complementar** — nunca foi fonte primária, e a partir de 2026-09-27 nenhum ADR aponta para ela como destino de supersessão.

## Contexto

O produto nasceu como plataforma de agentes: `AgentEntity` era o aggregate root, cada agente tinha
provider, modelo, temperatura, tools e bases de conhecimento vinculadas. Sobre essa estrutura foi
construído um RAG completo: módulo `knowledge` próprio, Flyway próprio, pipeline oficial do
LangChain4j, embeddings reais, threshold calibrado por família de modelo, três ADRs e um E2E com
provider real.

O RAG **funcionava**. Passava em tudo, com 700 testes verdes, zero dívida, revisado por três rodadas.
E ainda assim era o lugar errado — não porque era ruim, mas porque não era o que ainda não existia.

**O loop elementar do produto não existia.** O chat funcionava, mas estava amarrado ao conceito
`Agent`: para conversar era preciso antes configurar uma entidade `Agent` — system prompt, provider,
modelo, temperatura, parâmetros e vínculo de base de conhecimento. Não havia fluxo "abrir e
conversar". O que estava em construção era a **segunda** capacidade (RAG), e ela foi construída antes
da primeira existir. Essa é a razão da decisão de escopo, e é a única.

A mesma classe de erro apareceu de novo depois, e é registrada aqui como **Regra 1** da seção
"A lição" — não como motivo desta decisão, porque é **posterior** (`15eb7f1`, Batch 3): o
`PromptSelector` pedia `size=100` a um servidor que corta em `50`, devolvendo **400 em toda montagem da
tela**, invisível a 145 testes porque o mock aceita qualquer número. O `PromptSelector` só nasceu no
Batch 2 (`6599a0b`) como parte do Assistant, então não pode ter Motivado a decisão de escopo de
2026-09-26.

## Decisão

1. **O produto é um Assistant**: conversa em que o usuário escolhe o prompt que responde à conversa.
   Referência de produto: o ChatGPT no início — sem agente, sem tools, sem base de conhecimento, não-agentico.
2. **O prompt é escolhido por conversa, na criação da sessão.** A sessão é a autoridade: a partir do
   segundo turno, o id de prompt que o cliente envie é ignorado de propósito.
3. **Provider e modelo são default do tenant** (`tenant_llm_config`, 1:1, `provider` por allowlist
   explícita, `NO ACTION` na FK para tenant — `tenant/dbupdate/.../tenant_db/V3__tenant_llm_config.sql`).
4. **Não existe entidade `Assistant`.** O aggregate root é a própria sessão de chat. Não há
   "assistentes" — há conversas.
5. **Remover, não congelar**: módulo `knowledge`, conceito de `Agent`, RAG, tools, bindings e telas
   correspondentes foram **deletados** do repositório. A reversibilidade ficou por conta da tag
   `pre-removal-rag-agent`, não por flag, esquema ou adaptador no código.
6. **Migrations reescritas** para refletir só criação e configuração. Nenhuma migration existe para
   consertar tabela. Ver seção própria.
7. **A porta `ChatTurnGateway` e o contrato `TurnReply` permanecem** (ADR-012 §Decisão 1 e 3): a
   geração de um turno não depende do conceito removido. O que mudou é o que entra no `TurnRequest`.

## Execução

| Batch | Commit | O que saiu |
|---|---|---|
| 1 — base de conhecimento | `48f36d6` | módulo `knowledge` inteiro (65 arquivos main, 4.895 linhas, 19 de teste), `agent/impl/rag/` (incluindo `RagTurnResolver`, 397 linhas), 2 endpoints de KB, `ChatResponse.ragUsed`/`ragReason`/`citations`, `KbPicker.vue`. Diff **+531 / −16.872** (net **−16.341**, 138 arquivos apagados) |
| 2 — conceito de agente | `6599a0b` | `AgentEntity`, `AgentService(Impl)`, `AgentControllerV1`, `AgentMapperDomain`, `DefaultAgentService` + 6 exceções, `ToolDefinition`, tabela `agent` (20 colunas), telas `AgentsView`/`AgentEditorView`/`KnowledgeBaseList`/`KnowledgeBaseDetail`/`AgentSelector`/`KbPicker`/`knowledge.service.ts`. `POST /v1/agents/validate` promovido a `POST /v1/providers/validate` |
| 3 — fecha o loop | `15eb7f1` | busca de verdade no seletor de prompt (teto de `size` documentado no código, `frontend/src/components/PromptSelector.vue:189`) e limpeza de divergência — entre elas o módulo `commons/test-support`, que não tem mais nenhum módulo listado em `commons/pom.xml` |

Ordem cronológica: decisão de escopo (2026-09-26) → Batch 1 `48f36d6` → Batch 2 `6599a0b` → marco
E2E `ab5cda1` (o loop do Assistant validado com provider real, **depois** da decisão) → Batch 3
`15eb7f1` → decisão de migrations (2026-09-27).

**Criado no lugar:** `tenant_llm_config` (1:1) · `chat_session.prompt_template_id` NOT NULL com FK
`ON DELETE RESTRICT`, com 409 `PROMPT_IN_USE` preventivo e reativo · tela de configuração de LLM do
tenant · `PromptSelector` na criação de sessão · guarda de `to.meta.permission` no router
(`frontend/src/router/index.ts:74-97`), documentada como **UX gate, não fronteira** — a fronteira é
o `@HasPermission` do backend, e o meta existir sem ser lido seria pior do que não existir.

**Preservado byte a byte**, verificado por `git diff` das linhas load-bearing e não por leitura:
split TX1/TX2 · as três classificações de erro (`MODEL_UNAVAILABLE`→422 com evict de discovery,
`MODEL_RETURNED_EMPTY`→422, `ProviderException`→502) · `isModelNotFound` · `TurnReply.toString()`
sem credencial · `ChatTrace` / `LoggingChatTraceRecorder` · isolamento de tenant · `@HasPermission`.

**Testes ao longo dos batches:** 700/118/222 → 376/66/191 (B1) → 376/76/158 (B2) → 371/77/189 (B3).
Zero falhas. Zero `@Disabled` em todo o repositório.

## Alternativas consideradas

| Alternativa | Decisão | Motivo |
|---|---|---|
| **Congelar o RAG** (`@ConditionalOnProperty`, ~1 linha, totalmente reversível) | **Rejeitada** | Mais barata em esforço e a mais reversível das duas. Rejeitada de propósito: o usuário quer **reiniciar a estrutura**, com mais estudo de LangChain4j e avaliação de outros bancos vetoriais além de pgvector. Congelar preserva o código que se decidiu não manter, e mantém a tentação de reativá-lo no próximo sprint |
| **Apagar (escolhida)** | **Adotada** | Custo: reconstrução de ~5.700 linhas de código testado, 3 ADRs e um E2E real. Benefício: a estrutura recomeça do loop de produto, e não de uma camada de infraestrutura já pronta. Reversibilidade preservada pela tag `pre-removal-rag-agent` — o que torna a decisão de escopo reversível e a de infraestrutura, sem custo de manutenção |
| Reescrever in place / reverter e reaplicar | Não existentes como opção | Reescrever preserva as premissas erradas; reverter e reaplicar é o mesmo que apagar, com trabalho extra |

O contra-argumento está registrado sem suavização: congelar custava 1 anotação e apagar custou
5.700 linhas testadas. A escolha foi escopo, e escopo é decisão de produto — não de engenharia.

## A lição

> **Construir a segunda abstração antes do primeiro loop estar fechado custa caro mesmo quando a
> segunda está correta.**

O RAG estava funcional, testado, com E2E real, revisado por três rodadas e documentado por três ADRs.
**E mesmo assim era o lugar errado** — não porque era ruim, mas porque não era o que ainda não
existia. Em 2026-09-26 o loop elementar do produto não existia: o chat funcionava, mas só depois de
configurar uma entidade `Agent`, e não havia fluxo "abrir e conversar". O RAG era a segunda
capacidade, construída antes da primeira.

O sintoma que antecede essa classe de erro: **otimizar a camada de infraestrutura enquanto o loop de
produto está aberto.** E o custo **não aparece em métrica de qualidade** — o RAG passava em tudo,
700 testes verdes, zero dívida, e o loop de produto não existia. Aparece quando um humano tenta
usar.

**Regra 1.** Antes de construir a segunda capacidade, exercite a primeira em ambiente real, por um
humano, no browser. Não por teste de integração, não por `curl`, não por mock. Caso concreto neste
repositório, e **posterior à decisão de escopo** (`15eb7f1`, Batch 3, depois que o `PromptSelector`
nasceu no Batch 2): o seletor pedia `size=100` contra um servidor que corta em `50` — 400 em toda
montagem da tela, invisível a 145 testes porque o mock aceita qualquer número. O conserto durável não
é o número: é o comentário no código citando o teto do servidor (`PromptSelector.vue:189`), para que
o próximo não o suba de novo. Vale registrar a ordem em que as coisas apareceram, porque a tentação
de ler este caso como causa da decisão de escopo é exatamente o erro de cronologia que este ADR
corrige.

**Regra 2.** Rede de segurança estrutural morre junto com o código que ela protegia — e o registro
que importa é o **intervalo**, não o estado final. `HardcodedSimilarityCutOffGuard` é um guard de
análise estática que proibia hardcodar threshold de similaridade. O único decidedor de threshold do
sistema — o RAG — morreu no Batch 1 (`48f36d6`), e entre o Batch 1 e o Batch 3 (`15eb7f1`) o guard
ficou órfão: em `src/main` de um módulo de **test-support**, protegendo um erro que não pode mais
ocorrer, sem quebrar nada e sem que ninguém notasse. O `15eb7f1` removeu o guard e o módulo
`commons/test-support` inteiro (que hoje não tem entry em `commons/pom.xml`). O detalhe que fecha a
lição: estar em `src/main` é precisamente o problema — um guard de verificação de teste, promoted a
código de produção por consequência do empacotamento do módulo, parece util por estar no lugar
certo na árvore de diretórios.

**Anti-padrão explícito.** Otimizar a camada de infraestrutura enquanto o loop de produto está
aberto. E o corolário: **a segunda abstração pode estar correta e ainda assim ser o lugar errado** —
correção não é o critério de priorização; o que ainda não existe é.

## Migrations: reescrita

Decisão de 2026-09-27: **como não há teste nem produção, as migrations são reescritas para refletir
só criação e configuração. Nenhuma migration existe para consertar tabela.**

Isso resolve o bloqueio das **58 sessões de chat** que impediam o ambiente de desenvolvimento de
subir: a V15 (backfill + guard) e a V16 (`DROP TABLE agent`) deixam de existir. O módulo `agent` passa
de **12 migrations** (V2–V10, V14, V15, V16, com buracos em V11–V13 que eram do `knowledge`) para
**~4**, e nenhuma delas corrige nada.

Custo aceito e registrado: o guard da V15 era engenharia de migration exemplar — abortava dentro da
transação, com mensagem autossuficiente, e o reviewer executou ponta a ponta num PostgreSQL 16 real.
Está sendo removido mesmo assim, porque preservar histórico de dado que ninguém vai ler custa mais do
que vale.

Um resíduo que a reescrita **deve resolver em vez de carregar adiante**:
`backend/agent/dbupdate/src/main/resources/db/migration/agent/V9__create_provider_credential.sql`
cria a tabela de credencial de provedor dentro do histórico de migrations do módulo `agent`. É
resíduo do ADR-009, que trocou o provider de agente para provedor sem mover a migration. Como a
reescrita já elimina 8 dos 12 arquivos, é a ocasião de o `provider` voltar a ter o seu próprio
histórico — e o registro disso pertence à reescrita, não a este ADR, que só registra a dívida.

## O que este ADR não decide

- **APIs públicas** — modelo de autenticação e cota, indefinido. É a próxima decisão de produto.
- **`ChatAssistant` / `AIAgent`** (opção D do ADR-012) — morre de vez. Quando existir a primeira tool,
  o eixo será **estratégia de turno** (`SingleShotTurn` / `ToolLoopTurn`, implementações de
  `ChatTurnGateway`), não tipo novo. ADR próprio, gatilho: primeira tool.
- **`skills`** — ADR de plataforma, gatilho: `langchain4j-skills` existir em versão
  não-experimental. Hoje não existe na 1.19.0 e a doc é da 1.20.1-beta30, marcada *experimental*.
- **Reconstrução da base de conhecimento** — não é deste ADR. Quando vier, será com mais estudo de
  LangChain4j e avaliação de outros bancos vetoriais além de pgvector, e começa pelo loop de produto,
  não pela camada de recuperação.

## Gatilhos de reavaliação

1. Existir a **primeira tool** em um turno (reabre o eixo de estratégia de turno).
2. O usuário pedir **memória de longo prazo** ou múltiplas conversas independentes (reabre o aggregate
   root — hoje é a sessão, e talvez volte a precisar de um aggregate acima dela).
3. Surgir requisito de **citação de fonte** (reabre RAG como necessidade de produto, não como
   refinamento de infraestrutura).
4. Estar disponível `langchain4j-skills` em versão estável (reabre a opção E).

## Conformidade

- [ ] Nenhum `AiServices` no código; a porta `ChatTurnGateway` continua sendo a abstração do turno
- [ ] Nenhuma referência a `knowledge`, `ragUsed`, `ragReason`, `citations`, `KbPicker` ou `AgentSelector`
- [ ] `chat_session.prompt_template_id` NOT NULL com FK `ON DELETE RESTRICT`
- [ ] `tenant_llm_config` 1:1 com `provider` por allowlist explícita
- [ ] Split TX1/TX2 e as três classificações de erro intactos (verificado por `git diff` das linhas load-bearing)
- [ ] `commons/test-support` removido e ausente de `commons/pom.xml`
- [ ] Os **6** blocos "Supersedido em 2026-09-27" (ADR-002, ADR-003, ADR-007, ADR-008, ADR-010, ADR-012) apontam para **este** arquivo; o Brain aparece só como registro secundário
- [ ] A referência inline a `scope-assistant-sem-rag` em ADR-009 aponta para este arquivo

## Referências

- Commits: `d8d9ea3` (pipeline oficial de RAG) · `901ae25` (costuras do turno) · `48f36d6` (Batch 1) ·
  `61f38f0` (registro da decisão de escopo) · `6599a0b` (Batch 2) · `896a3d3` (estado) ·
  `ab5cda1` (E2E com provider real) · `15eb7f1` (Batch 3) · tag `pre-removal-rag-agent`
- ADRs: ADR-002 (frontend MVP) · ADR-003 (contrato da API de chat) · ADR-007 (persistência e TX split) ·
  ADR-008 (administração de prompts e agentes) · ADR-009 (LangChain4j framework único) ·
  ADR-010 (RAG manual com pgvector) · ADR-011 (provider custom, intacto) ·
  ADR-012 (porta do turno, parcialmente superado) · `ADR_Prompt_Versioning` (intacto, `Proposed`)
- Código: `backend/tenant/dbupdate/.../tenant_db/V3__tenant_llm_config.sql` ·
  `backend/agent/dbupdate/.../agent/V15__chat_session_prompt_template.sql` ·
  `backend/agent/dbupdate/.../agent/V16__drop_agent.sql` ·
  `frontend/src/router/index.ts:74-97` · `frontend/src/components/PromptSelector.vue:189`
