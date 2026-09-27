# ADR-012 — Porta do turno de chat: `ChatTurnGateway` é a porta; AI Services é implementação alternativa, não pré-requisito

- **Status:** Aceito (revisável — ver "Gatilhos de reavaliação")

> ### ⚠️ Supersedido em 2026-09-27 — decisão `scope-assistant-sem-rag`
>
> Este ADR **não foi reescrito**: ele é registro do raciocínio da época, e reescrever seria apagar a
> lição. A decisão está em **[ADR-013 — Assistant, sem base de conhecimento e sem agente](ADR-013-Assistant-Sem-Rag-Sem-Agente.md)**.
> O Brain (`arquitetura/projetos/hive/decisoes/scope-assistant-sem-rag`) é registro secundário e
> complementar, não fonte primária.
>
> O produto virou um **Assistant**: chat simples, sem agente, sem tools, sem base de conhecimento.
> A **opção D** foi revertida. O que sobrevive — e passou a importar mais — é `ChatTurnGateway` como a
porta do turno: sem RAG, ela é a costura onde o prompt entra como campo do `TurnRequest`, e é o
diagnóstico e o trace (o que achou os 6 bugs do loop elementar) que valem mais agora, não menos.

- **Data:** 2026-09-26
- **Contexto:** Hive AI (Java 21 + Spring Boot 4.1.0 + LangChain4j 1.19.0 + PostgreSQL 16 + pgvector 0.8.2)
- **Decisores:** architect (parecer de validação) + usuário
- **Refina:** ADR-010 §Decisão 2 e §Alternativas ("AiServices exigiria reescrever sessões/TX") — aquela linha continua correta; este ADR registra *por quê*, com o estado do código depois da extração

## Contexto

Até 2026-09-25 o LangChain4j era usado como biblioteca de tipos (`ChatModel`, `RetrievalAugmentor`, `DefaultContentInjector`), não como framework. A doc de RAG estava adotada (commit `d8d9ea3`) e funcionava ponta a ponta. A conclusão de que a lógica do chat estava "chumbada" levou a uma pergunta de arquitetura: adotar `AiServices` como a abstração do turno de chat.

O fluxo de chat do projeto **não é um `String chat(String)`**, e essa é a diferença que decide a questão:

1. A mensagem do usuário é persistida em **TX1, antes** da chamada ao provider; a mensagem do assistant e o touch da sessão são persistidos juntos em **TX2** (`ChatServiceImpl.java:188` e `ChatServiceImpl.java:258`). Um turno que falha em gerar mantém a pergunta — que é o que o usuário precisa para reenviar — e nunca grava linha de assistant, porque `chat_message.content` é `NOT NULL` e uma resposta em branco já chegou a essa coluna como 409 de integridade.
2. Cada turno produz **três observações** que só o chamador consegue nomear: `recordTurn` (`ChatServiceImpl.java:289`), a linha de diagnóstico RAG e a linha de falha. São elas que encontraram os seis defeitos pelos quais este fluxo foi validado, e `systemBlockChars=5028` estável em três rodadas de E2E é a prova de que elas medem, e não estimam.
3. A classificação de `MODEL_UNAVAILABLE` é regra de negócio sobre semântica HTTP de provider (`LangChain4jTurnGateway.java:145`): 404 só é "modelo removido" se o corpo for JSON de erro citando o modelo; 404 com corpo HTML é `baseUrl` sem `/v1` e não pode virar 422 enganoso (`LangChain4jTurnGateway.java:174`).

O estado da extração (commit `901ae25`) — **já implementado, não proposto**:

| Antes | Depois |
|---|---|
| `ChatServiceImpl` 770 linhas / 15 dependências de construtor | 340 linhas / 11 dependências (`ChatServiceImpl.java:94-104`) |
| 6 responsabilidades numa classe | porta + 2 colaboradores com um método cada |
| `resolveRagContext` só testável via `ChatServiceImpl` | `RagTurnResolver` (`RagTurnResolver.java:73`), com `RagRequest` próprio (`:153`) |

Suíte: **700** testes unitários (641 antes desta extração), 118 de integração, 222 de frontend. `langchain4j-pgvector` removido dos dois poms (`backend/dependencies/pom.xml:213-221`; `backend/knowledge/impl/pom.xml:68` agora é o Tika).

## Decisão

