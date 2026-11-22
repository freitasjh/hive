# SPEC — ADR-003: Gestão de credenciais com UI admin

> Escopo: somente a decisão da ADR-003. Não especifica cota (ADR-004), fronteira de módulos (ADR-005) nem mudanças no contrato público (ADR-001). Rastreabilidade: cada `RF`/`NFR` aponta para uma cláusula do `ADR.md`.

## 1. ADRs

### ADR-003 (este ciclo)

- **Decision:** serviço de gestão em `publicapi/impl` (reuso + lacunas pontuais), endpoints `/v1/public-api/clients` na chain humana com `@HasPermission(SecurityConstants.AGENT_MANAGE)`, UI em `frontend/src/modules/hive/admin/public-clients/`; manter `client_credentials`. Paginação `page`/`size`; `client_id` gerado no servidor.
- **Context:** provisionamento só por CLI; schema V1 já suporta rotação/revogação/grants/pin; **não existe permissão humana de admin de API pública** (`PUBLIC_CHAT_COMPLETE` é grant de client); `rotateSecret` hoje descarta o plaintext.
- **Trade-offs:** autonomia do operador versus nova superfície HTTP, mudança de assinatura do serviço e risco de vazamento de secret; reuso de `AGENT_MANAGE` evita redesenho de RBAC fora de escopo.

ADRs afetados: ADR-001 (contrato congelado); ADR-002 (merged, sem impacto). ADR-004 usa a mesma tabela de tenant para cota, mas não altera este escopo.

## 2. Domain Context, Tenant Strategy & Provisioning

- **Bounded Context:** `publicapi` continua dono de credenciais, grants e concessões de prompt.
- **System Type:** Spring Boot 4.1.0, Java 25 preview, React no frontend.
- **Tenant:** vem da linha `public_api_client.tenant_id`; `client_id` globalmente único; um tenant pode ter N clients com `label` distinto. UI não troca tenant de client existente.

## 3. Identity & Access Management

- `client_credentials` inalterado. Superfície admin na chain ordinária (`.anyRequest().authenticated()`) com **`@HasPermission(SecurityConstants.AGENT_MANAGE)`** — decisão 2026-11-22. Não criar permissão nova: a lista de claims é hardcoded e idêntica para todos os usuários (`SecurityTokenServiceImpl:175-182`), então uma capability nova não daria isolamento real sem redesenho de RBAC.
- `PUBLIC_CHAT_COMPLETE` continua sendo **grant de client** (`PublicApiProvisioningRunner:28`), não permissão humana. Nenhum endpoint admin em `/v1/public/**` nem em `/manager/**` (`permitAll`).
- MFA: nenhuma mudança; proibido tocar o ciclo MFA.

## 4. Security, Key Management & Disaster Recovery

- `secret_hash` BCrypt permanece não recuperável. Plaintext apenas na resposta de criação/rotação — exige que `PublicApiClientService.rotateSecret` passe a devolver o valor (hoje `void`, `PublicApiClientServiceImpl:73-85`). Nenhum plaintext em log; a API admin nunca expõe `secret_hash`/`previous_secret_hash`.
- Rotação mantém a janela `previous_secret_hash`/`previous_secret_expires_at` (10 min, `PublicApiClientServiceImpl:81`), já honrada pelo issuer (`:77-82`); revogação imediata via Redis + DB (`:89-95` e issuer `:182-191`), inalterada.
- Path admin `/v1/public-api/clients` — segmento `public-api` não casa `securityMatcher("/v1/public/**")`; `/manager/**` evitado por ser `permitAll` (`SecurityConfig:85`).
- Nenhuma chave JWT ou segredo de provider é alterado. RTO/RPO: N/A.

## 5. Data Model & Concurrency

- Nenhuma migration nesta ADR; usa as tabelas `public_api_client*` do V1 (`UNIQUE(client_id)`, `UNIQUE(tenant_id,label)`, checks de `token_ttl_seconds`, pin e janela de secret). V2 (cota por tenant) pertence à ADR-004.
- Lista paginada por tenant via `JpaRepository.findByTenantId(tenantId, Pageable)`, `page` ≥ 0, `size` padrão 20 e teto 100 — sem nova query nativa.
- Concorrência: revogação imediata já é atômica no domínio; nenhuma nova transação distribuída.

## 6. Event Model, Integrations & Messaging Isolation

- Nenhum evento ou integração nova. Provisionamento por CLI permanece disponível.

## 7. Invariants & Property-Based Testing

