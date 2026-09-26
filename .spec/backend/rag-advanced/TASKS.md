# FEATURE — RAG avançado: pipeline oficial LangChain4j + chunks com contexto

- **Data:** 2026-09-26
- **Decisão:** usuário aprovou **B2** (adoção do pipeline oficial) — e antes **B1**
- **Estado:** RAG **funcionando** hoje (`ragUsed=true`, `citations=4`, resposta citando o PDF)
- **Motivo:** as respostas ainda são imprecisas porque os **chunks vão truncados e sem contexto**
- **Estrutura:** 2 passos com **portão de medição** entre eles

## Diagnóstico que motiva (dado, não palpite)

Chunks gravados hoje (`kb_embeddings_768`, KB `teste`):
```
"guia_pratico_mente_magra_sos_v3.pdf\n\n GUIA PRÁTICO: MENTE MA…"
"guia_pratico_mente_magra_sos_v3.pdf\n\nnutritivo. Siga a sua r…"
"guia_pratico_mente_magra_sos_v3.pdf\n\no seu processo e contin…"
```
Fragmentos que **começam e terminam no meio da frase**, com o nome do arquivo colado no topo.
A documentação do LangChain4j adverte exatamente sobre isso:

> "The challenge lies in ensuring each segment provides sufficient context/information for the
> LLM to understand it. **Missing context can lead to the LLM misinterpreting the given segment
> and hallucinating.**" —/tutorials/rag, seção "To split or not to split?"

E recomenda: `DocumentSplitters.recursive(...)` com `TokenCountEstimator`, mais um
`textSegmentTransformer` que inclua o **título do documento** em cada `TextSegment`.

## ⚠️ Dois avisos de método (o dev tem que respeitar)

1. **A documentação que li é da versão 1.20.1-beta30; o projeto usa `langchain4j` 1.19.0.**
   Verifique cada classe/método **no jar 1.19.0** (`javap` / `unzip -l`) antes de usar.
   Já caímos hoje em "a doc diz X" e o código era Y (o `@Size`, o endpoint do KB).
2. **Não escolha threshold.** O `0.60` do nomic foi calibrado nos chunks **antigos** (5 chunks
   de PDF pequeno, margem ~0.02). Depois do re-chunking os scores mudam. **Eu** reindexo e re-meço.
   Se o dev mexer no threshold sem medição, ele repete o erro de quase injetar a pergunta
   irrelevante.

---

# PASSO 1 — Chunks com contexto (re-chunking + título)

> Alvo: que o LLM receba segmentos que fazem sentido sozinhos.

## 1.1 Splitter oficial
- Usar `dev.langchain4j.data.document.splitter.DocumentSplitters.recursive(maxSegmentTokens, chunkOverlapTokens, tokenCountEstimator)`.
- **Verificar no 1.19.0**: assinatura exata e qual `TokenCountEstimator` usar. Hoje o projeto tem
  `RecursiveCharacterSplitter` próprio (512/50 **em caracteres**, não tokens) — ver
  `KbIngestionService`. Caracteres ≠ tokens: um chunk de 512 caracteres pode ser bem menor ou maior
  que 512 tokens. Isso é parte do bug.
- `maxSegmentTokens` / `chunkOverlap`: manter compatibilidade com o `MAX_TOKENS` já usado na
  ingestão (verificar constante atual) ou justificar a mudança.

## 1.2 Título do documento em cada chunk
- Prefixar cada `TextSegment` com o **título/nome** do documento, via um
  `TextSegmentTransformer` (ou equivalente na ingestão atual), no formato da doc:
  `fileName + "\n" + text`.
- **A chave de que isso é o problema:** hoje o nome do arquivo já aparece, mas o **título real**
  (primeira linha significativa do PDF) não. O chunk 0 é a capa; o chunk 4 é a página de direitos
  autorais. Nem todo chunk carrega a informação que o torna compreensível.
- Não invente um extrator de título pesado. Comece com o que já existe (`source_uri`/`file_name` do
  `KbDocumentEntity`) e, se o **título** estiver disponível no documento ingerido, use-o.

## 1.3 Preservar o que já funciona
- O `metadata->>'convention'` gravado em `kb_embeddings_*.metadata` **deve continuar** — é o que
  detecta espaço misturado. A reindexação vai regravar com a convenção correta.
- O `chunk_index` e o `text` continuam no formato atual (a busca e a citação dependem).
- **Todo** o resto do comportamento de ingestão: limite de 10MB, magic-bytes, PDF escaneado não
  suportado, status `PENDING → INDEXING → READY | FAILED`, reindex idempotente.

## 1.4 Testes
- `KbIngestionServiceTest`: assert do **texto efetivamente gravado** — chunk começa com o título e
  **não** termina no meio de uma palavra (assert de fronteira: o chunk termina em fronteira de
  sentença/frase, ou ao menos em token completo). Este é o teste que pega o bug.
