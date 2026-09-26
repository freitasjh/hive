# rag-advanced — PASSO 2: conformidade com a arquitetura oficial de RAG

- **Data:** 2026-09-26
- **Decisão:** usuário aprovou **A — conformidade total** com `docs.langchain4j.dev/tutorials/rag`
- **Precondição:** Passo 1 entregue e commitado (`465bb4d`), portão de medição fechado
- **Execução:** 2 delegações (2a+2c juntos, depois 2b), porque 2c **se conecta no** augmentor
  que 2a constrói, e 2b é outra camada (store/modelo), com outro risco

## Situação medida antes de começar (baseline)

| Métrica | Valor |
|---|---|
| Recuperação (`rag outcome` elapsedMs) | **57ms** |
| Chat **sem** RAG | 6,44s |
| Chat **com** RAG (citando) | 10,48s · 13,12s |
| Margem relevante-irrelevante | 0.0339 (nomic, threshold 0.60) |

O custo é dominado pela **geração do LLM** (modelo de raciocínio), não pela recuperação.
**Requisito do usuário: a busca não pode demorar.** Qualquer item do Passo 2 que acrescente
latência mensurável precisa ser medido, não afirmado.

---

# PARTE 2a — `RetrievalAugmentor` + `ContentRetriever` + `ContentInjector`

## 2a.1 ⚠️ Verificar ANTES de codar: dá para usar sem AI Services?
A doc apresenta `RetrievalAugmentor` como entrada do pipeline via `AiServices.builder(...).retrievalAugmentor(...)`.
**Este projeto NÃO usa AI Services** — chama `client.chat(messages)` direto.

- **Verifique no jar 1.19.0** se `DefaultRetrievalAugmentor` pode ser usado **standalone**
  (a interface é `ChatMessage augment(ChatMessage)`). Se der, use direto.
- Se **não** der sem AI Service: **PARE e reporte** — introduzir AI Service é uma mudança de
  arquitetura grande e não está aprovada. Avalie se as partes (retriever + injector) podem ser
  usadas sem o augmentor e documente o alcance real da conformidade.
- Não assuma nem invente. Já caímos 3 vezes em "a doc diz X" nesta sessão.

## 2a.2 `ContentRetriever` próprio, multi-tenant
A doc: `ContentRetriever` é a interface; `EmbeddingStoreContentRetriever` é a implementação para
embedding store. **Nós não somos um embedding store genérico** — somos multi-tenant com KB
selecionada por agente. Então:
- Implementar `ContentRetriever` **envolvendo a recuperação existente**, preservando: filtro de
  tenant, KBs vinculadas, threshold por modelo, `RagOutcome`, `RagDiagnostic`, `maxResults`.
- Usar `dynamicMaxResults` / `dynamicMinScore` da doc para o threshold por modelo **sem constante
  global** — é exatamente o uso que a doc descreve.
- `List<Content>` ordenado por relevância, do mais alto pro mais baixo, como a doc exige.

## 2a.3 `ContentInjector` com template
A doc: `DefaultContentInjector` + `PromptTemplate`; "metadata entries can also be included,
providing the LLM with additional information".
- Usar `PromptTemplate` explícito, **não** concatenação.
- **Preservar o delimitador anti prompt-injection** que já existe no `systemBlock`. Verificar se o
  template oficial o preserva; se não, compor o texto com o template e manter o delimitador.
- **Cuidado**: a doc mostra o conteúdo sendo injetado no **`UserMessage`**, não no system prompt.
  A doc: *"This list of Contents is injected into the original UserMessage"*. **Isso é uma mudança
  real de comportamento** em relação ao nosso `systemBlock`. Avalie: injetar na `UserMessage` é o
  que a doc define, mas muda como o modelo enxerga instrução vs contexto. **Decida, documente o
  porquê, e me reporte** — eu aprovo antes se você mudar injeção de system para user.

## 2a.4 Testes
- `ContentRetriever` próprio: tenant alheio → lista vazia **sem vazar**; KB sem vínculo → vazio;
  threshold por modelo via `dynamicMinScore`; ordem por relevância.
- `ContentInjector`: o texto recuperado entra no **template** com metadados; o **delimitador
  anti-injeção está presente** no resultado; um chunk com `"ignore previous instructions"` **não**
  escapa do delimitador.
- `ChatServiceImpl`: com KB, o augmentor é chamado e o contexto entra; sem KB, `ragUsed=false` e
  `ragReason=no_link` **como hoje** (não regredir o diagnóstico).
- `ragReason`, `citations`, `RagDiagnostic` inalterados — o que mudou foi **como** o contexto é
  montado, não o que é reportado.
- H1 completo 0 falhas.

---

