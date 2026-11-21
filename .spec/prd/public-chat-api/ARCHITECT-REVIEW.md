# ARCHITECT-REVIEW — API pública do Assistant

**Data:** 2026-11-21 · **Agente:** `architect` · **Natureza:** parecer + decisões + blueprints. Não autoriza implementação. Brain sincronizado antes da decisão (`brain-context.md`): notas `arquitetura/projetos/hive/decisoes/api-publica-chat`, `estudos/projetos/estudos/hive/public-chat-api-premissa-tenant-2026-11-21`, `arquitetura/projetos/arquitetura/projetos/hive/public-chat-api-plano-2026-11-21`.

## 1. Parecer — barreira de esteira

**Veredito: PROSADA. Caminho escolhido = gerar `PRD.md` com §16 e NÃO gerar `ADRs/**` agora.**

Evidência: `.agents/rules/prd-adr-pipeline.md:9-16` exige `PRD.md` (16 seções, §16 = aprovação humana), `RESEARCH.md`, `ADR-INDEX.md` canônico e só então `ADRs/ADR-00N-{slug}/ADR.md`. Antes deste parecer existiam apenas artefatos de estudo (`EXPLORATION`, `CONSTRAINTS`, `DESIGN-DECISIONS`, `ARCHITECTURE`), um ledger de candidatos (`ADR-INDEX`) e `HANDOFF` — sem `PRD.md`, sem `RESEARCH.md`, sem diretório `ADRs/`.

A exceção de `:43-45` ("arquitetural sem valor de produto novo ⇒ ADR sem PRD") **não se aplica**: limite por tenant e UI admin são valor de produto novo. Registrar exceção seria burlar a esteira. Gerar ADRs antes de §16 aprovado seria pior.

Ação tomada: criado `PRD.md` com as 16 seções (§1, §2, §5, §6, §7, §9 e §16 presentes — PRD-01) e criado `RESEARCH.md`. **§16 está preenchido no formato, mas com status PENDENTE e não é aprovação.** Aprovação é ato humano; este agente não a forja. Trava explícita no §16: nenhum `ADRs/**` nasce até o operador assinar. `ADR-INDEX.md` permanece como ledger de candidatos até a assinatura, quando deve virar índice canônico com as colunas `order`, `id`, `title`, `status`, `depends_on`, `dir`, `branch`, `merge` (`:20-27`). `prd_version` (2 vs legacy) fica decidido no mesmo ato — com `2`, linhas `in-progress`/`merged` exigem `unit: adr` (`:28-39`).

**Confiança: alta** (regra citada literalmente; inventário conferido no disco).

## 2. Decisão — pré-condição springdoc

**Veredito: ADR 1 é ANOTAÇÃO + UPGRADE DE DEPENDÊNCIA. Versão alvo `3.2.0`, não `3.2.1`, não `2.8.14`.**

Evidência (deepsearch, 2026-11-21):
- `https://springdoc.org/faq.html` — matriz: `4.1.x → 3.1.x - 3.2.x`; `4.2.x → 3.2.1`; "springdoc-openapi 3.x is compatible with spring-boot 4".
- `https://github.com/springdoc/springdoc-openapi/releases` — `v3.2.0` = "Spring Boot 4.1.x line (built on Spring Boot 4.1.2)", swagger-ui 5.34.0, Scalar 0.6.83; `v3.2.1` = linha 4.2 com swagger-core 3.0, Jackson 3 (`tools.jackson`) e rename de `swagger-*-jakarta` → `swagger-*`.
- `https://springdoc.org/` — coordenada `org.springdoc:springdoc-openapi-starter-webmvc-scalar` existe na linha 3.x.
- Repo: `backend/dependencies/pom.xml:33,126-128` declara `2.8.14` (linha 2.x, fora da matriz Boot 4).

`2.8.14` com Boot `4.1.0` não é "provavelmente problemático": é fora da matriz declarada. Anotar controllers antes de validar a geração produziria spec vazia/incorreta e exigiria retrabalho. `3.2.1` roda em 4.1 mas importa quebra de Jackson 3 e rename de artefatos sem necessidade para este PRD — risco desproporcional.

**Gate de validação (primeira tarefa do ADR 1, antes de qualquer `@Operation`):**
`GET /v3/api-docs` → 200; `paths` conter `/v1/public/auth/token` e `/v1/public/v1/chat/completions`; `components.schemas` não vazio e conter `ChatCompletionRequestDTO`, `ChatCompletionResponse`, `OpenAiError`. Falhou com `3.2.0` → avaliar `3.1.1` (swagger-core 2.2.55, mesma linha 4.1) e revalidar; **não** refundar o contrato. Verificar também `/scalar/**` e `/v3/**` após eventual restrição na chain humana (hoje `permitAll`, `SecurityConfig.java:77-87` citado no estudo).

