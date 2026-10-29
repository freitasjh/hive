# ARCHITECTURE — API pública de chat

- **Fase:** dev-planner 3 (Projete)
- **Data:** 2026-09-27
- **Sem código de implementação.** Trechos de DDL e de payload são ilustrativos e definitivos; trechos
  de Java são esquemáticos e estão marcados como tal.

---

## 1. Visão de sistema

```
                        ┌──────────────── ERP / sistema B2B ────────────────┐
                        │  client_id + client_secret no CI                │
                        │  base_url = https://host/hive/v1/public          │
                        └───────────────────┬──────────────────────────────┘
                                            │
   ═════════════════════════════════════════ ▼ ═════════════════════════════════════════
   SecurityFilterChain  @Order(1)  —  casa APENAS /v1/public/**
   ═════════════════════════════════════════════════════════════════════════════════
     permitAll:  POST /v1/public/auth/**
     autenticado: POST /v1/public/v1/chat/completions   (aud = hive.public)
     ─────────────────────────────────────────────────────────────────────────────
     PublicApiSecurityFilter
       0. Content-Length > 1 MiB → 413  (antes do Jackson — teto de corpo, D2)
       1. extrai Bearer
       2. verifica assinatura + iss + aud=hive.public + exp      [0 SELECT]
       3. jti em blacklist?          → 401                     [1 Redis GET]
       4. clientId revogado?          → 401                     [1 Redis GET]
       5. TenantContext.runWithTenant(tenant-id do token, …)    ← âncora do @Filter
       6. authorities = concessões do token (do banco, no momento da emissão)
       7. finally: limpa SecurityContextHolder E TenantContext (SecurityFilter:71 faz; o público
          também precisa — 2 requests encadeados na mesma thread provam)
     ⚠️ ScopedValue NÃO propaga pra thread pool: @Async aqui = leitura cross-tenant SEM ERRO.
   ═════════════════════════════════════════════════════════════════════════════════
                                    │
   ┌────────────────────────────────┴───────────────────────────────────────┐
   │  MÓDULO NOVO  publicapi                                                │
   │                                                                        │
   │  publicapi/web/public/v1/                                              │
   │    PublicAuthController      POST /auth/token                           │
   │    PublicChatController      POST /v1/chat/completions                 │
   │        └ @ExceptionHandler LOCAL  → OpenAI error envelope (D9)          │
   │                                                                        │
   │  publicapi/impl/                                                      │
   │    PublicClientTokenIssuer ──────►  concede a partir de public_api_client_grant
   │    PublicChatService ────────────►  reusa ChatTurnGateway (D3)          │
   │        ├ PromptGrantResolver     ►  public_api_client_prompt + prompt_template
   │        ├ ProviderResolution     ►  (D4, collaborator novo, compartilhado)
   │        ├ TenantQuotaGuard       ►  Redis (D5)
   │        └ TurnOutcomeMapper      ►  (D4, collaborator novo, compartilhado)│
   │                                                                        │
   │  publicapi/dbupdate/   V1__public_api_client.sql                        │
   │  (flyway_schema_history_publicapi · baselineOnMigrate · SEM repair)     │
   └────────────┬──────────────────────┬──────────────────────┬──────────────┘
                │                      │                      │
   ═════════════▼══════════   ══════════▼══════════   ════════▼═══════════════
   SecurityFilterChain      agent (api/impl)          tenant
   @Order(2) — humano       ChatTurnGateway           TenantLlmConfigService
   /v1/**                    LangChain4jTurnGateway    (provider/model default)
   denyAll /v1/public/**     PromptTemplateService
   @HasPermission(...)      provider (api)
                             LlmClientFactory
   ═════════════════════════════════════════════════════════════════════════
        Redis  (existente: TokenCacheServiceImpl · + quota pública)
```

## 2. Modelo de dados

**Dono:** módulo `publicapi`. **3 tabelas.** Todas `NOT NULL` em `tenant_id`, todas com
`AbstractEntity` (para receber o `@Filter` e o `TenantListener`), todas com constraint nomeada,
sem FK cross-módulo (mesma razão de `provider_credential` e `provider` — históricos Flyway
independentes não garantem ordem; ver ADR-013 §"`provider_credential` fica no módulo `agent`").

### 2.1 `public_api_client` — a credencial (P-a + P-c)

