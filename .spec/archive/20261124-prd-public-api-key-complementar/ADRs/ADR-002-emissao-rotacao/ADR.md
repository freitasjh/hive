# ADR-002 — Emissão, hash, rotação e revogação da API key

**Status:** merged (merge `aae4379`, PR #12) · **Data:** 2026-11-24 · **PRD:** `public-api-key-complementar` · **Branch:** `adr/public-api-key-complementar/02-emissao-rotacao`

**depende-de:** ADR-001 (merged — formato `Bearer sk-`, porta `PublicApiKeyAuthenticator` e OpenAPI).

## Contexto

A ADR-001 entregou o formato `sk-` e a porta `PublicApiKeyAuthenticator` em `publicapi/api`, com `ObjectProvider` no filtro. **Não há implementação de produção da porta**: todo `sk-` recebe 401 fail-closed por desenho. Esta ADR precisa registrar o bean que valida a key e devolve o mesmo principal do JWT.

Evidência interna: `PublicApiSecurityFilter.java:62-93` (ramo `sk-`, `ObjectProvider.getIfAvailable()` em `:73`), `PublicApiKeyAuthenticator.java` (porta com record `AuthenticatedKey`), `PublicClientTokenIssuer.java:114-139` (claims atuais), `PublicApiClientServiceImpl.java:36,81` (BCrypt padrão e janela de 10 min), `V1__public_api_client.sql` e `V2__public_api_tenant_quota.sql` (histórico `publicapi_db`). Nenhuma fonte externa nova — decisão interna de storage/rotação.

Achados da revisão de 2026-11-24 que fecham as decisões:

- **`publicapi_db/` tem V1 e V2**; próxima versão livre = **V3**. `PublicApiDbUpdateConfig:17-21` usa `baselineOnMigrate(true)` e **sem `repair()`** — V3 aplica sem realinhar checksums.
- **Dois beans** fariam `getIfAvailable()` lançar `NoUniqueBeanDefinitionException`, capturada pelo `try` do filtro (`:66-136`) ⇒ 401 fail-closed, não erro de startup. Ainda assim, exatamente um bean é obrigatório.
- `PublicClientTokenIssuer:114-121` monta `plim` com `m/c/t` do client e `r` da cota do tenant. Reconstruir esse mapa na porta causaria drift — decisão: método compartilhado `policyFor(client)` no próprio emissor, sem mudar valores.
- Revogação de client usa `hive:public:revoked-clients` + `revoked_at` (`PublicClientTokenIssuer:172-193`) — padrão reutilizável, com chave distinta para key.

## Decisão (fechada)

1. **Bean único da porta.** Implementar `PublicApiKeyAuthenticator` em **`publicapi/impl`** com exatamente **uma** implementação `@Service`. `authenticate(rawKey)` devolve `AuthenticatedKey` ou **`null`** para key inválida/revogada — nunca lança e nunca tenta outro caminho. O `ObjectProvider` do filtro resolve sem alteração; teste de contexto prova o bean único.
2. **Emissão one-shot.** Novo `PublicApiKeyControllerV1` sob `@RequestMapping(RestPath.V1 + "/public-api/clients")`, fora de `/v1/public/**` e `/manager/**`, chain humana com `@HasPermission(SecurityConstants.AGENT_MANAGE)` em todas as operações, tenant da sessão. `POST /{clientId}/keys` → 201 com `clientId`, `keyId` e `api_key` em plaintext **somente nesta resposta**; list/get nunca contêm plaintext nem hash. Cross-tenant → 404 (sem vazar existência).
3. **Migration V3 em `publicapi_db`** (`V3__public_api_client_key.sql`). Tabela `public_api_client_key`: `id`, auditoria, `tenant_id`, `client_ref` (FK), `key_id VARCHAR(64) UNIQUE`, `key_hash VARCHAR(72) NOT NULL`, `previous_key_hash`, `previous_key_expires_at`, `revoked_at`, `expires_at`, `last_used_at`, com `CHECK` pareando o par `previous_*`. Nenhum `DROP`, nenhum `repair()`. Nomes `key_*` explícitos — proibidos `secret`/`token`/`quota` genéricos.
4. **Hash.** BCrypt **custo 10**, mesma primitiva de `client_secret` (`PublicApiClientServiceImpl:36`); hash do valor completo `sk-...`, sem transformação. Verificação por `matches(rawKey, key_hash)`; se falhar e houver `previous_key_hash` ainda dentro da janela, `matches(rawKey, previous_key_hash)`. Sem recuperação, sem plaintext em banco ou log.
5. **Janela de rotação — fechada.** **10 minutos fixos**, igual ao `client_secret` (`PublicApiClientServiceImpl:81`), sem propriedade nova de configuração. `POST /{clientId}/keys/{keyId}/rotate` devolve o novo plaintext uma vez e grava o hash antigo em `previous_key_hash` com expiração; dentro da janela a key antiga autentica, fora dela → 401.
6. **Revogação imediata.** `POST /{clientId}/keys/{keyId}/revoke` (**POST**, não DELETE — padrão do controller de clients `:80`) marca `revoked_at` no DB e insere `keyId` no set Redis **`hive:public:revoked-keys`**, distinto de `hive:public:revoked-clients`. O autenticador checa Redis primeiro e o DB por fallback (sem cache negativo), espelhando `PublicClientTokenIssuer:172-193` sem duplicar a regra de revogação de client.
7. **Claims sem drift.** `PublicClientTokenIssuer` passa a expor `policyFor(client)` com `permissions`/`pcode`/`plock`/`plim`; `issueToken` e o autenticador chamam o mesmo método. **`plim` inalterado** (`m/c/t/r`, fallback 100). Cota própria da key e telemetria (`turnId`/`clientId`) ficam na ADR-003. Nenhum diff em `ChatTurnGateway`, `ChatTrace` ou `TurnOutcomeMapper`.
8. **Fora desta ADR:** UI admin para keys; mudança de `plim.r`; migração de clients existentes; crédito/budget; `x-goog-api-key`.

## Alternativas rejeitadas

| Alternativa | Motivo |
|---|---|
| Guardar plaintext da key | Vazamento irreversível; BCrypt é verificável e não recuperável |
| HMAC com chave do servidor | Exige gestão de chave mestra para segredo de terceiros; BCrypt já é o padrão aqui |
| Reutilizar `client_secret` como key | Mistura fluxo de emissão de token com credencial de chamada direta; quebra one-shot |
| Duas implementações da porta | `getIfAvailable()` lança e o filtro devolve 401 para toda key — proibido |
| Janela configurável por propriedade | 10 min fixos já é o padrão do `client_secret`; sem config nova nesta ADR |
| Reconstruir `plim` no autenticador | Drift garantido em relação ao emissor; método `policyFor(client)` compartilhado |
| Chave Redis de revogação igual à de client | Colisão entre revogar client e revogar key; usar `hive:public:revoked-keys` |

## Interface congelada

- `POST /v1/public/auth/token` e `POST /v1/public/v1/chat/completions` — paths, status e envelope da ADR-001 anterior preservados.
- Formato `sk-` da ADR-001; nenhum `x-goog-api-key`; nenhum fallback entre credenciais.
- `AuthenticatedKey` — mesmo principal, authorities, atributos de request e `TenantContext` do JWT; `authenticate` devolve `null` quando inválida.
- Claims `tenant-id`, `permissions`, `pcode`, `plock`, `plim` — mesmos valores do emissor de JWT via `policyFor(client)`; **`plim` sem mudança de semântica**, cota própria da key é ADR-003.
- `ChatTurnGateway`/`TurnRequest`/`TurnReply`, `ChatTrace` com `turnId`/`clientId` e `TurnOutcomeMapper` — sem diff.
- Envelope de erro OpenAI `{message,type,param,code}`; 401 para key inválida/revogada/fail-closed; 404 para cross-tenant no admin.
- Nenhuma credencial em log plaintext; plaintext só em `POST .../keys` e `POST .../rotate`.

## Consequências

- **Positivas:** destrava o uso real de `sk-`; rotação e revogação com padrões já provados; tenant e grants vêm da linha; claims compartilhados impedem drift.
- **Negativas:** nova tabela (V3), novo controller admin e mais um segredo a gerenciar; janela de 10 min admite key antiga por um período; extrair `policyFor` toca o emissor de JWT (sem mudar valores).
- **Neutras:** contrato público, `plim`, telemetria e UI de clients da ADR-003 anterior inalterados; nenhuma aresta nova de módulo e nenhuma configuração nova.

## Evidência exigida

Migration V3 aplicada em Postgres real (sem `repair()`/`DROP`); IT de emissão one-shot (plaintext só no create/rotate), autenticação com key válida, key antiga dentro/fora dos 10 min, revogação Redis+DB no próximo request e cross-tenant 404; IT de ausência de plaintext em list/get/log; teste de contexto com **um único** bean da porta e `authenticate` devolvendo `null` na falha; `git diff` sem tocar `plim`, `ChatTurnGateway`, `ChatTrace`, `TurnOutcomeMapper`, ADR-001, PRD/RESEARCH e arquivos arquivados; suíte completa sem regressão; `code-reviewer` sem 🔴.
