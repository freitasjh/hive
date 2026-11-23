# RESEARCH — API pública do Assistant

**Data:** 2026-11-21 · **PRD:** `public-chat-api` · **Natureza:** consolidação de `EXPLORATION.md` + `DESIGN-DECISIONS.md` + verificação de versões feita pelo `architect`.

Regra aplicada: `.agents/rules/prd-adr-pipeline.md:10` (≥2 alternativas com fonte + trade-off + recomendação) e `.agents/rules/deepsearch.md` (URL + data). Inferência sem fonte é rotulada.

## 1. Frente de contrato e documentação

| Alternativa | Fonte | Trade-off |
|---|---|---|
| A — manter `Map` + `@Operation`/`@ApiResponse` + `OpenApiCustomizer` | `https://springdoc.org/` (2026-11-21) | custo baixo; schema de `Object` opaco → spec não descreve `choices[].message.content`, `usage`, `metadata.hive` |
| B — DTOs tipados + anotações springdoc | `https://learn.openapis.org/best-practices.html` (2026-11-21) + `https://springdoc.org/` (2026-11-21) | custo médio; schemas inferidos e testáveis; exige remover DTO duplicado e versionar extensões |
| C — design-first, spec como fonte única com geração em CI | `https://learn.openapis.org/best-practices.html` (2026-11-21) | custo alto; conflita com a realidade code-first do repo |

**Recomendação: B.** OAI recomenda design-first, mas o contrato é simples e descritível; uma spec gerada e versionada em CI cumpre a função de fonte única sem mudança de processo. C fica como evolução futura.

## 2. Frente de centralização

| Alternativa | Fonte | Trade-off |
|---|---|---|
| A — unificar conversão/precedência do `SystemMessage` em `agent/api` | `.spec/prd/langchain4j-ai-services/TECH-BRIEF.md:101-188`; `arquitetura/projetos/hive/decisoes/api-publica-chat` (Brain, 2026-09-27) | baixo-médio; ITs de caracterização já existem; exige baseline de ordem de mensagens |
| B — mover `ProviderResolution`/`TurnOutcomeMapper` para `agent/api` | idem | médio-alto; extingue aresta K3, mas muda grafo de módulos e pode reabrir ADR-012/021 |
| C — Ai Services LangChain4j | `TECH-BRIEF.md:243-258` (riscos A8/A11/A12/A15) | alto; depende de deepsearch de compatibilidade Boot 4.1 × LangChain4j 1.19.0 |

**Recomendação: A neste PRD; B no ADR 5; C fora.**

## 3. Frente de credenciais

| Alternativa | Fonte | Trade-off |
|---|---|---|
| A — manter `client_credentials` + UI admin | `https://www.rfc-editor.org/rfc/rfc6749.html` §2.3.1/§4.4/§5.1 (2026-11-21) | médio; expiração/escopo/rotação/revogação já implementados; exige cuidado com campos de limite |
| B — migrar para API keys `hk_` | `.spec/ROADMAP.md:326-370` (legado de planejamento, nunca implementado) | alto; chave longa sem expiração/escopo nativos; quebra clientes atuais |

**Recomendação: A.** RFC exige Basic (já implementado); o modelo `hk_` é legado, não decisão corrente.

## 4. Frente de cota

| Alternativa | Fonte | Trade-off |
|---|---|---|
| A — sliding-window Lua + `checkTenant` + headers | `https://redis.io/learn/howtos/ratelimiting` (2026-11-21); `https://developers.openai.com/api/docs/guides/rate-limits` (2026-11-21); `RedisQuotaGuard.java:114-116,158-160` | baixo-médio; fail-open é decisão explícita; não resolve K12 |
| B — manter limite por client além do teto do tenant (`checkClientAndTenant`, `RedisQuotaGuard.java:118-121`) | código | médio; dois contadores; operador escolheu **não** fazer |
| C — token bucket com custo por token | inferência (sem fonte nova consultada); OpenAI separa RPM/TPM em `https://developers.openai.com/api/docs/guides/rate-limits` (2026-11-21) | alto; muda semântica do 429; crédito fora deste PRD |

**Recomendação: A.** Um contador por tenant, default 100 req/min, origem em `publicapi_db`; coluna antiga por client fica obsoleta sem remoção abrupta. K12 e crédito como débitos.

## 5. Verificação de versão springdoc (pré-condição)

Fonte primária: `https://springdoc.org/faq.html`, seção "What is the compatibility matrix of springdoc-openapi with spring-boot?" — consultada 2026-11-21. Matriz literal: `4.2.x → 3.2.1`; `4.1.x → 3.1.x - 3.2.x`; `4.0.x → 3.0.x`; "springdoc-openapi 3.x is compatible with spring-boot 4". A FAQ também recomenda, em geral, o último estável (`3.2.1`) — mas a matriz é mais específica para Boot 4.1.

Fonte secundária: `https://github.com/springdoc/springdoc-openapi/releases` — consultada 2026-11-21. Release `v3.2.0` (24 out) declara "Spring Boot 4.1.x line (built on Spring Boot 4.1.2)", dependências `Spring Boot 4.1.2 · swagger-ui 5.34.0 · Scalar 0.6.83`. Release `v3.2.1` (20 nov) declara "Spring Boot 4.2.x line (built on Spring Boot 4.2.0)" e mudanças quebráveis: swagger-core 3.0, mappers Jackson 3 (`tools.jackson`), rename de `swagger-*-jakarta` para `swagger-*`. Release `v2.10.1` é a linha Boot 3.5.x. Coordenada `springdoc-openapi-starter-webmvc-scalar` confirmada na linha 3.x em `https://springdoc.org/` (2026-11-21).

**Recomendação: upgrade para `3.2.0`** (linha 4.1), não `3.2.1` (linha 4.2, quebra Jackson/artefatos), e não `2.8.14` (linha 2.x, fora da matriz Boot 4). A versão `2.8.14` aparece em `backend/dependencies/pom.xml:33,126-128` (verificado 2026-11-21).

**Gate de validação (antes de qualquer anotação):** `GET /v3/api-docs` responder 200; `paths` conter `/v1/public/auth/token` e `/v1/public/v1/chat/completions`; `components.schemas` não vazio e conter `ChatCompletionRequestDTO`, `ChatCompletionResponse`, `OpenAiError`. Se falhar com 3.2.0, avaliar `3.1.1` (linha 4.1, swagger-core 2.2.55 — `https://github.com/springdoc/springdoc-openapi/releases`, 2026-11-21), sem refundar o contrato.

## 6. Fontes consultadas (2026-11-21)

`https://www.rfc-editor.org/rfc/rfc6749.html` · `https://platform.openai.com/docs/api-reference/chat/create` · `https://developers.openai.com/api/docs/guides/error-codes` · `https://developers.openai.com/api/docs/guides/rate-limits` · `https://redis.io/learn/howtos/ratelimiting` · `https://springdoc.org/faq.html` · `https://springdoc.org/` · `https://github.com/springdoc/springdoc-openapi/releases` · `https://learn.openapis.org/best-practices.html`.
