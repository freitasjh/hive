# ADR-004 — Cota por tenant (100 req/min compartilhado) e bugfix do 429 no token

**Status:** in-progress · **Data:** 2026-11-22 · **PRD:** `public-chat-api` · **Branch:** `adr/public-chat-api/04-cota-tenant`

**depende-de:** ADR-001 (merged — contrato público tipado e spec OpenAPI). ADR-003 (merged) trata credenciais/admin; sem acoplamento com esta ADR.

## Contexto

O operador fixou em 2026-11-21: limite de chat é **por tenant compartilhado** — um contador por tenant, com os clients do mesmo tenant competindo pelo mesmo budget, default **100 requisições/minuto**, origem em `publicapi_db`. Hoje não existe limite por tenant: `PublicChatServiceImpl` usa `checkClient(clientId, 60)` fixo, embora `RedisQuotaGuard.checkTenant` e `tenantKey` já estejam implementados sem chamador de produção. A coluna `public_api_client.rate_limit_per_minute` (default 60) ficou obsoleta para o chat desde a decisão.

A ADR-001 (Decisão 7) registrou que o `429` com `Retry-After` no token endpoint **não existe hoje**: `PublicAuthControllerV1:156-163` (handler genérico) transforma `QuotaExceededException` em `400 invalid_request`. O chat já tem handler dedicado (`PublicChatControllerV1:93-98`, 429 + `Retry-After`). **Superscrição explícita:** a Interface congelada da ADR-001 registrava o 400 como *comportamento real observado*, não como decisão de mantê-lo; a Decisão 5 desta ADR é a mudança planejada e destinada a este ADR desde 2026-11-21. O `VERIFY.md` da ADR-001 continua válido — descrevia o estado naquele commit. A ADR-001 **não é editada**.

Verificações de 2026-11-22 que ajustam a decisão:

- **V2 é a próxima livre.** `publicapi_db/` contém só `V1__public_api_client.sql`. `PublicApiDbUpdateConfig` usa `locations("classpath:/db/migration/publicapi_db")`, tabela `flyway_schema_history_publicapi`, `baselineOnMigrate(true)`, `baselineVersion("0")` e **deliberadamente sem `repair()`** — V2 aplica sem realinhamento de checksum.
- **Leitura da cota não cria aresta.** O repositório `PublicApiTenantQuotaRepository` e sua entidade ficam em `publicapi/impl` (ao lado dos repositórios existentes), no mesmo datasource de `public_api_client`. `PublicClientTokenIssuer` já está nesse módulo; nenhuma dependência nova de módulo.
- **`plim` é aditivo.** `PublicApiSecurityFilter:87-88` apenas copia o claim para `publicapi.plim`; `PublicChatServiceImpl:120-122` lê `m`/`c`/`t` por chave via `limit(map, key, default)`. Incluir `r` não quebra os chamadores atuais.
- **O guard não expõe métricas hoje.** O Lua (`RedisQuotaGuard.java:33-57`) já devolve `{status, retryAfter|remaining}`, mas `acquire` (`:130-152`) é `void` e descarta o segundo valor (exceto para montar a exceção). Para os headers é necessário um método que **devolva** a decisão — sem mudar o algoritmo ZSET/Lua/sufixo/fail-open.

Crédito/budget consumível fica fora do PRD, registrado como débito. Nomes de coluna devem deixar espaço para `credit_balance`/`token_per_minute` no futuro.

## Decisão

1. **Migration V2 em `publicapi_db`.** Nova tabela `public_api_tenant_quota` com `tenant_id UUID` como chave primária, `chat_requests_per_minute INT NOT NULL DEFAULT 100`, colunas de auditoria e `CHECK (chat_requests_per_minute BETWEEN 1 AND 10000)`. Backfill: uma linha por `tenant_id` distinto em `public_api_client`, com valor 100.
2. **Snapshot no JWT.** Entity + repositório de cota em `publicapi/impl` (sem nova aresta de módulo); `PublicClientTokenIssuer` injeta o repositório e grava `plim.r` com o `chat_requests_per_minute` do tenant no instante da emissão. Tenant sem linha → fallback **100**, nunca erro de emissão. `m`/`c`/`t` inalterados. Staleness aceita: mudança de limite só vale para tokens novos; urgência é revogar os clients do tenant.
3. **Guard no caminho de chat.** `PublicChatServiceImpl` troca `checkClient(clientId, 60)` por `checkTenant(tenantId, limit(plim, "r", 100))`. Algoritmo ZSET + Lua atômico, sufixo aleatório e **fail-open** permanecem intactos. Dois clients do mesmo tenant compartilham a chave `tenantKey(tenantId)`; tenants diferentes não compartilham.
4. **Contrato do guard para headers — sem mudar o algoritmo.** `RedisQuotaGuard` ganha um método que **devolve** uma decisão (limit, remaining, retryAfter, reset) e o `QuotaExceededException` passa a carregar esses metadados (construtor legado preservado). O Lua **não muda**: `remaining` já vem do segundo valor em `{1, remaining}`; `retryAfter` de `{0, retryAfter}`; `reset` é derivado em Java (`now + retryAfter` na negação, `now + windowSeconds` na permissão), em epoch seconds. Métodos `void` existentes (`check`, `checkClient`, `checkTenant`, `checkTokenEndpoint`) continuam válidos e delegam ao mesmo caminho.
5. **Headers de cota.** `429` do chat mantém `Retry-After` e expõe `X-RateLimit-Limit-Requests` (= limite efetivo), `X-RateLimit-Remaining-Requests` (= 0 na negação) e `X-RateLimit-Reset-Requests` (epoch seconds). As mesmas chaves são emitidas nas respostas de sucesso **quando o guard tem valores medidos**; em **fail-open** (Redis indisponível) os headers são omitidos e o turno segue. Token endpoint mantém apenas `Retry-After` — os limites 30/120/30 usam três chaves distintas e não há um único `limit`/`remaining` para agregar; continuam fixos, como hoje.
6. **Bugfix do token endpoint.** `PublicAuthControllerV1` ganha handler **local** para `QuotaExceededException`: **429** com `Retry-After` e envelope `error.{message,type,param,code}` (`quota_exceeded`). Handler local tem precedência sobre o `@RestControllerAdvice` global (`ControllerExceptionHandler`), que hoje mapearia `BaseException` para `application/problem+json` — o mesmo padrão já usado por `PublicChatControllerV1:93-98`. **Supera explicitamente** o trecho da Interface congelada da ADR-001 que registrava 400; ADR-001 permanece sem edição e seu VERIFY não é contrariado.
7. **Campo antigo sem remoção.** `public_api_client.rate_limit_per_minute` fica obsoleto no caminho de chat, sem `DROP` e sem exposição na UI admin da ADR-003. Limpeza exige ADR própria.
8. **Fora de escopo:** crédito/budget, token bucket com custo por token, TPM, leitura da tabela de cota a cada turno, configurabilidade por perfil dos limites 30/120/30 do token e UI de ajuste do limite por tenant.

