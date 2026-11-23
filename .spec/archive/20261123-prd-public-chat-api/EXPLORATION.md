# EXPLORATION — API pública do Assistant (chat-completion)

**Data:** 2026-11-21 · **Fase:** Descoberta (Fase 0/1 do dev-planner) · **Status:** insumo para decisão, sem autorização de implementação.

## 1. Problema e objetivo

A superfície pública do assistant já existe e funciona, mas cresceu por camadas: contrato compatível com OpenAI montado à mão, credencial OAuth `client_credentials` com provisionamento por CLI, duas cadeias de segurança, cota em Redis e nenhuma documentação OpenAPI. O objetivo deste PRD é **estudar e planejar** a evolução em quatro frentes:

1. **Contrato + Swagger** — DTOs tipados, especificação OpenAPI publicada, erros documentados.
2. **Centralização com o assistant** — convergência das regras de prompt, histórico e telemetria entre o chamador humano e o público.
3. **Gestão de credenciais com UI admin** — ciclo de vida de `public_api_client*` (criar/rotacionar/revogar/listar) acessível por interface.
4. **Cota e resiliência** — limite compartilhado por tenant (default 100 req/min), alinhamento entre schema, código e semântica de `429`; crédito fica como débito futuro.

## 2. Estado real da superfície pública (varredura 2026-11-21)

| Frente | Achado | Evidência no repo |
|---|---|---|
| Chat-completion | `POST /v1/public/v1/chat/completions`. Envelope OpenAI construído como `Map<String,Object>`, com extensões `prompt_code` e `metadata.hive`. | `PublicChatControllerV1.java:29-43`; `PublicChatServiceImpl.java:63-221` |
| Credencial | OAuth 2.0 `client_credentials`. `client_id` + `client_secret` (Basic preferido, body aceito), BCrypt com secret anterior, JWT HS256 `iss=hive.api`, `aud=hive.public`, claims `tenant-id`, `permissions`, `pcode`, `plock`, `plim`. Revogação via Redis + checagem DB. | `PublicAuthControllerV1.java:40-95`; `PublicClientTokenIssuer.java:37-193` |
| Schema | 3 tabelas: `public_api_client`, `public_api_client_grant`, `public_api_client_prompt`. Histórico Flyway próprio (`publicapi_db/V1`). | `V1__public_api_client.sql:3-86` |
| Acesso | Duas `SecurityFilterChain`: ordem 1 para `/v1/public/**` (stateless, `PublicApiSecurityFilter`, `/auth/**` permitAll); ordem 2 com `denyAll()` no path público. `@HasPermission("PUBLIC_CHAT_COMPLETE")` no endpoint. | `SecurityConfig.java:40-101`; `PublicApiSecurityFilter.java:39-120`; controller `:39` |
| Contrato de request | `ChatCompletionRequestDTO` **duplicado** em `publicapi/api` e `publicapi/web/internal/v1` (mesmo shape). `ignoreUnknown=false`; parâmetros OpenAI (`temperature`, `max_tokens`, etc.) aceitos e descartados via `@JsonIgnore`. `role` restrito a `user|assistant`; `stream` rejeitado com 400. | `publicapi/api/.../ChatCompletionRequestDTO.java:14-72`; `publicapi/web/.../ChatCompletionRequestDTO.java:14-101` |
| Contrato de response | Nenhum DTO. Erro local `OpenAiErrorResponse` com `{message,type,param,code}` e handlers no próprio controller — não passa pelo advice global. | `OpenAiErrorResponse.java:5-37`; controller `:45-111` |
| Swagger | springdoc `2.8.14` + starter `webmvc-scalar` no dependency management; `scalar.path=/scalar`; paths `/v3/**`, `/scalar/**`, `/swagger-resources/**` permitAll na chain humana. **Zero** `@Operation`, `@Tag`, `@ApiResponse` nos controllers públicos. | `dependencies/pom.xml:33,126-128`; `application.properties:24`; `SecurityConfig.java:77-87`; `.spec/ROADMAP.md:157,192,367-370` |
| Cota | `RedisQuotaGuard`: sliding-window log em `ZSET`, um `EVAL` Lua atômico, membro com sufixo aleatório, fail-open. Chat usa **60 fixo** (`checkClient(clientId, 60)`), embora `rate_limit_per_minute` exista na entidade. Token endpoint usa 30/120/30 fixos. | `RedisQuotaGuard.java:16-57,110-128`; `PublicChatServiceImpl.java:134-135`; `PublicAuthControllerV1.java:78-81`; `V1__public_api_client.sql:16` |
| Centralização | Já centralizado: `ProviderResolution` + `TurnOutcomeMapper` (ADR-021) — ambos os chamadores usam o mesmo proxy. Em aberto: conversão divergente (K11), `turnId/clientId` não populados na trace (K6), aresta `publicapi/impl → agent/impl` (K3), custo de ocupação não coberto por cota (K12). | `.spec/prd/langchain4j-ai-services/TECH-BRIEF.md:101-188,249-257`; `PublicChatServiceImpl.java:156-167`; `TurnOutcomeMapper.java:181-189` |

