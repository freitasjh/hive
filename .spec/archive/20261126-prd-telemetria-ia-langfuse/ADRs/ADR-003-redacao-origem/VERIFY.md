<!-- scope: adr/ADR-003 -->
# VERIFY — ADR-003: Redação na origem e privacidade do texto

> Gate: `.agents/skills/sdd-compiler/asserts/verify-gate.md` (Completeness / Correctness / Coherence).
> Executado em 2026-11-26, pós-merge `1c09ac1` (PR #17; HEAD `63baf69`). Nenhum código novo nesta sessão.

## Evidência comum da esteira (reexecução 2026-11-26)

- `npm test` → `EXIT_NPM=0`: frontend 414/414 (34 arquivos), backend 621 testes unitários, job 29/29. Log: `/tmp/opencode/verify/npm-test.log`.
- `mvn -f backend/pom.xml -Pintegration verify` → `EXIT_MVN=0`: **181 ITs**, 0 falhas, 0 erros, 0 skips, `BUILD SUCCESS`. Log: `/tmp/opencode/verify/mvn-it.log`.
- Registro anterior "650 backend" = 621 backend + 29 job na contagem fresca; divergência de rótulo, não de resultado.
- `git diff --check` limpo; `grep -rInE 'TODO|FIXME|HACK'` nos caminhos implementados ⇒ 0 ocorrências.
- Code review: ciclo inicial `CHANGES_REQUIRED` com **2🔴** (CNPJ alfanumérico nu/minúsculo e vazamento em JSON) → correções em `RedactionPolicy` (case-insensitive, alternância numérica/alfanumérica, `Character.digit`/`validCnpjAlnum`) → **APPROVED, 0🔴** (registro de sessão; merge `8b5be7e`). `workflow-state.json` defasado; não usado como fonte.
- `brain_store` **INDISPONÍVEL** nesta sessão (namespace removido): nenhuma nota Brain gravada.

## 1. Completeness ✅

- [x] **Tasks 100%:** 103/103 checkboxes em `TASKS.md` marcados; 0 pendentes.
- [x] **Sem TODOs:** varredura em `assistant/impl/diagnostics`, `assistant/service`, ITs e fixtures ⇒ vazio.
- [x] **Sem tasks puladas:** nenhuma task N/A; nenhuma regra de entropia promovida a contrato (RED-04 mantém fora).
- [x] **BDD cobertos:** 12 suítes unitárias do commit `8b5be7e` (`RedactionPolicyTest`, `TextCarrierListenerTest`, `PlaceholderContractTest`, `RedactionBeforeExportTest`, `FailClosedRedactorTest`, `RedactionFailureMetricTest`, `NoRawTextSurfaceTest`, `FrozenRedactionNamingTest`, `FailOpenConfigAbsenceTest`, `NoServerSideMaskingTest`, `RedactorDecisionGateTest`) + `SensitiveFixture` com CPF, CNPJ numérico e alfanumérico, e-mail, telefone, `sk-` e senha. ITs na reexecução: `GenerationChildTextIT` 1/1, `RedactionCanaryIT` 1/1, `ForcedRedactorFailureIT` 1/1, `ChatContractWithRedactionIT` 1/1.
- [x] **Rastreabilidade:** 18 tasks `RED-01..18` ↔ 13 RF + 5 NFR, 1:1 sem órfão; emenda RED-01 com URL+data.

## 2. Correctness ✅

- [x] **Build limpo:** `mvn -f backend/pom.xml -Pintegration verify` → `BUILD SUCCESS`, `EXIT_MVN=0`.
- [x] **Testes passando:** 181 ITs 0 falhas; `npm test` exit 0 (414/621/29).
- [x] **Cobertura mínima:** `backend/assistant/impl/target/site/jacoco/jacoco.xml` — `RedactionPolicy` 98%, `TextRedactor` 100%, `TextCarrier` 90%, `ChatTurnSpanScope` 99%, `SpanAttributeMapper` 100%. Módulos sem relatório não são afirmados.
- [x] **Requisitos funcionais:** RF-01 portador de texto fora de `ChatTrace`; RF-02 redação antes da serialização OTLP (`RedactionBeforeExportTest`); RF-03 `input`/`output` só no filho `generation` (`GenerationChildTextIT` 1/1); RF-04/05 PII e segredos com placeholders exatos (`RedactionPolicyTest` com os dois CNPJs e `sk-`); RF-06 conjunto fechado de 5 placeholders; RF-07 fail-closed atômico com metadados preservados (`FailClosedRedactorTest` 3 modos + `ForcedRedactorFailureIT` 1/1); RF-08 zero literal em log/payload (`NoRawTextSurfaceTest` + canário); RF-10 canário sobre protobuf decodificado (`RedactionCanaryIT` 1/1).
- [x] **RBAC/tenant:** nenhuma permissão nova; nenhum texto bruto em log, payload, rótulo de métrica ou mensagem de erro; portador por turno/tentativa sem mistura entre turnos (isolamento herdado do ADR-001, `TurnScopeIsolationIT` verde).
- [x] **Não regressão:** resposta do chat idêntica com redator ativo, forçado a falhar e sem telemetria (`ChatContractWithRedactionIT` 1/1).

## 3. Coherence ✅

- [x] **ADRs refletidos:** regras próprias em JDK (`Pattern`/`Matcher` + dígitos verificadores), zero dependência nova (0 diff em `pom.xml`), CNPJ numérico e alfanumérico, `sk-` como requisito local, entropia fora do contrato.
- [x] **Sem vazamento de camada:** `assistant/api` inalterado no commit `8b5be7e`; redação confinada a `assistant/impl/diagnostics`; nenhum masking EE/Collector (`NoServerSideMaskingTest`).
- [x] **Naming:** chaves `langfuse.observation.input/output` exclusivamente no filho; placeholders exatos congelados por `FrozenRedactionNamingTest`.
- [x] **Frontend:** N/A — nenhuma UI nesta fatia.

## Riscos residuais (não bloqueantes para VERIFY)

- **TextCarrier higiene (🟡 aceito no review):** `attemptStarted()` sem `attemptSucceeded`/`Failed` não chama `remove()`; resta texto **já redigido** na thread até o próximo `start()`. Sem dado bruto e sem mistura entre turnos — higiene, não vazamento.
- Entropia não é contrato: token sem prefixo conhecido não é redigido nesta fatia (RED-04); nova emenda seria necessária.
- Canário valida payload protobuf decodificado/fixture, não uma instância Langfuse real com chaves de produção.

## Resultado Final

```
VERIFY STATUS: APPROVED ✅

Bloqueadores encontrados:
- nenhum (ausentes os bloqueadores absolutos do gate)

Observações:
- Checkboxes fechados nesta sessão (103/103); os 2 🔴 do review inicial foram corrigidos antes do merge.
- brain_store indisponível: sem nota Brain registrada.
```
