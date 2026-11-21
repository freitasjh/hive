# TECH-BRIEF — LangChain4j AI Services no backend do hive

> **Natureza:** insumo técnico para o PRD. **Não decide arquitetura.** Quem decide é `architect`, depois que `product-manager` aprovar o PRD (`.agents/rules/prd-adr-pipeline.md:15-16`).
> **Agente:** `engineer` (md-only; permissão `edit` restrita a `.spec/**` em `opencode.json:43-53`).
> **Data:** 2026-11-21. **Escopo:** Leitura A — todo o backend (`backend/**`). Fora: frontend, desenvolvimento/código, ciclo MFA em andamento (`workflow-state.json:2-6`).
> **Estado da esteira:** nenhum `PRD.md`, `RESEARCH.md`, `ADR-INDEX.md`, `ADRs/**`, `SPEC.md` ou `TASKS.md` criado por este agente. Único artefato deste lote: este arquivo.

## 0. Convenções de leitura

- **Regra atual** = comportamento verificável no código, citado como `arquivo:linha`.
- **Regra proposta** = candidato de centralização. É insumo; não é decisão e não é especificação.
- **Afirmação de API** segue o formato `Afirmação — URL — 2026-11-21`. Fontes `raw.githubusercontent.com/langchain4j/langchain4j/1.19.0/...` são **fixadas na tag 1.19.0**. Páginas `docs.langchain4j.dev` são **não versionadas** e estão rotuladas como tal; onde a distinção importa, a afirmação foi confirmada também no código-fonte 1.19.0.
- **"não encontrado"** significa busca feita em `backend/**` nesta data; não significa "não existe no LangChain4j".
- Nenhuma assinatura de API foi aceita de memória: todas as assinaturas abaixo foram lidas nas fontes do §7.

---

## 1. Inventário do backend

Agregador raiz: `backend/pom.xml:16-27` declara 10 módulos de primeiro nível (`dependencies`, `tenant`, `commons`, `bootstrap`, `identity`, `security`, `agent`, `provider`, `publicapi`, `integration-test`). Total: **44 `pom.xml`**. Abaixo, todos.

### 1.1 Governança / build

| Módulo Maven | Papel atual | Acoplamento chat/IA | Evidência |
|---|---|---|---|
| `backend` (raiz) | agregador + compiler/surefire/jacoco/flatten; Java 25 preview | `--enable-preview` atinge todo o backend | `backend/pom.xml:16-27`, `:36-39`, `:88-98` |
| `dependencies` | BOM interno; fixa LangChain4j **1.19.0** e Tika **1.19.0-beta29**; gerencia 6 artefatos `langchain4j-*` + agregador `langchain4j` | fonte única da versão; nenhum starter declarado | `backend/dependencies/pom.xml:37`, `:46`, `:186-243` |
| `bootstrap` | aplicação Spring Boot; único módulo que leva artefatos `dev.langchain4j` para runtime (escopo `compile`) | declara `hive-provider-langchain4j`, `langchain4j-ollama`, `langchain4j-core`, `langchain4j-open-ai`, agregador `langchain4j` | `backend/bootstrap/pom.xml:198-233`; classe em `backend/bootstrap/src/main/java/br/com/systec/hive/HiveApplication.java` |

### 1.2 `commons` — agregador `backend/commons/pom.xml:19-21`

| Módulo | Papel atual | Acoplamento chat/IA | Evidência |
|---|---|---|---|
| `commons/api` | domínio base e exceções (`BaseException`, `TenantNotFountException`) | exceção de tenant é lançada por `TenantContext.getCurrentTenant()` | `backend/commons/api/src/main/java/br/com/systec/hive/commons/api/exceptions/TenantNotFountException.java`; `commons/impl/.../TenantContext.java:11-13` |
| `commons/impl` | isolamento de tenant + HTTP pinado + rate limiter | `TenantContext` é `ScopedValue`; `TenantAspect` habilita `@Filter` em repositórios JPA | `commons/impl/.../TenantContext.java:8-16`; `TenantAspect.java:23-35`; `commons/impl/.../entity/AbstractEntity.java:16-17` |
| `commons/api-tools` | controller REST base, `@HasPermission`, envelope global de erro | afeta o contrato de erro do turno (não o turno em si) | `commons/api-tools/.../exceptions/ControllerExceptionHandler.java`; `.../security/HasPermissionAspect.java` |

### 1.3 `tenant` — agregador `backend/tenant/pom.xml:18-21`, web `:18`

| Módulo | Papel atual | Acoplamento chat/IA | Evidência |
|---|---|---|---|
| `tenant/api` | `TenantLlmConfig`, serviço `require(tenantId)` | é a origem do par provider/modelo padrão do turno | `tenant/api/.../domain/TenantLlmConfig.java`; `tenant/api/.../service/TenantLlmConfigService.java` |
| `tenant/impl` | entidade/repositório/serviço de config LLM | `TenantLlmConfigServiceImpl`, `TenantLlmConfigRepository` | `tenant/impl/.../service/TenantLlmConfigServiceImpl.java`; `.../repository/TenantLlmConfigRepository.java` |
| `tenant/dbupdate` | Flyway do bounded context | não participa do turno | `tenant/dbupdate/.../config/TenantDbUpdateConfig.java` |
| `tenant/web`, `tenant/web/internal`, `tenant/web/internal/v1` | agregadores + controller de config LLM | `TenantLlmConfigControllerV1` lê tenant do `TenantContext` | `tenant/web/pom.xml:18`; `tenant/web/internal/pom.xml:18`; `tenant/web/internal/v1/.../TenantLlmConfigControllerV1.java:82` |

