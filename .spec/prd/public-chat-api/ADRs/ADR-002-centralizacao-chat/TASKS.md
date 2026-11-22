<!-- scope: adr/ADR-002 -->
# TASKS — ADR-002: Centralizar conversação, system message e telemetria

> Backlog TDD-ready derivado 1:1 da `SPEC.md`. Teste antes da implementação em toda tarefa. K11 já fechado por ADR-020: o baseline existe e só precisa ser registrado e estendido — nenhuma conversão é reescrita.

| ID | RF/NFR | Prioridade | Risco | Tipo | Depende de |
|---|---|---|---|---|---|
| CZ01 | NFR1, AC-1 | P0 | Alto | teste | — |
| CZ02 | RF1, AC-3 | P0 | Alto | contrato | CZ01 |
| CZ03 | RF2, RF3, AC-1, AC-3 | P0 | Alto | refactor | CZ02 |
| CZ04 | RF4, AC-2 | P0 | Médio | correção | CZ02 |
| CZ05 | NFR1..4, AC-4, AC-5 | P0 | Alto | qualidade | CZ04 |

---

### CZ01 — Congelar baseline de conversão e telemetria

- **RF/NFR:** NFR1 · **Prioridade:** P0 · **Risco:** Alto · **Tipo:** teste
- **Arquivos:** nenhum de produto; estender `backend/integration-test/.../ChatCallerParityIT.java`.
- **Teste primeiro:** o baseline **já existe** — `ChatCallerParityIT:157-167` congela 4 mensagens (`SystemMessage` do template → `UserMessage`/`AiMessage`) para ambos os chamadores; `AiServiceCharacterizationTest:40-80` e `LangChain4jTurnGatewayTest:157-166` congela `120/340/460/STOP`. Registrar valores exatos com data; estender o IT para afirmar os números de token no stub.
- **Cenários:**
  - Sucesso: baseline verde registrado com data; ordem e números idênticos aos existentes.
  - Falha: qualquer divergência prévia → parar, não tocar conversão.
  - Borda: confirmar que o diagnóstico de `model-not-found` é o mesmo nos dois caminhos.
- **DoD:** baseline documentado com fonte e data; nenhum teste removido ou desabilitado.

### CZ02 — Conversão canônica em `history/api` (guarda, não criação)

- **RF/NFR:** RF1 · **Prioridade:** P0 · **Risco:** Alto · **Tipo:** contrato
- **Arquivos:** nenhum de produto — `history/api/.../ProviderMessages.java` (ADR-017) permanece; `ProviderMessagesTest.java` como guarda.
- **Teste primeiro:** `ProviderMessagesTest:35-86` já afirma mapeamento de papel, descarte de nulo e preservação da ordem de entrada. Adicionar asserção explícita de que `blank` **não** é descartado aqui (regra congelada).
- **Cenários:**
  - Sucesso: mesma lista do baseline para o mesmo input; nenhum tipo neutro novo.
  - Falha: ordem de entrada alterada ou regra de descarte mudada → parar.
  - Borda: nenhuma aresta `ai/api → history/api` — verificar `git diff` dos poms (zero mudança).
- **DoD:** testes unitários verdes; zero dependência nova de módulo; sem novo tipo neutro.

### CZ03 — Adaptadores nos dois chamadores (guarda de não-regressão)

- **RF/NFR:** RF2, RF3 · **Prioridade:** P0 · **Risco:** Alto · **Tipo:** refactor
- **Arquivos:** `PublicChatServiceImpl:161-172`, `ChatServiceImpl:242` — já corretos; sem reescrita.
- **Teste primeiro:** `grep` por `new SystemMessage|new UserMessage|new AiMessage` fora de `history/api`; IT de CZ01 falha se a conversão mudar.
- **Cenários:**
  - Sucesso: zero montagem local; ambos os chamadores seguem chamando `ProviderMessages.toProviderMessages`.
  - Falha: qualquer montagem local ou divergência de ordem → revertar para o baseline.
  - Borda: `role: system` no request público continua 400; contrato da ADR-001 intacto.
- **DoD:** baseline idêntico; suíte completa verde; nenhuma linha de conversão reescrita.

### CZ04 — Trace atribuível (K6)

- **RF/NFR:** RF4 · **Prioridade:** P0 · **Risco:** Médio · **Tipo:** correção
- **Arquivos:** `assistant/impl/.../TurnOutcomeMapper.java:109-111,174-189`, 2 chamadores, `TurnOutcomeMapperTest`, asserções de trace.
- **Teste primeiro:** IT afirma `turnId`/`clientId` não nulos no turno público (origem: `PublicChatServiceImpl.java:174` e `:81`); humano passa `null`/`null` explícitos (`ChatTrace.java:26-27`) — `ChatTurnGateway` inalterado.
- **Cenários:**
  - Sucesso: público com ambos preenchidos; humano com ambos `null` por contrato.
  - Falha: `null` no público ou valor inventado no humano → corrigir propagação.
  - Borda: `tenantId` permanece como está; nenhuma mudança de `ScopedValue`.
- **DoD:** asserção verde nos dois caminhos; nenhum log público com `turnId=null clientId=null`.

### CZ05 — Evidência e fechamento

- **RF/NFR:** NFR1..4 · **Prioridade:** P0 · **Risco:** Alto · **Tipo:** qualidade
- **Arquivos:** nenhum de produto neste passo; relatório de evidência.
- **Teste primeiro:** suíte completa e `git diff --name-only` filtrado.
- **Cenários:**
  - Sucesso: 0 falhas, build 0 erros, `code-reviewer` sem 🔴, H1–H6 verdes.
  - Falha: loop de correção, máx. 3 iterações.
  - Borda: zero diff em poms (`ai/api`, `history/api`, `assistant/impl`, `publicapi/impl`), `Mfa*`, `workflow-state.json`, `.flattened-pom.xml`; portas livres por PID.
- **DoD:** `VERIFY.md` somente após merge; `brain_store` registrado.
