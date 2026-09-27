# brain_store PENDENTE (MCP brain fora do ar: HTTP 410 em 2026-09-26)

Duas notas pretendidas para `brain_store` quando o Brain MCP voltar.
`brain_ping` e `brain_store` retornam `HTTP 410` (endpoint removido) durante todo o refactor.

---

## Nota 1 → layer=arquitetura, scope=projetos, path=hive/agent/chat-costuras-rag-e-geracao

# Costuras do chat com RAG — o que foi extraído e por quê (2026-09-26)

Refactor de fronteira pura em `ChatServiceImpl` (15 dependências, 6 responsabilidades).
Regra dominante: ZERO mudança de comportamento. O fluxo já tinha sido validado em E2E real
(`ragUsed=true`, retrieval 46-49ms) e 6 bugs encontrados no caminho.

| Costura | Tipo | Dono de |
|---|---|---|
| `RagTurnResolver` | classe concreta (`agent/impl/rag`) | link agente↔KB, augmentor, **1 diag por turno**, citations, guard de conteúdo vazio |
| `ChatTurnGateway` | **porta** `agent/api/port` | prompt, bloco RAG, histórico, chamada ao model, retry, resposta vazia, `isModelNotFound` |
| `KbVectorStore` | **porta** `knowledge/api/port` | `search`/`insert`/`removeByKb`/`removeByDoc` — a escrita não tinha porta |

### Decisões que NÃO reabrir
- **Não adotar `EmbeddingStore` do LangChain4j.** `add` retorna `void` em 1.19.0 e `String` em 1.20
  (impede reconciliar `embedding_id = chunkId`, de que as citations dependem); `removeAll(Filter)`
  é `default` e lança `UnsupportedOperationException` (delete por KB viraria no-op silencioso).
- **Não criar `ChatAssistant` nem `AIAgent`.** Não existe tool no backend (`AgentEntity.tools` é
  `text[]` vazio; nada referencia `@Tool`/`ToolProvider`). O nome colide com `ChatAssistantTxService`.
- **Não extrair `ModelFailureClassifier`.** `isModelNotFound` é função pura com um call-site.

### Lição 1 — a falha é dado, não exceção, quando a telemetria precisa do que a exceção leva
`TurnReply` tem `Status.FAILED` e carrega a exceção do provider **desembrulhada**.
A trace da chamada que falhou precisa de `promptMessages`/`promptChars`, e o prompt é montado
**dentro** da chamada que falhou. Se a porta lançasse, o chamador reconstruiria um número que nunca
mediu — a "segunda opinião sobre uma chamada que já aconteceu" que estas linhas foram reescritas
para evitar. O mesmo vale para `EMPTY_RESPONSE`: `finishReason` e token usage só existem no objeto
de resposta. Foi assim que um reply vazio virou 409 de integridade no passado.
`RagDiagnostic.causeOf` é transparente a embrulho (`rootOf` desce à causa mais profunda), mas
embrulhar mudaria o rótulo — daí a exigência de desembrulhar.

### Lição 2 — a porta é o que torna a troca de store barata, não a abstração
A leitura já tinha porta (`KbRetrievalPort`); a escrita não: `insert`, `removeByKb`, `removeByDoc`
eram chamados **direto** por `KbIngestionService`, `KbDocumentServiceImpl` e
`KnowledgeBaseServiceImpl`. Era isso que travava a portabilidade para Qdrant.
Ao criar a porta, **pine as queries**: `KbVectorStoreTest` fixa o texto SQL (tabela por dimensão,
`CAST(? AS vector)`, `ANY(?::uuid[])`, e o `WHERE tenant_id = ?` em cada statement). Uma store que
mantivesse as 4 assinaturas e derrubasse o predicado de tenant passaria nos testes que ela substitui
— e seria um vazamento cross-tenant.

### Lição 3 — o sentinela numérico é colisão semântica
`MIN_SCORE_FROM_KB_CONVENTION = 0d` dizia duas coisas: "ninguém escolheu" e "sem corte".
`RagQuerySettings` (sealed: `ResolveFromKbConvention` | `Exact(double)`) torna as duas intenções
irrepresentáveis-uma-como-a-outra.
Onde mora: **`knowledge/api/domain`**, não `agent/api` — o grafo de módulos força isso, porque
`agent/api` já depende de `knowledge/api` e a porta que consome o tipo é de lá. Colocar em
`agent/api` e eliminar o sentinela são mutuamente exclusivos.
Resolução fica no impl (`EmbeddingTaskConvention` é a única camada que sabe o modelo).
Efeito colateral **documentado**: `Exact(0)` e `Exact(-1)` agora são honrados verbatim, onde antes
caíam na convenção. Nenhum call site de produção faz isso; a suíte fixa a semantics nova.

### Lição 4 — onde o prompt é montado decide se a trace sobrevive a uma falha
O `buildMessages` ficava **fora** do try no original. Ao movê-lo para a porta, foi mantido fora do
try de propósito: defeito ali é defeito da classe, não falha de provider.

