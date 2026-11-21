# DESIGN-DECISIONS — API pública do Assistant

**Data:** 2026-11-21 · **Fase:** Desafio e Projeto (Fases 2/3 do dev-planner) · **Natureza:** insumo para `architect`; não é ADR.

Critérios usados: custo de mudança, acoplamento de módulo, testabilidade, segurança, aderência ao stack (Spring Boot 4.1.0, Java 25 preview, LangChain4j 1.19.0).

## Pré-condição (bloqueante, não é alternativa)

Validar a geração de spec no springdoc instalado antes de qualquer anotação. `2.8.14` está fora da matriz oficial para Boot 4.1 (FAQ: `4.1.x` → `3.1.x`–`3.2.x`); `v3.2.0` é a linha 4.1 e `v3.2.1` roda em 4.1 mas traz swagger-core 3.0/Jackson 3 com rename de artefatos — `https://springdoc.org/faq.html` — 2026-11-21; `https://github.com/springdoc/springdoc-openapi/releases` — 2026-11-21.
Gate: `GET /v3/api-docs` com paths `/v1/public/**` presentes e schemas não vazios. Se falhar, atualizar a dependência é a primeira tarefa do ADR 1 — não refundar o contrato.

---

## Decisão 1 — Contrato e documentação

| Alternativa | Custo | Acoplamento | Testabilidade | Risco |
|---|---|---|---|---|
| **A** — manter `Map` na response e documentar com `@Operation`/`@ApiResponse` + `OpenApiCustomizer` | baixo | nenhum | baixa (schema de `Object` é opaco) | spec descreve mal o payload real |
| **B** — DTOs tipados de request/response + anotações springdoc | médio | nenhum novo | alta (schemas inferidos, validação de contrato) | exige deleter DTO duplicado e versionar extensões |
| **C** — design-first: specversionada como fonte única, código gerado/validado em CI | alto | mudança de processo | alta | conflita com a realidade code-first do repo; OAI recomenda, mas exige disciplina nova |

**Recomendação: B agora; C como evolução futura se estabilidade de contrato virar requisito de produto.**

Razão: hoje o contrato existe só no código e a response é um `Map`, então o OpenAPI gerado por A não consegue declarar `choices[].message.content`, `usage` nem `metadata.hive`. B elimina também a duplicação de `ChatCompletionRequestDTO` (manter a cópia de `publicapi/api`, que já tipa a porta `PublicChatService`; remover a de `web/internal/v1`). Extensões (`prompt_code`, `metadata.hive`) entram via `@Schema` com nomes explícitos — não como campos OpenAI canônicos.

A OAI recomenda design-first e uma única fonte de verdade — `https://learn.openapis.org/best-practices.html` — 2026-11-21. B é code-first; o texto alerta que OpenAPI não descreve todo HTTP possível. Aqui o contrato é simples e descritível, então o risco é aceitável **desde que** a spec gerada seja versionada e validada em CI (cumpre o princípio de fonte única: uma spec gerada, não duas escritas à mão).

Documentar com `GroupedOpenApi` restrito a `/v1/public/**` (ou `springdoc.pathsToMatch`) para não inflar a spec com a API interna. `GroupedOpenApi`, `@Operation`, `@ApiResponse`, `@Schema` e `springdoc.pathsToMatch` estão documentados em `https://springdoc.org/` — 2026-11-21. Decisão em aberto do PICCO: spec pública ou autenticada — recomendação inicial: **autenticada na chain humana**, já que `/v3/**` hoje é permitAll (`SecurityConfig.java:77-87`); expor publicamente revela `prompt_code` e o formato de erro antes de existir consumidor externo.

## Decisão 2 — Centralização com o assistant

| Alternativa | Custo | Acoplamento | Testabilidade | Risco |
|---|---|---|---|---|
| **A** — unificar conversão de histórico + precedência do `SystemMessage` em `agent/api`, sem mover `ProviderResolution`/`TurnOutcomeMapper` | baixo-médio | nenhum novo | alta (ITs de caracterização já existem) | exige baseline de ordem de mensagens |
| **B** — mover `ProviderResolution` + `TurnOutcomeMapper` para `agent/api`, extinguindo aresta `publicapi/impl → agent/impl` | médio-alto | muda grafo de módulos | média | decisão de `architect`; pode reabrir ADR-012/021 |
| **C** — adotar Ai Services do LangChain4j (B/C/D do TECH-BRIEF) | alto | proxy + possível `ChatMemoryStore` | média | A8 (concorrência por `@MemoryId`), A11 (ordem do system), A12 (memória ≠ histórico), A15 (starter não verificado) |

**Recomendação: A neste PRD; B em ADR separado; C fora do escopo.**

Razão: K11 é divergência real e silenciosa — `PublicChatServiceImpl.java:156-167` não descarta conteúdo nulo nem trata `SYSTEM`, enquanto `agent/api/.../ProviderMessages.java:48-63` trata. K6 (`turnId/clientId` nulos na trace) é débito verificável e pode ser corrigido na mesma frente de telemetria. B resolve K3, mas muda fronteira de módulo e merece decisão própria com o mesmo rigor de evidência do TECH-BRIEF §8. C carrega riscos documentados em `TECH-BRIEF.md:243-258` e não deve entrar sem deepsearch de compatibilidade Spring Boot 4.1 × LangChain4j 1.19.0.

