# ADR-003 — Cota própria da key e telemetria preservada

**Status:** merged · **Data:** 2026-11-24 · **PRD:** `public-api-key-complementar` · **Branch:** `adr/public-api-key-complementar/03-cota-telemetria` · **Merge:** `6118a3a` (PR #13)

**depende-de:** ADR-001 (formato Bearer) e ADR-002 (emissão/rotação da key), ambas merged.

## Contexto

PRD §7 fixou o invariante: **a key pode ter cota própria, distinta do `plim.r` do JWT**, e `turnId`/`clientId` e os headers de rate limit devem continuar corretos. Hoje:

- `PublicChatServiceImpl.java` lê `plim` dos atributos de request e aplica `acquireTenant(tenantId, limit(limits,"r",100))`.
- `PublicApiSecurityFilter` preenche `publicapi.clientId/pcode/plock/plim` para JWT e para key (`AuthenticatedKey` da ADR-002).
- `RedisQuotaGuard.acquireTenant` devolve `QuotaDecision` com `limit/remaining/retryAfter/resetEpoch/measured`.
- `TurnOutcomeMapper` grava `turnId`/`clientId` na trace (ADR-002 do PRD anterior).
- A cota por tenant do PRD anterior (`public_api_tenant_quota`, V2) permanece intocada.

Se a key usar o mesmo `plim.r` do JWT, não há cota própria — requisito do PRD não atendido. Se o limite mudar sem refletir nos headers, a resposta OpenAI-style fica incoerente.

Achados da revisão de 2026-11-24 que fecham as decisões:

- `publicapi_db/` tem V1, V2 e V3 (`public_api_client_key`); **próxima livre = V4**. Config sem `repair()`.
- `RedisQuotaGuard.acquireDecision(key, limit)` já devolve `QuotaDecision(limit, remaining, retryAfter, resetEpoch, measured)` com Lua e fail-open intactos; `acquireTenant` usa `tenantKey` = `hive:public:quota-tenant:{tenantId}` — **compartilhar essa chave com a key colidiria com o JWT do mesmo tenant** e misturaria limites diferentes.
- `PublicChatServiceImpl:141-146` já aplica `acquireTenant(...)` e grava `publicapi.quota.{limit,remaining,reset}` **somente quando `measured()`**; `PublicChatControllerV1:155-170` deriva os headers desses atributos — nenhuma mudança de controller é necessária.
- `turnId` é gerado incondicionalmente (`PublicChatServiceImpl:180`) e `clientId` vem do atributo preenchido para os dois caminhos; ambos vão para `TurnOutcomeMapper.resolve(...)` (`:188-189`) — telemetria da key já funciona.
- `AuthenticatedKey` hoje carrega só principal/claims; `PublicApiKeyAuthenticatorImpl:81-82` o constrói a partir da entidade da key; `PublicApiSecurityFilter:67-94` já distingue key/JWT e é o único lugar que precisa setar atributos novos.

## Decisão (fechada)

1. **Migration V4 e default — fechados.** `V4__public_api_client_key_chat_rpm.sql` em `publicapi_db` adiciona `key_chat_requests_per_minute INT NOT NULL DEFAULT 100` em `public_api_client_key`, com `CHECK (… BETWEEN 1 AND 10000)`. **Backfill de keys existentes copia a cota do tenant**: `UPDATE ... SET key_chat_requests_per_minute = COALESCE((SELECT q.chat_requests_per_minute FROM public_api_tenant_quota q WHERE q.tenant_id = k.tenant_id), 100)` — preserva o limite efetivo que a key já usava via `plim.r`. Chaves novas usam o default 100. Sem `repair()`, sem `DROP`; V1/V2/V3 intactas. Nome `key_*` obrigatório (`limit`/`quota`/`rate_limit` proibidos).
2. **Namespace Redis distinto por key — fechado.** `RedisQuotaGuard` ganha `keyKey(keyId)` = **`hive:public:quota-key:{keyId}`** e `acquireKey(keyId, limit)`, ambos delegando ao `acquireDecision` existente. Mesmo Lua, sufixo aleatório e fail-open; sem nova lógica de janela. Rejeitado reutilizar `quota-tenant:{tenantId}` com valor da key — colisão com JWT do mesmo tenant e limites divergentes no mesmo contador.
3. **Propagação — fechada (menor diff sem tocar `plim`).** `AuthenticatedKey` ganha **`keyId`** e **`keyChatRequestsPerMinute`**; `PublicApiKeyAuthenticatorImpl` preenche ambos a partir da entidade. `PublicApiSecurityFilter` seta `publicapi.keyId` e `publicapi.keylim` **apenas no ramo `sk-`** (`:67-94`) — nenhum atributo novo no ramo JWT. `PublicChatServiceImpl` escolhe: key ⇒ `acquireKey(keyId, keylim)`; JWT ⇒ `acquireTenant(tenantId, limit(plim,"r",100))` inalterado. `plim` e o emissor de JWT não são tocados; nenhum campo de cota entra no claim.
4. **Headers coerentes — confirmado.** `QuotaDecision` já carrega o limite aplicado; as mesmas chaves `publicapi.quota.{limit,remaining,reset}` são gravadas para os dois caminhos e o controller já emite `X-RateLimit-*`. Em fail-open (`measured=false`) os atributos não são gravados e os headers são omitidos. `Retry-After` preservado. Nenhuma mudança em `PublicChatControllerV1`.
5. **Telemetria — preservada.** `turnId`/`clientId` já preenchidos no caminho da key; a trace não os distingue por `null`. COT04 apenas reforça a asserção; `ChatCallerParityIT` e IT de trace da key são evidência suficiente. Nenhuma mudança em `TurnOutcomeMapper`.
6. **Fora desta ADR:** crédito/budget, TPM, tabela `public_api_tenant_quota` do PRD anterior, `ChatTurnGateway`/`TurnRequest`/`TurnReply`, regras de prompt, UI e migração de clientes existentes.

## Alternativas rejeitadas

| Alternativa | Motivo |
|---|---|
| Reutilizar `plim.r` do tenant para a key | Não é cota própria; requisito do PRD |
| Colocar limite da key dentro de `plim` | Tocaria o claim do JWT e o emissor; quebra ADR-004 do PRD anterior |
| Reutilizar `quota-tenant:{tenantId}` com valor da key | Colisão com JWT do mesmo tenant e limites diferentes no mesmo contador |
| Reconstruir `plim` no emissor de JWT | Mudaria o JWT sem necessidade; `AuthenticatedKey` + atributo de request bastam |
| Limite em memória/config por key | Sem persistência; rotação/revogação perderiam o valor |
| Nome genérico `rate_limit` | Travaria crédito/TPM futuros |
| Default 100 para keys existentes sem backfill | Mudaria o limite efetivo de tenants com cota customizada; copiar da tabela V2 preserva comportamento |

## Interface congelada

- Formato `sk-`, envelope OpenAI, duplo `/v1`, códigos de erro e one-shot inalterados.
- JWT `aud=hive.public`, claims `tenant-id`/`permissions`/`pcode`/`plock`/`plim` inalterados; caminho JWT continua com `acquireTenant(tenantId, plim.r)`.
- `AuthenticatedKey` ganha `keyId` e `keyChatRequestsPerMinute`; `authenticate → null` na falha e um único bean permanecem.
- Namespace Redis: `hive:public:quota-key:{keyId}` para key; `hive:public:quota-tenant:{tenantId}` permanece exclusivo do JWT. Lua e fail-open intocáveis.
- `ChatTurnGateway.execute(TurnRequest): TurnReply`, `TurnRequest`/`TurnReply` e regras de prompt inalterados.
- `ChatTrace` com `turnId`/`clientId` preenchidos nos dois caminhos; `TurnOutcomeMapper` sem diff.
- `X-RateLimit-*` derivados de `QuotaDecision` com o limite aplicado; omitidos em fail-open (`measured=false`).

## Consequências

- **Positivas:** cota por key persistida e auditável; namespaces Redis separados evitam colisão; headers coerentes com a credencial usada; telemetria atribuível sem tocar o gateway.
- **Negativas:** V4, dois campos novos em `AuthenticatedKey` e dois atributos de request; backfill depende da tabela V2; keys antigas com tenant sem linha recebem 100.
- **Neutras:** caminho JWT, `plim`, cota por tenant do PRD anterior, UI e contrato público inalterados; nenhuma dependência nova de módulo ou biblioteca.

## Evidência exigida

IT com key limite X e JWT limite Y no **mesmo tenant**, provando contadores e headers independentes; IT de headers `X-RateLimit-*` refletindo o limite aplicado e omissão em fail-open; IT de `turnId`/`clientId` preenchidos no caminho da key e `ChatCallerParityIT` verde; migration V4 em Postgres real com backfill copiando a cota do tenant e fallback 100; `git diff` sem tocar `plim`, emissor de JWT, `ChatTurnGateway`/`TurnRequest`/`TurnReply`, `TurnOutcomeMapper`, regras de prompt, tabela V2, UI, ADR-001/002, PRD/RESEARCH e arquivos arquivados; suíte completa sem regressão; `code-reviewer` sem 🔴.
