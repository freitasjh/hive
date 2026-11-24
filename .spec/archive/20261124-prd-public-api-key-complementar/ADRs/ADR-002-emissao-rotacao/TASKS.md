<!-- scope: adr/ADR-002 -->
# TASKS — ADR-002: Emissão, hash, rotação e revogação da API key

> Backlog TDD-ready derivado 1:1 da `SPEC.md`. Teste antes da implementação. Decisões fechadas: migration V3, BCrypt custo 10, janela fixa 10 min, `POST` no revoke, bean único, chave Redis `hive:public:revoked-keys`. Cota/telemetria ficam na ADR-003.

| ID | RF/NFR | Prioridade | Risco | Tipo | Depende de |
|---|---|---|---|---|---|
| KEY2-01 | NFR1 | P0 | Médio | teste | — |
| KEY2-02 | RF1, RF2 | P0 | Alto | migração/serviço | KEY2-01 |
| KEY2-03 | RF2 | P0 | Alto | contrato HTTP | KEY2-02 |
| KEY2-04 | RF3, RF4 | P0 | Alto | rotação/revogação | KEY2-03 |
| KEY2-05 | NFR1..4 | P0 | Médio | evidência | KEY2-04 |

---

### KEY2-01 — Baseline do fail-closed

- **RF/NFR:** NFR1 · **Prioridade:** P0 · **Risco:** Médio · **Tipo:** teste
- **Arquivos:** nenhum de produto; ITs existentes de auth/cota/contrato.
- **Teste primeiro:** registrar que `sk-` sem bean devolve 401 (`PublicApiKeyNoBeanIT`), que JWT funciona e que `PublicClientTokenIssuer` produz `permissions`/`pcode`/`plock`/`plim` para o client baseline.
- **Cenários:** sucesso = baseline verde; falha = divergência prévia → parar; borda = contrato OpenAI e `plim` (`m/c/t/r`) preservados.
- **DoD:** baseline documentado; nenhum teste removido.

### KEY2-02 — Tabela e bean da porta

- **RF/NFR:** RF1, RF2 · **Prioridade:** P0 · **Risco:** Alto · **Tipo:** migração/serviço
- **Arquivos:** `V3__public_api_client_key.sql` em `publicapi_db`, entidade, repositório, impl de `PublicApiKeyAuthenticator` em `publicapi/impl`, `PublicClientTokenIssuer.policyFor` compartilhado. **Nenhum pom novo.**
- **Teste primeiro:** IT de migration em Postgres real; IT de autenticação com key gravada e claims idênticos ao JWT.
- **Cenários:** sucesso = tabela com colunas `key_*`, um bean `@Service`, `authenticate` devolve `null` na falha; falha = dois beans ou migration duplicada → parar; borda = V3 é a próxima livre, sem `repair()` e sem `DROP`.
- **DoD:** migration aplicada; IT verde; contexto Spring sobe com um único bean; `plim` sem mudança de valores.

### KEY2-03 — Emissão one-shot

- **RF/NFR:** RF2 · **Prioridade:** P0 · **Risco:** Alto · **Tipo:** contrato HTTP
- **Arquivos:** `PublicApiKeyControllerV1` em `publicapi/web/internal/v1` (`@RequestMapping(RestPath.V1 + "/public-api/clients")`), DTOs, serviço, ITs.
- **Teste primeiro:** IT afirma que plaintext aparece só em `POST /{clientId}/keys` e `POST /{clientId}/keys/{keyId}/rotate`.
- **Cenários:** sucesso = 201 com `api_key`, `keyId` e `clientId`; list/get sem plaintext nem hash; falha = vazamento em log/list → corrigir; borda = cross-tenant 404 e `AGENT_MANAGE` negado → 403.
- **DoD:** ITs verdes; `@HasPermission(AGENT_MANAGE)` em todas as operações; hash BCrypt custo 10 do valor completo `sk-...`.

### KEY2-04 — Rotação com janela e revogação

- **RF/NFR:** RF3, RF4 · **Prioridade:** P0 · **Risco:** Alto · **Tipo:** rotação/revogação
- **Arquivos:** serviço, repositório, ITs.
- **Teste primeiro:** IT com key antiga dentro dos 10 min → 200; fora → 401; `POST .../revoke` → 401 no próximo request.
- **Cenários:** sucesso = janela fixa de 10 min e revogação via `hive:public:revoked-keys` + `revoked_at`; falha = key antiga aceita fora da janela → corrigir; borda = tempo controlado no teste, sem `sleep` longo.
- **DoD:** ITs verdes; nenhuma credencial em log; chave Redis distinta de `hive:public:revoked-clients`.

### KEY2-05 — Evidência e fechamento

- **RF/NFR:** NFR1..4 · **Prioridade:** P0 · **Risco:** Médio · **Tipo:** evidência
- **Arquivos:** nenhum de produto; relatório H1–H6.
- **Teste primeiro:** suíte completa e `git diff --name-only` filtrado.
- **Cenários:** sucesso = 0 falhas, build 0, review sem 🔴, `VERIFY.md` após merge; falha = loop de correção, máx. 3 iterações; borda = zero diff semântico em `plim`, `ChatTurnGateway`, `ChatTrace`, `TurnOutcomeMapper`, ADR-001/PRD/RESEARCH/arquivadas/MFA/estado/UI/cota/telemetria, portas por PID (lsof+kill), nunca `pkill -f`.
- **DoD:** brain_store + harness report; sem commit antes da autorização.