**Confiança: alta** na matriz e nas notas de release; **média** na afirmação de que `3.2.0` roda limpo em `4.1.0` exatamente (built em `4.1.2`) — é justamente o que o gate prova.

## 3. Validação das 4 decisões e dos 5 ADRs candidatos

| # | Veredito | Correção / refinamento obrigatório | Confiança |
|---|---|---|---|
| Decisão 1 — contrato | **VALIDADA** | Manter B. Response DTOs em `publicapi/api/dto`; remover duplicata em `web/internal/v1`; `@Schema` explícito em parâmetros aceitos-e-descartados; extensões `prompt_code`/`metadata.hive` **não** como campos OpenAI canônicos; handlers locais, sem advice global. D1 (`model` ausente → default) e C4 (`role:system` → 400) permanecem como extensões documentadas, não "correções" para compatibilidade estrita. | Alta |
| Decisão 2 — centralização | **VALIDADA** | A agora: função pura em `agent/api` para conversão com descarte de conteúdo nulo e precedência do `SystemMessage`; `TurnOutcomeMapper` recebe `turnId`/`clientId` explícitos (K6). B vai para ADR 5 (não reabrir ADR-012/021 por acidente). C fora com riscos A8/A11/A12/A15 registrados. | Alta (K11 estrutural); média em C (sem deepsearch de LangChain4j × Boot 4.1) |
| Decisão 3 — credenciais | **VALIDADA COM CORREÇÃO** | A. **Corrigir `DESIGN-DECISIONS.md:53`**: a UI **não** deve definir `rate_limit_per_minute` por client — isso contradiz `:55`, C8 (`CONSTRAINTS.md:35`) e a premissa do operador. Campos da UI: label/tenant (somente leitura), grants, `prompt_code` + `pin_mode`/`pinned_version`, `token_ttl_seconds`, `max_messages`/`max_message_chars`/`max_total_chars`, rotação e revogação. `client_id` globalmente único (`V1:28`), tenant da linha, `UNIQUE(tenant_id,label)` (`V1:29`) — UI não troca tenant de client existente. Basic preferido; corpo legado mantido por compatibilidade. | Alta |
| Decisão 4 — cota | **VALIDADA COM CORREÇÃO** | A. `checkTenant` substitui `checkClient` no chat; token endpoint mantém 30/120/30 (`PublicAuthControllerV1.java:81`) com constantes por perfil; coluna antiga obsoleta sem drop; headers `X-RateLimit-*`; fail-open preservado. **Corrigir `ARCHITECTURE.md:88`**: "limite vem da credencial" está obsoleto — a origem é a tabela de tenant em `publicapi_db`. B rejeitada (um contador, operador decidiu), C fora (crédito). | Alta |
| ADR 1 | **VALIDADO** | Inclui upgrade springdoc + gate; ver §2. | Alta |
| ADR 2 | **VALIDADO** | Depende de 1; evidência de caracterização obrigatória. | Alta |
| ADR 3 | **VALIDADO** | Depende de 1; ver correção da Decisão 3. | Alta |
| ADR 4 | **VALIDADO COM CORREÇÃO DE DEPENDÊNCIA** | `depends_on` muda de **3 para 1**. Limit source = migration em `publicapi_db`, não UI admin; ADR 3 e ADR 4 são paralelos após 1. Isso reduz acoplamento e respeita KISS. | Alta (cadeia de dependência é inferência de arquitetura — média-alta) |
| ADR 5 | **VALIDADO** | Depende de 2, estrutural, adiável; único que muda grafo (`publicapi/impl → agent/impl`, K3). | Alta |

Teto de 5 ADRs mantido. Nenhum ADR foi criado nesta etapa.

## 4. Decisões sobre os dois pontos em aberto do ADR 4

### 4a. Staleness do claim de limite

**Correção factual prévia:** não existe `plim.r` hoje. `PublicClientTokenIssuer.java:111-114` monta `plim = {m, c, t}` (max messages / chars / total chars); `withClaim("plim", plim)` em `:129`. A chave `r` é **nova**, criada no ADR 4/3.

**Decisão: manter snapshot no JWT (limite lido na emissão), NÃO ler a tabela por turno.**