- Assert de que o tamanho é medido em **tokens**, não caracteres.
- `KbEmbeddingConventionIT` e os demais ITs continuam verdes **sem** mudar expectativa.
- H1: `mvn clean install -DskipTests` + `mvn test` + `mvn verify -pl integration-test -Pintegration -am`
  → 0 falhas · `npm run test` + `npm run build` → 0 falhas.

## Critérios de aceite — Passo 1
- [ ] Splitter oficial do LangChain4j, medido em tokens
- [ ] Título do documento em cada chunk
- [ ] Nenhum chunk truncado no meio de palavra
- [ ] `convention` preservada no metadata
- [ ] Comportamento de ingestão preservado
- [ ] Teste que falha com o splitter antigo
- [ ] H1 0 falhas
- [ ] `fullstack-code-reviewer` zero 🔴 (agora **com diff** — repo gitizado)
- [ ] Brain (MCP fora — deixar nota)

---

# PASSO 2 — Pipeline oficial de RAG

> Só começa **depois** do portão de medição do Passo 1.

## 2.1 `RetrievalAugmentor` no lugar do `systemBlock` manual
Hoje `ChatServiceImpl` monta o `systemBlock` concatenando strings. A doc define o pipeline:
`UserMessage → QueryTransformer → QueryRouter → ContentRetriever → ContentAggregator → ContentInjector`.

- `RetrievalAugmentor` (interface) com `DefaultRetrievalAugmentor` como default.
- **Verificar no 1.19.0**: dá para usar `DefaultRetrievalAugmentor` **diretamente**
  (`augment(ChatMessage)`), já que o projeto chama `client.chat(messages)` direto e **não** usa
  `AiServices`. Se só der via AI Service, avaliar se vale introduzir AI Service (mudança grande) ou
  manter a montagem manual e gains parciais. **Não assuma** — verifique e reporte.

## 2.2 `ContentRetriever` próprio, multi-tenant
- Implementar `ContentRetriever` que **envolve a recuperação existente** (mantendo filtro de
  tenant, KBs vinculadas, threshold por modelo, `RagOutcome`, `RagDiagnostic`).
- O `ContentRetriever` é a interface oficial; nosso continues sendo quem conhece multi-tenant e KB.
- `maxResults` / `minScore` podem ser **dinâmicos** (`dynamicMaxResults`, `dynamicMinScore`) — usar
  para o threshold por modelo sem constante global.

## 2.3 `ContentInjector` com template
- `DefaultContentInjector` com `PromptTemplate` explícito, em vez de concatenação.
- O template deve deixar claro ao LLM que o texto é **contexto recuperado** e não instrução
  (anti prompt-injection — o sistema já tem delimitador, confirmar que o template oficial o preserva).

## 2.4 `CompressingQueryTransformer` — condicional
> **Tensão a resolver:** o transformer faz **uma chamada extra de LLM por pergunta**. O usuário
> exigiu que a busca não demore. Aplicá-lo sempre piora a latência de todo chat.

- Ativar **apenas quando a pergunta parecer continuação**: curta, sem sujeito explícito, começando
  com conectivo ("e ", "então", "qual o melhor"), ou com pronome indefinido ("ele", "isso").
- Heurística deve ser **testável e observável** (log de "transformer aplicado? por quê?"), não
  mágica. Se a heurística for frágil demais, deixar atrás como P2 em vez de arriscar piorar a
  latência媒体. **Decisão minha: se a heurística não for defensável em teste, NÃO implemente — registre.**

## 2.5 `embeddingInputType` — oportunista
A doc define `EmbeddingInputType.DOCUMENT` (ingestor) / `QUERY` (retriever) como a forma **oficial**
de distinguir, com `UnsupportedFeatureException` se o modelo não suportar. É melhor que o nosso
prefixo — **para os modelos que suportam** (Cohere, Voyage, Google).
- Manter o prefixo para Ollama/nomic (não suporta).
- Verificar no 1.19.0. Se `embeddingInputType` existir: adicionar, mas **só ativar** para modelos
  que declarem suporte, sem cambiar o comportamento dos atuais. Registra em `supportedParameters`.

## Fora de escopo
- Busca híbrida (full-text/BM25): a doc diz que só existe em Azure AI Search e Elasticsearch.
  Implementar BM25 é projeto separado.
- `QueryRouter` multi-fonte, `GraphTransformer`, reranking: fases futuras.

## Critérios de aceite — Passo 2
- [ ] `RetrievalAugmentor` no lugar do `systemBlock` (ou decisão documentada se não viável)
- [ ] `ContentRetriever` próprio preservando tenant/KB/threshold/outcome
- [ ] `ContentInjector` com template e delimitador anti-injection preservado
- [ ] `CompressingQueryTransformer` condicional **ou** decisão registrada de não fazer
- [ ] `embeddingInputType` oportunista (só onde suportado)
- [ ] Latência do chat **não** piorou de forma mensurável (medir antes/depois)
- [ ] H1 0 falhas · review zero 🔴
