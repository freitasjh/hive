<!-- scope: adr/ADR-004 -->
# VERIFY — ADR-004: Controle de acesso no proxy e retenção externa de 30 dias

> Gate: `.agents/skills/sdd-compiler/asserts/verify-gate.md` (Completeness / Correctness / Coherence).
> Executado em 2026-11-26, pós-merge `9f91256` (PR #18; HEAD `63baf69`). Nenhum código novo nesta sessão.

## Evidência comum da esteira (reexecução 2026-11-26)

- `npm test` → `EXIT_NPM=0`: frontend 414/414 (34 arquivos), backend 621 testes unitários, **job standalone 29/29**. Log: `/tmp/opencode/verify/npm-test.log`.
- `mvn -f backend/pom.xml -Pintegration verify` → `EXIT_MVN=0`: **181 ITs**, 0 falhas, 0 erros, 0 skips, `BUILD SUCCESS`. Log: `/tmp/opencode/verify/mvn-it.log`.
- Registro anterior "1046 testes (414 front + 603 back + 29 job)" era do corte do ADR-004; corte final tem 621 backend após ADR-005. Divergência de rótulo/corte, não de resultado.
- `git diff --check` limpo; `grep -rInE 'TODO|FIXME|HACK'` em `retention-job`, `proxy` e `docker` ⇒ 0 ocorrências.
- Code review: **APPROVED · 0🔴** com 🟠 "antes de produção" (gate e IT real da chave de delete) — registro de sessão; `workflow-state.json` defasado, não usado como fonte.
- `brain_store` **INDISPONÍVEL** nesta sessão (namespace removido): nenhuma nota Brain gravada.

## 1. Completeness ✅

- [x] **Tasks 100%:** 128/128 checkboxes em `TASKS.md` marcados; 0 pendentes.
- [x] **Sem TODOs:** varredura em job, proxy, docker e cron ⇒ vazio.
- [x] **Sem tasks puladas:** nenhuma task N/A. Caminhos de teste propostos (`ACC_TEST/`) foram materializados como `retention-job/src/test/java/...` e `retention-job/src/it/key-scope-it.sh`; desvio de localização, não descarte de cenário.
- [x] **BDD cobertos localmente:** 29 testes do job em 5 suítes — `RetentionJobTest` 9/9, `StaticConfigTest` 10/10, `ProxyEnforcementTest` 5/5, `KeyScopeModelTest` 3/3, `ProductionGateTest` 2/2 — além de `docker/check-retention-gate.sh`, `proxy/langfuse-proxy.conf.template` e `key-scope-it.sh` (IT manual, ver riscos).
- [x] **Rastreabilidade:** 23 tasks `ACC-01..23` ↔ 17 RF + 6 NFR, 1:1 sem órfão; 4 decisões operacionais datadas em `ADR.md`.

## 2. Correctness ✅

- [x] **Build limpo:** job standalone compila e roda via `npm test` (exit 0); `mvn -f backend/pom.xml -Pintegration verify` → `BUILD SUCCESS`.
- [x] **Testes passando:** 29/29 testes do job, 181 ITs backend, 0 falhas.
- [x] **Cobertura mínima:** `retention-job` não publica relatório JaCoCo — **não afirmamos percentual**. Cobertura comportamental provada pelas 29 asserções acima; domínio backend instrumentado tem relatório próprio (VERIFY-001/003).
- [x] **Requisitos funcionais:** negação 403 por capacidade ausente, grafia exata `telemetry.read`/`telemetry.admin`, leitura vs administração separadas, hardening e TLS no proxy, `Application` sem leitura/delete, `Viewer` separado em cofre, 0 credencial em log/payload/resposta, seleção de roots por `isRootObservation=true&fields=core,basic&toStartTime=$CUTOFF` com paginação por cursor, exclusão em lote `DELETE /api/public/traces`, `retention_days=30` auditável, fronteira T29 presente/T31 ausente modelada e auditada, auditoria JSON por linha com 6 campos + `traceIds`/`window`/`timestamp`, trava de produção sem job ativo, irreversibilidade.
- [x] **RBAC/tenant:** controle é capacidade no proxy, não RBAC por projeto (RF-10); 0 recurso EE; job roda fora do proxy e do backend, sem capacidade `telemetry.*`; 1 projeto, nenhuma promessa de isolamento por projeto.

## 3. Coherence ✅

- [x] **ADRs refletidos:** CLI Java standalone (`HttpClient` + Jackson), cron do host via Compose às 03:00, stdout → agregador em JSON por linha, cutoff = `startTime` do root, contagem = `submitted` — todos registrados com data em `ADR.md` e materializados em `retention-job/` + `docker/docker-compose-retention.yml` + `docker/cron/langfuse-retention.cron`.
- [x] **Sem vazamento de camada:** nenhum módulo Java do backend alterado no commit `a9632a0`; job fora do backend; chave de delete fora do backend.
- [x] **Naming:** capacidades, endpoints, `retention_days=30` e campos de auditoria congelados; 0 rename.
- [x] **Frontend:** N/A — UI do Langfuse é produto terceiro.

## Riscos residuais (bloqueiam produção, não este VERIFY)

- **Full access inferida / IT real pendente (🟠 do review):** `retention-job/src/it/key-scope-it.sh` exige `LANGFUSE_REAL_BASE_URL`, `APPLICATION_AUTH`, `VIEWER_AUTH`, `FULL_AUTH` e `CUTOFF` fornecidos pelo operador; **não foi executado nesta sessão**. Modelo local (`KeyScopeModelTest` 3/3) não prova o comportamento do Langfuse real. ADR.md:78-79, :92 e :131 mantêm a inferência como tal.
- `docker/check-retention-gate.sh` valida cron/Compose/"30", mas **não exige evidência do `key-scope-it.sh`** antes do deploy (ADR.md:95). Ação "antes de produção": reforçar o gate com a evidência do IT 403.
- Sem relatório JaCoCo no job; ver limitação em Correctness.

## Resultado Final

```
VERIFY STATUS: APPROVED ✅

Bloqueadores encontrados:
- nenhum bloqueador absoluto do gate (build, testes, RF sem implementação, vazamento de tenant)
- IT real de 403 e reforço do gate são itens "antes de produção", registrados como 🟠 no review — não impedem este VERIFY

Observações:
- Checkboxes fechados nesta sessão (128/128); RT de Full access permanece inferência até IT com chaves reais.
- Arquivar PRD e atualizar ADR-INDEX/workflow-state são atribuições do orquestrador.
- brain_store indisponível: sem nota Brain registrada.
```
