# ADR-001 — Contrato tipado e especificação OpenAPI da API pública

**Status:** merged · **Data:** 2026-11-21 · **PRD:** `public-chat-api` · **Branch:** `adr/public-chat-api/01-contrato-openapi` · **Merge:** `d0d3890` (PR #4)

**depende-de:** nenhuma ADR. Pré-condição técnica: validar a geração de spec no springdoc compatível com Spring Boot 4.1.0 antes de anotar controllers.

## Contexto

A superfície pública já expõe `POST /v1/public/auth/token` e `POST /v1/public/v1/chat/completions`, mas o contrato existe apenas no código: a response de chat é um `Map<String,Object>` (`PublicChatServiceImpl.java:63-221`), o DTO de request está duplicado em `publicapi/api` e `publicapi/web/internal/v1`, e não há nenhuma anotação `@Operation`/`@Tag`/`@ApiResponse`. O springdoc declarado é `2.8.14` (`dependencies/pom.xml:33,126-128`), fora da matriz oficial para Spring Boot `4.1.0` (FAQ: `4.1.x` → `3.1.x`–`3.2.x`). Sem spec gerada, qualquer documentação adicional descreveria um contrato opaco.

Fontes: `https://springdoc.org/faq.html`, `https://github.com/springdoc/springdoc-openapi/releases`, `https://springdoc.org/`, `https://learn.openapis.org/best-practices.html`, `https://platform.openai.com/docs/api-reference/chat/create`, `https://developers.openai.com/api/docs/guides/error-codes` — todas 2026-11-21.

## Decisão

1. Subir springdoc de `2.8.14` para **`3.2.0`** (linha Spring Boot 4.1.x, built em 4.1.2). Não adotar `3.2.1`: é a linha 4.2 e traz swagger-core 3.0, mappers Jackson 3 e rename de artefatos `swagger-*-jakarta` → `swagger-*`. Se o gate falhar em Boot `4.1.0`, testar `3.1.1` — não refundar o contrato.
2. Gate obrigatório **antes** de qualquer anotação: `GET /v3/api-docs` → 200, paths `/v1/public/auth/token` e `/v1/public/v1/chat/completions` presentes, `components.schemas` contendo `ChatCompletionRequestDTO`, `ChatCompletionResponse`, `OpenAiError`.
3. Substituir o `Map` da response por DTOs tipados em `publicapi/api/dto`: response de chat, choice, message, usage, `metadata.hive` e envelope de erro. Remover a cópia duplicada de `web/internal/v1`, mantendo a de `publicapi/api` que já tipa a porta `PublicChatService`.
4. Declarar `GroupedOpenApi` com group `public` restrito a `/v1/public/**`. Manter os handlers de erro locais do controller; nenhum advice global novo.
5. Documentar extensões Hive (`prompt_code`, `metadata.hive`) como propriedades adicionais explícitas — não como campos OpenAI canônicos. `model` ausente continua caindo no default do tenant; `model` divergente continua com `400 unknown_model` (decisão canônica registrada no Brain).
6. **Exposição da spec — decidida neste ADR:** a documentação (`/v3/**`, `/swagger-resources/**`, `/scalar/**`) fica **autenticada na chain humana**. Remover esses três padrões do bloco `permitAll` em `SecurityConfig.java:77-87`; eles passam a cair em `.anyRequest().authenticated()` (`:88`). Os demais padrões do bloco (`/**.html`, `/webjars/**`, `/configuration/**`, `/actuator/**`, `/manager/**`, `/notification/**`) ficam inalterados. O gate `GET /v3/api-docs` da cláusula 2 roda com sessão de usuário humano; sem autenticação deve devolver 401. O group `public` descreve endpoints públicos, mas o **acesso ao documento não é público**. Decisão alinhada ao parecer do architect de 2026-11-21 ("spec GroupedOpenApi public autenticada").
7. **Fora de escopo — cota do token endpoint:** o comportamento `429` com `Retry-After` em `POST /v1/public/auth/token` **não existe hoje** e **não é implementado neste ADR**. `PublicAuthControllerV1.java:81` chama `quotaGuard.checkTokenEndpoint(...)`; sem `@ExceptionHandler` para `QuotaExceededException`, a exceção cai no handler genérico (`:156-163`) e devolve `400 invalid_request` — diferente do chat, que tem handler dedicado (`PublicChatControllerV1.java:93-98`: 429 + `Retry-After`). A "Interface congelada" abaixo registra o comportamento **real**. A mudança de comportamento vai como **bugfix separado**, com ADR/PRD registrando, no escopo de cota/rate limit (ADR-004). Nenhum handler novo é criado aqui: este ADR preserva os handlers locais existentes (cláusula 4) e não muda cota (Consequências).

## Alternativas rejeitadas

- **`2.8.14` mantido** — fora da matriz oficial; risco de spec vazia ou incompleta.
- **`3.2.1`** — linha 4.2; muda Jackson e nomes de artefatos sem necessidade para este PRD.
- **Design-first puro (spec escrita à mão como fonte única)** — a OAI recomenda, mas o custo de processo não se justifica hoje; este ADR adota code-first com spec gerada e versionada, evitando duas fontes de verdade.
- **Só anotações sobre o `Map`** — schema opaco; não permite declarar `choices[].message.content`, `usage` nem `metadata.hive`.

## Interface congelada

- `POST /v1/public/auth/token` — `grant_type=client_credentials`, Basic preferido, corpo legado aceito; 200 com `access_token`, `token_type: Bearer`, `expires_in`, `scope` e headers `Cache-Control: no-store`, `Pragma: no-cache`; 400 `invalid_request` (**inclui cota excedida** — handler genérico `PublicAuthControllerV1.java:156-163`), 401 `invalid_client`. **429 com `Retry-After` NÃO é comportamento atual deste endpoint**: registrado como bugfix separado (ADR-004), fora deste ADR. Não documentar 429 na spec gerada neste ciclo.
- `POST /v1/public/v1/chat/completions` — request `{model?, prompt_code, messages:[{role:user|assistant, content}], stream?}`; 200 com `id`, `object: chat.completion`, `created`, `model`, `choices`, `usage`, `prompt_code`, `metadata.hive`; erros `400 unknown_model`/`streaming_not_supported`/`system_role_not_allowed`, 403 `prompt_not_in_allowlist`, 404 `prompt_not_found`, 409 `prompt_not_active`/`prompt_version_not_found`, 429 `quota_exceeded`, 500 `internal_error`.
- Envelope de erro preservado: `{"error":{"message","type","param","code"}}`, snake_lower, handlers locais.
- `GET /v3/api-docs` com group `public` — contrato de documentação **autenticado na chain humana** (401 anônimo, 200 autenticado); não muda o comportamento dos endpoints.
- Duplo `/v1` em `/hive/v1/public/v1/chat/completions` preservado por compatibilidade com SDK OpenAI.

## Consequências

- **Positivas:** spec verificável em CI; schemas inferidos dos DTOs; fim da duplicação de request; contrato de erro estável.
- **Negativas:** upgrade de dependência no primeiro batch (risco controlado pelo gate); DTOs de response são nova superfície a manter; spec passa a exigir login humano para ser lida (cláusula 6).
- **Neutras:** conteúdo dos endpoints e regras de prompt inalterados; nenhuma mudança de cota, credencial ou módulo de IA neste ADR. A correção de contrato do item 7 é documental — congela o 400 real; o 429/Retry-After fica para bugfix separado com ADR/PRD (ADR-004).

## Evidência exigida

Spec com paths e schemas preenchidos; DTO duplicado removido; contrato de erro idêntico no formato (token endpoint caracterizado como 400 `invalid_request`, sem handler 429 novo); `/v3/api-docs` 401 anônimo e 200 autenticado; suíte completa sem regressão; zero diff em `backend/security/**/Mfa*` e `identity/**/Mfa*`; diagnóstico de `model-not-found` idêntico ao do chat humano.
