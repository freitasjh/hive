# BUGS — chat dá timeout (~90s) · KB nunca recupera · fluxo sem visibilidade

- **Data:** 2026-09-26
- **Severidade:** 🔴 P0 timeout · 🔴 P0 vínculo KB inexistente · 🟡 P1 ausência de logs
- **Origem:** reporte do usuário · reproduzido com evidência antes de codar
- **Decisão:** usuário aprovou C → A → B (observabilidade → timeout → caminho de escrita)
- **Rodada 1 (este despacho): C + A** · **Rodada 2: B**

---

## Bug 1 🔴 — Vínculo KB↔agente não tem caminho de escrita (NÃO CONSERTO NESTA RODADA)

### Evidência
| Camada | Achado |
|---|---|
| Frontend | `KbPicker` coleta ids, mas o save envia `knowledgeBases: null` (`AgentEditorView.vue:277`) |
| Backend | `AgentServiceImpl.74-80` descarta o campo e loga WARN `agent legacy knowledgeBases write ignored (use /v1/agents/{id}/knowledge-bases)` |
| Endpoint indicado | **não existe** — `AgentControllerV1` não tem mapping de KB |
| `KbAgentLinkPort` | **só leitura**: `linkedKbIds`, `linkedKbNames` |

Teste: criar agente com `knowledgeBases:['3b51cdb5…']` → `agent.knowledge_bases` **vazio** e **nenhuma
linha** em `agent_knowledge_base`. Os vínculos que existem no banco vieram de IT via SQL.

### Efeito
`resolveRagContext` → `linkedKbIds` vazio → `RagContext.empty()` → `ragUsed:false`,
`citations:[]`. O LLM responde como se não tivesse recebido contexto. Silencioso.

### Dados (verificados — estão OK)
- KB `teste` (`3b51cdb5…`): tenant `44059cfc…` (o mesmo), 1 documento `READY`, **5 embeddings** em `kb_embeddings_768`.
- `nomic-embed-text` presente no Ollama local (HTTP 200).
- Logo: dado e embedding OK. Falta o vínculo.

---

## Bug 2 🔴 — Chat dá timeout e trava ~90s

### Evidência (log do backend)
```
11:11:28.719  chat inicia (glm-5.2, pedido de 1500 palavras)
11:11:58.733  HttpTimeoutException (30s) → RetryUtils: "Remaining retries: 2 of 2"
11:12:29.258  HttpTimeoutException (30s) → "Remaining retries: 1 of 2"
```
Latência real medida no mesmo agente: 2.3s (curto) · 9s (médio) · **>30s** (longo).

### Causas
1. `LangChain4jClientFactory:26` — `CLIENT_TIMEOUT = Duration.ofSeconds(30)`.
2. **`maxTokens` e `topP` do agente nunca chegam ao provider.** `LlmClientFactory` só recebe
   `temperature`; `ChatServiceImpl:180-181` não passa o resto. O limite configurado no agente é
   **silenciosamente ignorado** → geração sem teto.
3. **Auto-retry 2× em timeout** do LangChain4j → 3 × 30s = ~90s sem retorno.

---

## Bug 3 🟡 — Fluxo sem telemetria (o que o usuário pediu)

`KbRetrievalService.retrieve` retorna lista vazia **sem log** em `kbs.isEmpty()` e
`chunks.isEmpty()`. Só há log quando algo lança (`rag retrieval fallback`). Resultado: grep
no backend não acha nada e não há como saber onde o fluxo parou. No chat não existe nenhuma
linha de log com parâmetros, tempo ou consumo de tokens.

---

# Rodada 1 — Parte C (observabilidade) + Parte A (timeout)

## C. Observabilidade

### C1. `resolveRagContext` — log por etapa
Um `INFO` por mensagem de chat com o resultado do RAG, cobrindo **todos** os retornos, inclusive
os vazios que hoje são silenciosos:
- `kbIds` vinculados (contagem e ids) — ou `none`
- KBs encontradas para o tenant (contagem) — ou `kb_not_found_for_tenant`
- chunks recuperados (contagem) + latência
- motivo do vazio: `no_link` | `kb_not_found_for_tenant` | `no_chunk_above_threshold` | `timeout` | `error`
- `systemBlock` montado? (boolean + tamanho)
Sugestão de formato: `rag resolved agentId={} tenantId={} linkedKbs={} foundKbs={} chunks={} elapsedMs={} outcome={}`

### C2. `KbRetrievalService.retrieve` — silêncio eliminado
- `kbs.isEmpty()` → `log.warn` com `tenantId`, `kbIds` e o motivo (KB não pertence ao tenant / não existe).
- `chunks` vazio → `log.info` com contagem e threshold (para diferenciar "KB vazia" de "nada acima do threshold").
- Sucesso → `log.info` com `chunks={}` e `elapsedMs`.
- `future.get` estourando → manter o `warn` **e** incluir a causa-raiz (`e.getClass().getSimpleName()` + `getMessage()`), hoje só `e.getMessage()`.
- **Nunca** logar o texto da query do usuário nem o conteúdo dos chunks (podem ter dado sensível).

