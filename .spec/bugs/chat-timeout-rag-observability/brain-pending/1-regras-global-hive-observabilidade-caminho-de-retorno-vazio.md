# brain_store(layer="regras", path="hive/observabilidade/caminho-de-retorno-vazio", scope="global")

## Regra: todo retorno vazio numa borda de integração precisa de log com o motivo

### Contexto
Fluxo de RAG no Hive AI: `ChatServiceImpl.resolveRagContext` → `KbRetrievalPort.retrieve`.
O `retrieve` devolvia `List.of()` em **quatro** situações distintas sem log nenhum:
1. `kbIds` vazio (agente sem KB vinculado)
2. `kbs.isEmpty()` (KB não pertence ao tenant / não existe)
3. `chunks` vazio (nada acima do threshold de similaridade)
4. `future.get` estourou (timeout de 2s no executor de retrieval)

Só o caso de exceção logava. Resultado: `grep` no backend não achava nada e o operador via
só o sintoma final — o modelo respondendo "não recebi contexto". Bug reportado em 2026-09-26.

### Por que é regra (não caso isolado)
"Sem vínculo" ≠ "KB não é do tenant" ≠ "nada acima do threshold" ≠ "timeout" são **quatro
falhas com quatro correções diferentes**:
- sem vínculo → falta o caminho de escrita KB↔agente
- não é do tenant → vínculo gravado com tenant errado, ou KB removida
- nada acima do threshold → embedding/documento/limiar
- timeout → infraestrutura (pgvector/Ollama lento)

### Regra
1. `return` de lista/contexto vazio numa borda de integração **precisa** de log com o motivo,
   no mesmo nível que decide o retorno. Não basta logar quando algo lança.
2. O motivo é um **código estável** (enum com `wireName()` em snake_case), não texto livre:
   `no_link`, `kb_not_found_for_tenant`, `no_chunk_above_threshold`, `timeout`, `error`.
3. O código do motivo vai até o cliente (campo opcional no DTO). Sem ele, o modelo inventa
   uma explicação e o usuário culpa o prompt em vez do vínculo.
4. **Nunca** logar o texto da query nem o conteúdo dos chunks (dado sensível). Logue contagens,
   ids, limiar e latência.
5. Causa-raiz = `SimpleName: message` do throwable mais profundo, **sem stack trace** (o stack
   pode conter dado do usuário). `getMessage()` sozinho é vazio com frequência em timeout embrulhado.
6. Se a função tem vários retornos vazios com causas diferentes, a interface precisa retornar o
   motivo — `List.of()` colapsa causas e torna o motivo irrecuperável para o chamador.

### Implementação de referência (Hive, 2026-09-26)
- `RagOutcome` (enum com `wireName()`) + `RagDiagnostic` (record) + `RagDiagnosticRecorder` (port)
- `KbRetrievalPort.retrieveWithOutcome(...)` → `RagRetrievalResult(outcome, chunks, elapsedMs)`
- `LoggingRagDiagnosticRecorder`: INFO para `ok`/`no_chunk_above_threshold`, WARN para os demais

### Teste
Preferir **recorder injetável** a assert de stdout: asserção sobre log por string é frágil e
quebra em qualquer reformatação. Assert no recorder + um teste do `format()` para o contrato
da linha.

tags: [observabilidade, rag, retorno-vazio, log, eng/backend]