```sql
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TABLE public_api_client
(
    id                       UUID NOT NULL DEFAULT uuid_generate_v4(),
    create_at                TIMESTAMP WITHOUT TIME ZONE,
    update_at                TIMESTAMP WITHOUT TIME ZONE,
    tenant_id                UUID NOT NULL,          -- âncora do isolamento

    client_id                VARCHAR(64)  NOT NULL,  -- GLOBALMENTE único (D8)
    label                    VARCHAR(255) NOT NULL,  -- "produto X / produção" (decisão 7)
    secret_hash              CHAR(64)     NOT NULL,  -- SHA-256 hex, 64 chars
    previous_secret_hash     CHAR(64),               -- rotação com janela de graça
    previous_secret_expires_at TIMESTAMP WITHOUT TIME ZONE,

    -- Decisão 8: a cota é dado da credencial, não constante.
    rate_limit_per_minute    INT NOT NULL DEFAULT 60,
    token_ttl_seconds        INT NOT NULL DEFAULT 600,

    -- D2: o teto de payload também é dado da credencial. Número em código é número que
    -- ninguém calibra sem deploy. Reclamado no claim `plim`; o gate mede, não a constante.
    -- Recusa EXPLÍCITA (400), nunca truncamento silencioso: o ERP receber 200 OK com metade
    -- da conversa é pior que 400.
    max_messages             INT NOT NULL DEFAULT 200,
    max_message_chars        INT NOT NULL DEFAULT 32000,
    max_total_chars          INT NOT NULL DEFAULT 256000,

    revoked_at               TIMESTAMP WITHOUT TIME ZONE,  -- revogação cirúrgica (decisão 7)
    last_used_at             TIMESTAMP WITHOUT TIME ZONE,
    expires_at               TIMESTAMP WITHOUT TIME ZONE,  -- NULL = sem expiração

    CONSTRAINT pk_public_api_client PRIMARY KEY (id),
    -- D8: global, não por tenant. O endpoint de token roda com o @Filter desligado
    -- (TenantAspect engole TenantNotFountException), então a busca por client_id precisa
    -- ser inequívoca e o tenant tem que vir DA LINHA, nunca do request.
    CONSTRAINT uk_public_api_client_client_id UNIQUE (client_id),
    CONSTRAINT uk_public_api_client_tenant_label UNIQUE (tenant_id, label),
    CONSTRAINT chk_public_api_client_rate_limit  CHECK (rate_limit_per_minute >= 1),
    CONSTRAINT chk_public_api_client_token_ttl    CHECK (token_ttl_seconds BETWEEN 60 AND 3600),
    CONSTRAINT chk_public_api_client_max_messages CHECK (max_messages >= 1),
    CONSTRAINT chk_public_api_client_max_msg_chars CHECK (max_message_chars >= 1),
    CONSTRAINT chk_public_api_client_max_total_chars CHECK (max_total_chars >= max_message_chars),
    -- O segredo anterior só existe enquanto a janela de graça está aberta. Sem este CHECK,
    -- um previous_secret_hash órfão continuaria sendo aceito por indefinitely — a mesma
    -- família de bug que `UNIQUE` inerte em NULL já produziu neste projeto.
    CONSTRAINT chk_public_api_client_previous_secret CHECK (
        (previous_secret_hash IS NULL AND previous_secret_expires_at IS NULL)
        OR (previous_secret_hash IS NOT NULL AND previous_secret_expires_at IS NOT NULL)
    )
);

CREATE INDEX idx_public_api_client_tenant ON public_api_client (tenant_id);
CREATE INDEX idx_public_api_client_revoked ON public_api_client (revoked_at) WHERE revoked_at IS NOT NULL;
```

**Por que `secret_hash` e não `AesGcmAttributeConverter`:** D6 / `CONSTRAINTS.md §1.9`. Segredo é
verificável, não recuperável; e a master key não deve governar o ciclo de vida de segredos de
integração.

**Por que o segredo anterior:** rotação sem derrubar o ERP. Procedimento: provisionar novo segredo →
o ERP recebe o novo e o antigo continua válido por uma janela → o ERP troca no CI → janela expira →
o antigo deixa de valer. É o que o briefing chama de "rotação" e é o que a escala determina
("rotate regularmente, e sempre quando suspeitar de exposição").

**Nota de segurança que precisa estar no código:** `previous_secret_hash` é mais um segredo em
claro-cifrado? Não — é outro SHA-256. Mas a comparação precisa ser em **tempo constante**
(`MessageDigest.isEqual` / `constant-time compare`), e o `secret_hash` **nunca** aparece em log,
em `toString()`, ou em resposta de erro.

### 2.2 `public_api_client_grant` — as concessões (P-a)

```sql
CREATE TABLE public_api_client_grant
(
    id             UUID NOT NULL DEFAULT uuid_generate_v4(),
    create_at      TIMESTAMP WITHOUT TIME ZONE,
    update_at      TIMESTAMP WITHOUT TIME ZONE,
    tenant_id      UUID NOT NULL,

    client_ref     UUID NOT NULL,
    permission     VARCHAR(64) NOT NULL,   -- 'PUBLIC_CHAT_COMPLETE'

    CONSTRAINT pk_public_api_client_grant PRIMARY KEY (id),
    CONSTRAINT uk_public_api_client_grant UNIQUE (client_ref, permission),
    -- NO ACTION, não CASCADE: apagar a credencial é ato explícito que passa pela aplicação
    -- (revogação é `revoked_at`, não DELETE), e a FK que cascadeia esconde a intenção.
    CONSTRAINT fk_public_api_client_grant_client FOREIGN KEY (client_ref)
        REFERENCES public_api_client (id) ON DELETE NO ACTION
);

CREATE INDEX idx_public_api_client_grant_client ON public_api_client_grant (client_ref);
```

**Este é o grant model, e ele é pequeno de propósito.** Uma credencial pública recebe, no
provisionamento, **exatamente uma** concessão: `PUBLIC_CHAT_COMPLETE`. Não há herança, não há
role, não há `RoleControllerV1` para manter em paridade.

⚠️ **Restrição do PM honrada:** isto **não** é RBAC de usuário. Não existe `role`, não existe
`permission → role`, não existe tela de roles. As 6 permissões hardcoded de
`SecurityTokenServiceImpl:182` **não são tocadas** — o `.spec/tech-debts/security-rbac/` P1-1
continua aberto, e a superuseração de usuário **continua existindo**. O que muda é que a
superfatoração **pública** tem dono: uma linha.

⚠️ **Por que a permissão nova NÃO entra em `SecurityConstants.DEFAULT_PERMISSIONS`:** aquela lista é
só exibição, e um teste de paridade (`LoginIT`) compara o claim `permissions` de um token **humano**
com ela. Inserir `PUBLIC_CHAT_COMPLETE` lá quebraria a paridade e, pior, faria a permissão pública
aparecer em uma tela de roles que não existe. **A permissão pública é um conceito do módulo
`publicapi` e vive lá.**

### 2.3 `public_api_client_prompt` — a allowlist + o pin (decisões 4, 5)

