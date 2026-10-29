# EXPLORATION — API pública de chat (OpenAI-compatible) para o ERP

- **Fase:** dev-planner 0 (Descubra)
- **Data:** 2026-09-27
- **Autor:** architect
- **Escopo:** planejamento. **Nenhuma implementação neste documento.**
- **Fontes:** briefing do usuário + 6 ADRs + leitura direta do código (ver `CONSTRAINTS.md §Verificado`)

---

## 1. O problema, em uma frase

O ERP e o sistema B2B da própria organização precisam conversar com o Hive usando prompts que
**eles mesmos criam na plataforma**, identificadas por `prompt_code`, e o primeiro consumidor é um
**teste de integração interno** — não um produto vendido.

## 2. O que já está decidido (não redesenhar)

| # | Decisão fechada | Consequência de projeto que ela cria |
|---|---|---|
| 1 | `POST /hive/v1/public/auth/token` — `clientId`+`clientSecret` → token curto (`client_credentials`) | Endpoint `permitAll` **novo**, fora do `anyRequest().authenticated()`. Segredo longo só ali. |
| 2 | `POST /hive/v1/public/v1/chat/completions` — Bearer, formato OpenAI, **stateless** | **Não** cria `chat_session`. Novo caminho que não passa por `ChatServiceImpl.chat()`. |
| 3 | Request `{model, prompt_code, messages[], user?}`; resposta OpenAI + `prompt_code` + bloco `hive:{}` | Contrato de wire próprio, **diferente** de `ChatRequestDTO`/`ChatResponseDTO` |
| 4 | Allowlist de `prompt_code` por credencial. **Sem endpoint de busca** | Mata enumeração cross-tenant — desde que a checagem venha **antes** de qualquer lookup (§4.3) |
| 5 | A credencial fixa a VERSÃO, opt-in "seguir latest" | ⚠️ **O motivo declarado não é atingido pelo mecanismo.** Ver §4.1 — este é o achado que muda o desenho |
| 6 | Só `ACTIVE` é chamável; `DRAFT` → erro distinguível | Existe `uk_prompt_template_active_version` (1 ACTIVE por code) — a regra é do banco, não do código |
| 7 | Um `clientId` por integração, com label humano | Revogação cirúrgica. Também significa `client_id` **globalmente único** (§4.4) |
| 8 | Cota é dado da credencial, não constante; teto por tenant | Novo modelo de quota. Decisão de backend: **Redis, não memória** (§4.5) |
| 9 | Sem SSE na v1 | `request.stream=true` → erro explícito, não silêncio |
| 10 | Códigos válidos entregues no ato do provisionamento | Confirma ausência de discovery. O `hive:{}` da resposta **não** pode listar códigos |

## 3. Perguntas de descoberta que mudam arquitetura

O requisito de discovery foi fixado pelo usuário: **o que o ERP precisa além de `chat/completions`?**
Levantei 9. Separei em **BLOQUEANTES** (a resposta muda o desenho, não é parametro) e **PARAMETRO**
(a resposta muda um valor, não a forma).

### 3.1 BLOQUEANTES — mudam a arquitetura

| # | Pergunta | Por que é bloqueante | Estado |
|---|---|---|---|
| **Q1** | `PINNED` deve ser **tripwire** (o prompt se move → a integração é notificada com erro) ou **snapshot** (o prompt se move → a integração continua no texto antigo)? | O modelo de dados **não tem snapshot**. `update()` muta a linha in-place (§4.1). Snapshot exige mudar o editor da UI: batch próprio, fora do escopo. Tripwire é FREE. **Assumindo tripwire, com o nome e os códigos de erro corrigidos.** | ✅ **respondido (usuário, 2026-09-27): tripwire** |
| **Q2** | `messages[]` pode conter `role: "system"`? | Se sim, o ERP sobrescreve o prompt do tenant e a decisão 3 (prompt é do cliente, na plataforma) vira decorativa. Se não, é uma restrição de contrato a declarar. **Assumindo: rejeitar `system`.** | ✅ **respondido (D3, 3/3 + usuário): rejeitar com 400** |
| **Q3** | O ERP quer o **transcript persistido** no Hive (mesmo sendo stateless no request)? | A resposta muda o modelo: stateless rejeita, mas um `chat_session` órfão criado por chamada muda reuso, quota e privacidade. **Assumindo: não, zero persistência de conteúdo.** | ✅ **respondido: não persistir; metadados em log (D4)** |
| **Q4** | O contrato do ERP é estável o bastante para que o Hive **não** seja a fonte da conversa? | Se o ERP reenviar o histórico inteiro a cada turno, o custo de prompt cresce sem limite e a v1 precisa de um teto de mensagens. **Assumindo: o ERP reenvia as últimas N; a v1 recusa acima de um teto.** | ✅ **respondido (D2): teto = dado da credencial (`plim`), default 200/32000/256000** |
| **Q5** | O caminho de autenticação do ERP prefere `client_secret_basic` (header) ou `client_secret_post` (body)? | Afeta só o parser do endpoint de token. RFC 6749 recommends `client_secret_basic`. **Assumindo: aceitar os dois, `basic` como preferido.** | ✅ **respondido: ambos, `basic` preferido** |

