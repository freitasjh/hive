# PLAN — API pública de chat

- **Fase:** dev-planner 4 (Estime)
- **Data:** 2026-09-27
- **Público:** `developer-engineer`. Nenhuma implementação foi feita.
- **Handoff:** ao fim, `sdd-compiler` (SPEC → PLAN → TASKS) ou execução direta por batches.

---

## 0. Portão de entrada (H2.2) — obrigatório, antes de qualquer task

```bash
docker compose -f docker/docker-compose-vetor.yml up -d
cd backend
mvn clean install -DskipTests
mvn test
mvn verify -pl integration-test -Pintegration -am
```

**Se qualquer um falhar: para.** `.spec/tech-debts/{contexto}/TASKS.md` com cada falha, saneamento
antes desta feature. Não existe "falha pré-existente tolerada" nesta regra.

| # | Task | Critério de pronto |
|---|---|---|
| 0.1 | Rodar os 3 comandos e gravar `SUITE-BASELINE.md` com os counts **e o sha do commit** | Baseline único; os 3 números divergentes (376/76/158 · 505/118/222 · 341/63/177) **removidos** dos artefatos |

⚠️ **Não usar** `mvn clean install -Pintegration` como gate: o perfil `integration` define
`<skip>true</skip>` no surefire, roda **zero** unitários e reporta `BUILD SUCCESS`.
⚠️ **`-am` é obrigatório** no comando de integração — sem ele o Maven consome jar `stale` do `~/.m2`
e "passa" com código antigo.

**Baseline — P6 da revisão (2026-09-27): RE-MEDIR, não adotar.** Os 3 artefatos tinham **3 números
diferentes** e H2.2 exige baseline **inequívoco**. Número de planejamento não é baseline — é pista.

---

## 1. Ordem das tasks — e por que esta ordem

A ordem minimiza retrabalho porque respeita **duas dependencias que não são de código**:

1. **A extração vem antes da feature** (D4). Se a rota pública for escrita contra o
   `ChatServiceImpl` monolítico, ela **força** a cópia — e depois a extração passa a ser um refactor
   em cima de código já duplicado, que é a forma mais cara de fazer as duas coisas.
2. **Os pré-requisitos de segurança (P-a/P-b/P-c) vêm antes de qualquer endpoint.** Um token público
   emitido antes de P-a é um token com 6 permissões hardcoded, e ele **já** alcança
   `provider-credential`. Não existe "só um endpoint de teste": o primeiro token emitido já é o
   breach.

```
BATCH 0  Portão H2.2 (baseline verde) + SUITE-BASELINE.md (counts + sha do commit)
   │
BATCH 1  Extração D4/D5 — o que destrava a API pública sem fork
   │     ⚠️ gate de comportamento: git diff das linhas load-bearing + E2E systemBlockChars
   │
BATCH 1B ChatTrace: +tenantId +turnId +clientId (D4 — atribuição da rota pública)
   │     ~15 linhas, zero migration, nenhum teste existente quebrado
   │
BATCH 2  Gate R2 — verificar createClientWithSession com sessionId=null
   │     (decide: null tolerado, ou sessionId sintético)
   │
BATCH 3  P-a  Grant model persistido  ─┐
BATCH 4  P-b  Token com tenant-id      ├─ os três vão no mesmo batch de DB+security,
BATCH 5  P-c  Ciclo de vida do segredo ┘  porque a migration V1 cria as 3 tabelas de uma vez
   │     ⚠️ NENHUM endpoint público exposto ainda
   │
BATCH 6  Cadeia de segurança pública (D2) — ainda sem controller
   │     (um filter chain sem rota é inerte; a IT prova que /v1/** continua igual)
   │
BATCH 7  Cota em Redis (D5) — ainda sem controller
   │
BATCH 8  POST /public/auth/token
   │     ⚠️ primeiro token emitido: P-a/P-b/P-c já verdes
   │
BATCH 9  POST /public/v1/chat/completions
   │
BATCH 10 Provisionamento via CommandLineRunner (D6 — tela CORTADA) + guia de integração
   │
BATCH 11 Gate de conexão com o ERP  (VALIDATION.md §4)
   │
BATCH 12 H6 — code review, brain_store, workflow-state
```

---

## 2. As tasks

### BATCH 1 — Extração D4 (pré-requisito de arquitetura, **não** de segurança)