Evidência obrigatória: IT de caracterização comparando a ordem `SystemMessage → conversation` e os números medidos de `TokenUsage`/`FinishReason` antes e depois (K4/K5).

## Decisão 3 — Gestão de credenciais

| Alternativa | Custo | Acoplamento | Segurança | Risco |
|---|---|---|---|---|
| **A** — manter `client_credentials` e construir UI admin sobre `public_api_client*` | médio | nenhum novo | alta: expiração, escopo, rotação e revogação já implementados | exige expor campos de limite com cuidado |
| **B** — migrar para API keys estáticas `hk_` do ROADMAP Phase 3 | alto | nova cadeia de auth | inferior: chave longa sem expiração/escopo nativos | quebra clientes atuais; ROADMAP desatualizado em relação ao código |

**Recomendação: A.**

Razão: RFC 6749 §4.4 define `client_credentials` para confidential clients e §2.3.1 exige Basic — ambos já implementados —, `https://www.rfc-editor.org/rfc/rfc6749.html` — 2026-11-21. O modelo `hk_` do `.spec/ROADMAP.md:326-370` nunca foi implementado; legado de planejamento, não decisão corrente. A UI admin deve cobrir: criar client (label/tenant), conceder `PUBLIC_CHAT_COMPLETE`, vincular `prompt_code` com `pin_mode`/`pinned_version`, definir `token_ttl_seconds` e `max_*` (limite de requisições fica fora da UI de client: é do tenant, ADR 4), rotacionar secret (a janela `previous_secret_hash` já existe) e revogar. O plaintext do secret aparece **uma vez**, no create/rotate — `secret_hash` é BCrypt, não recuperável (`PublicClientTokenIssuer.java:77-85`).

Atenção: `client_id` é globalmente único (`V1__public_api_client.sql:28`) e o tenant vem da linha, nunca do request — a UI não pode oferecer troca de tenant de um client existente. A UI também não deve exibir limite de requisições por client: ele passa a ser do tenant (ADR 4).

## Decisão 4 — Cota e resiliência

| Alternativa | Custo | Acoplamento | Segurança | Risco |
|---|---|---|---|---|
| **A** — manter sliding-window Lua; ativar `checkTenant` com limite do tenant (default 100 req/min) vindo de migration em `publicapi_db`; adicionar headers `X-RateLimit-*`; manter token endpoint separado | baixo-médio | nenhum | fail-open já é decisão explícita | não resolve K12 |
| **B** — manter também limite por client além do teto do tenant (`checkClientAndTenant`) | médio | nenhum | complexidade de dois contadores | operador escolheu **não** fazer: um contador por tenant |
| **C** — substituir por token bucket com custo por token (crédito) | alto | médio | — | muda semântica de cota e do 429; fora deste PRD |

**Recomendação: A. K12 e crédito registrados como débitos de produto.**

Razão: `rate_limit_per_minute` existe no schema (`V1__public_api_client.sql:16`), mas `PublicChatServiceImpl.java:135` usa `60` fixo por **client**; não existe limite por tenant. `RedisQuotaGuard.checkTenant` já está implementado (`RedisQuotaGuard.java:114,158`), mas sem chamador de produção. A decisão do operador é um contador por tenant, default 100 req/min, compartilhado entre os clients — portanto `checkTenant` entra no lugar de `checkClient` no caminho de chat, e a origem do limite é uma nova tabela em `publicapi_db`. O campo antigo por client fica obsoleto, sem remoção abrupta de coluna.

OpenAI expõe `Retry-After` e `x-ratelimit-limit-requests`/`remaining`/`reset` — `https://developers.openai.com/api/docs/guides/rate-limits` — 2026-11-21 —; o Hive já devolve `Retry-After`, faltam os headers de remaining/reset. Redis ZSET + Lua atômico é adequado para janela deslizante distribuída — `https://redis.io/learn/howtos/ratelimiting` — 2026-11-21. Crédito é outra dimensão (budget consumível, não janela temporal) e não deve ser forçado dentro da coluna `rate_limit_per_minute` — inferência (sem fonte consultada); validar no ADR de crédito.

Não alterar o fail-open sem decisão: `RedisQuotaGuard.java:23-24` registra o custo aceito (Redis não pode virar dependência dura de todo turno).

## Fontes adicionais

- `https://learn.openapis.org/best-practices.html` — 2026-11-21 — design-first, fonte única, spec em source control.
- `https://springdoc.org/` — 2026-11-21 — `GroupedOpenApi`, anotações, `pathsToMatch`, módulo scalar.
- `https://www.rfc-editor.org/rfc/rfc6749.html` — 2026-11-21 — §2.3.1, §4.4, §5.1.
- `https://developers.openai.com/api/docs/guides/error-codes` — 2026-11-21 — formato de erro e 429/503.
- `https://platform.openai.com/docs/api-reference/chat/create` — 2026-11-21 — contrato de chat completion.

Inferência explícita (sem fonte consultada): a UI admin pode seguir o padrão visual de `frontend/src/modules/hive/admin/`; verificar `vendor-catalog.ts`/`state.ts` e o design system do destino antes de propor componentes.
