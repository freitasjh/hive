# CODE-REVIEW — ADR-005 (fronteira de módulos / K3)

**Data:** 2026-11-23 · **Branch:** `adr/public-chat-api/05-fronteira-modulos` · **Árvore:** sem commit no momento do veredito.

## Veredito

**APPROVED** — 0 🔴 · 0 🟠 · 0 🟡 · 0 🔵 · `must_fix_before_done: []`. `reviewer_session`: `ses_dffa34effffeZMrCoNwL2t4pqv`.

## Guardas verificadas

| # | Guarda | Resultado |
|---|---|---|
| 1 | Portas em `assistant/api`, impls em `assistant/impl`; uma `@Service` por porta, sem `@Qualifier`; resolução por tipo, sem bean ambíguo | ✅ |
| 2 | Assinatura real do `TurnOutcomeReporter`: **11 parâmetros** (`sessionId, tenantId, promptTemplateId, provider, model, promptTemplate, history, reply, elapsedMs, turnId, clientId`), preservando telemetria e trace | ✅ |
| 3 | `grep -R "assistant.impl" publicapi/impl/src/main/java` limpo; `hive-assistant-impl` removido do pom; única aresta nova `assistant/api → history/api` (api→api), sem ciclo | ✅ |
| 4 | `ChatTurnGateway`/`TurnRequest`/`TurnReply`/`ChatTrace` sem diff; `ChatCallerParityIT` operacional (ordem e `120/340/460/STOP`); nenhum teste adicionado ou removido (526 inalterado é esperado em refactor com testes de caracterização) | ✅ |
| 5 | Contratos ADR-001/003/004 intactos (zero arquivos tocados); `OpenApiSpecIT` 9/9, `PublicApiClientAdminIT` 10/10, `PublicApiTenantQuotaIT` 4/4 | ✅ |
| 6 | Zero diff em ADR-001..004, skills, AGENTS.md, `Mfa*`, `workflow-state.json`, configs, UI e `.flattened-pom.xml`; `git diff --check` limpo; evidência 411 front, 526 backend, 133 ITs, build 0 | ✅ |

## Correção documental aplicada

O parecer e os artefatos descreviam "9 parâmetros"; a assinatura real do `TurnOutcomeReporter` tem 11 (a ADR-002 acrescentou `turnId`/`clientId`). O código estava correto — o texto foi ajustado em `ADR.md`, `SPEC.md` e `TASKS.md` antes do fechamento.

## Risco residual

Startup não foi reexecutado pelo reviewer (papel somente leitura); a ausência de `NoUniqueBeanDefinitionException` foi verificada por inspeção: uma `@Service` por porta, sem `@Qualifier`. O QA confirma o contexto na reexecução.

## Bloqueio de âncora

O bloqueio real é a âncora B do `gate.js` lendo `code_review_status`. Este artefato e o registro escopado `workflow-state.json.code_reviews_by_adr.ADR-005` tornam o veredito verificável sem alterar `current_phase` nem o registro da Fase 4 MFA.