Contexto adicional do Brain: `sessoes/hive/2026-10-29` regera implementação aprovada (679 testes, 0 críticos) com pendências — gate externo ERP manual (VALIDATION §4), SEC-02 (401 na cadeia humana), `application-dev.properties` sujo. `workflow-state.json` corrente é `mfa-onboarding-opcional`/Testing; este PRD não deve tocá-lo.

## 3. O que o domínio diz (fonte externa)

- **OAuth 2.0 (RFC 6749)**: `client_credentials` é para **confidential clients** (§4.4); Basic é o mecanismo preferencial e o servidor **MUST** aceitá-lo (§2.3.1); credenciais no corpo são **NOT RECOMMENDED**, limitadas a clientes que não conseguem Basic (§2.3.1); resposta de token **MUST** incluir `Cache-Control: no-store` e `Pragma: no-cache` (§5.1) — `https://www.rfc-editor.org/rfc/rfc6749.html` — 2026-11-21.
- **Compatibilidade OpenAI**: mudanças retrocompatíveis incluem adicionar parâmetros opcionais e propriedades a objetos de resposta; `model` é obrigatório na requisição; `stream` produz SSE; `user` está sendo substituído por `safety_identifier`/`prompt_cache_key` — `https://platform.openai.com/docs/api-reference/chat/create` — 2026-11-21.
- **Erros OpenAI**: formato `error.{message,type,param,code}`; `429` deve ser tratado com `Retry-After` quando presente, senão backoff exponencial com jitter; `429`/`503` exigem inspeção de `error.code` — `https://developers.openai.com/api/docs/guides/error-codes` — 2026-11-21.
- **Headers de cota OpenAI**: `Retry-After`, `x-ratelimit-limit-requests`, `x-ratelimit-remaining-requests`, `x-ratelimit-reset-requests` e correlatos — `https://developers.openai.com/api/docs/guides/rate-limits` — 2026-11-21. O Hive devolve apenas `Retry-After`.
- **Redis**: sliding-window com `ZSET` + Lua atômico é o padrão para janela deslizante distribuída; `429` com `Retry-After` — `https://redis.io/learn/howtos/ratelimiting` — 2026-11-21.
- **springdoc**: matriz oficial — Spring Boot `4.1.x` → springdoc `3.1.x`–`3.2.x`; `v3.2.1` é built em Boot 4.2 mas roda em 4.1, com mudanças quebráveis (swagger-core 3.0, Jackson 3, rename de artefatos) — `https://springdoc.org/faq.html` — 2026-11-21. Release `v3.2.0` é a linha Spring Boot 4.1.x (built em 4.1.2) — `https://github.com/springdoc/springdoc-openapi/releases` — 2026-11-21.

