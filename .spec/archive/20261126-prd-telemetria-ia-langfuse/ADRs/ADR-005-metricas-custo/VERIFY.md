<!-- scope: adr/ADR-005 -->
# VERIFY — ADR-005: Métricas operacionais Micrometer/Prometheus e custo derivado

> Gate: `.agents/skills/sdd-compiler/asserts/verify-gate.md` (Completeness / Correctness / Coherence).
> Executado em 2026-11-26, pós-merge `b5aee24` (PR #19; HEAD `63baf69`). Nenhum código novo nesta sessão.

## Evidência comum da esteira (reexecução 2026-11-26)

- `npm test` → `EXIT_NPM=0`: frontend 414/414 (34 arquivos), backend 621 testes unitários, job 29/29. Log: `/tmp/opencode/verify/npm-test.log`.
- `mvn -f backend/pom.xml -Pintegration verify` → `EXIT_MVN=0`: **181 ITs**, 0 falhas, 0 erros, 0 skips, `BUILD SUCCESS`. Log: `/tmp/opencode/verify/mvn-it.log`. Desta fatia: `ChatMetricsExposureIT` 6/6, `LangfuseCostDerivationIT` 2/2 e `OpenApiSpecIT` **15/15**.
- Registro anterior "650 backend" = 621 backend + 29 job na contagem fresca; divergência de rótulo, não de resultado.
- `git diff --check` limpo; `grep -rInE 'TODO|FIXME|HACK'` nos caminhos implementados ⇒ 0 ocorrências.
- Code review: ciclo inicial `CHANGES_REQUIRED` (1🟠 context-path `/hive` no entry point, 2🟡) → correção em `SecurityConfig.java` com `req.getContextPath()` → **re-revisão APPROVED · 0🔴 · 0🟠 · 2🟡 residuais** (registro de sessão "Re-review ADR-005"). `workflow-state.json` defasado; não usado como fonte.
- `brain_store` **INDISPONÍVEL** nesta sessão (namespace removido): nenhuma nota Brain gravada.

## 1. Completeness ✅

- [x] **Tasks 100%:** 115/115 checkboxes em `TASKS.md` marcados; 0 pendentes.
- [x] **Sem TODOs:** varredura em `assistant/impl/diagnostics`, `bootstrap/config`, `security/config`, ITs e fixtures ⇒ vazio.
- [x] **Sem tasks puladas:** nenhuma task N/A. Substituição de `SECCFGTEST/ActuatorExposureAccessTest.java` por `IT/ChatMetricsExposureIT.java` está registrada em `TASKS.md` MET-08 (módulo `security/impl` sem `spring-boot-webmvc-test`, sem dependência nova) — desvio documentado, não task descartada.
- [x] **BDD cobertos:** unit `ChatMetricsRecorderTest`, `FrozenMetricsContractTest`, `UsageBucketsNormalizerTest`, `CachedTokenUsageTest`, `RedactionFailureMetricTest`; ITs `ChatMetricsExposureIT` 6/6 (loopback IPv4/IPv6, externo 403, `/hive` 403, health/info 200, env 404/401-403, 7 séries no scrape), `LangfuseCostDerivationIT` 2/2 (payload sem custo, custo derivado), `OpenApiSpecIT` 15/15.
- [x] **Rastreabilidade:** 23 tasks `MET-01..23` ↔ 13 RF + 10 NFR, 1:1 sem órfão; decisão B de exposição coberta em MET-08, sem ticket novo.

## 2. Correctness ✅

- [x] **Build limpo:** `mvn -f backend/pom.xml -Pintegration verify` → `BUILD SUCCESS`, `EXIT_MVN=0`.
- [x] **Testes passando:** 181 ITs 0 falhas; `npm test` exit 0 (414/621/29); `OpenApiSpecIT` 15/15.
- [x] **Cobertura mínima:** domínio instrumentado em `backend/assistant/impl/target/site/jacoco/jacoco.xml` — `UsageBucketsNormalizer` 96%, `Buckets` 100%, `TextRedactor` 100%. `bootstrap/target/site/jacoco/jacoco.xml` tem 0 classes e `SecurityConfig` não aparece no JaCoCo de `security/impl` — **não afirmamos percentual** para config/exposição; comportamento é provado pelos ITs acima.
- [x] **Requisitos funcionais:** 7 séries lógicas/exposição congeladas por `FrozenMetricsContractTest` e pelo scrape (14 nomes); nome canônico `chat.telemetry.export.failures_total` (grafia `.total` retirada: grep ⇒ 0 no código de produção); `token_type` restrito a `input|output|total`; `usage_details` com buckets excludentes (fixture 17903/17817/188/18091, sem cache ⇒ sem chave); 0 `cost_details`/`gen_ai.usage.cost`/`costDetails` e 0 preço literal no payload; consulta v2 com `query` JSON e `sum_totalCost`; só traces OTLP (0 `/v1/metrics`, 0 `/v1/logs`); flag herdada `HIVE_TELEMETRY_ENABLED` sem novo botão.
- [x] **RBAC/segurança:** scrape só loopback (`127.0.0.1`/`::1`) com 403 e corpo `{"error":"Acesso negado"}` para IP externo, inclusive sob `context-path=/hive`; health/health/**/info `permitAll`; `/actuator/**` fora do `permitAll`, `env` 404 autenticado e 401/403 anônimo; `application.properties` sem alteração; 0 credencial nova; 0 rótulo com UUID/texto/`sk-`.
- [x] **Multi-tenancy:** nenhum dado de tenant em rótulo; claims de chat/cota/RBAC inalteradas.

## 3. Coherence ✅

- [x] **ADRs refletidos:** decisão de exposição aplicada na chain `@Order(2)` de `SecurityConfig.java` com `WebExpressionAuthorizationManager` descontando `getContextPath()`; único arquivo de código autorizado alterado; sem controller, rota, credencial ou registry novo.
- [x] **Sem vazamento de camada:** registro centralizado em `ChatMetricsRecorder` (bootstrap) nos pontos já instrumentados; mapper de spans do ADR-001 intacto salvo atributo de usage; nenhuma dependência de `impl` em outro `impl`.
- [x] **Naming:** pares lógico/Prometheus exatos; 0 série semconv duplicada; 0 `ObservationChatModelListener` (grep ⇒ 0).
- [x] **Frontend:** N/A — nenhuma UI nesta fatia.

## Riscos residuais (não bloqueantes para VERIFY)

- **IT positivo de scrape loopback sob `/hive` (🟡 do review):** o IT prova 403 externo com `contextPath("/hive")`, mas não o 200 com as 7 séries por `127.0.0.1` sob `/hive`. Decisão tombada: matcher MVC descarta context path; caso positivo não executado nesta sessão.
- Corpo do 401 nas rotas não-prometheus não é asserção explícita no IT (só status) — 🟡 residual aceito.
- **Coletor remoto:** scrape restrito à loopback; coletor Prometheus em outro host não alcança o endpoint sem nova decisão (CIDR explícito ou Basic) — ADR.md:120.
- **"Tokenizer reflection" (item do briefing):** não localizei evidência de reflection em runtime no ADR-005. O que existe é tokenização provider-reportada, sem tokenizer local (ADR.md:101); reflection aparece apenas em testes de contrato do ADR-001/003. Registro o termo sem afirmar defeito não provado.
- Sem relatório JaCoCo para config/exposição; ver limitação em Correctness.

## Resultado Final

```
VERIFY STATUS: APPROVED ✅

Bloqueadores encontrados:
- nenhum (ausentes os bloqueadores absolutos do gate)

Observações:
- Checkboxes fechados nesta sessão (115/115); 🟡 residuais do review documentados acima.
- Arquivar PRD e atualizar ADR-INDEX/workflow-state são atribuições do orquestrador.
- brain_store indisponível: sem nota Brain registrada.
```