> ⚠️ **Este batch não entrega nada visível.** Ele entrega a **possibilidade** de a API pública não ser
> um fork. Se ele for pulado, a decisão D3 (reusar a porta) continua verdadeira e a decisão D4
> (reusar a política) vira mentira.

| # | Task | Arquivos | Critério de pronto |
|---|---|---|---|
| 1.1 | Extrair `ProviderResolution` de `ChatServiceImpl:176-186` | novo `agent/impl/.../ProviderResolution.java`; `ChatServiceImpl` chama | 3 collaborators de 1 método; `chat()` sem `llmConfig`/`vendorCatalog`/`credentialResolver` inline |
| 1.2 | Extrair `TurnOutcomeMapper` de `ChatServiceImpl:203-246` | novo `.../TurnOutcomeMapper.java` | 3 ramos (`EMPTY_RESPONSE`, `FAILED` com evict, `ANSWERED`) preservados byte a byte; `IllegalStateException` do ramo inalcançável preservado |
| 1.3 | `toProviderMessages` → verificar se `agent/impl/util/ProviderMessages` já é a mesma função. Se **não** for, criar como **estático em `agent/api/util/ProviderMessages`** (D5: a função pura mora no `api`, o adaptador no `impl`) | `agent/api/util/ProviderMessages.java`, `ChatServiceImpl` | **Uma** função de conversão, usada pelos dois caminhos. (`ARCHITECTURE.md §5`) |
| 1.4 | `chat()` deixa de ler ambiente | `ChatServiceImpl:123,127` | `TenantContext.getCurrentTenant()` e `SecurityUtils.currentUserId()` ficam **só** no método público `chat(ChatRequest)`, que os passa por parâmetro. O método de 2 params não lê nada ambiente |
| 1.5 | Testes: mover/adicionar unit tests dos 2 collaborators | `agent/impl/src/test/...` | `ProviderResolutionTest` (tenant sem config→422, vendor inválido→400, credencial ausente→422) e `TurnOutcomeMapperTest` (3 status + `isModelNotFound` verdadeiro/falso + evict chamado/não) |
| 1.6 | **Gate de comportamento** | — | `git diff` das linhas load-bearing mostra só *movimentação*; `mvn test` verde; **E2E com `systemBlockChars` igual ao baseline** (precedente: `batch1_costuras_rag.metricas.baseline==refactor==correcoes: True`) |
| 1.7 | **Mover `isModelNotFound` + `ModelNotFound` para `agent/api`** — emenda ratificada (D5). `LangChain4jTurnGateway` **é tocado**: a regra sai do arquivo, o arquivo passa a importá-la | `LangChain4jTurnGateway`, `agent/api` | `ChatServiceImpl:227` continua chamando estaticamente, pela **nova** casa. Sem esta task, ou o gateway fica intacto e D3 vira mentira, ou a regra é **copiada** — que é o que ADR-012 existe para evitar |
| 1.8 | `PromptTemplateService.findByCodeAndVersion` (1 método novo; `findActiveByCode` **já** resolve `FOLLOW_ACTIVE`) | `agent/api/service/PromptTemplateService` | **Zero query nova**: as duas queries já existem em `PromptTemplateRepository`. Zero aresta `impl → impl` |

⚠️ **1.6 não é opcional.** O ADR-013 §Regra 1: "construir a segunda capacidade antes do primeiro loop
estar fechado custa caro mesmo quando a segunda está correta". Este batch **move policy** — se a
evidência for só mock, o próximo bug aparece em produção como o `PromptSelector` apareceu.

---

### BATCH 2 — Gate R2: `sessionId` nulo no gateway

| # | Task | Critério de pronto |
|---|---|---|
| 2.1 | Ler as implementações de `LlmClientFactory.createClientWithSession` (GO, ZEN) e verificar se o `sessionId` nulo chega ao header `x-opencode-session` | Resposta escrita em comentário de código: **tolera null** ou **não tolera** |
| 2.2 | Se não tolera: `TurnRequest.sessionId` recebe um UUID sintético por request (o `turn_id` da resposta), e `ChatTrace.sessionId` fica `null` | Nenhum header com valor `"null"` no wire. Teste unitário que_affirme: com `sessionId` sintético, o header sai preenchido |

⚠️ Não pular. É a **única** forma de o `ChatTurnGateway` poder ser reusado por um fluxo stateless, e
o briefing não lista isso. Se a resposta for "não tolera", **D3 continua correta** (o gateway é
reusado) e muda só o valor do campo.

