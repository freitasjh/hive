# SPEC — ADR-004: Cota por tenant e bugfix do 429 no token

> Escopo: somente a decisão da ADR-004. Não especifica crédito/budget, TPM, leitura de cota por turno nem fronteira de módulos (ADR-005). Rastreabilidade: cada `RF`/`NFR` aponta para uma cláusula do `ADR.md`.

## 1. ADRs

### ADR-004 (este ciclo)

- **Decision:** tabela `public_api_tenant_quota` (V2), snapshot `plim.r` no JWT, `checkTenant` no chat, contrato de decisão do guard + headers `X-RateLimit-*`, bugfix 429 no token endpoint.
- **Context:** limite hoje é 60 por client, fixo; não existe limite por tenant; token endpoint devolve 400 (`PublicAuthControllerV1:156-163`) quando a cota estoura. Guard já mede `remaining`/`retryAfter` no Lua, mas `acquire` é `void`.
- **Trade-offs:** compartilhamento por tenant (decisão do operador) versus possibilidade de um client impactar os outros; snapshot no JWT versus staleness; devolver métricas do guard sem alterar o algoritmo Lua.

ADRs afetados: ADR-001 (Interface congelada registrava o 400 como comportamento real; **esta ADR supera explicitamente esse trecho**, sem editar a ADR-001 nem contrariar seu VERIFY), ADR-003 (campo de cota permanece fora da UI). ADR-002 e ADR-005 não são tocadas.

## 2. Domain Context, Tenant Strategy & Provisioning

- **Bounded Context:** `publicapi` continua dono de cota, credenciais e emissão de token.
- **System Type:** Spring Boot 4.1.0, Java 25 preview, Redis para sliding-window.
- **Tenant:** contador por `tenant_id`; clients do mesmo tenant competem pelo mesmo budget; tenants diferentes não compartilham chave Redis.

## 3. Identity & Access Management

- `client_credentials` inalterado. Chave JWT pública separada permanece. Nenhuma mudança em MFA, RBAC humano ou permissões da ADR-003.

## 4. Security, Key Management & Disaster Recovery

- Nenhuma chave nova. `plim.r` é claim de política, não segredo.
- Fail-open do guard preservado: indisponibilidade do Redis não pode derrubar o turno. Nesse caso os headers `X-RateLimit-*` são **omitidos** (não há valor medido).
- Handler 429 do token é **local** ao controller (precedência sobre `@RestControllerAdvice`), com envelope OpenAI `error.{message,type,param,code}` e `quota_exceeded` — nunca `application/problem+json` do advice global.
- RTO/RPO: N/A.

## 5. Data Model & Concurrency

| Item | Definição |
|---|---|
| Tabela | `public_api_tenant_quota` em `publicapi_db`, migration V2 |
| Colunas | `tenant_id UUID PK`, `chat_requests_per_minute INT NOT NULL DEFAULT 100`, auditoria, `CHECK BETWEEN 1 AND 10000` |
| Backfill | uma linha por `tenant_id` distinto em `public_api_client`, valor 100 |
| Repositório | `PublicApiTenantQuotaRepository` + entidade em `publicapi/impl`, mesmo datasource de `public_api_client` — nenhuma aresta de módulo nova |
| Redis | chave `hive:public:quota-tenant:{tenantId}` (`RedisQuotaGuard.tenantKey:158-160`), ZSET + Lua atômico, sufixo aleatório, `EXPIRE` — script inalterado |
| Decisão do guard | novo método devolve `limit`/`remaining`/`retryAfter`/`reset`; `reset` derivado em Java (epoch s); métodos `void` legados permanecem |
| Coluna antiga | `public_api_client.rate_limit_per_minute` obsoleta, sem `DROP` |

## 6. Event Model, Integrations & Messaging Isolation

- Nenhum evento ou integração nova.

## 7. Invariants & Property-Based Testing

| Tipo | Invariante | Verificação |
|---|---|---|
| Cota | um contador por tenant; clients do mesmo tenant competem | IT com dois clients, segundo recebe 429 |
| Isolamento | tenants diferentes não compartilham chave Redis | IT de isolamento |
| Compatibilidade | `429` com `Retry-After` no chat e no token; token usa envelope OpenAI, não `problem+json` | ITs de contrato |
| Headers | `X-RateLimit-{Limit,Remaining,Reset}-Requests` no 429 e no sucesso quando medidos; omitidos em fail-open | asserção no IT |
| Algoritmo | Lua ZSET inalterado (`git diff` do script vazio); sufixo aleatório preservado | diff + teste do guard |
| Resiliência | Redis indisponível → fail-open e turno segue | teste de rollback/fail-open |
| Contrato | ADR-001/002/003 não editadas; só o 429 do token é superscrito, com registro explícito | ITs existentes reexecutados |

