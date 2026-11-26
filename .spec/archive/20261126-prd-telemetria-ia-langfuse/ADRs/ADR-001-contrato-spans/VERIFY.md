<!-- scope: adr/ADR-001 -->
# VERIFY — ADR-001: Contrato de spans turno/provider

> Gate: `.agents/skills/sdd-compiler/asserts/verify-gate.md` (Completeness / Correctness / Coherence).
> Executado em 2026-11-26, pós-merge `f380216` (PR #15; HEAD `63baf69`). Nenhum código novo nesta sessão.

## Evidência comum da esteira (reexecução 2026-11-26)

- `npm test` (`harness.config.json.commands.suiteFull`) → `EXIT_NPM=0`: frontend 414/414 (34 arquivos), backend 621 testes unitários, job 29/29. Log: `/tmp/opencode/verify/npm-test.log`.
- `mvn -f backend/pom.xml -Pintegration verify` → `EXIT_MVN=0`: **181 ITs**, 0 falhas, 0 erros, 0 skips, `BUILD SUCCESS`. Log: `/tmp/opencode/verify/mvn-it.log`.
- Registro anterior citava "650 backend"; contagem fresca do Surefire é 621 backend + 29 job = 650. Divergência de rótulo, não de resultado.
- `git diff --check` limpo; `grep -rInE 'TODO|FIXME|HACK'` nos caminhos implementados ⇒ 0 ocorrências.
- Code review final: APPROVED · 0🔴 · 0🟠 · 0🟡 · 3🔵, após 4 achados fechados (registro de sessão "Re-review ADR-001"). `workflow-state.json` está defasado em relação a esta esteira e não é usado como fonte.
- `brain_store` **INDISPONÍVEL** nesta sessão (namespace removido): nenhuma nota Brain foi gravada; não afirmar nota Brain como evidência.

## 1. Completeness ✅

- [x] **Tasks 100%:** 77/77 checkboxes em `TASKS.md` marcados; 0 pendentes.
- [x] **Sem TODOs:** varredura em `backend/assistant/impl/src/main/.../diagnostics/`, `bootstrap/config`, `security/config`, `retention-job`, `proxy`, `docker` ⇒ vazio.
- [x] **Sem tasks puladas:** nenhuma task marcada N/A; nenhuma justificativa de descarte necessária.
- [x] **BDD cobertos:** 8 suítes unitárias do commit `c9769fd` (`ChatTurnRecorderPortTest`, `ChatTurnSpanScopeTest`, `ProviderCallSpanListenerTest`, `SpanAttributeMapperTest`, `AttemptCounterTest`, `FailureSpanMappingTest`, `FrozenNamingTest`, `ChatTraceShapeTest`) + `ChatSpanContractIT` 2/2 e `TurnScopeIsolationIT` 1/1 na reexecução. Cenários de sucesso/falha/borda das 15 tasks têm teste correspondente no payload ou no reflection de contrato.
- [x] **Rastreabilidade:** 15 tasks `SPAN-01..15` ↔ 8 RF + 7 NFR da `SPEC.md`, 1:1 sem órfão.

## 2. Correctness ✅

- [x] **Build limpo:** `mvn -f backend/pom.xml -Pintegration verify` → `BUILD SUCCESS`, `EXIT_MVN=0`.
- [x] **Testes passando:** 181 ITs 0 falhas; suíte `npm test` exit 0 (414 front / 621 back / 29 job).
- [x] **Cobertura mínima:** `backend/assistant/impl/target/site/jacoco/jacoco.xml` — `SpanAttributeMapper` 100%, `ChatSpan` 100%, `ChatTurnSpanScope` 99%, `ProviderCallSpanListener` 94%, `FailureClassifier` 98%. Módulos sem relatório não são afirmados aqui.
- [x] **Requisitos funcionais:** RF-01/02 (root único por turno, filho por tentativa) provados em `ChatSpanContractIT`; RF-03 (5 chaves `langfuse.*`, `clientId` nulo omitido) e RF-05 (`attempt` só no filho) no mesmo IT; RF-04 (`level=ERROR` + rótulo classificado, sem stack/prompt) em `FailureSpanMappingTest`; RF-06 (porta `record(ChatTrace)` inalterada) em `ChatTurnRecorderPortTest`; RF-07 (zero texto em `ChatTrace`) em `ChatTraceShapeTest`; RF-08 (2 tentativas, 1 root, 2 filhos, 0 órfãos, mesmo `traceId`) no IT de contrato.
- [x] **NFRs:** profundidade=2 e timestamps em nanosegundos no payload; `FrozenNamingTest` congela nomes; NFR-06 sem regressão (suíte verde, 0 diff em contrato/cota/RBAC/autenticação).
- [x] **RBAC/tenant:** nenhuma permissão nova; `TurnScopeIsolationIT` 1/1 com 2 turnos/tenants concorrentes sem mistura de atributos; fluxo humano sem `clientId` sem `langfuse.user.id`.

## 3. Coherence ✅

- [x] **ADRs refletidos:** decisão de 2 níveis implementada em `ChatTurnSpanScope`/`ProviderCallSpanListener`/`SpanListeningChatModel`; hierarquia parent/trace no payload; uso de `ChatModelListener` 1.19.0, sem `ObservationChatModelListener` (grep ⇒ 0).
- [x] **Sem vazamento de camada:** `assistant/api` (`ChatTrace`, `ChatTraceRecorder`) sem diff no commit `c9769fd`; captura e escopo restritos a `assistant/impl`; nenhuma entidade, controller ou dependência de `impl` em outro `impl`.
- [x] **Naming:** `chat.turn`, `chat.provider.call`, `langfuse.trace.name` e chaves de correlação exatas congeladas por `FrozenNamingTest`.
- [x] **Frontend:** N/A — nenhuma UI nesta fatia (sem Mudança frontend; H3 não exigido).

## Riscos residuais (não bloqueantes para VERIFY)

- **Error do sink propaga:** `ChatTurnSpanScope.close():128-132` isola `publish` com `catch (RuntimeException)`, não `catch (Throwable)`; um `Error` lançado pelo sink ainda pode alterar o desfecho do turno. Aceito no review final (0🔴), sem teste dedicado.
- Propagação listener→root é thread-local; virada reativa exige novo ADR (ADR.md:89) — não exercitada aqui.
- Ingestão real no Langfuse não foi executada nesta sessão; a prova é o payload capturado pelo IT e a cadeia do ADR-002.

## Resultado Final

```
VERIFY STATUS: APPROVED ✅

Bloqueadores encontrados:
- nenhum (ausentes os bloqueadores absolutos do gate: build, testes, RF sem implementação, vazamento de tenant)

Observações:
- Checkboxes fechados nesta sessão (77/77) sem código novo; evidências de execução são de 2026-11-26.
- Arquivar PRD e atualizar ADR-INDEX/workflow-state são atribuições do orquestrador, fora deste VERIFY.
- brain_store indisponível: sem nota Brain registrada.
```