---

### BATCH 3+4+5 — P-a, P-b, P-c (um batch de dados + segurança)

> As 3 tabelas nascem em **uma** migration (`V1__public_api_client.sql` cria as três). Abrir uma
> migration por tabela seria "uma migration por mudança lógica" só se as mudanças fossem independentes
> — e elas não são: `client_grant` e `client_prompt` têm FK para `client`.

| # | Task | Gate de segurança | Critério de pronto |
|---|---|---|---|
| 3.1 | `publicapi/` módulo Maven: `api` + `impl` + `dbupdate` + `web` (4 poms) + pai | | `agent`, `security`, `identity`, `tenant` **inalterados** |
| 3.2 | `publicapi/dbupdate` — `PublicApiDbUpdateConfig` com `locations`, `table("flyway_schema_history_publicapi")`, `baselineOnMigrate(true)`, **sem `repair()`** (D1) | | Comentário no arquivo explicando **por que** a ausência é deliberada, citando a armadilha do `repair()` |
| 3.3 | `V1__public_api_client.sql` — as 3 tabelas, constraints nomeadas, `tenant_id NOT NULL`, `CHECK`s do par `(pin_mode, pinned_version)` e do par `(previous_secret_hash, previous_secret_expires_at)`, `UNIQUE (client_id)` **global** (D8), colunas de teto `max_messages`/`max_message_chars`/`max_total_chars` com `CHECK` (D2). **Sem `$$`**, nem em comentário | | `mvn clean install -DskipTests` **antes** de subir; conferir no `psql`, nunca no log |
| 3.10 | `PublicApiSecurityFilter`: **teto de corpo** — `Content-Length > 1 MiB` → **413 antes do Jackson**. Teto 1 MiB porque JSON escapa non-ASCII `\uXXXX` (6 bytes/char): 20×8000×6 = 960 KB | | IT-BODY-CAP: corpo grande → 413 **e** `verifyNoInteractions(gateway)` — o provider não é chamado |
| 3.4 | 3 `*Entity extends AbstractEntity`, getters/setters manuais, `@Column` espelhando o DDL | | `tenant_id` com `NOT NULL` no DDL e `nullable` coerente na entidade — sem divergência, porque com `ddl-auto=none` divergência é mentira silenciosa |
| 3.5 | 3 `*Repository extends JpaRepository` | | ⚠️ **A interface importa**: o `@Before` de `TenantAspect` só intercepta `JpaRepository+.*`. Um `CrudRepository` próprio **não** recebe o `@Filter` |
| 3.6 | `PublicApiClientService`: provisionar (gera `clientId` + `secret` aleatório de 256 bits, grava `SHA-256`, devolve o raw **uma vez**), rotacionar (janela de graça), revogar (`revoked_at` + `SADD` no Redis), `label` humano | **P-c** | Segredo nunca em log/`toString`; comparação em **tempo constante**; `secret` não é recuperável (existe um teste que tenta) |
| 3.7 | `PublicClientTokenIssuer` (`publicapi/impl`): lê as **concessões** de `client_grant` e os `prompt_code`/pins/limites de `client_prompt`; monta o JWT com `aud="hive.public"`, `sub=clientId`, `jti`, `tenant-id`, `permissions`, `pcode`, `plock`, **`plim`** (tetos), `exp`, **`typ`** | **P-a + P-b + D2** | ⚠️ **`SecurityTokenServiceImpl` não é modificado**; a **chave é separada** (`api.security.public.token.secret`, D8) com `decodeAndVerify` por parâmetro — `JUnit` dos dois sentidos (token de um domínio não passa no outro). Teste que falha se uma lista literal de permissão aparecer no caminho de emissão |
| 3.8 | Testes: SHA-256 do segredo, comparação constante, rotação com janela, `CHECK` do par de pin (subindo `PINNED` sem versão o banco recusa — teste de integração) | | |
| 3.9 | IT: 3 tabelas entram no `TRUNCATE` do `DatabaseCleaner` **sozinhas** (ele descobre por `pg_tables`); `isAuthenticated` como `client` na chain humana continua **funcionando** | | `LoginIT` continua verde (a paridade de `DEFAULT_PERMISSIONS` não foi tocada) |