### 3.2 PARAMETRO — mudam um valor, não a forma

| # | Pergunta | Premissa que vou adotar se não houver resposta |
|---|---|---|
| **Q6** | Qual a vazão esperada do ERP em testes? | Default da credencial **60 req/min**; teto por tenant **600 req/min**. Ambos editáveis na credencial, sem deploy. |
| **Q7** | Se o Redis cair, a API pública **nega** (fail-closed) ou **passa** (fail-open)? | **fail-open + log `error`**, porque o limitador em memória existente vira o piso degradado. fail-closed transformaria o Redis em dependência dura de todo chat. Registrar o custo. |
| **Q8** | TTL do access token público? | **10 min** (`expires_in` devolvido). O interno usa 15 (`SecurityTokenServiceImpl:215`); 10 é o meio de 5–15 recomendado para M2M. |
| **Q9** | A quota conta **por requisição** ou **por token**? | **Por requisição de chat** (`/chat/completions`), não pelo `/auth/token`. Token é de graça e de baixo volume; contá-lo resolve problema que não existe. |

### 3.3 O que foi assumido **sem** resposta, e o custo de cada suposição

| Suposição | Custo se errada |
|---|---|
| Stateless, zero persistência de conteúdo (Q3) | Alto. Muda tudo. Por isso é bloqueante e não premissa. |
| `system` rejeitado (Q2) | Baixo. É uma linha de validação + um código de erro. |
| `client_credentials` sem refresh token | Zero. RFC 6749 §4.4.3: `client_credentials` **não** emite refresh token. O ERP reautentica quando o token expira. |
| Sem idempotency key | Baixo agora. Um reenvio do ERP chama o provider duas vezes. **Não** construir idempotência na v1 — a resposta é determinística por `messages[]`, e o ERP pode reenviar. Registrar como dívida. |
| Sem negociação de versão no header | **Médio.** O caminho já carrega `/v1` duas vezes (`/hive/v1/public/v1/chat/completions`). Versionamento por **path**, não por header, porque o SDK OpenAI monta o path — ver §4.6. |
| Sem `stream` | Baixo. `stream:true` → erro explícito. |
| Sem discovery de modelos | Zero. Decisão 10. |

## 4. Achados que mudam o desenho

> Cada um foi medido no código, não deduzido. A contradição com o briefing está anotada.

### 4.1 🔴 A decisão 5 não entrega o motivo declarado — e isso é *melhor* do que ela promete

**O briefing diz:** *"A credencial fixa a VERSÃO do prompt, opt-in 'seguir latest'. Motivo:
`PromptTemplateServiceImpl:82` incrementa `version` a cada update, então resolver para latest muda o
comportamento do ERP silenciosamente a cada edição."*

**O diagnóstico está certo. O remédio não.**

Medido:

- `PromptTemplateServiceImpl:82` — `update()` faz `promptTemplate.setVersion(existing.getVersion() + 1)`
  na **mesma linha**, e `mapper.updateEntity(existing, ...)` persiste **in-place**. `status` é preservado.
  → **editar um template ACTIVE não cria versão nova: muta a linha e incrementa o contador.**
- `V2__create_prompt_template.sql` — `uk_prompt_template_tenant_code_version UNIQUE (tenant_id, code, version)`
- `PromptTemplateRepository` já tem `findByTenantIdAndCodeAndVersion(tenantId, code, version)` — a query de pin **já existe**.

Consequência, com `PINNED` na versão N:

