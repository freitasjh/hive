<!-- scope: adr/ADR-004 -->
# TASKS — ADR-004: Cota por tenant e bugfix do 429 no token

> Backlog TDD-ready derivado 1:1 da `SPEC.md`. Teste antes da implementação em toda tarefa. Migration roda em banco real (IT), não em mock. Lua do guard não muda; superscrição do 400 da ADR-001 é registrada na ADR-004, sem editar a ADR-001.

| ID | RF/NFR | Prioridade | Risco | Tipo | Depende de |
|---|---|---|---|---|---|
| QT01 | RF1, AC-5 | P0 | Alto | migration | — |
| QT02 | RF2 | P0 | Alto | contrato/JWT | QT01 |
| QT03 | RF3, AC-1, AC-2 | P0 | Alto | serviço | QT02 |
| QT04 | RF4, RF5, AC-3, AC-4 | P0 | Alto | contrato HTTP | QT03 |
| QT05 | NFR1, AC-6 | P0 | Alto | resiliência | QT04 |
| QT06 | NFR2, NFR3 | P0 | Médio | evidência | QT05 |

---

### QT01 — Migration V2 e backfill

- **RF/NFR:** RF1 · **Prioridade:** P0 · **Risco:** Alto · **Tipo:** migration
- **Arquivos:** `backend/publicapi/dbupdate/src/main/resources/db/migration/publicapi_db/V2__*.sql`.
- **Teste primeiro:** IT afirma que a tabela não existe antes da migration e existe depois, com uma linha por tenant de `public_api_client`.
- **Cenários:**
  - Sucesso: `chat_requests_per_minute` default 100, `CHECK 1..10000`, backfill com 100.
  - Falha: migration não idempotente ou backfill duplicado → parar.
  - Borda: V2 é a próxima livre (`publicapi_db/` tem só V1); `PublicApiDbUpdateConfig` usa `baselineOnMigrate(true)` e **sem `repair()`** — não alterar. Nenhum `DROP` da coluna antiga.
- **DoD:** migration aplicada em banco real; rollback/forward documentado.

### QT02 — Claim `plim.r` no JWT

- **RF/NFR:** RF2 · **Prioridade:** P0 · **Risco:** Alto · **Tipo:** contrato/JWT
- **Arquivos:** entidade + `PublicApiTenantQuotaRepository` em `publicapi/impl` (sem nova aresta de módulo), `PublicClientTokenIssuer`, testes de emissão.
- **Teste primeiro:** token emitido para tenant com limite 250 contém `plim.r = 250`; tenant sem linha na tabela → fallback 100, nunca erro de emissão.
- **Cenários:**
  - Sucesso: claim `r` presente; `m`/`c`/`t` inalterados — consumidores leem por chave (`PublicChatServiceImpl:120-122`, `PublicApiSecurityFilter:87-88`).
  - Falha: tenant sem cota → fallback 100; tokens antigos sem `r` → fallback 100 no chat.
  - Borda: `aud=hive.public`, `tenant-id` e `permissions` inalterados.
- **DoD:** ITs de emissão verdes; chave JWT inalterada; `git diff` dos poms vazio.

### QT03 — Guard por tenant no chat

- **RF/NFR:** RF3 · **Prioridade:** P0 · **Risco:** Alto · **Tipo:** serviço
- **Arquivos:** `PublicChatServiceImpl:140`, testes de cota.
- **Teste primeiro:** IT com dois clients do mesmo tenant — o segundo recebe 429; outro tenant segue 200.
- **Cenários:**
  - Sucesso: `checkTenant(tenantId, limit(plim, "r", 100))`; chave `RedisQuotaGuard.tenantKey` (`hive:public:quota-tenant:{tenantId}`).
  - Falha: cliente do tenant A impactar tenant B → parar.
  - Borda: `checkClient` sai do caminho de chat; token endpoint mantém 30/120/30 fixos.
- **DoD:** ITs de competição e isolamento verdes; script Lua sem diff.

### QT04 — Headers e bugfix do 429 no token

- **RF/NFR:** RF4, RF5 · **Prioridade:** P0 · **Risco:** Alto · **Tipo:** contrato HTTP
- **Arquivos:** `RedisQuotaGuard` (método de decisão + metadados no `QuotaExceededException`), `PublicAuthControllerV1`, `PublicChatControllerV1`, ITs de contrato.
- **Teste primeiro:** IT do token endpoint com cota estourada espera 429 + `Retry-After` e envelope OpenAI `quota_exceeded`, não mais 400; IT do chat espera `X-RateLimit-{Limit,Remaining,Reset}-Requests` no 429 e no sucesso.
- **Cenários:**
  - Sucesso: guard devolve limit/remaining/retryAfter/reset; `reset` em epoch seconds derivado em Java; Lua inalterado; handler local tem precedência sobre `ControllerExceptionHandler`.
  - Falha: handler genérico (`PublicAuthControllerV1:156-163`) ainda interceptar `QuotaExceededException` → corrigir; `problem+json` vazando → corrigir.
  - Borda: token endpoint mantém só `Retry-After` (três buckets, sem agregação); limites 30/120/30 fixos; superscrição do 400 da ADR-001 registrada na ADR, ADR-001 não editada.
- **DoD:** ITs verdes; `git diff` do Lua vazio; contrato OpenAI preservado.

### QT05 — Resiliência do guard

- **RF/NFR:** NFR1 · **Prioridade:** P0 · **Risco:** Alto · **Tipo:** resiliência
- **Arquivos:** testes de `RedisQuotaGuard`.
- **Teste primeiro:** simular erro Redis e confirmar que o turno segue (fail-open) com log.
- **Cenários:**
  - Sucesso: indisponibilidade não derruba o turno e os headers `X-RateLimit-*` são **omitidos** (não inventados).
  - Falha: exceção vazar para o controller → corrigir.
  - Borda: sufixo aleatório do Lua preservado (concorrência na mesma janela); script sem diff.
- **DoD:** teste de fail-open verde; nenhuma dependência dura de Redis.

### QT06 — Evidência e fechamento

- **RF/NFR:** NFR2, NFR3 · **Prioridade:** P0 · **Risco:** Médio · **Tipo:** evidência
- **Arquivos:** nenhum de produto; relatório H1–H6.
- **Teste primeiro:** suíte completa e `git diff --name-only` filtrado.
- **Cenários:**
  - Sucesso: 0 falhas, build 0, `code-reviewer` sem 🔴, H6 verde, `VERIFY.md` após merge.
  - Falha: loop de correção, máx. 3 iterações.
  - Borda: zero diff em Mfa*/workflow-state/UI da ADR-003/ADR-001/002/003 e no Lua do guard; superscrição do 400 registrada apenas na ADR-004; portas encerradas por PID via lsof + kill, nunca `pkill -f`.
- **DoD:** brain_store + harness report; sem commit antes da autorização.