**Nenhum endpoint público é exposto neste batch.** É deliberado: os dados e o emissor existem, mas
ninguém pode chamar. Isso mantém reversibilidade (apagar o módulo) e mantém o breach fechado.

---

### BATCH 6 — Cadeia de segurança pública (D2), ainda sem controller

> ⚠️ **Causa do P-d, corrigida (B1/B2):** `SecurityFilter.java:28` é `@Component` →
> **auto-registrado no container para `/*`, fora do `FilterChainProxy`** — continua rodando em
> `/v1/public/**`. E a falha **não** é `loadUserByUsername:51` (inalcançável): é
> `validateTokenRevoke:49` → `decodeAndVerify:167-171` exigindo `aud=hive.web` → `BaseException`
> relançada em `:63` → **500**. Gate que aprova só 403/200 **passa o próprio defeito**.

| # | Task | Critério de pronto |
|---|---|---|
| 6.1 | `SecurityConfig`: `SecurityFilterChain` nova `@Order(1)` para `/v1/public/**`, `permitAll` só em `POST /v1/public/auth/**`; a chain humana `@Order(2)` ganha `.requestMatchers("/v1/public/**").denyAll()` **antes** do `anyRequest()`; **`FilterRegistrationBean<SecurityFilter>` com `setEnabled(false)`** (D7) | ⚠️ Os matchers **não** incluem `/hive` (context-path sai antes do Spring Security). **`SecurityFilter.java` com 0 linhas alteradas** — o duplo registro é corrigido por 1 `@Bean`. `shouldNotFilter` **rejeitado**: criaria 2º lugar com a lista de paths |
| 6.2 | `PublicApiSecurityFilter`: assinatura + `iss` + `aud=hive.public` + `exp`; `jti` blacklist; `client` revogado; `TenantContext.runWithTenant(tenant-id)` como **Callable**; **finally** limpando `SecurityContextHolder` **e** `TenantContext`; authorities = claim `permissions` | ⚠️ **Não** chama `loadUserByUsername`. `entryPoint`/`accessDeniedHandler` **próprios** (o existente responde problem+json — 3º envelope). ScopedValue não propaga pra thread pool |
| 6.3 | `TokenVerifier` em `security/api` se (e só se) reaproveitar `decodeAndVerify`; `SecurityTokenServiceImpl` continua intocado | Default é **não mexer** |
| 6.4 | **IT de superfície:** token público `GET /v1/prompt-templates` → 401; `GET /v1/provider-credential` → 401; `POST /v1/chat` → 401; `GET /v1/tenant/llm-config` → 401; token **humano** `POST /v1/public/v1/chat/completions` → 401 | **Nunca 200. Nunca 500** — 500 é o defeito passando, não a negação |
| 6.4.1 | **IT de caracterização do 500 atual** (antes da 6.1): token público em rota humana → **500**, Token público em rota pública → **500**, Token humano em rota pública → **500** | Documenta o defeito em teste que **falha** quando 6.1/6.2 entram. Gate que só aprova 200/403 aprova o bug |
| 6.5 | IT: token com `aud=hive.web` rejeitado na chain pública, e vice-versa (D7) | |
| 6.6 | **IT de sentinel** + **teste estático**: corpo que falha **antes** do handler (ex.: body não-MVC no chat público) devolve o **envelope do contrato**, não problem+json; e varre `backend/` afirmando que **nenhuma classe `@Component` implementa `jakarta.servlet.Filter`** | Fecha B10. O sentinel em `log.warn` foi **rejeitado** pelo security: instrumentação em prod é cheiro que o próximo copia |
| 6.7 | IT de 2 requests encadeados na mesma thread: o 2º **não** herda tenant/authentication do 1º | Prova o `finally` da 6.2 |

### BATCH 7 — Cota em Redis (D5), ainda sem controller