| Evento | O que a query de pin devolve | O que o ERP sente |
|---|---|---|
| Ninguém edita | linha N, ACTIVE | funciona |
| Cliente faz `PUT /prompt-templates/{id}` | **vazio** (a linha agora é N+1) | falha — **e a versão antiga nunca mais existe** |
| Cliente ativa uma versão nova | **vazio** (a linha N virou ARCHIVED) | falha |

Ou seja: **fixar a versão não congela o comportamento — pane a integração a cada edição, e nunca
devolve o prompt antigo.** Não há snapshot no modelo. O que seria necessário para congelar é tornar
template imutável-on-activate (editar = criar draft novo + ativar), o que **muda o editor da UI
existente** (hoje ele faz `PUT` in-place) e é batch próprio.

**A boa notícia: a decisão 6 já resolve o medo original.** Como só `ACTIVE` é chamável e há
`uk_prompt_template_active_version` (uma única versão ACTIVE por `(tenant_id, code)`), "seguir latest"
não é um drift silencioso — é uma **troca visível**, e desde que a resposta carregue
`prompt_code` + `prompt_version`, o ERP pode logar e afirmar. O medo real (mudança silenciosa)
requer que a resposta **echoe a versão efetiva**.

**Recomendação (mantém a decisão 5, corrige o que ela promete):**

1. `PINNED` continua, renomeado como **asserção de conformidade**: "esta integração roda exatamente na
   versão N; se o prompt se mover, ela é avisada." Não é snapshot, e a documentação diz isso.
2. Novo erro **`prompt_version_not_found`** (4º erro, não 3 — o briefing esqueceu este caso, e ele é
   o resultado mais provável de `PINNED`).
3. `FOLLOW_ACTIVE` fica, e ganha `hive.prompt_fingerprint` (hash do texto efetivo) na resposta.
4. O **snapshot real** fica registrado como dívida, condicionado à mudança do editor.

### 4.2 🔴 `SecurityFilter` não consegue autenticar um principal não-humano — gate que o briefing não listou

`SecurityFilter.doFilterInternal` (`backend/security/impl/.../filter/SecurityFilter.java`):

```
bearerToken != null
  → securityTokenService.validateTokenRevoke(bearerToken)
  → securityTokenService.getSubject(bearerToken)
  → authenticationService.loadUserByUsername(username)   ← INCONDICIONAL
  → resolveAuthorities(bearerToken, userDetails)
  → TenantContext.runWithTenant(tenantId, () -> filterChain.doFilter(...))
```

**Causa apontada errada no planejamento, corrigida na revisão (B2).** `loadUserByUsername:51` é
**inalcançável** nesse caminho: a validação de audiência morre antes —
`SecurityFilter:49` → `validateTokenRevoke` → `decodeAndVerify:167-171` exige `aud=hive.web` →
`BaseException` relançada em `:63` → **500**, não 401. Nenhum controller chega a ser chamado.
`loadUserByUsername` segue sendo defeito **latente** (token `aud=hive.web` forjado com `sub` de
não-usuário chegaria lá), mas não é a causa do 500 da rota pública. E `SecurityFilter.java:28` é
`@Component` — **auto-registrado no container para `/*`, fora do `FilterChainProxy`**: roda em
`/v1/public/**` mesmo com a cadeia nova (B1).

Este é um **gate duro**, do mesmo porte que P-a/P-b/P-c, e não estava na lista. Ele também
invalida a alternativa "ramo dentro do `SecurityFilter`": o `loadUserByUsername` é incondicional
hoje, então qualquer ramo ainda deixa o caminho legado exposto.

**Resolução: cadeia de segurança separada** (`@Order` + `SecurityFilterChain` própria para
`/v1/public/**`), com `PublicApiSecurityFilter` que: valida assinatura+`aud`+`exp`, checa revogação,
carrega a credencial, monta as autoridades a partir das **concessões persistidas**, e chama
`TenantContext.runWithTenant(tenantId, ...)`. A cadeia humana declara explicitamente
`.requestMatchers("/v1/public/**").denyAll()`.

Isso é o que responde à pergunta do briefing *"a allowlist não cobre quais endpoints"*: **a
separação de cadeia cobre, por construção.** Um token público nunca alcança
`/v1/prompt-templates`, `/v1/provider-credential` nem `/v1/tenant/llm-config` porque eles não estão
no filter chain que o autentica. **Não é preciso pagar a matriz de papéis de usuário** — a
restrição do PM é honrada e a lacuna é fechada por outro mecanismo.

