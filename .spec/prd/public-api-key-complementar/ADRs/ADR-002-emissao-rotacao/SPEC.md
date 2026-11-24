# SPEC — ADR-002: Emissão, hash, rotação e revogação da API key

> Escopo: dar implementação de produção à porta da ADR-001 e gerenciar o ciclo de vida da key. Cota própria e telemetria ficam na ADR-003.

## 1. ADRs

### ADR-002 (este ciclo)

- **Decision (fechada 2026-11-24):** bean único de `PublicApiKeyAuthenticator` em `publicapi/impl` (`authenticate` → `null` na falha), emissão one-shot via `PublicApiKeyControllerV1`, hash BCrypt custo 10, migration **V3** em `publicapi_db`, rotação com janela fixa de **10 min** e revogação via Redis `hive:public:revoked-keys` + DB. Claims compartilhados por `policyFor(client)` no emissor.
- **Context:** ADR-001 entregou o formato e o `ObjectProvider`, mas sem bean a key sempre recebe 401 fail-closed. V1/V2 ocupam o histórico; dois beans fariam `getIfAvailable()` lançar e o filtro devolver 401.
- **Trade-offs:** viabilizar a feature versus nova tabela, controller e segredo; extrair `policyFor` toca o emissor mas elimina drift de `plim`.

ADRs afetados: ADR-001 (porta e formato), ADR-003 (consumirá os claims). ADRs do PRD anterior arquivado não são tocadas.

## 2. Domain Context, Tenant Strategy & Provisioning

- **Bounded Context:** `publicapi` dono da key (porta, impl, entidade, repositório e controller admin); `security` consome a porta via `ObjectProvider` — nenhuma aresta de módulo nova.
- **Tenant:** vem da linha do client, nunca do request; `client_id` globalmente único; admin usa o tenant da sessão e devolve 404 para client/key de outro tenant.
- **Provisioning:** key emitida por `POST /v1/public-api/clients/{clientId}/keys` na chain humana, vinculada a client existente.

## 3. Identity & Access Management

- `PublicApiKeyControllerV1` sob `@RequestMapping(RestPath.V1 + "/public-api/clients")`, com `@HasPermission(SecurityConstants.AGENT_MANAGE)` em todas as operações — mesmo padrão de `PublicApiClientControllerV1:36-104`. Fora de `/v1/public/**` (chain M2M) e `/manager/**` (`permitAll`).
- `client_credentials` e o JWT permanecem intactos; a key não substitui o token endpoint (`shouldNotFilter` já exclui `/v1/public/auth/`).

## 4. Security, Key Management & Disaster Recovery

- Plaintext apenas em `POST .../keys` e `POST .../keys/{keyId}/rotate`; hash BCrypt custo 10 do valor completo `sk-...`; sem recuperação; nunca em log/list/get.
- Janela do hash anterior **fixa em 10 min**; revogação imediata via set Redis `hive:public:revoked-keys` + `revoked_at` no DB, distinto de `hive:public:revoked-clients`.
- Autenticador devolve `null` para key inválida/revogada; nenhum fallback e nenhum log de plaintext.
- RTO/RPO: N/A.

## 5. Data Model & Concurrency

| Item | Definição |
|---|---|
| Tabela | `public_api_client_key` em `publicapi_db`, migration **V3** (V1 client, V2 tenant quota) |
| Colunas | `id`, `tenant_id`, `client_ref` (FK), `key_id VARCHAR(64) UNIQUE`, `key_hash`, `previous_key_hash`, `previous_key_expires_at`, `revoked_at`, `expires_at`, `last_used_at`, `create_at`, `update_at`; `CHECK` do par `previous_*` |
| Nomes | explícitos `key_*`; proibido `secret`/`token`/`quota` genérico |
| Histórico | `baselineOnMigrate(true)` e **sem `repair()`**; sem `DROP` |
| Redis | set `hive:public:revoked-keys` com `keyId`; distinto de `hive:public:revoked-clients` |

## 6. Event Model, Integrations & Messaging Isolation

- Nenhum evento ou integração nova.

## 7. Invariants & Property-Based Testing