### 1.4 `identity` — agregador `backend/identity/pom.xml:17-20`, web `:18`, web/internal `:17`

| Módulo | Papel atual | Acoplamento chat/IA | Evidência |
|---|---|---|---|
| `identity/api` | domínio/serviços de usuário e MFA | nenhum direto; `ChatServiceImpl` recebe `userId` como parâmetro, não lê repositório de usuário | `identity/api/.../service/UserService.java`; `agent/impl/.../ChatServiceImpl.java:127-132` |
| `identity/impl` | JPA de usuário/MFA | nenhum direto | `identity/impl/.../repository/UserRepository.java` |
| `identity/dbupdate` | Flyway de identidade | nenhum | `identity/dbupdate/.../config/UserDbUpdateConfig.java` |
| `identity/web`, `identity/web/internal`, `identity/web/internal/v1` | controllers de conta/roles | nenhum | `identity/web/internal/v1/.../controller/UserAccountControllerV1.java` |

Ciclo MFA em andamento é tratado como **fora de escopo** (`workflow-state.json:2-6`). Nenhum artefato deste lote propõe tocar `identity/**/Mfa*` ou `security/**/Mfa*`.

### 1.5 `security` — agregador `backend/security/pom.xml:16-18`, web `:16`, web/internal `:17`

| Módulo | Papel atual | Acoplamento chat/IA | Evidência |
|---|---|---|---|
| `security/api` | portas de token/login/MFA | nenhum direto ao turno | `security/api/.../service/SecurityTokenService.java` |
| `security/impl` | filtros, `SecurityConfig`, token services, Redis | **dois filtros abrem o `TenantContext`**: humano e API pública | `security/impl/.../filter/SecurityFilter.java:56`; `.../filter/PublicApiSecurityFilter.java:103`; `.../config/SecurityConfig.java` |
| `security/web`, `security/web/internal`, `security/web/internal/v1` | controllers de auth/permissões | nenhum direto; é a 3ª opção de reuso descartada no parecer do Brain | `security/web/internal/v1/.../controller/AuthPermissionsControllerV1.java`; Brain `arquitetura/projetos/hive/arquitetura/api-publica-chat/parecer-consolidado-2026-09-27` |

### 1.6 `provider` — agregador `backend/provider/pom.xml:18-19`

| Módulo | Papel atual | Acoplamento chat/IA | Evidência |
|---|---|---|---|
| `provider/api` | `AiProvider`, `ChatRole`, `VendorCatalog`, `LlmClientFactory`, `OpencodeDiscoveryService`, `ProviderBaseUrlGuard` | contrato de criação de cliente; `LlmClientFactory` expõe `createClient`/`createClientWithSession` com `maxTokens`/`topP` | `provider/api/.../LlmClientFactory.java:8`, `:15`, `:24-25` |
| `provider/langchain4j` | fábrica concreta de 5 providers, política de retry, descoberta de modelos | `createClientWithSession` chama 4 builders + Ollama; `maxRetries(0)` em todos; descoberta usa HTTP pinado, **não** AiServices | `provider/langchain4j/.../LangChain4jClientFactory.java:41`, `:94-105`, `:115`, `:135`, `:153`, `:169`, `:185`; `.../LlmRetryPolicy.java:29-146`; `.../OpencodeDiscoveryServiceImpl.java:62-81` |

### 1.7 `agent` — agregador `backend/agent/pom.xml:16-19`, web `:18`, web/internal `:18`

| Módulo | Papel atual | Acoplamento chat/IA | Evidência |
|---|---|---|---|
| `agent/api` | domínio do turno (`TurnRequest`, `TurnReply`, `ChatSession`, `InferenceParams`, `ChatTrace`), porta `ChatTurnGateway`, funções puras (`ProviderMessages`, `ModelNotFound`, `ProviderOverrideGuard`) | já depende de `langchain4j-core` e `provider/api` | `agent/api/pom.xml:28-40`; `agent/api/.../port/ChatTurnGateway.java:29-36`; `.../util/ProviderMessages.java:39-64`; `.../util/ModelNotFound.java:35-86` |
| `agent/impl` | orquestração do turno, JPA, retry wrapper, adapter LangChain4j, telemetria | `ChatServiceImpl` → `ProviderResolution` → `CredentialResolver` → gateway; gateway chama `client.chat(prompt)` | `agent/impl/pom.xml:34-61`; `agent/impl/.../ChatServiceImpl.java:94-117`, `:204-225`; `.../LangChain4jTurnGateway.java:51-83` |
| `agent/dbupdate` | Flyway do agente (V2–V5; V7 de override por conversa) | `chat_session.provider/model` | `agent/dbupdate/.../config/AgentDbUpdateConfig.java`; `agent/api/.../domain/ChatSession.java:18-23`; Brain `arquitetura/projetos/hive/chat-provider-override-por-conversa` |
| `agent/web`, `agent/web/internal`, `agent/web/internal/v1` | controllers da UI de chat | `ChatControllerV1` chama `service.chat(...)`; DTO valida `maxTokens` | `agent/web/internal/v1/.../controller/ChatControllerV1.java:49`; `.../dto/InferenceParamsDTO.java:8-20` |

### 1.8 `publicapi` — agregador `backend/publicapi/pom.xml:16-19`, web `:17`, web/internal `:17`