### 4.3 A ordem das checagens é o que mata a enumeração (não a allowlist sozinha)

A allowlist só mata enumeração cross-tenant se a ordem for:

```
401 token inválido/revogado
429 cota
403 prompt_code fora da allowlist   ← ANTES de qualquer leitura de prompt_template
404 prompt_code não existe no tenant
409 prompt_code existe mas não está ACTIVE
400 model não bate / system role rejeitado / stream=true
422 tenant sem LLM config, ou modelo indisponível
502 falha do provider
```

A regra: **403 antes do primeiro lookup.** Se a checagem da allowlist viesse depois de resolver o
template, um `clientId` poderia aprender quais `prompt_code` existem no tenant por diferença entre
404 e 403. Com a ordem acima, `prompt_code` fora da allowlist devolve 403 **existindo ou não** — o
que é exatamente o que a decisão 4 quer.

### 4.4 `client_id` tem que ser globalmente único, não único-por-tenant

O endpoint de token roda **antes** de existir `TenantContext` — `SecurityFilter` só faz
`runWithTenant` quando há bearer, e `TenantAspect` engole `TenantNotFountException` e **liga o filtro
do Hibernate desligado** nesse caminho. Então a resolução de `clientId → tenant` acontece com o filtro
de tenant desligado. Se `client_id` fosse único por tenant, a busca seria ambígua e o tenant viria de
parâmetro do cliente — que é a direção de confiança errada.

**O `clientId` é o identificador; o tenant é dado do servidor, lido da linha da credencial.**
Isso exige `UNIQUE (client_id)` global, e é coerente com a decisão 7 (um `clientId` por integração,
com label humano).

### 4.5 A cota precisa nascer em Redis

`ChatRateLimiter` (`agent/impl/.../ChatRateLimiter.java`): `MAX_CALLS = 10`, `WINDOW_SECONDS = 60`,
delegando a `SlidingWindowRateLimiter`, que é
`ConcurrentHashMap<UUID, Deque<Instant>>` — **em memória, por JVM, sem eviction**.

Três fatos medidos que fecham a discussão:

1. **Redis já é dependência de produção**, não item de stack. `RedisConfig` declara
   `RedisTemplate<String,Object>` e `StringRedisTemplate`; `TokenCacheServiceImpl` já usa
   `hive:auth:refresh:{tenantId}:{token}` e `hive:auth:blacklist:{tenantId}:{jti}` com TTL. Existe
   precedente de chave com namespace e de TTL automático.
2. A cota da API pública é **por credencial**, e credencial é o identificador que sobrevive a
   restart e é o mesmo em todas as instâncias. Contar por nó é bug de correção — o limite efetivo
   seria `10N/min` com N instâncias, e o `ConcurrentHashMap` sem eviction é vetor de memória.
3. A referência de mercado é unânime: sliding-window log em `SORTED SET` com um único `EVAL` de Lua
   (`ZREMRANGEBYSCORE` → `ZCARD` → `ZADD` → `EXPIRE`), atômico, sem lock distribuído por request.

**Decisão: a cota da API pública nasce em Redis.** O `ChatRateLimiter` em memória **não é tocado** —
continua servindo `/v1/chat` com o comportamento atual, zero regressão, zero mudança de contrato
interno. As duas rotas passam a compartilhar o *algoritmo* (mesma forma de parametrização) e não o
*estado*.

### 4.6 O path `/hive/v1/public/v1/chat/completions` está certo, e é por isso

O duplo `/v1` não é descuido. O SDK OpenAI monta `{base_url}/chat/completions` e **espera que o
`/v1` esteja no `base_url`**. Com o context-path `/hive`, o ERP configura
`base_url = https://host/hive/v1/public` e o SDK produz
`/hive/v1/public/v1/chat/completions` — o caminho de qualquer provedor OpenAI-compatible
(`/v1/chat/completions`). Alternativa seria implementar um `base_url` sem `/v1` e receber
`/hive/v1/public/chat/completions`, o que **quebra a compatibilidade com o SDK**, não a resolve.

Consequência de versionamento: **versão de contrato é por path, não por header.** O header exigiria
interceptar a requisição antes do roteamento, e não há ganho — o `/v1` já está no path duas vezes.
O duplo `/v1` também é a mesma armadilha já registrada neste repositório: LangChain4j
`OpenAiChatModel` concatena só `/chat/completions` e o `baseUrl` **precisa** incluir `/v1`
(regra `regras/global/hive/integracao/openai-compatible-baseurl-v1`).