### Lição 5 — dependências: 15 → 11, e o ≤8 não fecha sem uma terceira extração
Os 11 restantes: 8 serviços de domínio de uso único ou duplo (session, message, tx-assistant, agent,
defaultAgent, credential, rateLimiter, trace) + `discoveryService` + as 2 costuras novas.
Chegar a 8 exigiria dissolver 3 serviços distintos numa fachada — abstração nova que o `architect`
não aprovou. Preferi 11 honestos a 8 com um `TurnCoordinator` inventado.

### Prevenção
- Ao extrair um método privado que a telemetria observa, verifique **de onde vêm os números que a
  trace registra**. Se vêm de dentro do que você está movendo, a fronteira precisa devolver medido —
  não relançado.
- Ao criar porta de escrita, escreva o teste que fixa o SQL, não só o que a assinatura promete.
- Sentinelas numéricos em fronteiras (`0` = "não escolhido", `-1` = "ausente") são bugs diferidos;
  prefira tipo selado desde o primeiro dia.

---

## Nota 2 → layer=regras, scope=global, path=java/padroes/refactor-de-fronteira-com-telemetria

# Refactor de fronteira quando existe telemetria que observa a fronteira

## Regra 1 — a fronteira devolve medido, e medido inclui o caminho de falha
Extrair `resolveX()` para um colaborador quebra a telemetria se o colaborador só devolve o caminho
feliz. A trace precisa de números que só existem dentro do que foi movido (`finishReason`,
`promptChars`, token usage) **e** precisa deles no caminho de erro.
Forma que funciona: o retorno carrega um estado por desfecho
(`ANSWERED` / `EMPTY_RESPONSE` / `FAILED`, com a exceção desembrulhada).
Exceção embrulhada quebra a classificação: `causeOf` lê a causa mais profunda e o `SimpleName` dela
vai para o log. Embrulhar troca `HttpException:not_found` por `Wrapper:unexpected`.

## Regra 2 — "diagnóstico fora do gateway" é fora do gateway, não fora da classe
Geração e observação compartilham o vocabulário do transporte. A **regra** (classificação) pertence
à camada que fala HTTP; o **efeito** (evict de cache, log, 422) pertence à camada que tem sessão e
tenant. Mover o efeito para dentro cria um log sem session id — o defeito que a separação evitava.

## Regra 3 — a configuração se dissolve nos construtores dos extraídos
`@Value` de config, `new XFactory()` e `RetryPolicy.defaults()` que viviam no construtor da classe
grande pertencem ao colaborador que os usa. Contar dependências sem mover isso é contagem de fachada.

## Regra 4 — o teste que existia "por causa" da falta de seam é o indicador de sucesso
Se existe uma classe de teste cuja única razão de existir é "a unidade só é testável através da
classe grande", o refactor não terminou quando compila — termina quando aquele teste pode ser
dividido em testes do colaborador. Verificar: a nova unidade cobre os mesmos caminhos? o teste
antigo encolheu para o que é de fato da classe grande (a ligação entre as costuras)?

## Prevenção
- Antes de extrair, liste todo número que a telemetria lê e de qual escopo ele vem.
- Se a telemetria lê algo que só existe dentro do método extraído, a fronteira precisa de um tipo de
  retorno com estados — não pode ser `T` nem pode lançar.
- Teste o caminho de falha da fronteira com a métrica preservada. É o caminho que ninguém cobre.

---

## Nota 3 → layer=regras, scope=global, path=java/padroes/record-com-campo-de-segredo

# Record com campo de segredo: `toString()` gerado é um vazamento esperando o momento

## Contexto
Refactor de fronteira (chat/RAG, 2026-09-26). `TurnRequest` passou a carregar `apiKey` como
componente de record; `TurnReply` carrega a `Throwable` do provider. O `toString()` autogenerado
lista tudo. `security-standard.md` proíbe logar segredos.

## O padrão
Um record **não tem** `toString()` seguro por padrão. Java gera `toString()` com **todos** os
componentes. Se o record cruza uma fronteira de API (porta, DTO, evento), o objeto é printável.

Antes do refactor, a chave era parâmetro de método e morria no frame. Depois, ela vive num record
com `toString()` público. Nenhum `log.*(request)` existe hoje — mas **um** `log.debug`, um
`MDC.put`, um dump de framework, ou um teste que falhe e imprima o objeto escreve a chave num log.
Log não se desgrava.

## Campos que NUNCA podem entrar no `toString()` gerado
1. **Credencial** (`apiKey`, `token`, `secret`, `password`) → `apiKey=***`
2. **`Throwable`** → só `getClass().getSimpleName()`. Motivo específico do LangChain4j:
   `HttpException.getMessage()` é o **payload bruto do provider** (echo da requisição, às vezes com
   a chave no corpo). É exatamente o texto que `RagDiagnostic.causeOf` existe para classificar em vez
   de citar.