```sql
CREATE TABLE public_api_client_prompt
(
    id                UUID NOT NULL DEFAULT uuid_generate_v4(),
    create_at         TIMESTAMP WITHOUT TIME ZONE,
    update_at         TIMESTAMP WITHOUT TIME ZONE,
    tenant_id         UUID NOT NULL,

    client_ref        UUID NOT NULL,
    prompt_code       VARCHAR(255) NOT NULL,
    pin_mode          VARCHAR(16) NOT NULL DEFAULT 'FOLLOW_ACTIVE',
    pinned_version    INT,

    CONSTRAINT pk_public_api_client_prompt PRIMARY KEY (id),
    CONSTRAINT uk_public_api_client_prompt UNIQUE (client_ref, prompt_code),
    CONSTRAINT fk_public_api_client_prompt_client FOREIGN KEY (client_ref)
        REFERENCES public_api_client (id) ON DELETE NO ACTION,
    -- Sem FK para prompt_template: cross-módulo. `prompt_code` é resolvido em serviço,
    -- dentro do @Filter de tenant. Um código órfão vira 404 `prompt_not_found`, que é
    -- o diagnóstico correto de erro de provisionamento.
    CONSTRAINT chk_public_api_client_prompt_pin_mode
        CHECK (pin_mode IN ('FOLLOW_ACTIVE', 'PINNED')),
    -- O par é um autômato, e o banco é quem o amarra. `pinned_version` isolado nullable
    -- seria inerte como NULL: PINNED sem versão (e FOLLOW_ACTIVE com versão) entrariam
    -- as duas, e o código desempacotaria Integer sem ter o que desempacotar. Mesma armadilha
    -- de `prompt_template.version`, que o projeto já mediu.
    CONSTRAINT chk_public_api_client_prompt_pin_pair CHECK (
        (pin_mode = 'PINNED'      AND pinned_version IS NOT NULL AND pinned_version >= 1)
        OR (pin_mode = 'FOLLOW_ACTIVE' AND pinned_version IS NULL)
    )
);

CREATE INDEX idx_public_api_client_prompt_client ON public_api_client_prompt (client_ref);
```

**Por que a allowlist e o pin vivem na MESMA linha, e não em duas tabelas:** o pin é uma propriedade
**da concessão**, não do prompt. Girar o pin de uma integração é um `UPDATE` de uma linha, e a
decisão 5 ("a credencial fixa a versão") é literalmente sobre a credencial. Separar criaria uma
terceira entidade para guardar um inteiro.

**Sem FK para `prompt_template`:** dois motivos já medidos neste repositório — (1) históricos Flyway
independentes não garantem ordem entre módulos, e foi exatamente esse o argumento que fixou
`provider_credential` no módulo `agent`; (2) `prompt_code` é a chave de negócio e não o `id`, então
a FK seria para uma chave não-única entre módulos.

**Sem `pin_mode` derivado de `pinned_version IS NULL`:** a regra do projeto é o oposto — se o
código trata o valor como número, ele é `NOT NULL` e o estado é um `CHECK`. Um valor nullable que
serve de discriminante é um valor que o próximo lê errado.