| Módulo | Papel atual | Acoplamento chat/IA | Evidência |
|---|---|---|---|
| `publicapi/api` | DTO OpenAI-compatible, exceções, porta `PublicChatService` | `system` é rejeitado na camada de serviço, não aqui | `publicapi/api/.../dto/ChatCompletionRequestDTO.java`; `.../exceptions/SystemRoleNotAllowedException.java` |
| `publicapi/impl` | 2º chamador da porta; cota Redis, grant de prompt, envelope de resposta | **aresta para `agent/impl`**: `ProviderResolution` e `TurnOutcomeMapper` | `publicapi/impl/pom.xml:33-47`; `publicapi/impl/.../PublicChatServiceImpl.java:9-10`, `:43-45`, `:145-172` |
| `publicapi/dbupdate` | Flyway próprio (`flyway_schema_history_publicapi`) | credenciais/grants, não mensagens | `publicapi/dbupdate/.../config/PublicApiDbUpdateConfig.java` |
| `publicapi/web`, `publicapi/web/internal`, `publicapi/web/internal/v1` | controllers `/public/auth/token` e `/v1/chat/completions` | envelope OpenAI + `metadata.hive` | `publicapi/web/internal/v1/.../controller/PublicChatControllerV1.java`; `PublicChatServiceImpl.java:175-213` |

### 1.9 Qualidade

| Módulo | Papel atual | Acoplamento chat/IA | Evidência |
|---|---|---|---|
| `integration-test` | ITs com banco real e providers mockados | `ChatProviderOverrideIT`, `ChatEmptyResponseIT`, `OpencodeGoZenIT` exercitam a fronteira do gateway/fábrica | `integration-test/src/test/java/.../ChatProviderOverrideIT.java:57-63`; `.../ChatEmptyResponseIT.java:275-286`; `.../OpencodeGoZenIT.java:100` |

**Grafo relevante (verificado):** `agent → tenant/api, provider/api, provider/langchain4j, commons/impl, identity/api`; `publicapi/impl → agent/api + agent/impl`; `security` e `agent` são **brothers** (Brain `arquitetura/projetos/hive/decisoes/api-publica-chat`). A aresta `publicapi/impl → agent/impl` existe em `pom.xml:33-37` e é usada em `PublicChatServiceImpl.java:9-10`.

---

## 2. Regras hoje espalhadas e candidatas a centralizar

### R1 — Montagem de prompt / system message

**Regra atual (occurrence list):**

- `LangChain4jTurnGateway.java:66-69` lê `InferenceParams` e monta `prompt = buildMessages(request)`; `:120-129` cria a lista, insere `new SystemMessage(template)` **somente se o template não for blank** e depois `addAll(conversation)`.
- `ChatServiceImpl.java:194` obtém o `PromptTemplate` da sessão; `:218-219` entrega template + conversação ao gateway.
- `PublicChatServiceImpl.java:151-162` **não** usa `ProviderMessages`: converte cada mensagem do request diretamente em `AiMessage`/`UserMessage` e rejeita `role=system` em `:69-73`.
- `ChatModelValidateProbe.java:10-11` chama `client.chat("ping")` — terceira montagem de mensagem, sem system e sem histórico.
- `ProviderMessages.java` existe **duas vezes**: a de `agent/api` converte **para** mensagens do provider (`:48-63`, com ramo `SYSTEM` em `:57-58` e descarte de conteúdo nulo em `:51-54`); a de `agent/impl` lê **de** mensagens/respostas (`:36-41`, `:44-55`, `:61-70`). O gateway importa a de `impl` (`LangChain4jTurnGateway.java:8`).

**Regra proposta (candidata):** uma única declaração de onde o `SystemMessage` entra, em que ordem, e qual conversão de histórico é usada pelos dois chamadores da porta. `systemMessageProvider(...)` do AiService é candidato natural, **mas a ordem muda**: em `MessageWindowChatMemory` 1.19.0, um `SystemMessage` novo com conteúdo diferente substitui o anterior e **é anexado ao fim** salvo `alwaysKeepSystemMessageFirst(true)` — `https://raw.githubusercontent.com/langchain4j/langchain4j/1.19.0/langchain4j/src/main/java/dev/langchain4j/memory/chat/MessageWindowChatMemory.java` — 2026-11-21. Hoje a regra de segurança é "template primeiro" (`LangChain4jTurnGateway.java:121-128`; reforçada no parecer do Brain `fechamento-d5-d7-d3`, seção D3).

### R2 — Conversão de histórico para mensagens do provider

**Regra atual:**

- `agent/api/.../ProviderMessages.java:48-63` — conversão canônica: `ASSISTANT → AiMessage`, `SYSTEM → SystemMessage`, resto `→ UserMessage`; mensagens sem conteúdo são descartadas.
- `ChatServiceImpl.java:210-212` — `findLast20` (20 mensagens, `ChatMessageServiceImpl.java:68-74`) convertidas uma vez e reutilizadas em `TurnRequest.conversation` (`TurnRequest.java:47-50`, `:18-22`).
- `PublicChatServiceImpl.java:152-167` — converte o payload direto, **sem ramo `SYSTEM` e sem descarte de conteúdo nulo**; passa `history = List.of()` (`:167`).
- Observação: a existência de duas conversões é intencionalmente documentada como "um conversor, um leitor" em `agent/api/.../ProviderMessages.java:31-37`; a divergência está entre `agent/api` e `publicapi/impl`, não entre os dois `ProviderMessages`.

**Regra proposta (candidata):** uma única função de conversão reusada por ambos os chamadores; explicitar se o caminho público continua stateless.

### R3 — Retry e classificação de transitoriedade

**Regra atual:**