3. **Conteúdo do usuário** (conversation, prompt, histórico) → **tamanho**, nunca texto. Um teste que
   falha imprime o objeto e põe as palavras do usuário no log de build.

## O que deixar no `toString()`
O suficiente para diagnosticar: ids, enums, tamanhos. `sessionId`, `agentId`, `provider`, `model`,
`ragUsed`, `ragOutcome`, `N message(s)`, `promptChars`.

## Regra de coerência entre records vizinhos
`RagContext` / `TurnRequest` / `TurnReply` tinham regras de null diferentes (um coagulava, outro não).
**Três regras diferentes em três records adjacentes é como o leitor para de confiar em qualquer uma.**

Escolha UMA e aplique nos três. A que este projeto adotou:
- **coleção é defaultada** — `null` e vazio são o mesmo fato
- **valor que carrega significado é obrigatório** (`Objects.requireNonNull`)

O contraexemplo concreto: `ragContext == null` coagulado para `RAG_DISABLED` dizia ao cliente "esta
rodada não tinha conhecimento" quando a verdade era "ninguém resolveu nada" — uma **mentira** sobre um
wiring quebrado, reportada como `ragReason`. Virou `NullPointerException` com stack trace.

## Regra (promovida de nota a regra — 3ª ocorrência no mesmo commit)

> **Todo record novo que carregue texto de documento, credencial ou payload de exceção precisa de
> `toString()` override, no mesmo commit em que o record é criado.**

A promoção aconteceu porque o defeito apareceu **três vezes** no mesmo commit (2026-09-26), em três
records diferentes, e **duas** das três vezes passou o code review. Não é um erro que o review
"pega" — é um erro que o revisor tem que notar sozinho.

### A sequência real, que é a lição
1. `TurnRequest` — carregava `apiKey`. Revisado como 🟡, corrigido.
2. `TurnReply` — carregava a `Throwable` do provider. Corrigido **junto**, no mesmo momento.
3. `RagContext` — **carregava o documento recuperado do cliente, verbatim**. Não foi pego.

Por que o terceiro passou: no momento da correção eu olhei "os records que eu tinha acabado de
endurecer", não "os records que existem". E `RagContext` era o **pior** dos três — é o único que
carrega o **conteúdo do documento do cliente** na forma exata em que o modelo o leu. Era
provavelmente o record mais sensível do pacote inteiro, e era o único sem override.

### O sinal que denuncia
O `RagContext` era um **record privado aninhado** em `ChatServiceImpl` antes do refactor. Ninguém
precisava de `toString()` seguro nele. O refactor de fronteira **promoveu a tipo público em
`agent/api`, carregado por 3 colaboradores**. A mudança de escopo — não a de conteúdo — foi que
criou o risco.

**Regra derivada:** quando um tipo sai do escopo do arquivo (privado → público no `api/`, local →
atravessa fronteira de módulo), reavalie `toString()`, `equals`, `hashCode` e serialização. O tipo
não mudou de conteúdo; mudou de **alcance**, e alcance é o que decide quem pode imprimi-lo.

### Como não deixar escapar de novo
Ao adicionar um record, listar os componentes **antes** de escrever a classe e perguntar, para cada
um: "isso pode ser impresso?". A lista é curta:

| Componente | Pode imprimir? | O que fazer |
|---|---|---|
| id, enum, boolean, int/long | sim | manter |
| `List<X>` de domínio | **depende de X** | contar, ou o nome de X se X for seguro |
| `String` de credencial | **nunca** | `***` |
| `Throwable` | **só classe** | `getClass().getSimpleName()` |
| texto de documento / prompt / conversa | **nunca** | tamanho |
| `Double` de threshold | sim | manter |

- **Override no mesmo commit** que cria o record. Não é dívida, é parte do tipo.
- **Escreva o teste que falha se vazar**, no formato `doesNotContain("<o segredo literal>")`. Ele
  custa 6 linhas e é a única prova de que o override não foi apagado depois.
- Se `toString()` é sobrescrito em um record, **o padrão se estende aos vizinhos do mesmo pacote** —
  pergunte explicitamente "e os outros dois?".

## Prevenção
- Ao criar um record que carrega segredo, `Throwable` ou texto de usuário: **override `toString()`
  no mesmo commit**, e escreva o teste que falha se o valor vazar.
- Ao promover um tipo de privado para público / de intra-módulo para `api/`: reavalie `toString()`,
  `equals`, `hashCode`, serialização. O conteúdo não mudou; o alcance mudou.
- Ao endurecer um record, pergunte "quais os **outros** records deste refactor?" — o terceiro do
  pacote passou o review justamente por essa pergunta não ter sido feita.
- `throw new IllegalArgumentException` em record curto **no compact constructor**, não no ponto de uso.
  Quem valida no uso re-cria a colisão silenciosa que o tipo existe para remover.