1. **A abstração do turno é a porta `ChatTurnGateway`** (`agent/api/.../port/ChatTurnGateway.java:29`), não uma interface gerada por `AiServices`. Um método, `TurnReply execute(TurnRequest)`.
2. **`AiServices` não é adotado agora.** Fica registrado como implementação alternativa da porta, a ser avaliada apenas mediante os gatilhos abaixo.
3. **O contrato da porta é total, não excepcional: falha é valor de retorno, não exceção.** `TurnReply.Status{ANSWERED, EMPTY_RESPONSE, FAILED}` (`TurnReply.java:49-60`). Não é estilo — ver motivo 1 em "Por que não `AiServices`".
4. **`FAILED` carrega a exceção do provider desembrulhada e inalterada** (`LangChain4jTurnGateway.java:77-84`, `TurnReply.java:84-87`). Embrulhar mudaria a classe raiz lida por `RagDiagnostic.causeOf` e o rótulo da trace passaria de `HttpException:not_found` para `<Wrapper>:unexpected`.
5. **Diagnóstico, trace, evict de discovery e as duas linhas de log ficam fora da fronteira.** O que saiu da porta é o que nenhuma das duas pontas consegue ver sozinha: a porta não tem session id, o serviço não tem finish reason (`LangChain4jTurnGateway.java:39`).
6. **Nulo que carrega significado é rejeitado na construção.** `TurnRequest.ragContext` é `requireNonNull` (`TurnRequest.java:61`). Um `null` intermediário era lido como `RAG_DISABLED` e chegava ao cliente como HTTP 200 com `ragReason=rag_disabled` — "não tenho conhecimento" quando a verdade é "ninguém resolveu nada". Um diagnóstico falso é pior que um 500, porque o 500 é honesto.
7. **A porta é escrita para receber um segundo impl, não para ter um.** Nenhuma decisão de prompt, tool ou memória é tomada dentro de `LangChain4jTurnGateway` além das que a doc descreve como comportamento do `ChatModel`.

## Por que não `AiServices`

Quatro razões, cada uma com o ponto do código que a prova. Nenhuma é preferência de gosto.

1. **`AiServices` é `String chat(String)`; não há como devolver os números da trace e a exceção classificada no mesmo retorno.** O prompt é montado *dentro* da chamada que falhou (`LangChain4jTurnGateway.java:68-69`), então quem lança perde `promptMessages`/`promptChars` e o chamador fica com `reply` nulo. A solução foi inventar um envelope de status — `TurnReply.Status` — **por causa** do problema que `AiServices` não resolve. Uma abstração que precisa de um tipo de retorno de três estados só para servir a telemetria já está pagando pelo próprio substituto.
2. **O split TX1/TX2 não tem costura.** `AiServices` é uma função síncrona. Persistir antes com uma transação e depois com outra é exatamente o que o desenho atual faz e o que o contrato de `chat_message.content NOT NULL` exige; adotar a função significa desfazer isso ou pendurar a transação na call.
3. **A classificação de `MODEL_UNAVAILABLE` não tem gancho.** Entre a chamada HTTP e o mapeamento para 422/502 existe a necessidade de gravar trace e de evictar `discoveryService` (`ChatServiceImpl.java:233-239`). Exception mapper é o lugar errado para as duas; sobrariam `registerListener` + mapper, isto é, mais maquinaria do que a que existe hoje — e com a responsabilidade repartida.
4. **O desvio documentado não se beneficia.** O contexto é injetado em `SystemMessage`, não no user turn como na doc, e `buildMessages` respeita a precedência de system prompt do agente (`LangChain4jTurnGateway.java:111-123`). Com `AiServices` isso vira anotação e vira Herança de framework. `KnowledgeContextInjector` já é um `DefaultContentInjector` próprio: **o ponto de injeção já é oficial.**
5. **Os 15 parâmetros de construtor nunca foram o problema.** `AiServices` não reduz nenhum. Resolveram-se com `TurnRequest` (11 dependências hoje, `ChatServiceImpl.java:94-104`); o número era sintoma de classe grande demais, não de framework errado.

## Alternativas Consideradas

