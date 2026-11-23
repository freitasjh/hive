# CONSTRAINTS — API pública do Assistant

**Data:** 2026-11-21 · **Fase:** Análise (Fase 1 do dev-planner).

## 1. Restrições de processo (PICCO confirmado em 2026-11-21)

1. Somente `.md`. Nenhum código-fonte, config, script ou migration criado/editado.
2. Artefatos restritos a `.spec/prd/**`. Nada em `backend/**`, `frontend/**` ou `protectedPaths`.
3. Não tocar ciclo MFA nem `workflow-state.json` (feature corrente: `mfa-onboarding-opcional`/Testing).
4. Parecer não autoriza implementação: sem branches, commits ou PRs.
5. Teto de 5 ADRs candidatos por PRD (`.agents/rules/prd-adr-pipeline.md:46`).
6. Toda recomendação de lib/API/versão exige deepsearch com URL + data; inferência sem fonte deve ser rotulada.

## 2. Restrições técnicas do stack

| # | Restrição | Evidência | Consequência |
|---|---|---|---|
| T1 | Spring Boot `4.1.0`, Java 25 preview, LangChain4j `1.19.0`. | `backend/dependencies/pom.xml:8-10,37` | Qualquer proposta de Ai Services/`starter` depende de verificação de compatibilidade (A15 do TECH-BRIEF, inferência rotulada). |
| T2 | springdoc declarado em `2.8.14`, fora da matriz oficial para Boot 4.1. | `dependencies/pom.xml:33,126-128`; `https://springdoc.org/faq.html` — 2026-11-21 | **Pré-condição**: validar geração de spec antes de anotar controllers; candidatos `3.1.x`–`3.2.0` (linha 4.1), `3.2.1` traz Jackson 3 (mudança quebrável). |
| T3 | `publicapi/impl` depende de `agent/impl` (`ProviderResolution`, `TurnOutcomeMapper`). | `publicapi/impl/pom.xml:33-37`; `PublicChatServiceImpl.java:9-10` | Mover para `agent/api` muda fronteira de módulo (K3); decidir em ADR, não de passagem. |
| T4 | `TenantContext` = `ScopedValue<UUID>`; não propaga para thread pool; `TenantAspect` engole ausência e segue com filtro desligado. | `TenantContext.java:8-16`; `TenantAspect.java:23-35` | Nenhuma solução com I/O assíncrono ou `ChatMemoryStore` sem política explícita de tenant (K9). |
| T5 | Envelope de erro público é local, não o advice global (`application/problem+json`, SNAKE_UPPER). | `PublicChatControllerV1.java:45-111`; Brain `decisoes/api-publica-chat` | Manter handler local; `@Order` global criaria precedência sutil. |
| T6 | Context-path `/hive`; endpoint público efetivo `/hive/v1/public/v1/chat/completions`. Matchers de segurança não incluem `/hive`. | Brain `decisoes/api-publica-chat` | Não "corrigir" o duplo `/v1`: SDK OpenAI espera `/v1` no `base_url`. |
| T7 | Chave separada `api.security.public.token.secret` para JWT público; `aud=hive.public`. | `PublicClientTokenIssuer.java:37-58,119-133` | Nunca reutilizar a chave do token humano. |

## 3. Restrições de compatibilidade de contrato

