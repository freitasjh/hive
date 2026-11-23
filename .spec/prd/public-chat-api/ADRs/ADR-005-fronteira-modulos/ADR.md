# ADR-005 — Fronteira de módulos: eliminar aresta `publicapi/impl → assistant/impl` (K3)

**Status:** in-progress · **Data:** 2026-11-23 · **PRD:** `public-chat-api` · **Branch:** `adr/public-chat-api/05-fronteira-modulos`

**depende-de:** ADR-002 (merged — conversão e trace centralizadas sem reabrir o gateway).

## Contexto

K3 está registrado no PRD como débito estrutural: `publicapi/impl` depende de `assistant/impl` apenas para duas classes de política de turno. Evidência atual:

- `backend/publicapi/impl/pom.xml:46-50` declara `hive-assistant-impl`.
- `PublicChatServiceImpl.java:10-11` importa `br.com.systec.hive.assistant.impl.service.ProviderResolution` e `TurnOutcomeMapper`; `:51-60` injeta ambos.

As ADRs 001–004 fecharam contrato, trace, UI admin e cota sem tocar esse grafo. A ADR-002 explicitamente deixou a mudança para cá: mover `ProviderResolution`/`TurnOutcomeMapper` na mesma rodada da correção de trace misturaria refactor load-bearing com feature. A ADR-021 moveu `ChatTurnGateway` e `TurnRequest`/`TurnReply` para `ai/api`; a ADR-019 renomeou `agent` para `assistant`.

Espera-se que esta seja a **última** ADR do PRD e a única que altera o grafo de módulos. Nenhuma fonte externa nova é necessária — a decisão é interna de fronteiras.

## Decisão

Eliminar a dependência de implementação para implementação preservando o comportamento já verificado.

**Forma escolhida: opção 2 — portas em `assistant/api`, implementações permanecem em `assistant/impl`.** Não fica nada "a validar" para o implementador.

1. **Por que não mover as classes (opção 1).** `ProviderResolution.resolve(...)` chama `CredentialResolver` (`ProviderResolution.java:53,105`), que puxa `ProviderCredentialEntity` e `ProviderCredentialRepository` (`CredentialResolver.java:4-5,22`) — todos em `assistant/impl`. O próprio javadoc de `ProviderResolution:42-45` registra que a classe vive na impl por motivo de build: `api → impl` seria ciclo Maven. Mover significaria arrastar **4 classes + repositório JPA** para a API, ou criar uma porta de credencial só para destravar o movimento. `TurnOutcomeMapper` também puxa `FailureClassifier` (`assistant/impl`). Diferença medida: opção 1 ≈ 6 arquivos de domínio/infra movidos; opção 2 = 2 portas + ajustes de assinatura.
2. **Portas e dono.** Declarar em **`assistant/api`** (não `ai/api`): a política de turno tem seu dono lá — `ChatTrace`, `ChatTraceRecorder`, `ProviderOverrideGuard`, `ConfigMissingException`, `ModelReturnedEmptyException` etc. já vivem em `assistant/api` (ADR-019 renomeou `agent` → `assistant`). `ai/api` é dono da **porta de geração** (`ChatTurnGateway`, `TurnRequest`/`TurnReply`, ADR-021), não da política de trace/provedor. Colocar as portas em `ai/api` exigiria novas arestas para tipos de `assistant/api` e `history/api`.
3. **Contrato das portas.** `TurnPolicyResolver` com `resolve(UUID)` e `resolve(UUID, AiProvider, String)` e o record `Resolved(provider, model, apiKey, baseUrl)` (hoje aninhado em `ProviderResolution`); `TurnOutcomeReporter` com a assinatura atual de `TurnOutcomeMapper.resolve(...)` (11 parâmetros, incluindo `turnId`/`clientId` da ADR-002). `ProviderResolution implements TurnPolicyResolver` e `TurnOutcomeMapper implements TurnOutcomeReporter`; **nenhuma regra é duplicada** — os impls continuam sendo os únicos donos do algoritmo. Como a assinatura do reporter usa `List<history.api.domain.ChatMessage>`, `assistant/api` passa a depender de `history/api` (aresta **api → api**, sem ciclo: `history/api` não depende de `assistant/api`).
4. **Injeção sem ambiguidade.** Cada porta tem **uma** implementação `@Service` (`ProviderResolution`, `TurnOutcomeMapper`); o Spring resolve por tipo sem `@Qualifier`. `publicapi/impl` troca import/campo/construtor para as portas; `ChatServiceImpl` permanece injetando as classes concretas. O bean de teste `@Primary` de `ChatTraceRecorder` em `ChatCallerParityIT` é de outra porta e não entra nessa resolução.
5. **Grafo final.** Remover `hive-assistant-impl` de `backend/publicapi/impl/pom.xml:46-50`; `publicapi/impl → assistant/api` (já existente), `ai/api` e `history/api` permanecem. Nenhuma dependência de impl para impl.

## Fora de escopo

Trocar `ChatTurnGateway` por Ai Services; adotar `ChatMemory`/`ChatMemoryStore`; mudar contrato OpenAI, cota, UI admin ou autenticação; reabrir ADR-001/002/003/004.

## Interface congelada

- `ChatTurnGateway.execute(TurnRequest): TurnReply` — assinatura e diagnóstico inalterados; `TurnRequest`/`TurnReply` sem diff.
- Envelope OpenAI e códigos de erro da ADR-001; claim `plim`/`plim.r` e `429` da ADR-004; UI admin da ADR-003.
- Ordem `SystemMessage → conversação` e números de token medidos (`120/340/460/STOP`), provados por `ChatCallerParityIT` na ADR-002.
- `ChatTrace` com `turnId`/`clientId` — humano `null/null`, público com valores reais (ADR-002); a assinatura do reporter preserva os 11 parâmetros.
- Nenhuma dependência de `publicapi/impl` para módulos de implementação; única aresta nova permitida é `assistant/api → history/api` (api → api).
- Uma implementação por porta: nenhum `@Qualifier`, nenhum bean duplo e nenhuma regra de política copiada.

## Consequências esperadas

- **Positivas:** grafo `publicapi → APIs` apenas; decisão de turno com dono explícito em `assistant/api`; menos acoplamento para futuras mudanças de provider; sem mover repositório JPA.
- **Negativas:** refactor load-bearing — qualquer erro atinge os dois chamadores; 2 novas portas e o record `Resolved` mudam de dono; exige baseline congelado e reexecução das paridades.
- **Neutras:** contrato externo e comportamento observável inalterados; `ChatServiceImpl`, `ChatTurnGateway`, `ChatTrace` e os impls continuam com a mesma lógica.

## Evidência exigida

`git diff` provando que `hive-assistant-impl` saiu de `publicapi/impl/pom.xml` e que nenhum import de `assistant.impl` permanece em `publicapi/impl`; teste de contexto (startup) provando uma única implementação por porta, sem `NoUniqueBeanDefinitionException`; `ChatCallerParityIT` e ITs de contrato/erro/cota/trace/admin verdes; suíte completa sem regressão; zero diff em `Mfa*`, `workflow-state.json`, ADRs 001–004 e UI; `code-reviewer` sem 🔴.