| Alternativa | Estado | Motivo |
|---|---|---|
| **A — `AiServices` como interface de chat agora** | Rejeitada | As 5 razões acima. Nenhuma delas é removível sem reescrever TX1/TX2, a trace ou a classificação 404 |
| **B — Porta própria `ChatTurnGateway`, `AiServices` como impl alternativa futura** | **Adotada** (`901ae25`) | Interface que o projeto possui; diagnóstico fora da fronteira; adoção futura sem reescrita |
| **C — `EmbeddingStore` do LangChain4j** | Adiada | `EmbeddingStore` em 1.19.0 tem **5** métodos abstratos (`add(Embedding)`, `add(String, Embedding)`, `add(Embedding, Embedded)`, `addAll(List)`, `search`); `add(String, Embedding)` retorna `void` em 1.19.0 e `String` em 1.20, então reconciliar `embedding_id` = `chunkId` das citations não tem assinatura estável; `removeAll(Filter)` é `default` que lança `UnsupportedOperationException`, e delete por KB viraria no-op indistinguível de sucesso; `minScore` é um `Double` no `EmbeddingSearchRequest` e `0` significa "sem corte", o que colide com o sentinela antigo (conferido no `langchain4j-core-1.19.0.jar`; o builder não expõe validação de faixa); o layout do store oficial (`id/vector/embedding/text/metadata`) não tem `tenant_id`/`kb_id` como colunas, então adotar seria migração de schema, não wrapper. **Já resolvido por outra via:** `KbVectorStore` (`knowledge/api/.../port/KbVectorStore.java:39`, 4 ops em `:50/:60/:64/:67`) é a porta de escrita e `KbRetrievalPort` a de leitura — as duas faces do contrato são vendor-free, então trocar de store é uma classe nova |
| **D — Separar `ChatAssistant`/`AIAgent`** | Adiada | `AgentEntity.tools text[]` existe e **nada** no backend referencia `@Tool`, `ToolSpecification` ou `ToolProvider`: não há uma única tool para orquestrar, então a separação é especulativa. Nome `ChatAssistant` colide com o `ChatAssistantTxService` existente (`ChatServiceImpl.java:85`, que é o helper transacional do assistant) e `AIAgent` duplica `Agent`. Ver "O que este ADR não decide" |
| **E — `skills` + orquestrador** | Bloqueada | `langchain4j-skills` não existe em 1.19.0; a doc é da 1.20.1-beta30 e está marcada *experimental*, com Tool Mode dependente de `ToolProvider` + `AiServices`. Adotar é subir a plataforma de 1.19.0 para beta, o que move o core já pinado. É ADR de plataforma com data de revisão, não feature |

## Consequências

**Positivas**

- A interface é **possuída**, não aceita: a assinatura, a política de nulos e o envelope de status foram escolhidos contra medição deste projeto.
- Diagnóstico, trace, evict e log ficam fora da fronteira, e ficam em uma classe que vê sessão, tenant e agente ao mesmo tempo.
- `AiServices` pode entrar depois como segunda implementação **sem reescrever** o chamador, o diagnosticador nem a trace.
- O split TX1/TX2 fica explícito no código e no Javadoc da classe (`ChatServiceImpl.java:60-66`), em vez de implícito na ordem de statements.
- `RagQuerySettings` (`knowledge/api/.../domain/RagQuerySettings.java:27`) substituiu o sentinela `0d` por hierarquia selada: não existe mais valor que seja ao mesmo tempo "um número" e "uma ausência", o que era a forma pela qual um cut-off por modelo foi hardcoded no lugar errado uma vez.

**Negativas**

- Uma porta com **uma** implementação: YAGNI pago de propósito, e a manutenção do envelope `Status` é nossa.
- **A tentação vai voltar.** Toda vez que alguém ler a doc de `ai-services` vai parecer que a porta reinventou o que a biblioteca faz. Este ADR existe para essa leitura.
- Adotar hooks da biblioteca significa reescrever testes de invariante, não só mover código. Precedente registrado: na extração do Passo A, as guardas `verify(llmClientFactory, never()).createClient(...)` que impedem a chamada LLM não medida no compressor (`RagTurnResolverTest.java:498, 508, 523, 536`) foram apagadas com o código e voltaram **só porque o reviewer as cobrçou**. A lição: toda extração que muda o dono de uma decisão leva os testes que fixavam a decisão junto.
- `TurnReply` carrega `Throwable`, o que é um campo de exceção dentro de um record de retorno: seguro porque `toString` nunca renderiza a falha, só o nome da classe (`TurnReply.java:101-113`) — o `HttpException` carrega o payload bruto do provider e um `toString` gerado despejaria o corpo da resposta no log.

## Gatilhos de reavaliação

Reavaliar **A** (e só A) quando **qualquer** destes ocorrer:

1. Existir a **primeira tool** executada em um turno. Aí a interface gerada, os parâmetros nomeados e o loop de tool-calling passam a ter valor próprio, e a porta precisa de um método a mais.
2. O split **TX1/TX2** sair do desenho. Aí a ordem "persistir antes de gerar" deixa de ser um argumento.
3. O **histórico** passar a ser gerenciado pela biblioteca. Hoje ele é nosso e limitado a 20 (`ChatServiceImpl.java:191`); se a biblioteca passar a gerenciar o histórico via `ChatMemory`/`chatMemoryProvider`, a porta muda de forma.
4. Subir para **1.20.x estável** e a doc de `ai-services` sair de beta. Enquanto a doc lida for de `1.20.1-beta30` e o projeto estiver em `1.19.0`, comparar é comparar coisas diferentes.
5. `ChatTurnGateway` ganhar uma **segunda implementação real** (não um mock de teste). A segunda implementação é o que valida a porta; um mock não valida.

Reavaliar **C** (`EmbeddingStore`) quando surgir uma necessidade que SQL não expressa — busca híbrida (es lexical + vetorial), tuning de ANN independente do `pgvector`, ou um store cuja semântica de filtro sirva melhor à regra de tenant. **Não** reavaliar por "o LangChain4j tem store oficial": `KbVectorStore` + `KbRetrievalPort` já dão a portabilidade que motivou a pergunta.

## O que este ADR não decide

- **`ChatAssistant` / `AIAgent` (opção D).** Quando chegar, o eixo não é "tipo de agente" e sim **estratégia de turno** (`SingleShotTurn` / `ToolLoopTurn`, implementações de `ChatTurnGateway`), sem tipo novo chamado "assistant" ou "agent". ADR próprio, gatilho: primeira tool. Antes disso, qualquer tipo novo é especulação e colide com o vocabulário existente.
- **`skills` e orquestrador (opção E).** ADR de plataforma, gatilho: `langchain4j-skills` existir em versão não-experimental. Decisão de plataforma (1.19.0 → beta) com data de revisão, não feature.
- **Se a knowledge module deve passar a expor `dev.langchain4j.rag` no seu contrato público.** Hoje `KnowledgeBaseContentRetriever` vive em `agent/impl/rag`; se um dia a autoridade sobre "por que não usamos o retriever oficial" migrar para junto do store, ela deveria ir para `knowledge/impl`.

## Conformidade

- [x] `ChatTurnGateway` permanece em `agent/api`; `agent/impl` continua fora do contrato
- [x] Nenhum `dev.langchain4j.service.AiServices` no código (verificado por grep)
- [x] Nenhum `dev.langchain4j.store.embedding.pgvector` no código nem nos poms (verificado)
- [x] `TurnReply` é o único portador de falha entre porta e chamador; o diagnosticador continua lendo a exceção original
- [ ] Toda extração futura que mude o dono de uma decisão traz os testes que a fixavam (precedente: `RagTurnResolverTest`)

## Referências

- Commits: `d8d9ea3` (pipeline oficial de RAG: `QueryTransformer → QueryRouter → ContentRetriever → ContentAggregator → ContentInjector`), `901ae25` (costuras do turno)
- `backend/agent/api/src/main/java/br/com/systec/hive/agent/api/port/ChatTurnGateway.java`
- `backend/agent/api/src/main/java/br/com/systec/hive/agent/api/domain/TurnRequest.java` · `TurnReply.java`
- `backend/agent/impl/src/main/java/br/com/systec/hive/agent/impl/service/LangChain4jTurnGateway.java` · `ChatServiceImpl.java`
- `backend/agent/impl/src/main/java/br/com/systec/hive/agent/impl/rag/RagTurnResolver.java`
- `backend/knowledge/api/src/main/java/br/com/systec/hive/knowledge/api/domain/RagQuerySettings.java` · `port/KbVectorStore.java`
- `backend/dependencies/pom.xml:37` (`langchain4j.store.version = 1.19.0-beta29`, hoje só Tika), `:213-221` (remoção do pgvector e o motivo)
- Versão da interface: `langchain4j-core-1.19.0.jar` (`EmbeddingStore`, `EmbeddingSearchRequest`), conferido no `~/.m2` local
- ADRs relacionados: ADR-010 (RAG manual com pgvector) · ADR-009 (LangChain4j como framework único) · ADR-011 (provider custom)