- `plim.r` = inteiro com o `chat_requests_per_minute` do tenant no instante da emissão; `PublicChatServiceImpl` troca `checkClient(clientId, 60)` (`:135`) por `checkTenant(tenantId, plim.r)`.
- TTL do token público: default 600 s, teto 3600 s (`token_ttl_seconds`, `V1:17` e `CHECK BETWEEN 60 AND 3600`, `V1:31`). Staleness máxima = TTL do token.
- Propagação de mudança de limite: reemitir token após a alteração; para mudanças urgentes, revogar clients do tenant (`revoked_at` + blacklist JTI já existem) — documentar isso na UI admin como efeito da alteração.
- Rejeitada leitura por turno: adiciona uma ida ao DB em cada requisição de chat, cria novo modo de falha num caminho que hoje é stateless e depende de `TenantContext` (`ScopedValue`, não propaga para thread pool — T4), e mudaria o contrato de resiliência sem necessidade. Se latência/freshness virar requisito real, abrir ADR próprio (fora do teto atual).
- Evidência exigida no ADR 4: token emitido antes da mudança mantém limite antigo até expirar; token emitido depois usa o novo; dois clients do mesmo tenant competem pelo mesmo contador.

**Confiança: alta** para o mecanismo (código citado); **média** para a proposta de chave `r` como convenção (não existe ainda).

### 4b. Migration V2 em `publicapi_db`

**Decisão: tabela específica de cota de chat, coluna nominalmente explícita, backfill de tenants existentes.**

Proposta (blueprint em §5.4 — **não é migration escrita nesta etapa**):

- Tabela `public_api_tenant_quota`, chave primária `tenant_id UUID`; coluna `chat_requests_per_minute INT NOT NULL DEFAULT 100`, auditoria `create_at`/`update_at`.
- `CHECK (chat_requests_per_minute BETWEEN 1 AND 10000)` com nome de constraint explícito (`chk_public_api_tenant_quota_chat_rpm`). Limite superior evita valor absurdo por engano; inferior preserva a intenção do `>= 1` da coluna antiga (`V1:30`).
- **Nominalidade**: `chat_requests_per_minute`, não `rate_limit_per_minute`, `limit`, `quota` ou `max_requests`. Nome genérico travaria a adição futura de crédito/TPM no mesmo modelo — este PRD separa RPM de crédito (C10); nome explícito deixa espaço para `credit_balance`/`token_per_minute` em ADR futuro.
- Backfill: `INSERT ... SELECT DISTINCT tenant_id, 100 FROM public_api_client` com `ON CONFLICT (tenant_id) DO NOTHING`. Tenants sem client não recebem linha — não podem chamar a API de qualquer forma; o código aplica default 100 quando a linha está ausente (sem exceção, sem `null`).
- Coluna antiga `public_api_client.rate_limit_per_minute` permanece (`DEFAULT 60`, `V1:16`) apenas para compatibilidade de dados; **fora da UI admin e fora do caminho de chat**. Drop exige ADR de limpeza.
- Código: `checkTenant` em `PublicChatServiceImpl` no lugar de `checkClient`; `checkClient` continua apenas em `checkTokenEndpoint` (`RedisQuotaGuard.java:123-128`).

**Confiança: alta** no desenho; **média** na numeração V2 (V1 existe no mesmo diretório; contiguamente livre inferido, confirmar no ADR).

## 5. Blueprints

### 5.1 system_overview

Monolito modular Spring Boot 4.1.0 (Java 25 preview), um deploy, dois bancos lógicos e Redis. A API pública é **segundo chamador** da porta `ChatTurnGateway`, nunca segunda implementação (Brain `decisoes/api-publica-chat`). Bounded context `publicapi` compõe `agent` e `security`; grafo inalterado neste PRD. Três fronteiras: (i) token endpoint OAuth com limites próprios 30/120/30; (ii) chat endpoint com cota por tenant; (iii) painel admin na chain humana. Spec OpenAPI gerada, groupada e validada em CI; Scalar permanece. Fail-open do Redis é decisão explícita.

### 5.2 component_diagram

```mermaid
flowchart LR
  ERP[Cliente ERP] -->|Basic + grant_type=client_credentials| TOK[POST /v1/public/auth/token]
  ERP -->|Bearer JWT aud=hive.public| CHAT[POST /v1/public/v1/chat/completions]
  ADM[UI admin /v1/**] -->|chain humana + @HasPermission| ADMAPI[PublicApiAdminService]

  TOK --> ISS[PublicClientTokenIssuer<br/>JWT: tenant-id, permissions, pcode, plock,<br/>plim={m,c,t,r}]
  ISS --> TG[checkTokenEndpoint 30/120/30]
  ISS --> DB[(publicapi_db<br/>public_api_client*<br/>public_api_tenant_quota)]
  TG --> RDS[(Redis)]

  CHAT --> FILTER[PublicApiSecurityFilter<br/>JWT + JTI + revogação]
  FILTER --> SVC[PublicChatServiceImpl]
  SVC --> Q[RedisQuotaGuard.checkTenant<br/>tenantId, plim.r]
  Q --> RDS
  SVC --> CONV[função pura em agent/api<br/>SystemMessage → conversation]
  CONV --> GW[ChatTurnGateway]
  GW --> PROV[(Provider OpenAI-compat)]
  SVC --> MAP[TurnOutcomeMapper<br/>turnId + clientId → ChatTrace]
  SVC --> SPEC[springdoc 3.2.0<br/>GroupedOpenApi public]

  ADMAPI --> DB
```