- `LlmRetryPolicy.java:31-35` (2 retries, 500 ms, expoente 1.5, jitter 20%), `:72-97` (loop + backoff), `:119-146` (transiente = 429/408/5xx; **não** transiente = timeout, `NonRetriableException`, `ModelNotFoundException`, `AuthenticationException`).
- Motivação: retry padrão do SDK 1.19.0 retenta todo `Exception` não-`NonRetriableException`, inclusive timeout (`:17-27`); `MAX_RETRIES = 0` em `LangChain4jClientFactory.java:41` e replicado nos 5 builders (`:115`, `:135`, `:153`, `:169`, `:185`).
- Gateway aplica a política em `LangChain4jTurnGateway.java:55`, `:59`, `:83`.
- **Débito de documento/código:** javadoc em `LlmRetryPolicy.java:53` diz "no jitter (single-node backend)", mas `defaults()` em `:55` passa `jitterEnabled = true`. Comportamento real = com jitter.
- Caminho de validação não usa retry: `ValidateServiceImpl.java:41`, `:86-104` (timeout de 15 s com executor daemon).

**Regra proposta (candidata):** uma única política de retry, com a mesma semântica para geração e probe, ou Declaração explícita de que o probe é exceção; corrigir o javadoc. **Não substituir por retry translúcido do AiService sem medir**: AiServices 1.19.0 não expõe config de retry no builder lido em `AiServices.java`; o retry continua sendo responsabilidade do `ChatModel` construído pela fábrica.

### R4 — Classificação de `model-not-found`

**Regra atual:**

- `ModelNotFound.java:42-43` (regex), `:48-70` (cadeia de causas; `ModelNotFoundException` → true; 404 só conta se corpo for JSON citando o modelo, `:54-61`; `:77-86`).
- `FailureClassifier.java:69-70` (`model_not_found`) e `:75-89` (404 genérico vira `not_found`, 5xx `server_error`) — **segundo classificador**, com vocabulário diferente.
- `TurnOutcomeMapper.java:136-146` usa `ModelNotFound.isModelNotFound` para decidir evict + 422, e `FailureClassifier.causeOf` para o rótulo da trace (`:134-135`).
- `LangChain4jTurnGateway.java:32-49` registra a mudança histórica da regra para `agent/api`.
- Regra de integração no Brain `regras/global/hive/integracao/openai-compatible-baseurl-v1`: 404 com corpo HTML = `/v1` ausente, não "modelo removido".
- `OpencodeDiscoveryServiceImpl.java:71-81` tem terceira classificação (non-200 → `DiscoveryFailedException`).

**Regra proposta (candidata):** um único tradutor transporte → domínio, com o mesmo par "status + corpo" para os três caminhos; manter evict e log no chamador (como já dizem os javadocs `LangChain4jTurnGateway.java:47-49` e `ModelNotFound.java:31-33`).

### R5 — Limites de `maxTokens` e de tamanho de requisição

**Regra atual:**

- `InferenceParams.java:10-21` guarda `temperature/topP/maxTokens`; `InferenceParamsDTO.java:9-19` valida `maxTokens ∈ [256, 4096]`, `temperature ∈ [0,2]`, `topP ∈ [0,1]`.
- `LangChain4jTurnGateway.java:66-68`, `:80-82` — repassa `maxTokens` direto para a fábrica. **Clamp no gateway: não encontrado.** O parecer do Brain `parecer-consolidado-2026-09-27` afirma que o gateway "pode clampar `maxTokens`" (tabela de omissões, linha `LangChain4jTurnGateway:76-78`); o código atual não contém `Math.min`/clamp nesse caminho. Tratar como divergência histórica a reconciliar, não como fato atual.
- `LangChain4jClientFactory.java:116-124` (OpenAI), `:138-144` (OpenCode), `:154-159` (Ollama/`numPredict`), `:170-176` (Gemini/`maxOutputTokens`), `:187-193` (Anthropic; null = default 1024, nunca 0).
- Telemetria do limite: `TurnOutcomeMapper.java:195-210`, `ChatTrace.java:31`, `:53`, `LoggingChatTraceRecorder.java:43`.
- **Regra diferente, não confundir:** `PublicChatServiceImpl.java:114-131` limita **número de mensagens e caracteres do request** (`m=200`, `c=32000`, `t=256000`, vindos do claim `plim`), não tokens de geração.

**Regra proposta (candidata):** separar nominalmente "teto de geração" de "teto de requisição" e decidir se há clamp único e onde. `ChatRequestParameters` permite parâmetros por chamada em Ai Services — `https://docs.langchain4j.dev/tutorials/ai-services` — 2026-11-21 (documentação **não versionada**; a classe `ChatRequestParameters` é importada por `AiServices.java` 1.19.0, confirmando existência na versão).

### R6 — Isolamento tenant/user

**Regra atual:**

- `TenantContext.java:8-16` — `ScopedValue<UUID>`; `getCurrentTenant()` **lança** quando ausente (`:11-13`); `runWithTenant` liga o valor só dentro do `Callable` (`:15-16`). `ScopedValue` **não propaga para thread pool** (fato verificado no próprio tipo; ver Brain `decisoes/api-publica-chat`, "Outros pontos verificados").
- `TenantAspect.java:23-35` — `@Before` em `JpaRepository+.*`; ao não encontrar tenant, **loga e segue com filtro desligado** (`:33-35`).
- `AbstractEntity.java:16-17` — `@FilterDef`/`@Filter tenantFilter`.
- Repositórios derivados com tenant explícito: `ChatSessionRepository.java:15-17`, `PromptTemplateRepository.java:19`, `ChatMessageServiceImpl.java:44`, `:59`, `:69-74`.
- `ChatServiceImpl.java:121-128` lê tenant de `TenantContext` e userId de `SecurityUtils`; `:131-132` oferece overload sem contexto ambiente.
- `PublicChatServiceImpl.java:79`, `:141`; filtros em `SecurityFilter.java:56` e `PublicApiSecurityFilter.java:103`.

