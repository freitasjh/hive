<!-- scope: adr/ADR-002 -->
# VERIFY — ADR-002: Ingestão OTLP/HTTP no Langfuse self-host v4

> Gate: `.agents/skills/sdd-compiler/asserts/verify-gate.md` (Completeness / Correctness / Coherence).
> Executado em 2026-11-26, pós-merge `d92c4e9` (PR #16; HEAD `63baf69`). Nenhum código novo nesta sessão.

## Evidência comum da esteira (reexecução 2026-11-26)

- `npm test` → `EXIT_NPM=0`: frontend 414/414 (34 arquivos), backend 621 testes unitários, job 29/29. Log: `/tmp/opencode/verify/npm-test.log`.
- `mvn -f backend/pom.xml -Pintegration verify` → `EXIT_MVN=0`: **181 ITs**, 0 falhas, 0 erros, 0 skips, `BUILD SUCCESS`. Log: `/tmp/opencode/verify/mvn-it.log`.
- Registro anterior "650 backend" = 621 backend + 29 job na contagem fresca; divergência de rótulo, não de resultado.
- `git diff --check` limpo; `grep -rInE 'TODO|FIXME|HACK'` nos caminhos implementados ⇒ 0 ocorrências.
- Code review: iteracao 1 `CHANGES_REQUIRED` (1🔴 contador de falha em lote bem-sucedido, 1🟠 config inválida silenciosa, 2🟡) → correções via `result.whenComplete`, validação de config, hierarquia pai/trace provada no payload e rejeição de userinfo/host → **iteração 2 APPROVED · 0🔴/0🟠/0🟡** (registro de sessão). `workflow-state.json` defasado; não usado como fonte.
- `brain_store` **INDISPONÍVEL** nesta sessão (namespace removido): nenhuma nota Brain gravada.

## 1. Completeness ✅

- [x] **Tasks 100%:** 81/81 checkboxes em `TASKS.md` marcados; 0 pendentes.
- [x] **Sem TODOs:** varredura em `backend/bootstrap/src/main/.../config`, `resources`, ITs e módulos afetados ⇒ vazio.
- [x] **Sem tasks puladas:** nenhuma task N/A. Desvios de implementação (headers Basic e `x-langfuse-ingestion-version` em código, não em properties) estão registrados em `ADR.md` §"Desvios de implementação" — não são tasks descartadas.
- [x] **BDD cobertos:** unit `FrozenIngestionContractTest`, `LangfuseOtlpHeadersTest`, `LangfuseEndpointValidationTest`; ITs `LangfuseOtlpExportIT` 1/1 (POST path congelado, `application/x-protobuf`, `resourceSpans`), `LangfuseOtlpBestEffortIT` 2/2 (exporter down, chat inalterado), `LangfuseOtlpSamplingIT` 3/3 (ausente/`false` ⇒ 0 span e 0 requisição; `true` ⇒ 100%).
- [x] **Rastreabilidade:** 16 tasks `OTL-01..16` ↔ 7 RF + 9 NFR, 1:1 sem órfão.

## 2. Correctness ✅

- [x] **Build limpo:** `mvn -f backend/pom.xml -Pintegration verify` → `BUILD SUCCESS`, `EXIT_MVN=0`.
- [x] **Testes passando:** 181 ITs 0 falhas; `npm test` exit 0 (414/621/29).
- [x] **Cobertura mínima:** `backend/bootstrap/target/site/jacoco/jacoco.xml` existe, mas não contém classes instrumentadas (0 classes) — **não afirmamos percentual** deste módulo. A cobertura comportamental das configurações é provada pelos 3 ITs e 3 suítes unitárias acima; domínio instrumentado do ADR-001 tem relatório próprio (ver VERIFY-001).
- [x] **Requisitos funcionais:** RF-01 path/transporte protobuf congelados; RF-02 Basic `pk:sk` injetado por secret (0 literal em `CFG`/`RES`); RF-03 header v4 em toda requisição; RF-04 isolamento best-effort (chat 200 com exporter recusando conexão); RF-05 root `chat.turn` por lote; RF-06 flag `HIVE_TELEMETRY_ENABLED` default `false`/`always-off`, `true`/`always-on`; RF-7 falha gera contador lógico e log sem credencial/texto bruto.
- [x] **RBAC/segurança:** credencial só no header Basic; `sk-`/base64 ausentes em log, payload e resposta; TLS validado por perfil (prod `https`, dev localhost `http`), URL com query/fragmento rejeitada.
- [x] **Multi-tenancy:** nenhum tenant criado; atributos de correlação vêm do ADR-001 e o IT de isolamento daquele ADR permanece verde.

## 3. Coherence ✅

- [x] **ADRs refletidos:** `LangfuseOtlpExporterConfig`/`LangfuseOtlpSpanSink` materializam endpoint, header v4, encoding protobuf e flag; nenhuma decisão reaberta; `http/json` não configurado (0 ocorrência).
- [x] **Sem vazamento de camada:** `ChatTrace`/`ChatTraceRecorder` inalterados (0 diff no commit `fc16d3b`); exporter isolado em `bootstrap`, sem processor global, sem OTLP metrics/logs, sem auto-instrumentação.
- [x] **Naming:** `FrozenIngestionContractTest` congela path, header, esquema Basic, encoding e par canônico `chat.telemetry.export.failures` → `chat_telemetry_export_failures_total`; 0 uso da grafia retirada.
- [x] **Frontend:** N/A — nenhuma UI nesta fatia.

## Riscos residuais (não bloqueantes para VERIFY)

- Integração com Langfuse v4 **real** não executada nesta sessão; o IT usa servidor OTLP fake que decodifica protobuf. `pk`/`sk` reais e disponibilidade do destino são responsabilidade operacional.
- Cobertura JaCoCo do módulo `bootstrap` não publicada — não afirmamos percentual; ver limitação em Correctness.
- Risco encaminhado ao ADR-005 (`/actuator/** permitAll`, scrape anônimo) foi fechado pela decisão de exposição daquele ADR; não é pendente deste.

## Resultado Final

```
VERIFY STATUS: APPROVED ✅

Bloqueadores encontrados:
- nenhum (ausentes os bloqueadores absolutos do gate)

Observações:
- Checkboxes fechados nesta sessão (81/81); desvios registrados no ADR, sem nova decisão arquitetural.
- brain_store indisponível: sem nota Brain registrada.
```