Efeito colateral no `SecurityConfig`: os matchers veem o path **sem** o context-path. Então
`permitAll` é `POST /v1/public/auth/**` e a cadeia pública casa `/v1/public/**` — não
`/hive/v1/public/**`.

### 4.7 `model` é asserção, não seletor

O SDK OpenAI exige `model` no request, e proxies OpenAI-compatible o ignoram
(vast.ai: *"The `model` field is required by the OpenAI SDK but is ignored by the proxy"*). No Hive
o provider/modelo é do tenant (`tenant_llm_config`, decisão do ADR-013 §Decisão 3).

Recomendação: `model` **ausente** → usa o default do tenant. **Presente e diferente** do modelo do
tenant → `400 unknown_model`. Isso transforma um campo obrigatório e inútil do SDK em **sanidade
check de configuração**: se o ERP aponta para o Hive errado, ele descobre no primeiro request em vez
de na semana seguinte. É o que dá sentido ao 3º erro do briefing.

## 5. O que o ERP precisa além de `chat/completions` — resposta consolidada

| Necessidade | Atendida como | Custo |
|---|---|---|
| Auth sem browser | `POST /public/auth/token` (`client_credentials`) | — |
| Endereço do endpoint | `base_url = https://host/hive/v1/public` | — |
| Como chamar o prompt certo | `prompt_code` no request; códigos no ato do provisionamento (decisão 10) | — |
| CorrelacionarLogs | `user` (ecoado) + `hive.turn_id` (novo, único por turno) | `turn_id` é UUID, não sequence — o contrato stateless não tem onde poêr |
| Afirmar que rodou a versão certa | `hive.prompt_version` + `hive.prompt_fingerprint` na resposta | ~5 linhas |
| Distinguir erro de ação | `error.code` de 4 valores (§4.1) | — |
| Tratar expiração de token | `expires_in` no token; 401 no chat quando expira | — |
| Retry | Idempotente por natureza (stateless, sem efeito colateral). 429 traz `Retry-After` | — |
| Timeout | Cliente configura. Servidor herda o do provider + `LlmRetryPolicy` | — |
| Idempotency key | **Não na v1.** Dívida registrada | — |
| SSE | **Não na v1** (decisão 9) | — |
| Descoberta de modelos/prompts | **Não** (decisão 10) | — |

## 6. Referências consultadas

- **Internas:** `docs/adr/ADR-012-Chat-Turn-Gateway-vs-AI-Services.md` (5 gatilhos de reavaliação),
  `docs/adr/ADR-013-Assistant-Sem-Rag-Sem-Agente.md` (§"O que este ADR não decide" → "APIs públicas:
  modelo de autenticação e cota, indefinido"), `.agents/rules/db-migration-flyway.md`,
  `.agents/rules/harness-continuous.md`, `.spec/tech-debts/security-rbac/TASKS.md`,
  `.spec/tech-debts/prompt-read-permission/TASKS.md`
- **Externas:**
  - *Implementing OAuth 2.0 Client Credentials Flow for Microservices* (2026-05) — `client_secret_basic`
    como recomendação de indústria, "no refresh tokens no client_credentials", TTL curto
  - *Securing M2M tokens in B2B SaaS* (Scalekit, 2025-05) — TTL 5–15 min para M2M, rotação, escopo
    mínimo
  - *Designing API Authentication for B2B SaaS* (2026-04) — **segredo de alta entropia é
    SHA-256, não bcrypt** (bcrypt é para senha de baixa entropia); prefixo para exibição
  - *Build 5 Rate Limiters with Redis* (redis.io) — sliding-window log em `SORTED SET` + Lua atômico,
    `EVALSHA`, sem lock por request
  - *Vast.ai OpenAI-compatible guide* — `model` é obrigatório no SDK e ignorado pelo proxy;
    parâmetros aceitos-e-ignorados (`user`, `suffix`)
  - *OAuth 2.0 Client Credentials Misuse in Public Apps* (Sentry, 2025-06) — `client_credentials` é
    para cliente **confidencial**; o ERP é (segredo em CI), então o grant está correto

---

**Próxima fase:** `CONSTRAINTS.md` — as restrições reais do código que já medi, e o compliance.
