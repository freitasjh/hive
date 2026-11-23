# SPEC — ADR-005: Fronteira de módulos (K3)

> Escopo: somente a decisão da ADR-005. Não reabre contrato (ADR-001), conversão/trace (ADR-002), UI admin (ADR-003) nem cota (ADR-004). Rastreabilidade: cada `RF`/`NFR` aponta para uma cláusula do `ADR.md`.

## 1. ADRs

### ADR-005 (este ciclo)

- **Decision (fechada em 2026-11-23):** eliminar `publicapi/impl → assistant/impl` com **portas em `assistant/api` e impls permanecendo em `assistant/impl`** — `TurnPolicyResolver` e `TurnOutcomeReporter`. Mover as classes foi rejeitado: `ProviderResolution` puxa `CredentialResolver` + repositório/entidade JPA (≈6 arquivos), `TurnOutcomeMapper` puxa `FailureClassifier`.
- **Context:** dois imports e uma dependência de pom sustentam a aresta; ADR-002 deixou a mudança para este ciclo. `assistant/api` já depende de `ai/api`; receberá `history/api` para o tipo `ChatMessage` na assinatura do reporter (api → api, sem ciclo).
- **Trade-offs:** fronteira limpa e zero movimento de infra versus 2 novas portas e refactor load-bearing que atinge os dois chamadores.

ADRs afetados: ADR-019 (rename), ADR-021 (gateway em `ai/api`), ADR-002 (paridade congelada). ADR-001/003/004 não são tocadas.

## 2. Domain Context, Tenant Strategy & Provisioning

- **Bounded Context:** `publicapi` compõe `assistant` e `security`; a política de turno tem dono explícito em `assistant/api` (ADR-019 rename; `ai/api` é dono da porta de geração por ADR-021). Nenhum chamador externo depende de implementação.
- **System Type:** Spring Boot 4.1.0, Java 25 preview, monolito modular; único acréscimo de aresta permitido: `assistant/api → history/api`.
- **Tenant:** inalterado — `TenantContext` e claims `tenant-id` permanecem como estão.

## 3. Identity & Access Management

- Nenhuma mudança em `client_credentials`, JWT público, RBAC humano ou MFA.

## 4. Security, Key Management & Disaster Recovery

- Nenhuma chave nova. O envelope de erro e a rejeição de `role: system` permanecem. RTO/RPO: N/A.

## 5. Data Model & Concurrency

- Nenhuma entidade ou migration. Concorrência inalterada: as classes movidas são stateless por construção.

## 6. Event Model, Integrations & Messaging Isolation

- Nenhum evento ou integração nova.

## 7. Invariants & Property-Based Testing

| Tipo | Invariante | Verificação |
|---|---|---|
| Grafo | `publicapi/impl/pom.xml` sem `hive-assistant-impl`; zero imports `assistant.impl` em `publicapi/impl` | diff de pom + `grep` |
| Dono da regra | uma única implementação de cada porta; nenhuma política copiada | `implements` nos impls + diff sem código duplicado |
| Injeção | uma implementação `@Service` por porta, sem `@Qualifier` e sem bean ambíguo | startup/contexto + teste de injeção |
| Comportamento | ordem `SystemMessage → conversação` e tokens `120/340/460/STOP` idênticos | `ChatCallerParityIT` da ADR-002 |
| Contrato | envelope OpenAI, erros, `plim.r`/429 e UI inalterados | ITs de contrato/cota/admin |
| Trace | `turnId`/`clientId` com humano `null/null` e público real | IT de trace da ADR-002 |
| Gate | `ChatTurnGateway`, `TurnRequest`/`TurnReply` e `ChatTrace` sem diff | `git diff --name-only` |

## 8. Requisitos Funcionais

- **RF1** — remover a dependência `hive-assistant-impl` de `publicapi/impl`.
- **RF2** — declarar `TurnPolicyResolver` e `TurnOutcomeReporter` em `assistant/api` (com o record `Resolved`), fazer os impls existentes implementá-las e injetar as portas em `publicapi/impl` — sem duplicar regra.
- **RF3** — preservar diagnóstico e telemetria do turno (`ChatTrace`, 11 parâmetros do reporter).

## 9. Requisitos Não-Funcionais

- **NFR1** — suíte completa sem regressão; nenhum teste apagado ou desabilitado.
- **NFR2** — zero diff em ADR-001/002/003/004, `Mfa*`, `workflow-state.json`, UI e configs de harness.
- **NFR3** — nenhuma dependência nova de biblioteca; nenhuma recomendação sem deepsearch.
- **NFR4** — baseline de paridade congelado antes de mexer no grafo.
- **NFR5** — nenhuma aresta `api → impl`; única aresta nova autorizada `assistant/api → history/api`.

## 10. Critérios de Aceite

- `grep` em `publicapi/impl/src/main/java` não encontra `assistant.impl`; pom sem `hive-assistant-impl`.
- Portas em `assistant/api` com exatamente uma implementação cada; contexto Spring sobe sem bean ambíguo.
- `ChatCallerParityIT`, ITs de contrato, cota, admin e trace verdes; `ChatTrace` preserva `turnId`/`clientId`.
- `git diff` mostra `assistant/api → history/api` no pom e nenhuma outra aresta nova.
- Suíte 0 falhas; `code-reviewer` sem 🔴.

## 11. Riscos

| Risco | Mitigação |
|---|---|
| Arrastar infra para a API (opção 1) | forma escolhida foi portas; impls e `CredentialResolver`/repo permanecem na impl |
| Refactor muda ordem do prompt ou tokens | baseline congelado + `ChatCallerParityIT` reexecutado |
| Bean duplo ou ambiguidade de injeção | uma `@Service` por porta, sem `@Qualifier`; teste de startup/contexto |
| Assinatura do reporter perder `turnId`/`clientId` | port com os 11 parâmetros atuais; IT de trace da ADR-002 |
| Nova aresta `assistant/api → history/api` virar ciclo | `history/api` não depende de `assistant/api`; verificado nos poms |

## 12. Fontes

Internas (revisão 2026-11-23): `.spec/prd/public-chat-api/CONSTRAINTS.md` T3/K3, `ARCHITECTURE.md` §2.2, ADR-002/004 (`ADR.md`, `VERIFY.md`), `ARCHITECT-REVIEW.md`; `publicapi/impl/pom.xml:20-50`; `PublicChatServiceImpl.java:10-11,51-60,157`; `assistant/impl/.../ProviderResolution.java:42-45,52-59,68-117`; `CredentialResolver.java:4-5,22-26`; `TurnOutcomeMapper.java:3-18,71-111,174-189`; `FailureClassifier.java:6-14`; `ChatTraceRecorder.java`; `LoggingChatTraceRecorder.java:17`; `assistant/api/pom.xml:17-35`; `history/api/pom.xml:19-37`. Nenhuma fonte externa — decisão interna de fronteira.