| # | Task | Critério de pronto |
|---|---|---|
| 7.1 | `RedisQuotaGuard` em `publicapi/impl`: sliding-window log em `SORTED SET`, **um** `EVAL` de Lua com `ZREMRANGEBYSCORE`/`ZCARD`/`ZADD`/`EXPIRE`; membro com **sufixo aleatório** (fecha o bug de membro duplicado → subcontagem) | |
| 7.2 | Hash tag `{clientId}` nas chaves (restrição de Redis Cluster para multi-chave em Lua) | |
| 7.3 | 2 cotas: `rate_limit_per_minute` da credencial, e teto por tenant em chave separada | Um cliente não esvazia o orçamento do tenant |
| 7.4 | Limite separado e mais apertado no `/auth/token` (brute force de `client_secret`): por `{clientId}` **+ teto global** do endpoint **+ teto por IP** | ⚠️ **B8:** só por `{clientId}` é contornável — o forjador rotaciona `client_id` e ganha bucket novo. Bucket por atacante sem teto global é nenhum teto |
| 7.5 | `Retry-After` no 429 | O SDK OpenAI respeita; o ERP pode seguir |
| 7.6 | Degradação: Redis fora → **fail-open** + `log.error` | Q7. Registrado como custo aceito |
| 7.7 | Testes: janela deslizante real, limite exato, 2 clientes independentes, teto do tenant, `Retry-After`, fail-open | |
| 7.8 | ⚠️ `ChatRateLimiter` / `SlidingWindowRateLimiter` **não são tocados** | `ChatRateLimiterTest` continua verde sem edição |

---

### BATCH 8 — `POST /hive/v1/public/auth/token`

| # | Task | Critério de pronto |
|---|---|---|
| 8.1 | DTO + validação (`grant_type` obrigatório e `= client_credentials`; `scope` ignorado e intersectado) | |
| 8.2 | Parser de `client_secret_basic` (preferido) e `client_secret_post` | RFC 6749 §2.3.1 |
| 8.3 | `@ExceptionHandler` **local** (D9) — `invalid_client` 401, `invalid_grant` 400, `invalid_scope` 400. **Mensagem genérica** em credencial inválida: "OAuth 2.0 *não* deve revelar qual dos dois está errado" | Não vaza se o `clientId` existe. ⚠️ Envelope de **401/403 da chain** pública é o OpenAI envelope (entry point próprio, 6.2), **não** problem+json |
| 8.8 | **Catch-all** no handler local: `HttpMessageNotReadable`, `MethodArgumentNotValid`, 405, `DataIntegrityViolation` → envelope do contrato, corpo **sem** causa interna | B10. Sem ele, `application/problem+json` e `BaseException.getDetailMessage()` vazam na rota pública |
| 8.4 | `Cache-Control: no-store`, `Pragma: no-cache` no 200 | **Obrigatório** por RFC 6749 §5.1 |
| 8.5 | Verificação de `revoked_at` e `expires_at` do cliente | |
| 8.6 | IT: grant_type errado→400; segredo errado→401; cliente revogado→401; secret anterior dentro da janela→**200**; secret anterior fora da janela→401; `scope` não concedido→`invalid_scope`; `Cache-Control: no-store` presente | |
| 8.7 | ⚠️ **Nenhum log contém o `client_secret`** (nem o presented, nem o gerado) | grep no log de teste |

---

### BATCH 9 — `POST /hive/v1/public/v1/chat/completions`