### C3. Chat — uma linha por mensagem
`INFO` com `sessionId, agentId, provider, model, maxTokens, topP, temperature, historySize,
ragUsed, citationsCount, promptChars, elapsedMs, promptTokens, completionTokens, finishReason`.
Isso responde de imediato: "qual timeout foi, com qual modelo, com qual maxTokens".
Complementar com `logUsage` já existente (não duplicar; unificar o que fizer sentido).

### C4. Motivo visível ao usuário quando RAG não encontra nada
Hoje o modelo afirma "não recebi contexto". A resposta já tem `ragUsed` e `citations`; incluir o
**motivo** de forma que a FE possa mostrar (ex.: um campo opcional `ragReason` ou incorporate na
mensagem de erro padrão). **Decisão de contrato em aberto — proponho**: manter `ragUsed`/`citations`
e adicionar campo novo opcional, sem quebrar o DTO existente. Se preferir alterar a mensagem do
LLM, descrever a alternativa e o trade-off (o usuário passa a ver por que a KB não respondeu).

## A. Timeout e parâmetros

### A1. Passar `maxTokens` e `topP` ao client
Estender `LlmClientFactory` (`createClient` e `createClientWithSession`) para receber
`maxTokens`/`topP` e aplicar em **todos** os providers na factory (openAi, opencode, ollama,
gemini, anthropic). Atualizar `ChatServiceImpl` para passar os valores do agente
(`agent.getMaxTokens()`, `agent.getTopP()`, `null` = deixar o default do provider).
Cuidado: `maxTokens` do agente pode ser `null` (nãoApply = default do provider, não 0).
Atualizar todos os testes que implementem/chamem a interface.

### A2. Timeout realista
`CLIENT_TIMEOUT` 30s → **120s**. Comentário curto com a medição real (2.3s / 9s / >30s) e o motivo.
Não aumentar acima disso: o rate limit de 10/min por tenant continua valendo.

### A3. Desligar auto-retry em read timeout
O LangChain4j retenta 2× em timeout → 90s pendurados. **Retentar read timeout de geração longa é
o default errado**: o usuário espera 90s para receber um erro. Configurar para **não retentar em
timeout**, mantendo retry para 429/5xx (que são transitórios e baratos de repetir).
Verificar a API disponível na versão 1.19.0 do `langchain4j` e **documentar a opção usada** —
não inventar prop. Se a única forma for desabilitar tudo, justificar o custo (429 sem retry).

## Testes (C + A)
1. `ChatServiceImplTest`:
   - RAG sem vínculo → log com `outcome=no_link`
   - KB não encontrada para o tenant → `outcome=kb_not_found_for_tenant`
   - 0 chunks acima do threshold → `outcome=no_chunk_above_threshold`
   - exceção → `outcome=error` (sem stack de dado sensível no log; com `getMessage`)
   - Sucesso → `chunks=N`, `elapsedMs`, `ragUsed=true`
2. `KbRetrievalServiceTest`:
   - `kbs.isEmpty()` → log `warn` **contendo** tenantId e kbIds
   - chunks vazio → `info` com threshold
   - `future.get` estoura → `warn` **com** causa-raiz
3. `LangChain4jClientFactoryTest`:
   - `maxTokens`/`topP` aplicados em cada provider (usar a mesma técnica de introspecção já usada)
   - `null` de `maxTokens` **não** vira 0 nem trava o provider
4. `LlmClientFactory` com timeout: teste de que o read timeout **não** é retentado
   (ou que a config de retry está correta).
5. `ChatServiceImplTest`: o `INFO` de chat contém `maxTokens`/`topP`/`elapsedMs`.
6. Nenhum teste pode afirmar log por `assertj` em stdout — capturar o `Appender` do Logback ou
   manter as asserções no **comportamento observável** (ex.: um `RagDiagnostics` retornado).
   **Preferir um recorder injetável** a assert de log: assim o teste não fica frágil.
7. H1: `mvn clean install -DskipTests` + `mvn test` + `mvn verify -pl integration-test -Pintegration -am`
   → 0 falhas. `npm run test` + `npm run build` → 0 falhas. **Zero arquivo frontend tocado nesta rodada.**

## Critérios de aceite — Rodada 1
- [ ] `INFO` por mensagem de chat com params + tempos + tokens
- [ ] RAG sem retorno vazio silencioso: todo `outcome` logado
- [ ] `kbs.isEmpty()` loga
- [ ] `maxTokens`/`topP` chegam ao provider de fato
- [ ] Timeout 120s
- [ ] Read timeout não é retentado (ou justificativa explícita)
- [ ] 0 falhas na suite completa
- [ ] `fullstack-code-reviewer` zero 🔴
- [ ] `brain_store`
- [ ] Processos finalizados