| Tipo | Invariante | Verificação |
|---|---|---|
| Bean | exatamente uma implementação `@Service`; `authenticate` devolve `null` na falha | inspeção + startup/contexto |
| Segredo | plaintext só no create/rotate; nunca em list/get/log | IT de contrato |
| Rotação | hash anterior válido por 10 min, inválido depois | IT de emitir → rotacionar → autenticar (tempo controlado) |
| Revogação | `revoked_at` + `hive:public:revoked-keys`; efeito no próximo request | IT de revoke → 401 |
| Tenant | cross-tenant 404 no admin; tenant da linha/sessão | IT de isolamento |
| Claims | `policyFor(client)` compartilhado; `plim` com `m/c/t/r` inalterado | diff do emissor + IT de emissão |
| Grafo | `plim` (semântica), ChatTurnGateway, ChatTrace, TurnOutcomeMapper sem diff | `git diff --name-only` |

## 8. Requisitos Funcionais

- **RF1** — bean único de `PublicApiKeyAuthenticator` em `publicapi/impl`, com o mesmo principal do JWT via `policyFor(client)`.
- **RF2** — emissão one-shot em `POST /v1/public-api/clients/{clientId}/keys`, com hash BCrypt custo 10 e vínculo ao client.
- **RF3** — rotação com janela fixa de 10 min e plaintext devolvido uma vez.
- **RF4** — revogação imediata via `hive:public:revoked-keys` + `revoked_at`.

## 9. Requisitos Não-Funcionais

- **NFR1** — suíte 0 falhas, nenhum teste apagado/`@Disabled`.
- **NFR2** — zero diff em ADR-001, PRD/RESEARCH, arquivos arquivados, MFA, `workflow-state.json`, UI e cota/telemetria.
- **NFR3** — nenhuma credencial em log; nenhuma dependência nova de biblioteca.
- **NFR4** — comandos de `harness.config.json`/`AGENTS.md`; portas por PID, nunca `pkill -f`.
- **NFR5** — nenhuma mudança de semântica em `plim`, nenhuma aresta nova de módulo, nenhuma propriedade de configuração nova.

## 10. Critérios de Aceite

- IT: 200 no chat com key emitida; `sk-` sem bean continua 401 fail-closed.
- IT: plaintext só em `POST .../keys` e `POST .../rotate`; list/get/log sem plaintext nem hash.
- IT: key antiga autentica dentro dos 10 min e recebe 401 depois.
- IT: revoke → 401 no próximo request (Redis + DB); admin cross-tenant → 404.
- IT: autenticador e emissor produzem `permissions`/`pcode`/`plock`/`plim` idênticos para o mesmo client; `plim.r` inalterado.
- Migration V3 aplicada em Postgres real, sem `repair()`/`DROP`; um único bean; suíte 0 falhas; review sem 🔴.

## 11. Riscos

| Risco | Mitigação |
|---|---|
| Bean duplo | uma `@Service`; `getIfAvailable()` com dois beans já devolve 401 — teste de contexto impede |
| Janela mal calculada | 10 min fixos, IT com tempo controlado, sem `sleep` longo |
| Migration em histórico compartilhado | V3 é a próxima livre; `baselineOnMigrate(true)` sem `repair()` |
| Nome de coluna genérico travar crédito | nomes explícitos `key_*` |
| Drift de claims entre JWT e key | `policyFor(client)` compartilhado no emissor |
| Colisão de revogação client/key | `hive:public:revoked-keys` distinto de `hive:public:revoked-clients` |

## 12. Fontes

Internas (revisão 2026-11-24): `PRD.md` §7, `RESEARCH.md` §5, `ADR-001-formato-bearer` (merged); `PublicApiSecurityFilter.java:66-136`; `PublicApiKeyAuthenticator.java`; `PublicClientTokenIssuer.java:114-139,172-193`; `PublicApiClientServiceImpl.java:36,81`; `PublicApiClientControllerV1.java:36-104`; `PublicApiDbUpdateConfig.java:17-21`; `V1__public_api_client.sql`, `V2__public_api_tenant_quota.sql`. Nenhuma fonte externa — BCrypt e padrões de revogação já são do repo.