⚠️ **Default = `FOLLOW_ACTIVE`, e o provisionamento NUNCA depende do `DEFAULT`** (D1, arbitrado pelo
usuário). O `CommandLineRunner` do batch 10 **sempre informa** `pin_mode` explicitamente — o
`DEFAULT 'FOLLOW_ACTIVE'` no DDL só atende INSERT fora do runner (teste, seed, SQL manual). Motivo do
default: `PINNED` torna **todo PUT de rotina do prompt um 409** para a integração; a condição que
decidiu (pergunta do product-manager, respondida pelo usuário: *"1 integração, prompt do mesmo time
que faz o deploy"*)-aponta FOLLOW_ACTIVE. `PINNED` segue disponível, por credencial e por
`prompt_code`, onde o contrato exigir. O trio `(pin_mode, pinned_version)` continua amarrado pelo
`CHECK`: `PINNED` sem versão é **recusado pelo banco**, e um texto que afirme o contrário está
errado.

### 2.4 Tabela que **não** existe: tokens emitidos

**Não** há tabela de token. A revogação usa o que já existe:
`TokenCacheService.addToBlacklist(tenantId, jti, ttl)` e `isBlacklisted(...)` — precedente
`TokenCacheServiceImpl` com prefixo `hive:auth:blacklist:{tenantId}:{jti}`.

A revogação de **credencial** (não de grant) precisa ser imediata, e o blacklist é populado por
*rotação de refresh token*, não por ato administrativo. Então, um conjunto novo, no mesmo estilo:

```
hive:public:revoked-client:{clientId}     SET, sem TTL
```

- Escrita: ato administrativo de revogação → `SADD` (imediato).
- Leitura: `SISMEMBER` no filtro público, **junto** com o `isBlacklisted` (mesma ida ao Redis).
- Rebuild após restart do Redis: read-through. Ausência no Redis → 1 `SELECT` na
  `public_api_client`; negativo cacheado por 60s (padrão de mercado: cache curto + invalidação
  imediata, para que a revogação seja imediata sem um SELECT por request).

**Janela de revogação, declarada:**

| O que é revogado | Efetivo em |
|---|---|
| `client` inteiro (`revoked_at`) | **imediato** (Redis `SADD` + `revoked_at`) |
| grant de `prompt_code` removido | ≤ TTL do access token (**10 min**) |
| segredo rotacionado (janela de graça encerrada) | no instante em que a janela expira |
| `stream`/cota | — |

### 2.5 Entidades Java — o espelho do DDL

Três `*Entity extends AbstractEntity` em `publicapi/impl/entity/`, com **getters/setters manuais**
(sem Lombok — regra do projeto) e `@Column` espelhando o DDL acima, sabendo que com `ddl-auto=none`
o `@Column` é **metadado inerte** e o DDL é quem amarra.

`public_api_client.client_id` e `.label` **não** devem ir para `AbstractEntity`; `AbstractEntity` dá
`id`, `create_at`, `update_at`, `tenant_id` e o `@Filter` — que é o que importa aqui.

**Nome da entidade:** `PublicApiClientEntity` — o prefixo evita colisão conceitual com o
`Client` do LangChain4j, que existe no classpath.

---

## 3. Contrato do token

### 3.1 `POST /hive/v1/public/auth/token`

> No `SecurityConfig`, o matcher é `POST /v1/public/auth/**` — o context-path `/hive` **não** faz
> parte do que o Spring Security vê.

**Request** — aceita `client_secret_basic` (preferido) e `client_secret_post`:

```http
POST /hive/v1/public/auth/token HTTP/1.1
Content-Type: application/x-www-form-urlencoded
Authorization: Basic base64(urlencode(client_id) ":" urlencode(client_secret))
```

`grant_type` é **obrigatório e validado** (`client_credentials`). `scope` é **ignorado** — o escopo
vem das concessões, não do pedido. Um `scope` pedido fora das concessões é `invalid_scope`, o que
implementa "intersect requested scopes with allowed scopes" sem permitir ver um `scope` pedido e não
concedido nunca ser servido.

**Response 200** (OAuth 2.0, RFC 6749 §5.1):

```json
{
  "access_token": "<JWT>",
  "token_type": "Bearer",
  "expires_in": 600,
  "scope": "PUBLIC_CHAT_COMPLETE"
}
```

### 3.2 Claims do access token público

| Claim | Valor | Por que |
|---|---|---|
| `iss` | `hive.api` | reusa o emissor existente (`SecurityTokenServiceImpl:44`) |
| `aud` | **`hive.public`** | **D7.** Distingue de `hive.web`. Um token do painel web **não** é aceito na API pública, e vice-versa, apesar da mesma assinatura HMAC e do mesmo segredo |
| `sub` | `clientId` | principal não-humano. **Não** é um `users.username` — por isso o filtro público não pode chamar `loadUserByUsername` |
| `jti` | UUID | revogação. Precedente: `SecurityTokenServiceImpl:191` + blacklist Redis |
| `tenant-id` | `credential.tenantId` | **P-b. A âncora do `@Filter`.** Claim nomeado `tenant-id` (não `tenantId`) — é o nome que `SecurityTokenServiceImpl:42` já usa, e divergir quebraria `getTenantId(token)` e `validateTokenRevoke` |
| `permissions` | lista das **concessões persistidas** | **P-a.** Sai de `public_api_client_grant`, não de literal |
| `pcode` | lista dos `prompt_code` permitidos | **decisão 4.** Allowlist no token → **hot path sem banco**. Custo declarado: revogar um código só surte efeito no próximo token (≤10 min) |
| `plock` | mapa `code → versão pinada` (só os `PINNED`) | **decisão 5.** Mesmo custo/benefício do `pcode` |
| `plim` | `{m: max_messages, c: max_message_chars, t: max_total_chars}` | **D2.** Teto de payload reclamado do banco **na emissão**, não relido no hot path. Sem ele, calibrar teto = deploy |
| `exp` | `iat + token_ttl_seconds` | 10 min por default (D17), por credencial |
| `typ` | `JWT` | RFC 8725 §3.2. Ausente, e um token de outro emissor com o mesmo segredo é aceito |
| `kid` | id da chave de assinatura | Pré-requisito de rotação. Ausente, e rodar chave = downtime ou aceite duplo | |

**Nada de `role`, `scope` hierárquico, `email`, `name`, ou qualquer claim de PII.** O ERP mandou
`clientId` e recebe o tenant de volta — é um grant de máquina, não um perfil.

**O que muda em `SecurityTokenServiceImpl`:** **nada.** O emissor público é
`PublicClientTokenIssuer`, uma classe nova em `publicapi/impl`. A única sobreposição possível é
`decodeAndVerify`, hoje `private` — extraí-lo para um `TokenVerifier` compartilhado em
`security/api` é **opcional** e fica como tarefa de limpeza, não como dependência. Deixar
`SecurityTokenServiceImpl` intocado é uma propriedade desejável: é o código que emite token para
todo usuário autenticado, e o RBAC de usuário está sendo discutido separadamente.

⚠️ **A chave de assinatura É separada** (D8): `api.security.public.token.secret`, com
`decodeAndVerify` por parâmetro. O segredo HMAC compartilhado entre token **humano** e token **de
máquina** não é exceção aceita — é correção barata (~10 linhas). Com chave única, `aud` separa só o
token **honesto**: o forjado, com o mesmo segredo, passa. E a propriedade que D7 reivindica fica
condicional. `HMAC256` em si (algoritmo simétrico) é a exceção **aceita e registrada** — gatilho para
revisitar: 2º cliente externo, ou `kid`/rotação.

**Reutilização de `jti` + `tenant-id` + `permissions` na verificação:** o `PublicApiSecurityFilter`
**não** passa por `SecurityTokenServiceImpl.validateTokenRevoke`, porque esse método valida
`withAudience(AUDIENCE)` (`hive.web`) e rejeitaria o token público. A verificação pública é a própria
rotina do filtro, com `audience("hive.public")`.

### 3.3 TTL, refresh, e a resposta à pergunta do ERP

- **Sem refresh token.** RFC 6749 §4.4.3: `client_credentials` não emite. O ERP reautentica quando
  `expires_in` zera, e **deve** cachear o token até `expires_in - 60s` (evita martelar o endpoint de
  token).
- **Documentar no endpoint**: se o ERP reautentica a cada chamada, ele vai bater no rate limit do
  `/auth/token` (D5: limite próprio nesse endpoint). Esse é o erro de integração nº 1 esperado.

---

## 4. Contrato HTTP — `POST /hive/v1/public/v1/chat/completions`

> `base_url` do ERP = `https://host/hive/v1/public`. O SDK OpenAI concatena `/v1/chat/completions`.
> **Não** há versão por header (D14).

### 4.1 Request

```json
{
  "model": "mimo-v2.5",
  "prompt_code": "resumo-pedido-venda",
  "messages": [
    { "role": "user",      "content": "Pedido 4021, cliente ACME" },
    { "role": "assistant", "content": "O que você precisa saber sobre o pedido 4021?" },
    { "role": "user",      "content": "Prazo de entrega e valor total" }
  ],
  "user": "erp-pedido-4021",
  "stream": false
}
```

| Campo | Obrigatório | Regra |
|---|---|---|
| `model` | não (o SDK exige) | ausente → default do tenant; presente e ≠ modelo do tenant → **`400 unknown_model`** (D11) |
| `prompt_code` | **sim** | 1–255 chars, `[A-Za-z0-9._-]`. Ausente/vazio → `400 prompt_code_required` |
| `messages` | **sim** | 1..`max_messages` mensagens (default **200**, claim `plim`); roles `user`\|`assistant` **apenas**; `content` 1..`max_message_chars` (default **32000**) chars, não-blank; soma total ≤ `max_total_chars` (default **256000**) |
| `user` | não | ≤ 128 chars. Correlação e log. **Nunca** é âncora de tenant, nunca é prompt, nunca é confiável |
| `stream` | não | `true` → **`400 streaming_not_supported`** (D12) |
| `temperature`, `max_tokens`, `top_p` | **não** | Aceitos e **ignorados** (precedente vast.ai: "parâmetros aceitos e ignorados"). O `inference_params` do prompt manda. Se vier valor e vier divergente, loga em `debug` |

**Rejeições explícitas, e o motivo de cada uma:**

| Rejeitado | Código | Por que |
|---|---|---|
| `role: "system"` em `messages` | `400 system_role_not_allowed` | **D10.** `buildMessages` põe o template do cliente **primeiro** e `addAll(conversation)` **depois**. Um `system` no request chega por último e é a última instrução do modelo. Aceitar = o ERP sobrescreve o prompt que a plataforma é dona de |
| `messages` acima do teto da credencial | `400 too_many_messages` | Teto é **dado da credencial** (D2), não constante. Protege o provider: `LlmRetryPolicy` **retenta**, e payload sem teto × retry é a pior combinação de custo e erro |
| `content` ou soma total acima do teto | `400 request_too_large` | Idem. **Nunca truncamento silencioso** — o ERP receber 200 com metade da conversa é pior que 400 |
| corpo > 1 MiB | `413` no **filtro** | Decidido por `Content-Length` **antes** do Jackson. Teto 1 MiB porque JSON escapa non-ASCII como `\uXXXX` (6 bytes/char): 20×8000×6 = 960 KB, e 160 KB recusaria o que o contrato declara válido |
| campo desconhecido no body | `400 unknown_field` | Jackson `FAIL_ON_UNKNOWN_PROPERTIES` ligado **só** no DTO público. Falhar em campo desconhecido é melhor que ignorar em silêncio: um ERP mandando `functions` (fase 2) precisa saber que não foi atendido |

**Taxonomia de campos (D3):** 9 rejeitados por nome (`response_format`, `tools`, `tool_choice`,
`functions`, `function_call`, `n`, `logprobs`, `top_logprobs`, `parallel_tool_calls`) + 11
aceitos-e-ignorados (inclui `max_completion_tokens`) + 3 especiais (`model`, `stream`, `user`) +
`unknown_field` para o resto. **`max_tokens` do provider é clampeado** (IT-MAXTOKENS-CLAMP): o
`LangChain4jTurnGateway:76-78` monta o client, logo o teto é nosso.

### 4.2 Response 200 — formato OpenAI + `hive:{}`

```json
{
  "id": "chatcmpl-6f1c…",
  "object": "chat.completion",
  "created": 1790612345,
  "model": "mimo-v2.5",
  "choices": [
    { "index": 0, "message": { "role": "assistant", "content": "…" }, "finish_reason": "stop" }
  ],
  "usage": { "prompt_tokens": 812, "completion_tokens": 143, "total_tokens": 955 },
  "prompt_code": "resumo-pedido-venda",
  "metadata": {
    "hive": {
      "turn_id": "0f7c…",
      "prompt_code": "resumo-pedido-venda",
      "prompt_version": 4,
      "prompt_fingerprint": "sha256:9c1a…",
      "pin_mode": "PINNED",
      "provider": "OPENCODE_GO",
      "model": "mimo-v2.5",
      "elapsed_ms": 1840,
      "tenant_id": "…"
    }
  }
}
```

**`prompt_code` no topo E em `metadata.hive` é redundância deliberada.** O topo é o que um leitor
humano de log encontra; o bloco é o que o ERP faz parse programático, e é onde cabem os campos que
não são OpenAI. Esta é a decisão 3 ("resposta formato OpenAI + `prompt_code` e bloco `hive:{}`").

| Campo em `hive{}` | Para que serve |
|---|---|
| `turn_id` | correlação entre a resposta do ERP e a linha de trace. **Novo** — o fluxo stateless não tem session id para servir de id |
| `prompt_version` | **D13.** É o que torna `FOLLOW_ACTIVE` legítimo: o ERP pode logar e afirmar qual versão rodou |
| `prompt_fingerprint` | sha256 do texto efetivo **completo**, não "dos primeiros N bytes" (N indefinido truncaria o começo — o pior lado). É a **única identidade que sobrevive à promoção entre ambientes**, e por isso é coluna gravada e comparada, não recomendação |
| `pin_mode` | o ERP sabe se está sob asserção de conformidade |
| `elapsed_ms` | espelha `ChatTrace.elapsedMs`, que o ADR-012 nomeia como o sinal que distingue "provider travou" de "pedido grande demais" |
| `tenant_id` | echo do grant. Não é novo dado — o ERP já o conhece |

**O que NÃO está em `hive{}`:** lista de `prompt_code` permitidos (decisão 10 — seria discovery),
o `client_secret`, o `apiKey` do provider, o texto do prompt, ou o conteúdo da conversa.
`TurnRequest.toString()` e `TurnReply.toString()` já têm os overrides que impedem vazamento
(`apiKey=***`; falha só como nome de classe) — **esse é o padrão a seguir em qualquer record novo
desta feature.**

### 4.3 Os **4** erros distinguíveis

> O briefing pede 3. São 4, e o quarto é consequência direta do achado de versionamento
> (`EXPLORATION.md §4.1`): `PINNED` + edição do prompt é o resultado **mais provável**, não a exceção.

Envelope (OpenAI `error` object, D9 — handler **local** ao controller público):

```json
{ "error": { "message": "…", "type": "invalid_request_error", "param": "prompt_code", "code": "prompt_not_active" } }
```

| # | `error.code` | HTTP | `type` | Quando | Ação do ERP |
|---|---|---|---|---|---|
| 1 | `prompt_not_in_allowlist` | **403** | `permission_error` | o `prompt_code` não está na allowlist da credencial — **existindo ou não no tenant** (D16) | provisionar o código |
| 2 | `prompt_not_found` | **404** | `not_found_error` | está na allowlist mas não existe neste tenant | **erro de provisionamento** (admin), não do ERP |
| 3 | `prompt_not_active` | **409** | `invalid_request_error` | existe, mas o status não é `ACTIVE` (`DRAFT` ou `ARCHIVED`) — **decisão 6** | ativar a versão |
| 4 | `prompt_version_not_found` | **409** | `invalid_request_error` | `PINNED` na versão N e a versão N não existe (o `PUT` incrementou a linha, ou a versão foi arquivada) — **D13** | re-pinar |
| — | `unknown_model` | **400** | `invalid_request_error` | `model` ≠ modelo do tenant — **D11** | corrigir a config do ERP |
| — | `system_role_not_allowed` | **400** | `invalid_request_error` | `system` em `messages` — **D10** | remover |
| — | `stream_not_supported` | **400** | `invalid_request_error` | `stream:true` — **D12** | remover |
| — | `too_many_messages` / `request_too_large` / `unknown_field` | **400** | `invalid_request_error` | validação | corrigir |
| — | *(body > 1 MiB)* | **413** | `invalid_request_error` |Decidido no **filtro**, antes do handler — 413 morre no middleware, nunca chega ao controller | reduzir |
| — | `rate_limit_exceeded` | **429** | `rate_limit_error` + `Retry-After` | cota | esperar |
| — | `model_unavailable` | **422** | `invalid_request_error` | `MODEL_UNAVAILABLE` classificado — **mesma classificação do interno** | revisar config do tenant |
| — | `model_returned_empty` | **422** | `invalid_request_error` | `EMPTY_RESPONSE` | revisar `max_tokens` do prompt |
| — | `llm_not_configured` | **422** | `invalid_request_error` | tenant sem `tenant_llm_config` | admin configura |
| — | `provider_error` | **502** | `server_error` | `ProviderException` | retry |
| — | `invalid_client` | **401** | `invalid_request_error` | `clientId`/segredo inválido ou revogado | — |
| — | `invalid_grant` | **400** | `invalid_request_error` | `grant_type` ≠ `client_credentials` | — |

**As três classificações de erro do provider são as mesmas do interno, e é o ponto.** `MODEL_UNAVAILABLE`
→ 422 com evict de discovery, `MODEL_RETURNED_EMPTY` → 422, `ProviderException` → 502. É a razão de
D3 (reusar a porta): o diagnóstico **não pode divergir** entre os dois caminhos. `TurnOutcomeMapper`
(branch `FAILED`) chama `ModelNotFound.isModelNotFound(failure)` — estático, e a regra sofre em
`agent/api` para que os dois caminhos compartilhem o mesmo veredito.

**Os 3 códigos "de prompt" são distinguíveis entre si por status HTTP, não por corpo.** Isso é
deliberado: o ERP consegue ramificar por status **antes** de fazer parse do corpo, e o corpo
carrega o código para quem quiser ser mais específico.

### 4.4 Ordem de execução da requisição

```
 0. PublicApiSecurityFilter:  Content-Length > 1 MiB → 413  (antes do Jackson)
 1. PublicApiSecurityFilter:  Bearer → assinatura/iss/aud/exp → 401
 2. PublicApiSecurityFilter:  jti blacklist → 401   (Redis)
 3. PublicApiSecurityFilter:  client revogado → 401 (Redis)
 4. PublicApiSecurityFilter:  TenantContext.runWithTenant(tenant-id do token) [Callable]
 5. validação do body (@Valid + regras do DTO)     → 400
 6. @HasPermission(PUBLIC_CHAT_COMPLETE)           → 403 se a concessão não veio no token
 7. TenantQuotaGuard.check(clientId)               → 429  (antes do prompt)
 8. prompt_code está no claim `pcode`?             → 403 prompt_not_in_allowlist ← ANTES de qualquer SELECT
 9. PromptGrantResolver                            → 404 prompt_not_found
                                                  → 409 prompt_not_active / prompt_version_not_found
10. model bate com o default do tenant?            → 400 unknown_model
11. ProviderResolution.resolve(tenantId)           → 422 llm_not_configured
12. ChatTurnGateway.execute(TurnRequest)           → 422 / 502  (via TurnOutcomeMapper)
13. resposta OpenAI + metadata.hive{} + trace (sessionId = null, turn_id único)
```

⚠️ **Duas correções de ordem, ambas medidas (revisão de 2026-09-27):**

1. **A validação (400) roda ANTES do `@HasPermission` (403).** `@HasPermission` é um advice `@Around`
   e roda **depois** da resolução de argumentos — onde o `@Valid` acontece. A ordem documentada no
   planejamento ("permissão 5 → validação 6") **não é implementável**, e um desenho que promete o que
   o framework não entrega ensina o próximo a confiar na ordem errada. O que **não** muda: a
   allowlist (passo 8) continua antes de qualquer SELECT — é ela que mata a enumeração.
2. **`unknown_model` (400) vem DEPOIS do prompt (404/409), não antes.** A ordem anterior exigia 1
   SELECT antes do 1º SELECT. O custo é um `unknown_model` num prompt inexistente devolver 404 — e
   404 não é oráculo de modelo, porque o modelo do tenant não é segredo.

**D16 reescrito com a ordem real:** `401 → 413/400(validação) → 403(permissão) → 429 → 403(allowlist)
→ 404 → 409 → 400(unknown_model) → 422 → 502`. O princípio intacto: **allowlist antes do lookup**
(os passos 7+8 do gate de conexão provam).

---

## 5. Como `ChatTurnGateway` é reusado sem forkar `ChatServiceImpl`

**A extração (D4/D5) — porta e função pura em `agent/api`, adaptador em `agent/impl`.**
`ChatServiceImpl` mantém: sessão, TX1/TX2 e a chamada ao seam (`turnOutcomeMapper.resolve(...)`).
`recordTurn`, os 4 helpers do trace (`maxTokensOf`, `topPOf`, `temperatureOf`, `inferenceParams`) e as
duas linhas de log moram em `TurnOutcomeMapper` — saíram do `ChatServiceImpl` com os ramos a que
pertencem. O critério é
**decisão sem sessão**, e a revisão de 2026-09-27 (D5) moveu o que é função pura para o `api`:
`toProviderMessages` vira **estático em `agent/api/util/ProviderMessages`**, e `isModelNotFound` +
`ModelNotFound` sobem para `agent/api` — que é o que permite ao `LangChain4jTurnGateway` **ser
tocado** (emenda ratificada) sem criar uma segunda cópia da regra. `ProviderResolution`
**não** sobe: `CredentialResolver` está em `agent/impl`, e `agent/api → agent/impl` é ciclo Maven.

```
                    ┌──────────────────────────────┐
                    │   ChatTurnGateway (D3)       │
                    │   LangChain4jTurnGateway     │   ← MESMA instância, sem cópia
                    └──────────────┬───────────────┘
                                   │ TurnRequest / TurnReply
              ┌────────────────────┴────────────────────┐
              │                                         │
    ┌─────────▼──────────┐                  ┌───────────▼────────────┐
    │  ChatServiceImpl   │                  │  PublicChatService     │
    │  (interno, sessão) │                  │  (público, stateless) │
    ├────────────────────┤                  ├────────────────────────┤
    │ sessão / TX1 / TX2 │                  │ valida body            │
    │ recordTurn c/ sess.│                  │ resolve grant (in-token│)
    │ toProviderMessages │                  │   + status do prompt)  │
    │                    │                  │        │               │
    │  ▼ D4              │                  │        ▼               │
    │ ProviderResolution │◄─────────────────┤  ProviderResolution    │
    │ TurnOutcomeMapper  │◄─────────────────┤  TurnOutcomeMapper     │
    └────────────────────┘                  └────────────────────────┘
         /hive/v1/chat                            /hive/v1/public/v1/chat/completions
```

**O que o fluxo público monta no `TurnRequest` — e de onde vem cada campo:**

| `TurnRequest` | Fonte no fluxo público | Enriquece o interno? |
|---|---|---|
| `sessionId` | `null` — ⚠️ **gate de verificação** (`DESIGN-DECISIONS.md` R2): `createClientWithSession` pode enviar header `x-opencode-session: null` em GO/ZEN | não |
| `promptTemplate` | `PromptGrantResolver` — mesma `PromptTemplate` de domínio que `PromptTemplateService` devolve | **idêntico** |
| `provider`, `model` | `tenantLlmConfigService.require(tenantId)` — via `ProviderResolution` | **idêntico** |
| `apiKey`, `baseUrl` | `credentialResolver.resolve(tenantId, provider)` — via `ProviderResolution` | **idêntico** |
| `history` | `List.of()` — o ERP mandou a conversa no request; o `historySize` da trace é `messages.size()` | muda a métrica |
| `conversation` | `toProviderMessages(messages do request)` — **a mesma conversão**, com outra fonte | idêntico |

**`toProviderMessages` é a função a reusar sem mudar.** Ela é `private static` em
`ChatServiceImpl:328-344`, converte `ChatMessage` de domínio no tipo do LangChain4j, mantém
`SystemMessage` e descarta conteúdo nulo. Extraí-la para um collaborator (ou para
`commons`) é 1 extração a mais, e é a que garante que os dois caminhos **conversem com o provider do
mesmo jeito** — a mesma razão pela qual ADR-012 §Decisão 6 diz que `TurnRequest` converte uma vez e
compartilha: *"converter o histórico duas vezes seriam duas chances de discordar sobre o que foi
dito"*.

⚠️ **`ChatTrace.sessionId = null` + D4 (Batch 1B).** Não esconder: a linha de trace da rota pública
carrega `sessionId=null`, `promptTemplateId=<id>`, `tenantId` (do `TenantContext`), `turnId` e
`clientId` — os dois últimos **nascem no Batch 1B**, ~15 linhas, zero migration, nenhum teste
existente quebrado. Sem eles, o `turn_id` que o ERP recebe **não casa com linha de log nenhuma**, e a
finalidade declarada de P-a (atribuir o uso) fica sem prova. Retenção: **14 dias declarados como
premissa** (rotação do ambiente; nada no código impõe). Reversão: tabela (+1 migration + LGPD) se o
log do ambiente não for pesquisável — condição **verificada com o usuário**: é pesquisável.

**Extração resumida (4 itens, ~72 = região medida, ~28 = código que muda, zero mudança de comportamento):**

| # | Collaborator | De onde | Passos |
|---|---|---|---|
| D4.1 | `ProviderResolution` | `ChatServiceImpl:176-186` | 10 |
| D4.2 | `TurnOutcomeMapper` | `ChatServiceImpl:203-246` | 20–50 |
| D5.3 | `toProviderMessages` → **estático em `agent/api/util/ProviderMessages`** | `ChatServiceImpl:328-344` | 4 (já existe uma `ProviderMessages` em `agent/impl/util/` — **verificar se é a mesma função** antes de criar nova) |
| D5.4 | `isModelNotFound` + `ModelNotFound` → `agent/api`; `PromptTemplateService.findByCodeAndVersion` (1 método novo; `findActiveByCode` já resolve `FOLLOW_ACTIVE`) | `LangChain4jTurnGateway`, `agent/api` | **zero query nova, zero aresta impl→impl** |

⚠️ **Regra do `harness-continuous.md` H6 e a lição do ADR-013:** estas 3 extrações são um **batch
próprio, antes da feature**, com suíte + E2E de `systemBlockChars` provando que o prompt montado não
mudou. Se o batch de refactor e o batch da API forem o mesmo, a evidência que sustenta
`ChatServiceImpl` passa a ser só mock — que é exatamente como o `PromptSelector` com `size=100`
contra servidor que corta em 50 passou por 145 testes e quebrou 400 em toda montagem.

---

## 6. Cota em Redis (D5)

**Algoritmo:** sliding-window log, `SORTED SET` por chave, um único `EVAL` de Lua atômico.

```
chave cliente:  hive:public:quota:{clientId}
chave tenant:   hive:public:quota-tenant:{tenantId}

script(ZREMRANGEBYSCORE key 0 cutoff) → (ZCARD key) → se >= limit então {0, retryAfter}
        senão (ZADD key {now} {now}-{rand}) → (EXPIRE key window*2) → {1, remaining}
```

- **Atomicidade:** um `EVAL` (Lua roda atomicamente no servidor). Sem `MULTI`/`EXEC`, sem `WATCH`, sem
  lock distribuído por request. É o que a referência de Redis determina explicitamente: "distributed
  rate limiting does **not** require a distributed lock per request … locking is expensive, atomic
  scripting is not".
- **Membro único com sufixo aleatório:** o bug documentado de sliding-window-log em `SORTED SET` —
  dois requests no mesmo microssegundo geram membros idênticos, e o Redis trata membro duplicado como
  **update**, não como inserção → **subcontagem** e uma requisição a mais passa. O sufixo é o que
  fecha.
- **Memória:** `O(n)` por chave com `n ≤ limit`. Com `limit=60` e TTL, o estado é limitado mesmo com
  muitas credenciais — **ao contrário** do `ConcurrentHashMap` atual, que é ilimitado e não expira.
- **Hash tag** `hive:public:quota:{clientId}` para as duas chaves irem ao mesmo slot em Redis Cluster
  (restrição do cluster para multi-chave em Lua).
- **Teto por tenant:** chave separada, `limit` = o teto configurado. Um cliente não consegue esvaziar o
  orçamento do tenant batendo no seu próprio teto, e vice-versa.
- **Degradação:** Redis indisponível → **fail-open** + `log.error` (Q7). O `ChatRateLimiter` em
  memória não é usado como piso no caminho público (ele é chaveado por `tenantId` e teria o defeito
  de `10N/min`); o fail-open é declarado como custo aceito, porque fail-closed transformaria o Redis
  em dependência dura de todo chat.
- **Rate limit no `/auth/token`:** SEPARADO e mais apertado. Um `client_secret` sendo forjado é brute
  force. Chave `hive:public:quota-token:{clientId}` com limite próprio (proposto 5/min, acima do
  burst de um ERP que reautentica a cada expiração).
  ⚠️ **B8 (revisão): limitar só por `{clientId}` é contornável** — o forjador rotaciona `client_id`
  e ganha bucket novo por tentativa. Mitigações: (1) **teto global** do endpoint
  (`hive:public:quota-token:global`), que nenhum `clientId` furta; (2) teto por **IP** do chamador.
  Bucket por atacante sem teto global é nenhum teto — o global é o que custa ao atacante, o por-IP é
  o que custa ao distribuidor.

**O `ChatRateLimiter` em memória não é tocado.** Continua servindo `/v1/chat` com 10 msg/min por
tenant, exatamente como hoje. Zero regressão, zero mudança de contrato interno. As duas rotas
compartilham a **forma** (`check(key)` sobre uma janela deslizante), não o **estado**.

---

## 7. Envelope de erro — a coexistência (D9)

```
ControllerExceptionHandler  (@RestControllerAdvice, sem qualifier, tem @ExceptionHandler(Exception.class))
   → serve o /v1/** humano. RFC 7807. code = SNAKE_UPPER derivado do nome da classe.

PublicChatController / PublicAuthController  (@ExceptionHandler LOCAL, sem advice global novo)
   → serve o /v1/public/**. OpenAI error object. code = snake_lower, tabela explícita.
```

**Por que local e não `@RestControllerAdvice(basePackages=…)`:** o bean global já é
`@RestControllerAdvice` sem qualifier e já captura `Exception`. Duas peças capturando `Exception`
com `@Order` é precedência de handler — sutil, silenciosa quando erra, e o sintoma é um envelope de
erro errado em produção, que é bug de contrato com o ERP. Handler local é explícito e custa ~40
linhas. **Nenhuma linha de `ControllerExceptionHandler` é alterada.**

---

## 8. Ordem de provisionamento (decisão 10) e o que o ERP recebe

Não há endpoint de auto-serviço **e não há tela** (D6): o provisionamento é um
**`CommandLineRunner`** que chama `PublicApiClientService.provisionar()` e imprime o payload
**uma vez**. O `secret` é **sempre gerado por máquina** — campo de segredo digitado por humano é
proibido (SHA-256 de baixa entropia quebrável offline a partir de um dump).
`ProvisioningPayloadBuilder` é **código**, não template: único produtor de `base_url` +
`prompt_codes` + `limits`.

```
1. Admin cria o prompt template (código `resumo-pedido-venda`, status ACTIVE)
2. Admin roda o runner com: label = "produto X / produção"
                            rate_limit_per_minute = 60
                            max_messages/max_message_chars/max_total_chars = 200/32000/256000
                            pin_mode = FOLLOW_ACTIVE   ← default (D1); PINNED só onde o contrato exigir
                            → grant: PUBLIC_CHAT_COMPLETE
3. O runner IMPRIME, UMA VEZ:
     client_id      = "erp_produto_x"
     client_secret  = "<64 bytes base64url>"   ← nunca mais recuperável
     token_url      = "https://host/hive/v1/public/auth/token"
     base_url       = "https://host/hive/v1/public"
     prompt_codes   = ["resumo-pedido-venda (FOLLOW_ACTIVE)"]
     limits         = {m: 200, c: 32000, t: 256000}
4. O ERP grava client_id/secret no secret store do CI e codifica `base_url`
```

**A resposta do passo 3 é o contrato de provisionamento** e é o que substitui o discovery. O
`secret` só existe naquele output — e por isso o requisito de comparação em tempo constante e o de
nunca logar importam. ⚠️ **IT 10.7:** o segredo impresso pelo runner **autentica** no `/auth/token` —
prova de que o payload impresso é o payload real.

**Gatilho para voltar a ter UI (D6):** 2 credenciais de produção, ou alguém sem acesso ao ambiente
precisar provisionar. Aí o parecer do `ux-designer` é **gate do batch**, não revisão posterior.

---

**Próxima fase:** `PLAN.md` — tasks ordenadas, com o que é pré-requisito de segurança.
