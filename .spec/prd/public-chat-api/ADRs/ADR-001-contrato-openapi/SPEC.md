# SPEC — ADR-001: Contrato tipado e especificação OpenAPI da API pública

> Escopo: somente a decisão do ADR-001. Não especifica centralização de prompt (ADR-002), UI admin (ADR-003), cota por tenant (ADR-004) nem fronteira de módulos (ADR-005). Rastreabilidade: cada `RF`/`NFR` aponta para uma cláusula do `ADR.md`.

## 1. ADRs

### ADR-001 (este ciclo)

- **Decision:** upgrade springdoc para `3.2.0`, gate de spec, DTOs tipados, `GroupedOpenApi` group `public`, handlers locais preservados.
- **Context:** response montada como `Map`, DTO duplicado, zero anotações OpenAPI, springdoc fora da matriz.
- **Trade-offs:** spec verificável e schemas testáveis versus upgrade de dependência no primeiro batch.

ADRs afetados: nenhum. ADR-002 depende desta spec estável; ADR-003 e ADR-004 também.

## 2. Domain Context, Tenant Strategy & Provisioning

- **Bounded Context:** `publicapi` — superfície externa da API; compõe `agent` e `security`, sem nova dependência.
- **System Type:** Spring Boot `4.1.0`, Java 25 preview, `publicapi/web/internal/v1`.
- **Tenant Provisioning:** N/A — nenhum tenant, client ou credencial é criado ou alterado neste ADR.

## 3. Identity & Access Management

- **Protocol:** OAuth `client_credentials` já implementado, inalterado.
- **RBAC:** `@HasPermission("PUBLIC_CHAT_COMPLETE")` preservado.
- **MFA:** N/A — proibido tocar o ciclo MFA.

## 4. Security, Key Management & Disaster Recovery

- **Chaves:** nenhuma chave nova; chave JWT pública `api.security.public.token.secret` permanece separada.
- **Exposição da spec — decidida:** autenticada na chain humana. Remover `/v3/**`, `/swagger-resources/**` e `/scalar/**` do `permitAll` (`SecurityConfig.java:77-87`); caem em `.anyRequest().authenticated()` (`:88`). Outros padrões do bloco intactos. Gate `GET /v3/api-docs` roda autenticado: 401 anônimo, 200 com sessão humana. Decisão registrada no ADR (Decisão 6).
- **RTO/RPO:** N/A.

## 5. Data Model & Concurrency

- Nenhuma entidade, migration ou chave Redis é criada ou alterada.
- Concorrência: inalterada; o guard de cota e o gateway continuam com o mesmo comportamento.

## 6. Event Model, Integrations & Messaging Isolation

- Nenhum evento ou integração nova. A especificação é gerada em runtime pelo springdoc.

## 7. Invariants & Property-Based Testing

| Tipo | Propriedade | Verificação |
|---|---|---|
| Contrato | envelope de erro `{message,type,param,code}` preservado | IT de caracterização antes/depois |
| Contrato | duplo `/v1` preservado | teste de rota em `/hive/v1/public/v1/chat/completions` |
| Contrato | `model` ausente → default; divergente → 400 `unknown_model` | IT existente |
| Contrato | token endpoint com cota excedida → 400 `invalid_request` (handler genérico), sem 429 neste ciclo | IT de caracterização em `PublicAuthControllerV1` |
| Spec | paths `/v1/public/**` presentes e schemas não vazios | gate `GET /v3/api-docs` autenticado |
| Segurança | `/v3/api-docs`, `/swagger-resources/**`, `/scalar/**` → 401 anônimo e 200 autenticado | teste de chain humana em `SecurityConfig` |
| Código | zero diff em `Mfa*` e `workflow-state.json` | `git diff --name-only` filtrado |

## 8. Requisitos Funcionais

- **RF1** — DTOs tipados de response em `publicapi/api/dto`, substituindo o `Map`.
- **RF2** — `ChatCompletionRequestDTO` duplicado removido de `publicapi/web/internal/v1`.
- **RF3** — `GroupedOpenApi` group `public` com `pathsToMatch=/v1/public/**`.
- **RF4** — extensões `prompt_code` e `metadata.hive` declaradas no schema.
- **RF5** — handlers locais de erro com `@ApiResponse` correspondente. No endpoint de token, documentar somente os erros realmente implementados (400 `invalid_request`, 401 `invalid_client`); **nenhum `@ApiResponse` 429** neste ciclo — ver "Fora de escopo" abaixo.

## 9. Requisitos Não-Funcionais

- **NFR1** — upgrade springdoc para `3.2.0`; fallback `3.1.1` se o gate falhar.
- **NFR2** — compatibilidade OpenAI: formato de erro, duplo `/v1`, `stream` rejeitado com 400.
- **NFR3** — suíte completa sem regressão; nenhum teste apagado ou desabilitado.
- **NFR4** — deepsearch com URL + data para toda afirmação de versão/biblioteca.

## 10. Critérios de Aceite

- `GET /v3/api-docs?group=public` → 200 **com sessão humana** com os dois paths e os schemas `ChatCompletionRequestDTO`, `ChatCompletionResponse`, `OpenAiError`; → 401 sem sessão.
- Nenhuma ocorrência da classe duplicada em `web/internal/v1`.
- Resposta de chat com `choices`, `usage` e `metadata.hive` tipados, serialização idêntica à atual.
- Erros com o mesmo status e `code` do contrato atual; token endpoint com cota excedida segue 400 `invalid_request`, sem 429 documentado.
- `git diff` sem tocar MFA, `workflow-state.json`, cota ou credencial; nenhum `@ExceptionHandler(QuotaExceededException)` novo em `PublicAuthControllerV1`.

**Fora de escopo (bugfix separado, registrado em 2026-11-21):** `429` com `Retry-After` em `POST /v1/public/auth/token` é comportamento **desejado por convenção, mas não implementado**. Hoje `PublicAuthControllerV1.java:81` chama `quotaGuard.checkTokenEndpoint(...)` e, sem handler para `QuotaExceededException`, cai no genérico (`:156-163`) → 400 `invalid_request`. A correção de comportamento (handler dedicado, espelhando `PublicChatControllerV1.java:93-98`) vai como **bugfix separado com ADR/PRD registrando**, no escopo de cota/rate limit (ADR-004). A correção **textual do contrato** (Interface congelada do ADR) já está aplicada neste ciclo; nenhuma mudança de cota, handler novo ou 429 na spec entra nesta ADR-001.

## 11. Riscos

| Risco | Mitigação |
|---|---|
| `3.2.0` built em `4.1.2` não funcionar em `4.1.0` | gate de spec antes de anotar; fallback `3.1.1` |
| Spec vazia por controllers não escaneados | `pathsToMatch` explícito e verificação dos dois paths; hipótese de falha do `2.8.14` medida no gate, não afirmada |
| Mudança de serialização ao trocar `Map` por DTO | teste de caracterização da response antes/depois |
| Spec documentar 429 inexistente no token endpoint | congelar 400 real; 429 vai para bugfix separado (ADR-004) |
| Gate de spec quebrar ao autenticar `/v3/**` | gate com sessão humana; teste de 401 anônimo + 200 autenticado |

## 12. Fontes

`https://springdoc.org/faq.html` · `https://springdoc.org/` · `https://github.com/springdoc/springdoc-openapi/releases` · `https://learn.openapis.org/best-practices.html` · `https://platform.openai.com/docs/api-reference/chat/create` · `https://developers.openai.com/api/docs/guides/error-codes` — todas 2026-11-21.
