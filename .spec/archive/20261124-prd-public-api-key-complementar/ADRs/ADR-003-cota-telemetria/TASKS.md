<!-- scope: adr/ADR-003 -->
# TASKS — ADR-003: Cota própria da key e telemetria preservada

> Backlog TDD-ready derivado 1:1 da `SPEC.md`. Teste antes da implementação. Decisões fechadas: V4 com default 100 e backfill da cota do tenant; namespace `quota-key:{keyId}`; propagação por `AuthenticatedKey` + atributos `publicapi.keyId`/`publicapi.keylim` só no ramo key. Nenhuma mudança no caminho JWT.

| ID | RF/NFR | Prioridade | Risco | Tipo | Depende de |
|---|---|---|---|---|---|
| COT01 | NFR1 | P0 | Médio | teste | — |
| COT02 | RF1 | P0 | Alto | migration | COT01 |
| COT03 | RF2, RF3 | P0 | Alto | autenticação/cota | COT02 |
| COT04 | RF4 | P0 | Médio | telemetria | COT03 |
| COT05 | NFR1..4 | P0 | Médio | evidência | COT04 |

---

### COT01 — Baseline de cota e telemetria

- **RF/NFR:** NFR1 · **Prioridade:** P0 · **Risco:** Médio · **Tipo:** teste
- **Arquivos:** nenhum de produto; ITs de cota/contrato/trace existentes.
- **Teste primeiro:** registrar que JWT e key usam hoje `plim.r` do tenant no mesmo contador `quota-tenant:{tenantId}`; `turnId`/`clientId` já preenchidos; fail-open sem headers.
- **Cenários:** sucesso = baseline verde; falha = divergência prévia → parar; borda = `plim` m/c/t/r e emissor inalterados.
- **DoD:** baseline documentado; nenhum teste removido.

### COT02 — Migration V4 do limite por key

- **RF/NFR:** RF1 · **Prioridade:** P0 · **Risco:** Alto · **Tipo:** migration
- **Arquivos:** `V4__public_api_client_key_chat_rpm.sql`, entidade da key.
- **Teste primeiro:** IT em Postgres real afirma `key_chat_requests_per_minute INT NOT NULL DEFAULT 100`, CHECK 1..10000 e **backfill copiando `public_api_tenant_quota.chat_requests_per_minute`** (fallback 100 para tenant sem linha).
- **Cenários:** sucesso = V1/V2/V3 intactas, sem `repair()`/`DROP`; falha = migração duplicada ou nome genérico → parar; borda = tenant com cota customizada mantém o valor efetivo.
- **DoD:** migration aplicada; IT verde; nenhuma linha de key com limite fora de 1..10000.

### COT03 — Propagação do limite e headers

- **RF/NFR:** RF2, RF3 · **Prioridade:** P0 · **Risco:** Alto · **Tipo:** autenticação/cota
- **Arquivos:** `PublicApiKeyAuthenticator` (+ record), `PublicApiKeyAuthenticatorImpl`, `PublicApiSecurityFilter`, `RedisQuotaGuard` (`keyKey`/`acquireKey`), `PublicChatServiceImpl`, ITs.
- **Teste primeiro:** IT no **mesmo tenant** com key limite X e JWT limite Y — contadores `quota-key:{keyId}` e `quota-tenant:{tenantId}` independentes, headers com o limite de cada um.
- **Cenários:** sucesso = `AuthenticatedKey` leva `keyId`/limite, filtro seta `publicapi.keyId`/`publicapi.keylim` só no ramo key; falha = JWT passar a usar limite da key ou `plim` mudar → corrigir; borda = Lua e fail-open inalterados.
- **DoD:** ITs verdes; `X-RateLimit-*` refletem `QuotaDecision.limit()`; fail-open (`measured=false`) omite headers; zero diff no emissor de JWT.

### COT04 — Telemetria preservada

- **RF/NFR:** RF4 · **Prioridade:** P0 · **Risco:** Médio · **Tipo:** telemetria
- **Arquivos:** nenhum de produto; IT de trace.
- **Teste primeiro:** IT afirma `turnId`/`clientId` preenchidos no caminho da key e do JWT; `ChatCallerParityIT` reexecutado.
- **Cenários:** sucesso = ambos preenchidos sem distinção por `null`; falha = qualquer `null` → corrigir; borda = `ChatTurnGateway`/`TurnRequest`/`TurnReply` e `TurnOutcomeMapper` sem diff.
- **DoD:** IT verde; paridade verde; zero diff no gateway e na trace.

### COT05 — Evidência e fechamento

- **RF/NFR:** NFR1..4 · **Prioridade:** P0 · **Risco:** Médio · **Tipo:** evidência
- **Arquivos:** nenhum de produto; relatório H1–H6.
- **Teste primeiro:** suíte completa e `git diff --name-only` filtrado.
- **Cenários:** sucesso = 0 falhas, build 0, review sem 🔴, `VERIFY.md` após merge; falha = loop de correção, máx. 3 iterações; borda = zero diff em `plim`/emissor, gateway, trace, prompt, tabela V2, ADR-001/002, PRD/RESEARCH, arquivadas, MFA, estado, UI; portas por PID, nunca `pkill -f`.
- **DoD:** brain_store + harness report; sem commit antes da autorização.