| # | Task | Critério de pronto |
|---|---|---|
| 9.1 | DTO do request com `FAIL_ON_UNKNOWN_PROPERTIES` ligado **só** aqui; `@Valid` + regras de `messages` (**tetos do claim `plim`**: 1..`max_messages` default 200, `content` 1..`max_message_chars` default 32000, soma ≤ `max_total_chars`); rejeição de `system`; `stream:true`→400; taxonomia de campos (9 rejeitados / 11 ignorados / 3 especiais) | D2+D3. **Nunca truncamento**: acima → 400/413 |
| 9.2 | `@HasPermission("PUBLIC_CHAT_COMPLETE")` no método | A permissão vem do claim, que vem da linha de banco (P-a) |
| 9.3 | `PublicChatService`: ordem de execução de `ARCHITECTURE.md §4.4`, sem pular passo | ⚠️ **passo 9 (403 allowlist) antes do passo 11 (lookup)** — é o que mata a enumeração |
| 9.4 | `PromptGrantResolver`: `FOLLOW_ACTIVE` → `findByCodeAndTenantIdAndStatus(ACTIVE)`; `PINNED` → `findByTenantIdAndCodeAndVersion`. Usar os **queries que já existem** no `PromptTemplateRepository` | Nenhum query novo necessário. ⚠️ **Rodar dentro do `runWithTenant` do passo 4 de `ARCHITECTURE.md §4.4`**: a 1.8 criou `PromptTemplateService.findByCodeAndVersion`, que lê `TenantContext`, e o `TenantAspect` liga o `@Filter` lendo `TenantContext` — não parâmetro. Fora do escopo, a query volta vazia e um prompt existente vira 404 |
| 9.5 | Monta o `TurnRequest` reusando a `PromptTemplate` de domínio; `history = List.of()`, `conversation` pelo `ProviderMessages` estático do batch 1 | `ChatTurnGateway` **reusada**, sem cópia. `LangChain4jTurnGateway` **intacta exceto `isModelNotFound`** (emenda ratificada, task 1.7). ⚠️ A chamada ao gateway também roda **dentro** do `runWithTenant` do passo 4: `LangChain4jTurnGateway` encadeia colaboradores que passam pelo `@Filter` (`credential`, histórico), e o filtro liga por `TenantContext` |
| 9.6 | `TenantQuotaGuard.check(clientId)` antes do prompt | D16 |
| 9.7 | Resposta OpenAI + `prompt_code` no topo + `metadata.hive{}` com os 8 campos | `prompt_fingerprint` = sha256 do texto efetivo |
| 9.8 | `@ExceptionHandler` **local**: os 4 códigos de prompt + `unknown_model` + validações + **catch-all** (8.8) | Envelope OpenAI. **Nunca** `application/problem+json`; nunca causa interna no corpo |
| 9.9 | `recordTurn` com `sessionId = null` e `turn_id` no log; **um** `turn_id` por request | A linha de trace carrega `clientId` para ligar ao ERP |
| 9.10 | Testes unitários do `PublicChatService` (12 caminhos: 4 erros de prompt, 3 de status do gateway, allowlist, cota, validação, `model`, `system`, `stream`) | |
| 9.11 | IT do endpoint inteiro: os 4 erros de prompt com status distintos; `prompt_not_in_allowlist` **para code inexistente** (a prova de anti-enumeração); cross-tenant (credencial A não alcança prompt do tenant B); 401 sem token; 403 sem concessão | |
| 9.12 | IT de `prompt_not_active` **real**: seed de um template `DRAFT` na allowlist | Um teste que mocka o repository não prova o `CHECK` de 1 ACTIVE por code |
| 9.13 | IT de `prompt_version_not_found` **real**: `PINNED` numa versão, `PUT` no template (que incrementa), nova chamada → 409 | Este teste **é** a prova de D13. Sem ele, o achado de versionamento fica opinativo |
| 9.14 | **IT-MAXTOKENS-CLAMP**: `max_tokens` do provider acima do teto suportado → clampeado | O `LangChain4jTurnGateway:76-78` monta o client, logo o teto é nosso. Sem clamp, retry × payload grande = 3 chamadas de provider |
| 9.15 | **IT-BODY-CAP**: corpo > 1 MiB → **413**, com `verifyNoInteractions(gateway)` | Prova que o provider **não** é chamado. 1 MiB porque `\uXXXX` = 6 bytes/char |
| 9.16 | IT-QUOTA-ANTES-ALLOWLIST: estourar cota com `prompt_code` **fora** da allowlist → **429**, não 403 | Sem ela, o 403 antes da cota vazaria o bucket do atacante de graça |
| 9.17 | IT trace-atribuição: resposta `turn_id` == linha de log com `clientId`/`tenantId`/`turnId` | D4. Sem o Batch 1B, o `turn_id` do ERP não casa com linha nenhuma |
| 9.18 | IT de `system` **falsificável**: `role:"system"` → 400 **e** sem `metadata.hive` **e** sem turno registrado | D3. A ausência é a prova; a IT anterior (override declarado) era in-falsificável |

---

### BATCH 10 — Provisionamento (D6: tela CORTADA, virou CLI)

| # | Task | Critério de pronto |
|---|---|---|
| 10.5 | `CommandLineRunner` que chama `PublicApiClientService.provisionar()` e **imprime o payload 1 vez**; `ProvisioningPayloadBuilder` como **código** (único produtor de `base_url` + `prompt_codes` + `limits`); `pin_mode` **sempre explícito** (default `FOLLOW_ACTIVE`, D1); `secret` **sempre gerado por máquina** | Nenhum campo de segredo digitável. ⚠️ `ux-designer` **não** exigido (superfície headless); gatilho para voltar: 2 credenciais de produção, ou provisionamento por alguém sem acesso ao ambiente |
| 10.6 | `docs/product/PUBLIC_API_CHAT_INTEGRATION.md` — guia de integração (SDK OpenAI, `base_url`, cache do token até `expires_in - 60s`, os 4+ códigos de erro, taxonomia de campos). **3–4 h — estava sem orçamento** | Sem o guia, o erro de integração nº 1 (reautenticar a cada chamada → 429 no `/auth/token`) vira ticket |
| 10.7 | IT: o `secret` impresso pelo runner **autentica** no `/auth/token` | Prova de que o payload impresso é o payload real |