- C1 — Preservar formato `error.{message,type,param,code}`; é o formato OpenAI documentado (`https://developers.openai.com/api/docs/guides/error-codes` — 2026-11-21).
- C2 — Preservar `Retry-After` em `429`; OpenAI recomenda honrá-lo (`https://developers.openai.com/api/docs/guides/rate-limits` — 2026-11-21).
- C3 — `model` ausente → default do tenant; presente e divergente → `400 unknown_model`. Decisão canônica registrada no Brain.
- C4 — `role: system` rejeitado com 400: o gateway põe o template como `SystemMessage` primeiro, e um `system` do cliente chegaria por último e venceria (Brain `decisoes/api-publica-chat`; K4).
- C5 — `stream=true` rejeitado com 400 (`streaming_not_supported`). Documentar como limitação, não como bug.
- C6 — `prompt_code`, `metadata.hive` e `prompt_code` na response são extensões Hive; devem aparecer no OpenAPI como propriedades adicionais, não como campos OpenAI canônicos.
- C7 — RFC 6749 exige Basic como mecanismo de autenticação do cliente (§2.3.1) e headers `no-store`/`no-cache` na resposta de token (§5.1) — já implementados. Credencial no corpo é desaconselhada pela RFC (§2.3.1); remover é quebra de compatibilidade com clientes atuais.
- C8 — Limite de chat é **por tenant**, compartilhado entre os clients do tenant; default `100` requisições/minuto. Decisão do operador 2026-11-21.
- C9 — `client_id` continua globalmente único; o tenant vem da linha (`V1__public_api_client.sql:28-29`). O limite por tenant não altera essa regra.
- C10 — Crédito (budget consumível) fica fora deste PRD, registrado como débito. RPM por janela deslizante e crédito são dimensões distintas — OpenAI separa limites de requisições e de tokens — `https://developers.openai.com/api/docs/guides/rate-limits` — 2026-11-21.

## 4. Débitos herdados (não resolver por palpite)

| ID | Débito | Impacto no PRD |
|---|---|---|
| K3 | Aresta `publicapi/impl → agent/impl` | Decisão de `architect`: onde mora a política de turno. |
| K6 | `ChatTrace` tem `turnId`/`clientId`, mas `TurnOutcomeMapper` nunca os popula; log imprime `null`. | Corrigir em ADR próprio ou como pré-condição de telemetria. Não confundir com parecer antigo (campo inexistente). |
| K11 | `PublicChatServiceImpl.java:156-167` converte mensagens sem descartar vazias nem tratar `SYSTEM`, divergente de `agent/api/.../ProviderMessages.java:48-63`. | Argumento central para ADR de centralização. |
| K12 | Retry 2× + read timeout 120 s versus cota 60/min. | Custo de ocupação não coberto; débito de produto. |
| SEC-02 | 401 na cadeia humana pendente de decisão. | Fora do escopo deste PRD; registrar, não resolver. |
| D4 | springdoc fora da matriz. | Barreira de entrada para a frente de Swagger. |
| D6 | `rate_limit_per_minute` no schema, 60 fixo no código. | UI admin de credenciais não pode expor um campo inerte. |

## 5. Premissas não validadas (levadas ao `open_questions` do PICCO)

1. Envelope atual + documentação, ou DTOs de response tipados/versionados?
2. Centralizar só conversão/system/telemetria, ou mover `ProviderResolution`/`TurnOutcomeMapper` para `agent/api`?
3. Manter `client_credentials` e construir UI admin sobre `public_api_client*`, ou migrar para API keys `hk_`?
4. Alinhar claim de cota ao guard? K12 entra neste PRD?
5. Swagger só para `/public/**` ou para todo o ecossistema; spec pública ou autenticada?

## 6. Critérios de evidência que qualquer ADR filho deve exigir

- Spec OpenAPI gerada e validada (sem `200` vazio) na versão de springdoc compatível com Boot 4.1.
- ITs de caracterização: ordem do `SystemMessage`, baseline de `prompt_tokens`/`total_tokens`/`finish_reason`, e contrato de erro antes/depois.
- Suíte completa sem regressão;zero diff em `backend/security/**/Mfa*` e `identity/**/Mfa*`.
- diagnóstico de `model-not-found` idêntico entre o chat humano e o público.
- Teste de concorrência do guard de cota (o sufixo aleatório do Lua existe para isso).

## 7. Premissas confirmadas pelo operador (2026-11-21)

1. `client_id` **não** é por tenant: é globalmente único; o tenant vem da linha. Um tenant pode ter vários clients com labels distintos (`V1__public_api_client.sql:28-29`).
2. Limite de chat é **por tenant compartilhado**: um contador por tenant, clients competindo pelo mesmo budget, default `100` requisições/minuto.
3. Origem do limite: **migration em `publicapi_db`**, módulo dono da cota.
4. Crédito: **fora deste PRD**, apenas registrado como débito. Não estourar o teto de 5 ADRs.
5. Limites do token endpoint (30/120/30) não são o limite de chat e não recebem os 100 req/min.