**Regra proposta (candidata):** uma fronteira explícita de tenant que não dependa de `ScopedValue` dentro de callbacks assíncronos — relevante porque um `ChatMemoryStore` com I/O pode implementar variantes assíncronas (`https://docs.langchain4j.dev/tutorials/chat-memory` — 2026-11-21, documentação **não versionada**).

### R7 — Telemetria / tokens

**Regra atual:**

- `LangChain4jTurnGateway.java:92-108` lê `TokenUsage` e `FinishReason` da resposta e monta `TurnReply` com `promptTokens/completionTokens/totalTokens`.
- `TurnReply.java:45-87`, `:89-113` — falha é valor, não exceção; `toString()` nunca imprime texto do provider.
- `TurnOutcomeMapper.java:174-193` — uma linha de trace por turno, com números **medidos**, não reconstruídos; `:181-189` monta `ChatTrace`.
- `ChatTrace.java:45-64` — campos `tenantId`, `turnId`, `clientId` (`:47-49`).
- **Débito verificado:** em produção `TurnOutcomeMapper.java:181-189` popula `tenantId`, mas **nunca** `.turnId(...)` nem `.clientId(...)`; busca em `backend/**` encontra `.turnId(`/`.clientId(` apenas em `LoggingChatTraceRecorder.java:38-39` e em testes. Resultado: a linha de trace pública imprime `turnId=null clientId=null` apesar de `PublicChatServiceImpl.java:192`, `:200` e `:212` calcularem/terem esses valores. Isso **refuta parcialmente** o parecer de 2026-09-27, que dizia que `ChatTrace` não tinha os campos; o campo existe, a população não.
- `PublicChatServiceImpl.java:175-213` — envelope OpenAI com `usage` e `metadata.hive` (fingerprint SHA-256 do template em `:175`, `:229-239`).

**Regra proposta (candidata):** um único ponto de emissão, preservando `Result<T>`/`ChatResponse` final do AiService para não perder `TokenUsage` e `FinishReason`. `Result<T>` expõe `tokenUsage()`, `finishReason()` e `finalResponse()` — `https://docs.langchain4j.dev/tutorials/ai-services` — 2026-11-21 (**não versionado**); a existência de `Result` e `TokenUsage` na API 1.19.0 é confirmada em `AiServices.java` (raw, 2026-11-21).

---

## 3. Estado real do LangChain4j no repo

1. **Versão:** `<langchain4j.version>1.19.0</langchain4j.version>` — `backend/dependencies/pom.xml:37`. Artefato de parser isolado em `1.19.0-beta29` — `:39-46`.
2. **Artefatos gerenciados:** `langchain4j-ollama`, `langchain4j-open-ai`, `langchain4j-core`, `langchain4j-google-ai-gemini`, `langchain4j-anthropic`, `langchain4j-document-parser-apache-tika`, agregador `langchain4j` — `backend/dependencies/pom.xml:186-243`. Todos `provided`, salvo Tika.
3. **Runtime:** `bootstrap/pom.xml:209-233` declara 4 artefatos `dev.langchain4j` em `compile`, incluindo o agregador; comentário em `:224-228` explica o motivo. `agent/api/pom.xml:38-40`, `agent/impl/pom.xml:58-61`, `provider/langchain4j/pom.xml:39-57`, `publicapi/impl/pom.xml:48-51` usam `provided`.
4. **`langchain4j-spring-boot-starter`: AUSENTE.** Busca em todo `backend/**` por `langchain4j-spring-boot-starter`, `AiServices`, `ChatMemory`, `ChatMemoryStore`, `@SystemMessage`, `@UserMessage`, `@MemoryId`, `contentRetriever`, `Result<` retornou **zero ocorrências** em `*.java` e `pom.xml` (2026-11-21). Consequência: se Ai Services for adotado, a construção é **manual**, via `AiServices.builder(...)`, não por auto-configuração de starter.
5. **Chamada atual:** `LangChain4jTurnGateway.java:83` — `retryPolicy.call(() -> client.chat(prompt))`. Cliente criado por turno em `:80-82`; fábrica em `LangChain4jClientFactory.java:75-105`. Segunda chamada direta: `ChatModelValidateProbe.java:11` (`client.chat("ping")`).
6. **Streaming/tools/RAG no backend:** `TokenStream`, `@Tool`, `ContentRetriever`, `EmbeddingStore` — **não encontrado** em `backend/**` (2026-11-21).

---

## 4. Fatos de API verificados com fonte

Todas as afirmações abaixo foram lidas em 2026-11-21.

