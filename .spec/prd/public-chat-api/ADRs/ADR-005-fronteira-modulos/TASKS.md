<!-- scope: adr/ADR-005 -->
# TASKS — ADR-005: Fronteira de módulos (K3)

> Backlog TDD-ready derivado 1:1 da `SPEC.md`. Teste antes de mexer no grafo. Refactor load-bearing: baseline primeiro, paridade depois, commit por último. Forma já decidida na ADR: portas em `assistant/api`, impls na impl.

| ID | RF/NFR | Prioridade | Risco | Tipo | Depende de |
|---|---|---|---|---|---|
| MOD01 | NFR4, AC-3 | P0 | Alto | teste | — |
| MOD02 | RF1, RF2 | P0 | Alto | arquitetura | MOD01 |
| MOD03 | RF2, RF3 | P0 | Alto | refactor | MOD02 |
| MOD04 | RF1, AC-1, AC-2 | P0 | Alto | build/pom | MOD03 |
| MOD05 | NFR1, NFR2 | P0 | Médio | evidência | MOD04 |

---

### MOD01 — Congelar baseline de paridade

- **RF/NFR:** NFR4 · **Prioridade:** P0 · **Risco:** Alto · **Tipo:** teste
- **Arquivos:** nenhum de produto; IT existente reforçado.
- **Teste primeiro:** registrar ordem das mensagens e tokens `120/340/460/STOP` nos dois chamadores (`ChatCallerParityIT:157-167`, `AiServiceCharacterizationTest`).
- **Cenários:**
  - Sucesso: baseline verde e idêntico ao da ADR-002.
  - Falha: divergência prévia → parar, não mexer no grafo.
  - Borda: trace com `turnId`/`clientId` nos valores já conhecidos (público real, humano `null/null`).
- **DoD:** baseline documentado com data; nenhum teste modificado para esconder falha.

### MOD02 — Fixar as portas em `assistant/api`

- **RF/NFR:** RF1, RF2 · **Prioridade:** P0 · **Risco:** Alto · **Tipo:** arquitetura
- **Arquivos:** decisão já registrada na ADR (sem código neste passo): `TurnPolicyResolver` (com record `Resolved`) e `TurnOutcomeReporter` em `assistant/api`; impls ficam na impl.
- **Teste primeiro:** confirmar os colaboradores que impedem a mudança — `ProviderResolution:53,105` → `CredentialResolver` → `ProviderCredentialRepository`/Entity; `TurnOutcomeMapper` → `FailureClassifier` (todos em `assistant/impl`). Nada a decidir: opção 1 foi rejeitada.
- **Cenários:**
  - Sucesso: 2 portas, uma implementação `@Service` cada; nenhuma regra copiada.
  - Falha: surgir segunda implementação ou `@Qualifier` → parar.
  - Borda: única aresta nova `assistant/api → history/api` (para `List<ChatMessage>` no reporter); sem ciclo.
- **DoD:** decisão registrada no ADR e no Brain antes de editar código.

### MOD03 — Mover/adaptar sem mudar comportamento

- **RF/NFR:** RF2, RF3 · **Prioridade:** P0 · **Risco:** Alto · **Tipo:** refactor
- **Arquivos:** `assistant/api` (2 portas + record; pom com `hive-history-api`), `assistant/impl` (`ProviderResolution implements TurnPolicyResolver`, `TurnOutcomeMapper implements TurnOutcomeReporter`), `publicapi/impl` (imports/campo/construtor para as portas; `ProviderResolution.Resolved` → `TurnPolicyResolver.Resolved`), `PublicChatServiceImplTest`.
- **Teste primeiro:** `ChatCallerParityIT` falha se ordem/tokens divergirem; teste de startup falha se houver bean ambíguo.
- **Cenários:**
  - Sucesso: impls mantidos como único dono da regra; `ChatServiceImpl` continua com classes concretas; os 9 parâmetros do reporter preservam `turnId`/`clientId`.
  - Falha: lógica duplicada ou `ChatTrace` divergente → corrigir antes de seguir.
  - Borda: `ChatTurnGateway`, `TurnRequest`/`TurnReply` e `ChatTrace` sem diff; anônimo `new ProviderResolution(null,null){...}` do teste passa a retornar o record da porta.
- **DoD:** baseline idêntico; suíte verde.

### MOD04 — Remover a dependência de pom

- **RF/NFR:** RF1 · **Prioridade:** P0 · **Risco:** Alto · **Tipo:** build/pom
- **Arquivos:** `backend/publicapi/impl/pom.xml`.
- **Teste primeiro:** `grep -R "assistant.impl" publicapi/impl/src/main/java` ainda encontra imports.
- **Cenários:**
  - Sucesso: `hive-assistant-impl` removido; build compila.
  - Falha: compilação quebrar por import transitivo → ajustar dependência real, não reintroduzir a impl.
  - Borda: único acréscimo de pom permitido é `assistant/api → history/api`; `ai/api` sem alteração.
- **DoD:** `grep` limpo; `mvn install` 0 erros; ITs verdes.

### MOD05 — Evidência e fechamento

- **RF/NFR:** NFR1, NFR2 · **Prioridade:** P0 · **Risco:** Médio · **Tipo:** evidência
- **Arquivos:** nenhum de produto; relatório H1–H6.
- **Teste primeiro:** suíte completa e `git diff --name-only` filtrado.
- **Cenários:**
  - Sucesso: 0 falhas, build 0, review sem 🔴, H6 verde, `VERIFY.md` após merge.
  - Falha: loop de correção, máx. 3 iterações.
  - Borda: zero diff em ADR-001/002/003/004, Mfa*/workflow-state, UI, `ChatTurnGateway`/`TurnRequest`/`TurnReply`/`ChatTrace` e configs; portas por PID via lsof + kill, nunca `pkill -f`.
- **DoD:** brain_store + harness report; sem commit antes da autorização.