---

### BATCH 11 — Gate de conexão com o ERP

Ver `VALIDATION.md §4`. **Este é o H3 desta feature** (o H3 de UI não a cobre: o consumidor é externo
ao browser).

---

### BATCH 12 — H6

`fullstack-code-reviewer` após a última mudança · zero 🔴 · 🟡 avaliados · `brain_store` das lições ·
`workflow-state.json` com `code_review_status: passed` e `harness_status: passed` · processos
`:8080`/`:5173` encerrados.

---

## 3. Estimativa

| Batch | tasks | Esforço | Risco |
|---|---|---|---|
| 0 — portão + baseline | 1 | 20–40 min | — |
| 1 — extração D4/D5 | 8 | 5–7 h | 🟡 **médio** — muda `ChatServiceImpl` |
| 1B — trace (`ChatTrace`) | 5 | 1 h | 🟢 |
| 2 — gate R2 | 2 | 30 min | 🟢 |
| 3–5 — P-a/P-b/P-c | 10 | 10–14 h | 🔴 **alto** — é o núcleo de segurança |
| 6 — chain pública | 8 | 5–6 h | 🔴 **alto** — mexe no `SecurityConfig` |
| 7 — cota Redis | 8 | 3–4 h | 🟡 médio |
| 8 — endpoint de token | 8 | 2–3 h | 🟡 médio |
| 9 — endpoint de chat | 18 | 8–10 h | 🔴 **alto** |
| 10 — provisionamento CLI + guia | 3 | 4–6 h | 🟢 |
| 11 — gate do ERP | — | 1–2 h | 🟡 |
| 12 — H6 | — | 1 h | — |
| correções doc. (D1–D8 aplicadas) | — | 2 h | 🟢 feito |
| **Total** | **~75** | **~52–66 h** | |

**A estimativa anterior (32–44 h) foi refeita pelo architect após a revisão:** +correções
documentais, +`ModelNotFound` movido, +`FilterRegistrationBean` e 3 ITs de cadeia, +IT de
cota-vs-allowlist e claim `plim`; −`TurnContext` (não existe). SEC-01..SEC-24 são IDs **estáveis** e
não colidem com a numeração N.M do PLAN.

**Leitura honesta:** ~1.5 a 2 semanas de uma pessoa. O que **domina** não é o código — são os
batches 1, 3–5, 6 e 9, que juntos são **~20 h de risco alto** em código de segurança e no único
serviço cuja extração pode mudar comportamento silenciosamente.

**Sequenciamento abaixo não é negociável:**
- O batch 1 **antes** do 9 (senão fork).
- Os batches 3–5 **antes** do 8 (senão o primeiro token é um superusuário).
- O batch 6 **antes** do 9 (senão o token público alcança `provider-credential`).

**O que dá para cortar, se o prazo apertar** — nesta ordem, e com o custo declarado:

| Cortar | Custo |
|---|---|
| — (10.1 tela **já foi cortada** por decisão D6; o runner é o caminho) | ✅ Decidido. Registrar que o gatilho de volta é 2 credenciais de produção |
| 1.3 (compartilhar `toProviderMessages`) | 🟡 **Não recomendado** — reintroduz a divergência de prompt entre os caminhos, que é exatamente o que o ADR-012 §6 proíbe |
| `prompt_fingerprint` | 🟢 Baixo. Custa 5 linhas; perde a detecção de mudança de conteúdo sem mudança de versão |
| 6.5 (IT de `aud` cruzado) | 🟡 **Não recomendado** — é o teste que prova o D7 |

**O que não dá para cortar:** 1.6 (gate de comportamento), 6.4 (IT de superfície), 9.11 (IT de
anti-enumeração), 9.13 (IT de versionamento). São os três que fecham os três achados que motivaram
este replanejamento.

---

## 4. Ordem de dependência entre as features (o que serve ao cliente agente, fase 2)