| # | Afirmação — fonte — data | Tipo de fonte |
|---|---|---|
| A1 | `AiServices.builder(Class<T>)` existe e produz `AiServices<T>`; `AiServices.create(Class<T>, ChatModel)` é atalho — `https://raw.githubusercontent.com/langchain4j/langchain4j/1.19.0/langchain4j/src/main/java/dev/langchain4j/service/AiServices.java` — 2026-11-21 | tag 1.19.0 |
| A2 | `.chatModel(ChatModel)` e `.streamingChatModel(...)` são mutuamente exclusivos — mesma URL — 2026-11-21 | tag 1.19.0 |
| A3 | `.chatMemory(ChatMemory)` e `.chatMemoryProvider(ChatMemoryProvider)` existem e **não podem ser configurados simultaneamente**; o provider cria uma memória por `@MemoryId` — mesma URL — 2026-11-21 | tag 1.19.0 |
| A4 | `.systemMessage(String)` e `.systemMessageProvider(Function<Object,String>)` existem; `@SystemMessage` **tem precedência** sobre o provider quando ambos configurados — mesma URL — 2026-11-21 | tag 1.19.0 |
| A5 | `.chatRequestTransformer(UnaryOperator<ChatRequest>)` e sobrecarga `BiFunction<ChatRequest,Object,ChatRequest>` (2º arg = memoryId) existem; servem para alterar a requisição antes do envio — mesma URL — 2026-11-21 | tag 1.19.0 |
| A6 | `.tools(Object...)`, `.tools(Collection<Object>)`, `.tools(Map<...>)` e `.contentRetriever(ContentRetriever)` existem — mesma URL — 2026-11-21 | tag 1.19.0 |
| A7 | `Result<T>` é tipo de retorno válido para acessar `TokenUsage`, fontes, `FinishReason` e `ChatResponse` final — `https://docs.langchain4j.dev/tutorials/ai-services` — 2026-11-21; classe `Result` referida no javadoc de `AiServices.java` 1.19.0 | tag 1.19.0 + docs não versionadas |
| A8 | **"AI Service should not be called concurrently for the same `@MemoryId`, as it can lead to corrupted `ChatMemory`. Currently, AI Service does not implement any mechanism to prevent concurrent calls for the same `@MemoryId`."** — mesma URL de `AiServices.java` — 2026-11-21 ( repetido em `https://docs.langchain4j.dev/tutorials/ai-services` — 2026-11-21) | tag 1.19.0 + docs não versionadas |
| A9 | `ChatMemoryAccess.getChatMemory(Object)` e `evictChatMemory(Object)` existem em `dev.langchain4j.service.memory` — `https://raw.githubusercontent.com/langchain4j/langchain4j/1.19.0/langchain4j/src/main/java/dev/langchain4j/service/memory/ChatMemoryAccess.java` — 2026-11-21 | tag 1.19.0 |
| A10 | `ChatMemoryStore` (pacote `dev.langchain4j.store.memory.chat`) expõe `getMessages(Object)`, `updateMessages(Object,List<ChatMessage>)`, `deleteMessages(Object)` — `https://raw.githubusercontent.com/langchain4j/langchain4j/1.19.0/langchain4j-core/src/main/java/dev/langchain4j/store/memory/chat/ChatMemoryStore.java` — 2026-11-21 | tag 1.19.0 |
| A11 | `MessageWindowChatMemory` 1.19.0: sys message sempre retida, apenas uma, conteúdo igual é ignorado, conteúdo diferente substitui e **vai para o fim** salvo `alwaysKeepSystemMessageFirst(true)`; store padrão é `SingleSlotChatMemoryStore` — `https://raw.githubusercontent.com/langchain4j/langchain4j/1.19.0/langchain4j/src/main/java/dev/langchain4j/memory/chat/MessageWindowChatMemory.java` — 2026-11-21 | tag 1.19.0 |
| A12 | **Memory ≠ history**: LangChain4j oferece apenas "memory"; histórico integral deve ser mantido manualmente — `https://docs.langchain4j.dev/tutorials/chat-memory` — 2026-11-21 | docs não versionadas |
| A13 | `SystemMessage` em memória tem tratamento especial; mensagens evictadas de `ChatMemory` também saem do `ChatMemoryStore` — `https://docs.langchain4j.dev/tutorials/chat-memory` — 2026-11-21 | docs não versionadas |
| A14 | AI Services são **proxies de interface** (reflexão); `@SystemMessage`, `@UserMessage`, `@MemoryId`, `@V`, chaining de múltiplos serviços, `ChatRequestParameters` por chamada — `https://docs.langchain4j.dev/tutorials/ai-services` — 2026-11-21 | docs não versionadas; `@MemoryId`/`ChatRequestParameters` confirmados no javadoc/source 1.19.0 |
| A15 | Autoconfiguration de AI Service em Spring Boot depende do starter declarativo — `https://docs.langchain4j.dev/tutorials/ai-services` — 2026-11-21; **compatibilidade com Spring Boot 4.1.0 (`dependencies/pom.xml:8-10`) e com a versão 1.19.0 NÃO foi verificada** → inferência (sem fonte consultada); verificar matriz oficial antes de propor starter | docs não versionadas + inferência rotulada |

**Não verificado / não afirmado:** existência de config de retry no builder de AiServices; comportamento exato de `@MemoryId` com `UUID` do domínio; compatibilidade do starter com Spring Boot 4.1.0. Todos ficam como perguntas em aberto (§6).

---

## 5. Alternativas com critérios (recomendação de insumo, não decisão)

Critérios: **custo de mudança**, **acoplamento de módulo**, **testabilidade**, **segurança**, **aderência ao stack atual** (Java 25 preview, Spring Boot 4.1.0, LangChain4j 1.19.0, `provided` em quase todo módulo).

| Alt | Descrição | Custo | Acoplamento | Testabilidade | Segurança | Aderência |
|---|---|---|---|---|---|---|
| **A** | Manter `client.chat(List<ChatMessage>)` e apenas centralizar as 7 regras do §2 em funções/portas | baixo | nenhum novo | alta (testes atuais capturam prompt em `LangChain4jTurnGatewayTest.java:97`, `ChatServiceImplTest.java:315`) | preserva ordem atual do system message | máxima |
| **B** | `AiServices.builder(...)` manual (sem starter), **stateless**, substituindo só a montagem de prompt/parsing dentro do gateway; sem `ChatMemory` | médio | `agent/impl` passa a construir proxy; `agent/api` continua com a porta | média-alta (proxy é interface; ITs de caracterização existem) | atenção: ordem de system message muda (A11) e telemetria precisa de `Result<T>` (A7) | alta (1.19.0 já tem API; nenhum starter novo) |
| **C** | B + `ChatMemoryProvider` com `ChatMemoryStore` adaptando JPA; `@MemoryId = sessionId` | alto | adiciona dependência de store em `agent/impl` ou módulo novo | média (concorrência por `@MemoryId` — A8) | risco grave: memória ≠ histórico (A12), evict apaga store (A13), chamada concorrente corrompe memória (A8) | média (JPA + `ScopedValue` não propagam — R6) |
| **D** | Adotar `langchain4j-spring-boot-starter` com criação de bean por autoconfiguração | alto | auto-config cruza módulos e muda ciclo de beans | média | mesmo risco de B/C, mais superfície de config | **baixa até verificação**: compatibilidade Spring Boot 4.1.0/1.19.0 não consultada (A15). Sintaxe anotacional do starter **não verificada**: inferência (sem fonte consultada); verificar doc oficial do starter antes de propor |

