# BUG — Embeddings sem prefixo de tarefa: retrieval da KB não discrimina

- **Data:** 2026-09-26
- **Severidade:** 🔴 P0 (KB com embedding local nunca recupera nada de útil)
- **Origem:** medição real no pgvector durante a investigação do "KB não busca nada"
- **Decisão:** usuário aprovou (opção A) — implementar prefixo, reindexar, **re-medir**, e só então
  escolher o threshold com dado
- **Rodada:** única

## Medição que motivou (não é palpite)

Embeddings de `nomic-embed-text` (768d) da KB `teste` (`3b51cdb5-6912-4c7a-9de8-56be879db65b`),
consulta no pgvector, `1 - (embedding <=> query)`:

| Pergunta | Melhor score |
|---|---|
| Relevante (guia da Andrea Lopes, longa) | 0.6490 |
| Relevante curta | 0.5701 |
| **Irrelevante** (banco de dados) | **0.6141** |
| Sem relação (protein folding) | 0.5333 |

Margem entre relevante e irrelevante: **0.035** — ruído. Nenhum threshold separa isso.
O corte atual `RAG_MIN_SCORE = 0.7` rejeita tudo; baixar para 0.5 (como chegou a ser
proposto) **injetaria conteúdo irrelevante**.

## Causa raiz

`nomic-embed-text` é um modelo que **exige prefixo de tarefa**:
- indexação: `search_document: <texto>`
- consulta: `search_query: <texto>`

Sem os prefixos dos **dois** lados, o espaço vetorial colapsa para a faixa 0.53–0.68
observada. Confirmado por grep: **o projeto não usa prefixo nenhum**
(`search_document|search_query` → 0 ocorrências).

Teste de controle já feito: aplicar `search_query:` **só na consulta**, mantendo o documento
sem prefixo, **não melhora** (0.6028 relevante vs 0.5897 irrelevante) — como esperado,
porque o lado do documento continua no espaço errado.

## Solução

### 1. Papel explícito no contrato de embedding
Hoje `KbEmbeddingPort.embed(tenantId, model, dimension, text)` é usado **para os dois papéis**,
impossibilitando aplicar prefixos diferentes. Separar:

- `embedDocument(tenantId, model, dimension, text)` — usado pela ingestão/reindex
- `embedQuery(tenantId, model, dimension, text)` — usado pelo retrieval

`embed(...)` legado: manter delegando a `embedDocument` (ou remover se não houver chamador —
verificar). Os chamadores devem migrar para o método explícito; **não** deixar nenhum caminho
usando o método ambíguo.

### 2. Tabela de convenção por modelo
Aplicar prefixo **somente** nos modelos que exigem, e **nunca** nos que não (prefixo em
`text-embedding-3-*` degrada a qualidade):

| Modelo | Documento | Consulta |
|---|---|---|
| `nomic-embed-text` (e variantes `nomic*`) | `search_document: ` | `search_query: ` |
| `text-embedding-3-*` (OpenAI) | sem prefixo | sem prefixo |
| demais (mxbai, minilm, bge…) | sem prefixo | sem prefixo |

A tabela fica num único lugar, com o motivo commentado (link da doc do modelo). **Não** usar
regex solta espalhada. Se a lista crescer, um `EmbeddingConvention` dedicado por modelo
(registry simples) é melhor que um `if` — mas comece simples e justifique.

### 3. Observabilidade da convenção
- `log.info` na construção do modelo de embedding já existe (`kb embedding model ollama model={}`):
  **acrescentar a convenção aplicada** (`prefix=search_document`) para o operador ver.
- `RagDiagnostic` / `ChatTrace` já registram o outcome; incluir o **threshold efetivo** usado na
  busca, senão o operador não consegue explicar um `no_chunk_above_threshold`.

### 4. Migração dos dados (feita por mim, o orchestrator)
Ao trocar a convenção, **os embeddings gravados ficam em outro espaço** e misturar é pior que
qualquer bug. Após o fix eu reindexo as 2 KBs com `nomic-embed-text` e **re-meço**.
- Registrar explicitamente: enquanto não reindexar, o resultado da busca é **indefinido**.
- Deixar o risco escrito (P2) de misturar espaços vetoriais sem versionamento do esquema de
  embedding por KB.

## Testes
1. `KbEmbeddingServiceTest`:
   - modelo nomic + `embedDocument` → texto enviado começa com `search_document: `
   - modelo nomic + `embedQuery` → começa com `search_query: `
   - `text-embedding-3-small` → **sem** prefixo nos dois papéis
   - modelo desconhecido → sem prefixo
   - `embed(...)` legado sem chamador (verificar por grep e descrever)
2. `KbIngestionServiceTest` usa `embedDocument`; `KbRetrievalServiceTest` usa `embedQuery`.
   **Assert do texto efetivamente enviado ao provider** (com um `EmbeddingModel` fake), não só
   "o método foi chamado".
3. `KbRetrievalServiceTest`: o threshold default continua vindo do chamador; se resolver por
   modelo, testar o valor por modelo.
4. **Teste de regressão que pega o bug real**: um `EmbeddingModel` fake que devolve vetores
   **ortogonais/near-duplicados** e um caso em que o prefixo aplicado muda a similaridade.
   O objetivo é que um IT não possa passar com score 1.0 artificial.
5. H1: `mvn clean install -DskipTests` + `mvn test` + `mvn verify -pl integration-test -Pintegration -am`
   → 0 falhas · `npm run test` + `npm run build` → 0 falhas.

## Critérios de aceite
- [ ] `embedDocument` / `embedQuery` separados e em **todos** os chamadores
- [ ] Prefixo só nos modelos que exigem; tabela única e comentada
- [ ] Convenção no log; threshold efetivo no diagnóstico
- [ ] Nenhum chamador no método ambíguo
- [ ] Teste que falharia com o prefixo ausente
- [ ] Suite completa 0 falhas
- [ ] `fullstack-code-reviewer` zero 🔴
- [ ] Brain (MCP está fora — deixar nota para reprocessar)
- [ ] Processos finalizados