# PARTE 2c — `CompressingQueryTransformer` condicional

> ⚠️ **A doc não condiciona** — ela diz para usar. Aplicá-lo sempre custa **uma chamada extra de
> LLM por pergunta**, o que conflita diretamente com "a busca não pode demorar". Então isto é
> **julgamento nosso**, não conformidade. Mantenha isso explícito no código e no relatório.

## Motivo (da doc)
> "The user: Tell me about John Doe. AI: John Doe is a... User: Where did he live?" — a query
> sozinha não recupera nada. `CompressingQueryTransformer` usa o LLM para reescrever a query com a
> conversa anterior.

Hoje isso **não existe** → pergunta de follow-up busca sem contexto e falha.

## Requisitos
1. **Só quando a pergunta parecer continuação.** Heurística **testável e observável**:
   - tamanho curto (definir o limite com justificativa);
   - e/ou começa com conectivo ("e ", "então", "por que", "qual o melhor");
   - e/ou contém pronome indefinido / referência implícita ("ele", "isso", "o mesmo", "a outra").
2. **Log de "transformer aplicado? por quê?"** — sem isso o operador não entende por que uma
   resposta custou o dobro.
3. **Se a heurística não for defensável em teste, NÃO implemente** — registre como P2. Uma
   heurística frágil custa uma chamada de LLM e não devolve nada.
4. **Medir o custo**: tempo do transformer vs o ganho em recuperação. Reportar números, não
   opinião.
5. **O transformer precisa de um `ChatModel`** — qual usar? Provavelmente o mesmo do agente
   (caro) ou um mais barato. Avalie e justifique. O compressor envia o histórico; enviar o
   histórico inteiro num modelo caro é o pior caso.

---

# PARTE 2b — `EmbeddingStore` + `embeddingInputType` (separada)

> Outra camada (store/modelo), outro risco. Depois de 2a+2c medirem.

## 2b.1 `embeddingInputType` — oportunista
A doc:
> `.embeddingInputType(EmbeddingInputType.DOCUMENT)` no ingestor e
> `.embeddingInputType(EmbeddingInputType.QUERY)` no retriever.
> "When it is set, the selected EmbeddingModel must support the input type parameter, otherwise
> embedding fails fast with `UnsupportedFeatureException`."

- Adicionar **só** onde o modelo declarar suporte (`supportedParameters()`), verificando no 1.19.0.
- **Manter** o prefixo para Ollama/nomic (não suporta `input_type`).
- Não mudar o comportamento de nenhum modelo atual.

## 2b.2 `EmbeddingStore` + `Filter` por `Metadata`
A doc: `EmbeddingStore.search(EmbeddingSearchRequest)`; `Filter` com `IsEqualTo`/`IsIn`/…
e confirma: **PgVector suporta filtro por `Metadata`**, inclusive `ContainsString`.
- Hoje temos SQL cru em `KbEmbeddingStore` com filtro por `tenant_id`/`kb_id`.
- Avaliar se implementar `EmbeddingStore` (ou adapter) é realmente o caminho — e **se não for,
  dizer por quê**. Não é conformidade de fachada: se o adapter só embrulha o SQL existente, ganhamos
  a interface sem o benefício, e isso é pior que não fazer.

## 2b.3 `IngestionResult.tokenUsage()`
Hoje: `tokenUsage~=2763` (estimativa). A doc: `EmbeddingStoreIngestor.ingest()` retorna
`IngestionResult` com `TokenUsage` real. Registrar como P2 se não entrar em 2b.

---

## Fora de escopo
- `QueryRouter` multi-fonte, `GraphTransformer`, reranking, busca híbrida (doc: só Azure/Elasticsearch).
- Trocar o modelo de sentenças para `opennlp-pt` (P2 registrado; mudaria as fronteiras dos chunks
  e exigiria novo portão de medição).

## Critérios de aceite — Passo 2
- [ ] **`DefaultRetrievalAugmentor` standalone verificado no 1.19.0** (ou decisão documentada)
- [ ] `ContentRetriever` próprio preservando tenant/KB/threshold/outcome
- [ ] `dynamicMinScore`/`dynamicMaxResults` usados (sem constante global)
- [ ] `ContentInjector` com `PromptTemplate` + delimitador anti-injeção preservado
- [ ] Decisão documentada sobre injetar em `UserMessage` (doc) vs `SystemMessage` (atual)
- [ ] `CompressingQueryTransformer` condicional, com heurística testada e log — **ou P2 registrado**
- [ ] **Latência do chat medida antes e depois** — sem regressão mensurável
- [ ] `ragReason`/`citations`/`RagDiagnostic` inalterados
- [ ] H1 0 falhas · review zero 🔴