**Recomendação de insumo (sem valor de decisão):** sequenciar **A → B**, com C como decisão separada e D descartado até deepsearch de compatibilidade. Justificativa: A resolve as duplicações com risco quase zero; B usa API confirmada em 1.19.0 sem introduzir starter; C e D carregam riscos documentados (A8, A12, A13, A15) que exigem decisão de `architect` e, para limites/cota, de `product-manager`. **Nada aqui autoriza implementação.**

---

## 6. Riscos e débitos concretos para o PRD

| # | Risco / débito | Evidência | Impacto no PRD |
|---|---|---|---|
| K1 | **Memória não é histórico.** Substituir `chat_message`/`ChatSession` por `ChatMemory` apagaria o que o usuário vê na UI e a base das ITs. | `https://docs.langchain4j.dev/tutorials/chat-memory` — 2026-11-21; `ChatSession.java:11-26`; `ChatMessageServiceImpl.java:68-74` | Bloqueio: PRD deve dizer explicitamente que persistência JPA permanece. |
| K2 | **Chamada concorrente para o mesmo `@MemoryId` corrompe `ChatMemory`.** | `AiServices.java` 1.19.0 (javadoc de classe) e `https://docs.langchain4j.dev/tutorials/ai-services` — 2026-11-21 | Se C for escolhido, exige política de concorrência por sessão e teste de corrida; senão, manter stateless. |
| K3 | **Divisão `agent/impl` × `publicapi/impl`.** O 2º chamador importa classes de `impl`, criando aresta que o parecer classificou como pior que a aresta `agent→security` que P2 evita. | `publicapi/impl/pom.xml:33-37`; `PublicChatServiceImpl.java:9-10`; Brain `parecer-consolidado-2026-09-27` (omissão sobre `isModelNotFound` package-private) + `fechamento-d5-d7-d3` (D5) | Decisão de `architect`: onde mora a política de turno (`agent/api` vs `agent/impl`). |
| K4 | **Ordem do system message.** Hoje o template vem primeiro; `MessageWindowChatMemory` 1.19.0 põe system novo no fim por default. | `LangChain4jTurnGateway.java:121-128`; `MessageWindowChatMemory.java` (raw 1.19.0) — 2026-11-21; Brain `fechamento-d5-d7-d3` (D3: rejeição de `system` do cliente) | Regra de segurança; mudança precisa de IT de caracterização da ordem. |
| K5 | **Telemetria pode ser perdida.** `TurnReply` carrega números medidos porque uma exceção não os carrega; AiService devolve proxy e pode esconder `ChatResponse`. | `TurnReply.java:12-20`; `LangChain4jTurnGateway.java:92-108`; A7 | Exige `Result<T>`/`finalResponse()` e baseline de `promptTokens/totalTokens/finishReason` antes de qualquer troca. |
| K6 | **Trace pública não atribuível.** `ChatTrace` tem `turnId`/`clientId`, mas `TurnOutcomeMapper` nunca os popula; `LoggingChatTraceRecorder` imprime `null`. | `ChatTrace.java:47-49`; `TurnOutcomeMapper.java:181-189`; `LoggingChatTraceRecorder.java:38-39`; `PublicChatServiceImpl.java:192`, `:200` | Débito pré-existente; corrigir em ADR separado ou como pré-condição de telemetria. Não confundir com o parecer de 2026-09-27 (campo inexistente) — ele estava desatualizado. |
| K7 | **Javadoc vs código no retry.** `:53` diz sem jitter; `:55` liga jitter. | `LlmRetryPolicy.java:53-56` | Documentação enganosa; baixo risco, corrija no ADR de retry. |
| K8 | **Clamp de `maxTokens` não existe no gateway**, contrariando afirmação histórica do parecer. | `LangChain4jTurnGateway.java:66-82` (sem `Math.min`); `agent/web/internal/v1/.../InferenceParamsDTO.java:17-19` (faixa validada só no DTO) | Produto decide se há teto único; não herdar a afirmação antiga como fato. |
| K9 | **`TenantContext` = `ScopedValue`**, não propaga para thread pool; `TenantAspect` engole ausência e segue com filtro desligado. | `TenantContext.java:8-16`; `TenantAspect.java:23-35`; Brain `decisoes/api-publica-chat` | Qualquer `ChatMemoryStore` assíncrono ou offload de AiService pode virar leitura cross-tenant silenciosa. |
| K10 | **Starter não verificado com Spring Boot 4.1.0 / 1.19.0.** | `dependencies/pom.xml:8-10`, `:37`; A15 | Não propor D sem deepsearch de compatibilidade; risco de auto-config duplicando beans. |
| K11 | **Conversão divergente no caminho público.** `PublicChatServiceImpl.java:156-162` não descarta mensagens vazias nem trata `SYSTEM`, ao contrário de `agent/api/.../ProviderMessages.java:48-63`. | mesmas linhas | Duas conversões locais podem divergir silenciosamente — argumento central para o ADR de centralização. |
| K12 | **Custo de ocupação não coberto por cota.** Retry 2× + read timeout 120 s (`LangChain4jClientFactory.java:33`) versus cota 60/min (`PublicChatServiceImpl.java:134`). | mesmas linhas; Brain `parecer-consolidado-2026-09-27` (cota por minuto ≠ limite de recurso) | Fora do escopo estrito de Ai Services; registrar como débito de produto, não resolver por palpite. |