## 4. Divergências entre o contrato atual e as fontes

| # | Divergência | Impacto |
|---|---|---|
| D1 | `model` é opcional no Hive (default do tenant); obrigatório na API OpenAI. | Decisão canônica do Brain diz que ausência → default e divergente → `400 unknown_model` (Sanity check para ERP apontando ao Hive errado). Manter e documentar como extensão, ou abandonar compatibilidade estrita. |
| D2 | `prompt_code` é obrigatório e não existe no contrato OpenAI. | Extension field. Deve ser explicitado no OpenAPI; SDKs OpenAI genéricos não o enviam. |
| D3 | Credenciais no corpo aceitas além de Basic. | RFC desaconselha. Manter por compatibilidade com provisionamento atual? Decisão de segurança. |
| D4 | springdoc `2.8.14` com Spring Boot `4.1.0`. | Fora da matriz oficial. Pode gerar spec vazia/incorreta; precisa validação antes de qualquer anotação. |
| D5 | `429` só com `Retry-After`; sem `X-RateLimit-*`. | Clientes OpenAI-style esperam os headers de cota para backoff informado. |
| D6 | Limite de chat fixo em 60 por client apesar de `rate_limit_per_minute` na credencial; não existe limite por tenant. | Decisão 2026-11-21: limite passa a ser por tenant, compartilhado entre clients, default 100 req/min, com migration em `publicapi_db`; coluna antiga fica obsoleta. |

## 5. Alternativas macro (detalhamento em DESIGN-DECISIONS.md)

- **Contrato:** (A) manter `Map` e documentar via anotações + `OpenApiCustomizer`; (B) DTOs de response tipados e versionados; (C) gerar spec estática a partir de contrato OpenAPI first.
- **Centralização:** (A) só conversão/system/telemetria; (B) mover `ProviderResolution`/`TurnOutcomeMapper` para `agent/api` e extinguir aresta `publicapi/impl → agent/impl`.
- **Credencial:** (A) manter `client_credentials` + UI admin; (B) migrar para API keys estáticas `hk_` do ROADMAP Phase 3.
- **Cota:** (A) alinhar claim `plim`/schema ao guard existente; (B) trocar sliding-window por token bucket com custo por token (K12).

## 6. Fontes consultadas

| Fonte | Data | Uso |
|---|---|---|
| `https://www.rfc-editor.org/rfc/rfc6749.html` | 2026-11-21 | §2.3.1, §4.4, §5.1 — auth de cliente e headers de token |
| `https://platform.openai.com/docs/api-reference/chat/create` | 2026-11-21 | Parâmetros e response de chat completion |
| `https://developers.openai.com/api/docs/guides/error-codes` | 2026-11-21 | Formato de erro e semântica de 429/503 |
| `https://developers.openai.com/api/docs/guides/rate-limits` | 2026-11-21 | `Retry-After` e headers `x-ratelimit-*` |
| `https://redis.io/learn/howtos/ratelimiting` | 2026-11-21 | Sliding-window distribuído com Redis |
| `https://springdoc.org/faq.html` | 2026-11-21 | Matriz de compatibilidade springdoc × Spring Boot |
| `https://springdoc.org/` | 2026-11-21 | Módulos, `GroupedOpenApi`, `@Operation`, `@ApiResponse`, `springdoc.pathsToMatch` |
| `https://github.com/springdoc/springdoc-openapi/releases` | 2026-11-21 | Linhas 3.2.0 (Boot 4.1) e 3.2.1 (Boot 4.2) |

Inferência explícita (sem fonte consultada): a UI admin de credenciais pode reutilizar o padrão de telas `admin/` já existente no frontend (`vendor-catalog.ts`, `state.ts`); verificar em `frontend/src/modules/hive/admin/` antes de propor componentes.
