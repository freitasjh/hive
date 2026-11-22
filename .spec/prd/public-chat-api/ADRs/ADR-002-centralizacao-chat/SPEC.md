# SPEC — ADR-002: Centralizar conversação, system message e telemetria

> Escopo: somente a decisão do ADR-002. Não especifica UI admin (ADR-003), cota (ADR-004) nem fronteira de módulos (ADR-005). Rastreabilidade: cada `RF`/`NFR` aponta para uma cláusula do `ADR.md`.

## 1. ADRs

### ADR-002 (este ciclo)

- **Decision:** manter a única conversão pura já existente em `history/api` (ADR-017); travar com IT de paridade que os dois chamadores a usam; `TurnOutcomeMapper` preenche `turnId`/`clientId` na trace (K6). Gateway e poms inalterados.
- **Context:** K11 **já fechado** por ADR-020 (`PublicChatServiceImpl:161-172` e `ChatServiceImpl:242` chamam o mesmo `ProviderMessages.toProviderMessages`); K6 **aberto** (`TurnOutcomeMapper:181-189` não chama `.turnId()`/`.clientId()` do builder).
- **Trade-offs:** guarda de regressão + trace atribuível versus mudança de assinatura de um colaborador; sem novo tipo, sem nova aresta de módulo.

ADRs afetados: ADR-001 (contrato congelado), ADR-017 (dono da conversão), ADR-019 (rename `agent`→`assistant`), ADR-020 (paridade), ADR-021 (`assistant/impl`). ADR-012/013 inalterados. ADR-005 trata a fronteira de módulos depois.

## 2. Domain Context, Tenant Strategy & Provisioning

- **Bounded Context:** `history/api` **continua** dono da regra de conversão (ADR-017) — não se cria tipo neutro em `assistant/api` nem em `ai/api`. `publicapi/impl` e `assistant/impl` já dependem de `history/api`; nenhuma aresta nova. `TurnOutcomeMapper` permanece em `assistant/impl` (ADR-021); `ChatTrace` em `assistant/api`.
- **System Type:** Spring Boot 4.1.0, Java 25 preview, LangChain4j 1.19.0. Módulos renomeados por ADR-019: `agent` → `assistant`.
- **Tenant:** nenhuma mudança. `TenantContext` continua `ScopedValue<UUID>`; nada aqui introduz I/O assíncrono.

## 3. Identity & Access Management

- Nenhum fluxo de autenticação, MFA ou RBAC é tocado. `@HasPermission("PUBLIC_CHAT_COMPLETE")` permanece.

## 4. Security, Key Management & Disaster Recovery

- Nenhuma chave nova. A rejeição de `role: system` no request público permanece como regra de segurança: o template do cliente entra primeiro e um `system` do cliente chegaria por último.
- RTO/RPO: N/A.

## 5. Data Model & Concurrency

- Nenhuma entidade ou migration. `ChatTrace` ganha preenchimento de campos já existentes.
- Concorrência inalterada: a função de conversão é pura e stateless.

## 6. Event Model, Integrations & Messaging Isolation

- Nenhum evento ou integração nova.

## 7. Invariants & Property-Based Testing

| Tipo | Invariante | Verificação |
|---|---|---|
| Prompt | `SystemMessage` do template vem antes da conversação — responsabilidade do gateway (`telemetryPrompt:67-72`, `alwaysKeepSystemMessageFirst:118`) | `ChatCallerParityIT:157-167` (4 mensagens, ordem exata) |
| Prompt | conversão canônica: `SYSTEM→SystemMessage`, `ASSISTANT→AiMessage`, demais→`UserMessage`; nulo descartado; **ordem de entrada preservada**; `blank` não descartado aqui | `ProviderMessagesTest:35-86` |
| Chamadores | nenhuma montagem local de mensagens do provider fora de `history/api` | `grep` em `PublicChatServiceImpl`/`ChatServiceImpl` + paridade de prompts |
| Telemetria | `prompt_tokens=120`/`total_tokens=460`/`finish_reason=STOP` idênticos ao baseline | `AiServiceCharacterizationTest:40-80`, `LangChain4jTurnGatewayTest:157-166` |
| Trace | `turnId` e `clientId` preenchidos no turno público; `null` no humano | asserção no registro de trace (ambos os caminhos) |
| Contrato | envelope OpenAI e códigos de erro da ADR-001 inalterados (inclui 400 para `role: system`) | IT de contrato existente |
| Módulo | nenhuma aresta `ai/api → history/api`, `assistant/api → publicapi/**` ou `assistant/api → assistant/impl` | `git diff` dos poms — zero mudança |