---

## 7. Premissas não validadas e perguntas para `product-manager` / `architect`

1. AiService substitui **todo** o `ChatTurnGateway` ou apenas a montagem de prompt/parsing, mantendo `client.chat`? (arquitetura — ADR)
2. `ChatTurnGateway` permanece como porta entre `agent/api` e os dois chamadores? (arquitetura)
3. Haverá `ChatMemory` em memória, `ChatMemoryStore` sobre JPA, ou nenhuma? Se houver, `@MemoryId` = `sessionId` ou `turnId`? (arquitetura + segurança — A8/A12/A13/K1/K2)
4. Como preservar `promptMessages`, `promptChars`, `TokenUsage` e `FinishReason` com `Result<T>`? (arquitetura — K5)
5. Qual a política de concorrência por sessão, dado A8? (arquitetura)
6. Adotar ou não `langchain4j-spring-boot-starter`? Requer deepsearch de compatibilidade Spring Boot 4.1.0 × LangChain4j 1.19.0 (K10).
7. `maxTokens` tem teto único e onde ele mora — validação de DTO, gateway ou fábrica? (produto — K8)
8. Corrigir `turnId/clientId` da trace é pré-condição do ADR de telemetria ou ADR próprio? (produto + arquitetura — K6)
9. A aresta `publicapi/impl → agent/impl` entra neste PRD ou fica como débito separado? (arquitetura — K3)
10. Nenhuma destas mudanças pode tocar o ciclo MFA (`workflow-state.json:2-6`); confirmar que o PRD registra essa restrição. (produto)

---

## 8. Insumo de esteira (sem criar ADRs)

Ordem canônica: `PRD` (product-manager) → `RESEARCH` (≥2 alternativas com fonte, trade-off e recomendação — o §5 já fornece insumo) → `ADR-INDEX` → `ADRs/ADR-00N-{slug}/ADR.md` → branch → SDD (`.agents/rules/prd-adr-pipeline.md:7-16`). Teto de 5 ADRs por PRD (`:46`).

ADRs **candidatos** (nomes sugeridos; nenhum diretório criado):

| # | Título candidato | Depende de |
|---|---|---|
| 1 | Centralizar conversão de histórico e precedência do system message (R1/R2, K4, K11) | — |
| 2 | Escolher forma de integração com Ai Services (A/B/C/D; manter `AiServices.builder` manual) (§5) | 1 |
| 3 | Definir relação memória × histórico e política de `@MemoryId`/concorrência (K1/K2) | 2 |
| 4 | Unificar retry, classificação de transporte e telemetria de turno (R3/R4/R7, K5/K6/K7) | 1 |
| 5 | Resolver fronteira `agent/api` × `agent/impl` × `publicapi/impl` e limites de requisição (K3/K8/K12) | 1, 2 |

Evidência mínima que cada ADR deve exigir do implementador futuro: (a) IT de caracterização da ordem de mensagens e dos números de token, com baseline anterior; (b) suíte completa sem regressão; (c) nenhum diff em `backend/security/**/Mfa*` nem em `identity/**/Mfa*`; (d) verificação de que `publicapi` continua com o mesmo diagnóstico de `model-not-found` que o chat humano.

---

## 9. Fontes consultadas

| Fonte | Data | Uso |
|---|---|---|
| `https://raw.githubusercontent.com/langchain4j/langchain4j/1.19.0/langchain4j/src/main/java/dev/langchain4j/service/AiServices.java` | 2026-11-21 | A1–A8, A14 (javadoc de classe e métodos) |
| `https://raw.githubusercontent.com/langchain4j/langchain4j/1.19.0/langchain4j/src/main/java/dev/langchain4j/service/memory/ChatMemoryAccess.java` | 2026-11-21 | A9 |
| `https://raw.githubusercontent.com/langchain4j/langchain4j/1.19.0/langchain4j-core/src/main/java/dev/langchain4j/store/memory/chat/ChatMemoryStore.java` | 2026-11-21 | A10 |
| `https://raw.githubusercontent.com/langchain4j/langchain4j/1.19.0/langchain4j/src/main/java/dev/langchain4j/memory/chat/MessageWindowChatMemory.java` | 2026-11-21 | A11 |
| `https://docs.langchain4j.dev/tutorials/chat-memory` | 2026-11-21 | A12, A13 (documentação não versionada) |
| `https://docs.langchain4j.dev/tutorials/ai-services` | 2026-11-21 | A7, A8, A14, A15 (documentação não versionada) |
| Brain: `arquitetura/projetos/hive/arquitetura/api-publica-chat/parecer-consolidado-2026-09-27` | 2026-09-27 | histórico de decisões/débitos; sempre confrontado com código |
| Brain: `arquitetura/projetos/hive/arquitetura/api-publica-chat/fechamento-d5-d7-d3` | 2026-09-27 | D3/D5/D7 (system message, política de turno, filtro) |
| Brain: `arquitetura/projetos/hive/decisoes/api-publica-chat`, `.../chat-provider-override-por-conversa`, `regras/global/hive/integracao/openai-compatible-baseurl-v1` | 2026-09-27 / 2026-11-20 | isolamento, override, classificação 404 |

> Fontes de código citadas como `arquivo:linha` foram lidas diretamente em 2026-11-21. Nenhuma afirmação de API neste documento veio de memória.