### 5.3 api_contracts

| Método | Path | Request | Sucesso | Erros | Observações |
|---|---|---|---|---|---|
| POST | `/v1/public/auth/token` | `grant_type=client_credentials`, Basic preferido (body legado aceito); `scope` opcional | 200 `{access_token, token_type:"Bearer", expires_in, scope}` + `Cache-Control: no-store`, `Pragma: no-cache` | 400 `invalid_request`; 401 `invalid_client`; 429 (30/120/30) com `Retry-After` | JWT `aud=hive.public`, claim `tenant-id`, `plim.r` novo; ver RFC 6749 §2.3.1/§5.1 |
| POST | `/v1/public/v1/chat/completions` | `{model?, messages:[{role:"user"\|"assistant",content}], prompt_code, stream?:false, metadata?:{hive:...}}` | 200 `{id, object, created, model, choices:[{index,message:{role:"assistant",content},finish_reason}], usage:{prompt_tokens,completion_tokens,total_tokens}, metadata:{hive:{...}}}` | 400 `unknown_model` / `streaming_not_supported` / `system_message_not_allowed`; 401; 403 `missing_permission`; 404 `prompt_version_not_found`; 429 `rate_limit_exceeded` + `Retry-After` + `X-RateLimit-*` | duplo `/v1` é intencional (SDK OpenAI); `role:system` rejeitado; parâmetros OpenAI ignorados documentados no schema |
| GET | `/v3/api-docs` (group `public`) | — | 200 OpenAPI 3.1 com paths `/v1/public/**` e schemas preenchidos | 401/403 se restrito à chain humana | gate do ADR 1; verificar `/scalar/**` |
| GET/POST/PUT/DELETE | `/v1/admin/public-api-clients` (chain humana) | criar/rotacionar/revogar/grants/pin | 200/201; secret plaintext apenas no create/rotate | 400/401/403/404/409 | nunca listar `secret_hash`; sem campo de RPM por client |

Contrato de erro preservado: `{"error":{"message","type","param","code"}}` com snake_lower; handlers locais no controller, sem advice global novo.

### 5.4 data_model

```sql
-- PROPOSTA (blueprint). Nao executar nesta etapa; destino: publicapi_db, migration V2.
CREATE TABLE public_api_tenant_quota (
    tenant_id                 UUID PRIMARY KEY,
    chat_requests_per_minute  INT  NOT NULL DEFAULT 100,
    create_at                 TIMESTAMP WITHOUT TIME ZONE,
    update_at                 TIMESTAMP WITHOUT TIME ZONE,
    CONSTRAINT chk_public_api_tenant_quota_chat_rpm
        CHECK (chat_requests_per_minute BETWEEN 1 AND 10000)
);

-- Backfill de tenants ja existentes (todo tenant com client); idempotente.
-- INSERT INTO public_api_tenant_quota (tenant_id, chat_requests_per_minute)
-- SELECT DISTINCT tenant_id, 100 FROM public_api_client
-- ON CONFLICT (tenant_id) DO NOTHING;
```

Tabelas existentes mantidas: `public_api_client` (`client_id` único global `:28`; `UNIQUE(tenant_id,label)` `:29`; `rate_limit_per_minute DEFAULT 60` `:16` — obsoleta, sem drop), `public_api_client_grant`, `public_api_client_prompt`. Chave Redis: `hive:public:quota-tenant:{tenantId}` (`RedisQuotaGuard.java:158-160`) — distinta das chaves de client e de token (`:154-171`).

### 5.5 trade_offs