## 8. Requisitos Funcionais

- **RF1** — manter a função pura de conversão em `history/api` (ADR-017), com a regra congelada da Decisão 1; **nenhum tipo novo, nenhuma aresta de módulo**.
- **RF2** — `PublicChatServiceImpl` usa a função, sem montagem local (já verdadeiro — registrar guarda).
- **RF3** — `ChatServiceImpl` usa a mesma função, sem montagem local (já verdadeiro — registrar guarda).
- **RF4** — `TurnOutcomeMapper.resolve(...)` recebe `turnId`/`clientId` e preenche ambos no builder; humano passa `null`/`null` explícitos.

## 9. Requisitos Não-Funcionais

- **NFR1** — suíte completa sem regressão; nenhum teste apagado ou desabilitado.
- **NFR2** — zero diff em `Mfa*` e `workflow-state.json`.
- **NFR3** — diagnóstico de `model-not-found` idêntico nos dois chamadores.
- **NFR4** — nenhuma dependência nova de módulo.

## 10. Critérios de Aceite

- **AC-1** — `ChatCallerParityIT` prova ordem de mensagens idêntica nos dois chamadores (baseline 4 mensagens) e os números `120/340/460/STOP`.
- **AC-2** — Trace do turno público com `turnId` e `clientId` não nulos; turno humano com ambos `null`.
- **AC-3** — Zero montagem de mensagens do provider fora de `history.api.util.ProviderMessages`.
- **AC-4** — Contrato público da ADR-001 intacto (inclui 400 para `role: system`).
- **AC-5** — `code-reviewer` sem 🔴 e evidência H1–H6 registrada; poms inalterados.

## 11. Riscos

| Risco | Mitigação |
|---|---|
| Alguém "corrigir" a ordem dentro do conversor | responsabilidade registrada no gateway; `ChatCallerParityIT` congela o prompt final |
| Perda de `TokenUsage`/`FinishReason` | `TurnReply` permanece como está; baseline `120/340/460/STOP` já caracterizado |
| Refactor tocar comportamento de erro | IT de contrato da ADR-001 reexecutado |
| Trace com `null` indevido | asserção no público (preenchido) e no humano (`null` esperado) |
| Assinatura de `TurnOutcomeMapper.resolve` divergir entre chamadores | compilação + 2 chamadores e `TurnOutcomeMapperTest` atualizados no mesmo batch |

## 12. Fontes

Internas (revisão 2026-11-22): ADR-017, ADR-019, ADR-020, ADR-021; `history/api/.../ProviderMessages.java:48-63`; `ProviderMessagesTest:35-86`; `AbstractLangChain4jTurnGateway.java:67-72,118`; `ChatCallerParityIT:95-167`; `AiServiceCharacterizationTest:40-80`; `LangChain4jTurnGatewayTest:157-166`; `ChatTrace.java:26-27,104-112`; `TurnOutcomeMapper.java:109-111,174-189`; `PublicChatServiceImpl.java:81,161-182`; `ChatServiceImpl.java:242,254-255`; poms de `ai/api`, `history/api`, `assistant/impl`, `publicapi/impl`. `TECH-BRIEF.md` §2/R1–R2/R7, §4/K3/K6/K11; `ARCHITECTURE.md` §2.2; `EXPLORATION.md` §2. Nenhuma fonte externa nova — a ADR não recomenda biblioteca ou versão.