## 8. Requisitos Funcionais

- **RF1** — migration V2 com tabela de cota por tenant e backfill (sem `repair()`).
- **RF2** — `plim.r` emitido no JWT via repositório em `publicapi/impl`, com fallback 100; `m`/`c`/`t` inalterados.
- **RF3** — `checkTenant(tenantId, limit(plim,"r",100))` no chat, compartilhado entre clients do tenant.
- **RF4** — contrato do guard com método de decisão (Lua inalterado) + headers `X-RateLimit-*` e `Retry-After` no 429/sucesso, omitidos em fail-open.
- **RF5** — `429` local com `Retry-After` e envelope OpenAI no token endpoint (bugfix; superscreve o 400 da ADR-001).

## 9. Requisitos Não-Funcionais

- **NFR1** — fail-open e algoritmo Redis/Lua preservados (script sem diff).
- **NFR2** — suíte completa sem regressão; nenhum teste apagado ou desabilitado.
- **NFR3** — zero diff em `Mfa*`, `workflow-state.json`, UI/contrato de admin da ADR-003 e ADR-001/002/003.
- **NFR4** — nenhuma recomendação de versão/biblioteca sem deepsearch (URL + data).

## 10. Critérios de Aceite

- Dois clients do mesmo tenant: o segundo recebe `429` quando o somatório estoura; tenants diferentes não compartilham contador.
- `429` do chat e do token com `Retry-After`; token endpoint deixar de devolver 400 por cota e usar envelope OpenAI (não `problem+json`).
- Headers `X-RateLimit-Limit/Remaining/Reset-Requests` no 429 e no sucesso quando medidos; ausentes em fail-open; Lua sem diff.
- Token sem claim `r` usa fallback 100 e continua válido; `m`/`c`/`t` inalterados.
- Migration V2 aplicada em banco real com backfill correto e sem `repair()`.
- Superscrição do 400 da ADR-001 registrada na ADR-004, sem editar ADR-001/002/003.
- Suíte 0 falhas; `code-reviewer` sem 🔴; zero diff em MFA/workflow-state/UI da ADR-003.

## 11. Riscos

| Risco | Mitigação |
|---|---|
| Staleness do `plim.r` no JWT | aceito; propagação por reemissão/revogação documentada |
| Token antigo sem `r` | fallback 100 |
| Coluna genérica travar crédito futuro | nome explícito `chat_requests_per_minute` |
| Dois clients do mesmo tenant se impactarem | decisão do operador; documentado na ADR |
| Redis indisponível | fail-open preservado e testado; headers omitidos, nunca valores inventados |
| `retryAfter`/`remaining` descartados pelo `void` do guard | novo método de decisão; Lua inalterado; reset derivado em Java |
| Handler local conflitar com advice global | precedência local sobre `@RestControllerAdvice`; padrão já usado no chat |
| VERIFY da ADR-001 parecer contradito | superscrição registrada como mudança planejada, sem editar a ADR-001 |

## 12. Fontes

Internas (revisão 2026-11-22): `.spec/prd/public-chat-api/CONSTRAINTS.md` §7, `DESIGN-DECISIONS.md` Decisão 4, `ARCHITECT-REVIEW.md`; `V1__public_api_client.sql`; `PublicApiDbUpdateConfig.java` (sem `repair()`); `RedisQuotaGuard.java:33-57,101-160`; `PublicClientTokenIssuer.java:111-133`; `PublicApiSecurityFilter.java:87-88`; `PublicChatServiceImpl.java:111-140`; `PublicAuthControllerV1.java:156-163`; `PublicChatControllerV1.java:93-98`; `ControllerExceptionHandler.java:41-61`; `QuotaExceededException.java:8-26`. Externas: `https://www.rfc-editor.org/rfc/rfc6749.html` (§5.1) e `https://developers.openai.com/api/docs/guides/rate-limits` — ambas 2026-11-21.