- **Snapshot no JWT vs leitura por turno:** escolhido snapshot — zero I/O extra, preserva stateless e fail-open; custo é staleness ≤ TTL (3600 s), mitigada por reemissão/revogação. Leitura por turno só se freshness virar requisito; novo DB por request é risco maior que o benefício.
- **Upgrade springdoc 3.2.0 vs 3.2.1 vs manter 2.8.14:** 3.2.0 é a linha 4.1 sem quebra de Jackson; 3.2.1 traz Jackson 3/rename; 2.8.14 é fora da matriz. Custo do upgrade é uma tarefa no ADR 1; benefício é spec confiável.
- **DTOs tipados vs `Map` + customizer:** DTOs dão schemas inferidos e testabilidade; custo é remover duplicata e versionar extensões. `Map` economiza trabalho hoje e produz spec opaca — inaceitável para o objetivo do PRD.
- **Centralizar conversão agora vs mover `ProviderResolution`:** agora só a função pura (A); mover muda grafo e merece ADR 5 com evidência própria. Evita o erro histórico de misturar refactor load-bearing com feature.
- **Um contador por tenant vs client+tenant:** operador escolheu um; menos estado, menos complexidade, sem dupla cobrança. `checkClientAndTenant` existe (`RedisQuotaGuard.java:118-121`) mas fica fora.
- **Credencial OAuth vs API key `hk_`:** OAuth já implementado, RFC-conforme, com expiração/escopo/rotação; `hk_` é legado de roadmap.
- **Code-first com spec versionada vs design-first:** OAI recomenda design-first, mas o custo de processo não se justifica para contrato pequeno; spec gerada em CI é a fonte única.

## 6. Confiança e Unknowns

**Alta** — matriz springdoc e notas de release (URL+data citados); fatos de código (`V1__public_api_client.sql:16,28-31`; `RedisQuotaGuard.java:114-121,123-128,154-171`; `PublicClientTokenIssuer.java:111-129`; `PublicChatServiceImpl.java:135`; `PublicAuthControllerV1.java:81`; `dependencies/pom.xml:33,126-128`); regra de esteira (`prd-adr-pipeline.md:9-16,20-27,43-45`).

**Média** — `3.2.0` rodar limpo em Boot `4.1.0` (built em `4.1.2`); padrão visual da UI admin reaproveitando `frontend/src/modules/hive/admin/` (inferência do estudo, não verificada); dependência ADR 4 = 1 (inferência de arquitetura); numeração V2 livre.

**Baixa / a validar** — nada afirmado como fato. Itens abaixo são explicitamente desconhecidos:

1. `3.2.0` gera `components.schemas` não vazio neste projeto com Java 25 preview — provado pelo gate do ADR 1.
2. Restrição de `/v3/**` e `/scalar/**` na chain humana não quebra o viewer — testar no ADR 1.
3. Se tenants sem `public_api_client` precisam de linha explícita na tabela de cota — default 100 no código cobre o caso; confirmar com operador.
4. Faixa superior do `CHECK` (10000) aceitável para o negócio — confirmar com operador.
5. `turnId`/`clientId` chegam disponíveis a `TurnOutcomeMapper` no ponto de chamada (K6) — verificar assinatura no ADR 2.
6. Compatibilidade Boot 4.1.0 × LangChain4j 1.19.0 para Ai Services (C) — fora do PRD; deepsearch próprio se reconsiderado.

## 7. Checklist de qualidade

- [x] Solução não viola `.agents/rules/**`: barreira de esteira respeitada (sem ADRs pré-§16), teto de 5 ADRs, escopo de escrita `.spec/prd/**`, deepsearch com URL+data.
- [x] Testabilidade: gate de spec, ITs de caracterização, teste de concorrência do guard e de tenants distintos, H3 para UI.
- [x] Segurança em nível de design: Basic RFC 6749, chave JWT separada, spec autenticada por default, secret nunca listado, fail-open consciente.
- [x] Caminho de migração: DTOs code-first → design-first no futuro; snapshot → leitura por turno em ADR próprio; coluna antiga preservada até ADR de limpeza; ADR 5 isola mudança de grafo.

## 8. Próximo passo da esteira

1. **Operador:** ler `PRD.md` e assinar §16 (incluindo `prd_version`). Sem isso, nada de `ADRs/**`.
2. Após assinatura: converter `ADR-INDEX.md` em índice canônico. Correções de `DESIGN-DECISIONS.md:53`, `ARCHITECTURE.md:88` e `depends_on` do ADR 4 (3 → 1) já aplicadas em 2026-11-21; replicar nos ADRs 3 e 4 quando forem criados.
3. Abrir branch `adr/public-chat-api/01-contrato-openapi` (padrão `^adr/[a-z0-9-]+/\d{2}-[a-z0-9-]+$`), upgrade springdoc → `3.2.0`, gate `GET /v3/api-docs`, só depois DTOs e anotações.
4. Registrar decisão no Brain (layer `arquitetura` ou `estudos`, scope `projetos`) — feito nesta etapa.