O cliente agente é um **segundo chamador** da mesma porta. O que já fica pronto para ele:

| O que o cliente agente precisa | Onde nasce |
|---|---|
| Segunda implementação de `ChatTurnGateway` | **NÃO será preciso** — `DESIGN-DECISIONS.md §2` |
| Grant model por identidade | `public_api_client_grant` — mesma forma, outra permissão |
| Cota por identidade | `RedisQuotaGuard` — mesma chave, outro `clientId` |
| Envelope de erro | o `@ExceptionHandler` local já é parametrizável |
| Sessão persistente / Tools / memória | **não** — gatilho do ADR-013, batch próprio |

⚠️ **Contrapartida honesta:** `public_api_client` é nomeado para a API pública. O cliente agente vai
crescer dentro de `publicapi` e o nome vai ficar estreito. **Não renomear agora** — renomear é
migração de dado e de API por antecipação, e o ADR-012 já registrou o custo de manter um nome que
mente (`provider_credential` no módulo `agent`). Registrar o nome como dívida nomeada é mais barato
que corrigir cedo e errado.

---

## 5. Rastreabilidade decisão → task

| Decisão | Batch | Tasks |
|---|---|---|
| D1 módulo `publicapi` + Flyway sem `repair()` | 3 | 3.1, 3.2, 3.3 |
| D2 chain de segurança separada | 6 | 6.1, 6.2, **6.4** |
| D3 reusar o gateway (não 2ª impl) | 9 | 9.5 (+ 2.1, 2.2) |
| D4 extrair `ProviderResolution`+`TurnOutcomeMapper` | 1 | 1.1, 1.2, 1.3, 1.4, **1.6** |
| D4b `ChatTrace` +`tenantId`/`turnId`/`clientId` (metadados em log) | 1B | **1B.1–1B.5**, 9.17 |
| D5 política do turno em `agent/api`; `isModelNotFound` sobe; gateway **tocado** | 1 | 1.3, **1.7**, 1.8, 9.5 |
| D13b default `pin_mode` = `FOLLOW_ACTIVE`; provisionamento sempre explícito | 3, 9 | 3.3, **9.4**, **9.13**, 10.5 + `RUNBOOK-prompt-edition.md` |
| D2b teto = dado da credencial (`plim`); recusa, nunca truncamento | 3, 9 | 3.3, **3.7**, **3.10**, 9.1, **9.14**, **9.15** |
| D3b `system` → 400 + IT falsificável + guia de integração | 9, 10 | 9.1, **9.18**, **10.6** |
| D6b provisionamento por runner; `ux-designer` não exigido | 10 | **10.5**, 10.6, **10.7** |
| D7b `FilterRegistrationBean`; `SecurityFilter.java` intacto; catch-all | 6, 8 | **6.1**, 6.2, **6.4.1**, **6.6**, **6.7**, 8.3, 9.8 |
| D8b exceções de segurança registradas; chave separada; springdoc/TLS/perfil | 3, 6 | 3.7 (chave), **SEC-01..24**, 6.6 (estático) |
| P6 baseline re-medido | 0 | **0.1** |
| D5 cota em Redis | 7 | 7.1–7.8 |
| D6 SHA-256 no storage | 3 | 3.6, 3.8 |
| D7 `aud` distinto | 6 | 6.2, 6.5 |
| D8 `client_id` global | 3 | 3.3 |
| D9 envelope local | 8, 9 | 8.3, 9.8 |
| D10 rejeitar `system` | 9 | 9.1 |
| D11 `unknown_model` | 9 | 9.1, 9.10 |
| D12 `stream` explícito | 9 | 9.1 |
| D13 `PINNED` = tripwire | 3, 9 | 3.3 (CHECK), 9.4, **9.13** |
| D14 versão por path | — | contrato (D9 `ARCHITECTURE.md §3.1`) |
| D15 sem idempotency | — | dívida |
| D16 ordem 403→404→409 | 9 | 9.3, 9.6, **9.11** |
| D17 TTL 10 min | 3 | 3.3 (`CHECK` de faixa) |
| D18 hot path sem banco | 6 | 6.2, `ARCHITECTURE.md §2.4` |

**Cobertura: 18/18.** Nenhuma decisão sem task. 3 tasks marcadas em negrito são as que provam os
achados.

---

**Próxima fase:** `VALIDATION.md` — consistência, riscos remanescentes, e o gate de conexão com o ERP.