| Tipo | Invariante | Verificação |
|---|---|---|
| Identidade | `client_id` globalmente único, gerado no servidor, imutável; `UNIQUE(tenant_id,label)`; tenant da linha imutável | IT de criação duplicada + tentativa de trocar tenant |
| Segredo | plaintext apenas em create/rotate; nunca em listagem/get/log; nenhum hash exposto | IT de contrato da API admin |
| Rotação | janela do secret anterior preservada (10 min); `previousSecretExpiresAt` exposto como timestamp, sem hash | IT de rotação e emissão de token dentro/fora da janela |
| Revogação | efeito imediato em Redis + DB | IT de revogação seguido de 401 |
| Escopo | grants e pin gravados no JWT `pcode`/`plock` | IT de emissão |
| Acesso | `/v1/public-api/clients` exige chain humana + `AGENT_MANAGE`; anônimo → 401/403 | IT de permissão |
| Contrato | `/v1/public/**` e ADR-001 inalterados | ITs existentes reexecutados |

## 8. Requisitos Funcionais

- **RF1** — criar client: `client_id` gerado no servidor, tenant da sessão, label, grants, prompts/pin e limites de requisição (sem `rate_limit_per_minute`).
- **RF2** — listar clients paginados (`page`/`size`) e get, sem plaintext nem hash de secret.
- **RF3** — rotacionar secret **devolvendo o plaintext uma vez** e preservando a janela do secret anterior.
- **RF4** — revogar client com efeito imediato (Redis + DB, já implementado).
- **RF5** — gerenciar grants e vínculo de prompts com `pin_mode`/`pinned_version`, compartilhando a regra do CLI.
- **RF6** — UI em `admin/public-clients/` cobrindo criação, listagem, rotação e revogação, com secret plaintext apenas no ato.

## 9. Requisitos Não-Funcionais

- **NFR1** — endpoints em `/v1/public-api/clients`, fora de `/v1/public/**` e `/manager/**`, na chain humana com `@HasPermission(SecurityConstants.AGENT_MANAGE)`.
- **NFR2** — H3: 375px e 1280px, foco visível, console/network limpos, estados vazios/carregamento/erro (gate aplicável nesta ADR).
- **NFR3** — suíte completa sem regressão; nenhum teste apagado ou desabilitado.
- **NFR4** — zero diff em `Mfa*`, `workflow-state.json`, migrations e no contrato da ADR-001.
- **NFR5** — nenhuma recomendação de biblioteca sem deepsearch (URL + data).

## 10. Critérios de Aceite

- ITs de CRUD/paginação, rotação (plaintext devolvido), revogação e permissão (`AGENT_MANAGE`) verdes; anônimo → 401/403.
- Listagem/get nunca contém `secret`, `secret_hash` ou `previous_secret_hash`; `previousSecretExpiresAt` é timestamp.
- Token emitido com secret anterior dentro da janela continua válido; fora dela, 401; revogação gera 401 imediato.
- UI em `admin/public-clients/` responsiva em 375/1280, secret visível uma vez só no create/rotate, console/network limpos, stories/testes verdes.
- Contrato público da ADR-001 e zero diff em MFA/workflow-state/migrations confirmados.

## 11. Riscos

| Risco | Mitigação |
|---|---|
| Vazamento de secret na UI/listagem | resposta de create/rotate exclusiva e one-shot; IT de contrato; nunca persistir plaintext nem logar |
| `rotateSecret` continuar `void` e quebrar a promessa de plaintext | mudança de assinatura explicitada em RF3/ADM03 + IT de rotação |
| Exposição indevida da API admin | chain humana + `AGENT_MANAGE`; `/v1/public-api/clients` (não `/v1/public/**` nem `/manager/**`) |
| Divergência UI × CLI em grants/prompts | regra extraída para método compartilhado (ADR Decisão 2c) |
| UI fora do padrão do design system | área nova `admin/public-clients/` seguindo Container/View/stories/teste/i18n de `admin/`; H3 |
| Campo de cota por client enganoso | não exibir `rate_limit_per_minute`; cota é da ADR-004 |
| Permissão ampla demais | `AGENT_MANAGE` reusa precedente; dívida de RBAC por capability registrada, fora desta ADR |

## 12. Fontes

Internas (revisão 2026-11-22): `.spec/prd/public-chat-api/EXPLORATION.md` §2, `DESIGN-DECISIONS.md` Decisão 3, `ARCHITECT-REVIEW.md`; `SecurityConstants.java:17-31`; `SecurityTokenServiceImpl.java:175-182`; `SecurityConfig.java:45,76,85`; `CredentialControllerV1.java:34,48,59,70`; `V1__public_api_client.sql:28-38,44-84`; `PublicApiClientServiceImpl.java:47-108`; `PublicApiClientService.java:6-12`; `PublicClientTokenIssuer.java:66-133,172-193`; `PublicApiProvisioningRunner.java:86-130`; `frontend/src/services/api.ts:46`; `frontend/src/modules/hive/admin/` (i18n, AdminStates, padrão View/Container/stories/teste). Externa: `https://www.rfc-editor.org/rfc/rfc6749.html` (§2.3.1, §4.4, §5.1) — 2026-11-21.