## Alternativas rejeitadas

| Alternativa | Motivo |
|---|---|
| Limite por client (default 100) | Operador escolheu contador compartilhado por tenant |
| Limite por client **e** teto do tenant (`checkClientAndTenant`) | Dois contadores; operador rejeitou |
| Token bucket com custo por token/crédito | Outra dimensão; fora do PRD |
| Ler `public_api_tenant_quota` a cada turno | Custo de DB por requisição em caminho stateless; snapshot no JWT é suficiente |
| Mudar o Lua para devolver limit/reset | Algoritmo já mede `remaining`/`retryAfter`; basta devolver o valor em Java e derivar o reset, preservando o script atômico |

## Interface congelada

- `POST /v1/public/v1/chat/completions`: `429 quota_exceeded` com `Retry-After` e `X-RateLimit-{Limit,Remaining,Reset}-Requests`; sucesso também carrega os headers quando medidos; em fail-open, sem headers. Envelope OpenAI e demais status da ADR-001 inalterados.
- `POST /v1/public/auth/token`: **429** com `Retry-After` e envelope OpenAI `quota_exceeded` quando `QuotaExceededException` — **supera explicitamente** o `400 invalid_request` registrado na Interface congelada da ADR-001 (Decisão 7). Demais handlers e resposta de sucesso inalterados; limites 30/120/30 fixos.
- Claim `plim` passa a aceitar chave `r` opcional (inteiro); `m`/`c`/`t` inalterados e lidos por chave. `aud=hive.public`, `tenant-id` e `permissions` inalterados.
- Chave Redis `hive:public:quota-tenant:{tenantId}` (`RedisQuotaGuard.tenantKey:158-160`) — distinta das chaves de client e de token.
- Contrato do guard: métodos `void` permanecem; novo método devolve decisão com limit/remaining/retryAfter/reset; Lua e fail-open inalterados.
- O valor de 100 req/min é default da tabela e fallback; tenants podem ter valor diferente via migration/seed.
- Nenhuma mudança em UI, JWT humano, MFA, `workflow-state.json` ou contrato de admin da ADR-003; ADR-001/002/003 não são editadas.

## Consequências

- **Positivas:** campo de limite passa a ter efeito real; tenants com vários clients não multiplicam cota; token endpoint finalmente devolve 429; `Retry-After` preservado e headers `X-RateLimit-*` com semântica documentada (`Reset` em epoch seconds — `Retry-After` é a fonte autoritativa).
- **Negativas:** mudança de limite demora até reemissão do token; permissão de limite por tenant não tem UI nesta ADR; dois clients do mesmo tenant podem se impactar (decisão do operador); contrato do guard ganha um método de decisão e o `QuotaExceededException` ganha metadados.
- **Neutras:** algoritmo Redis/Lua, fail-open, emissão de token e contrato da ADR-001 (exceto o trecho superscrito) permanecem; nenhuma aresta de módulo nova.

## Evidência exigida

IT com dois clients do mesmo tenant competindo pelo contador e `429` no segundo; IT de isolamento entre tenants; IT do token endpoint com `429` + `Retry-After` e envelope OpenAI; asserção dos headers no 429 e no sucesso, e omissão em fail-open; teste unitário do método de decisão do guard (Lua inalterado, `git diff` do script vazio) e do fallback 100 para token sem `r`; migration V2 em banco real com backfill; suíte completa sem regressão; zero diff em `Mfa*`, `workflow-state.json`, ADR-001/002/003 e UI da ADR-003; `code-reviewer` sem 🔴.
