<!-- scope: adr/ADR-001 -->
# TASKS — ADR-001: Formato Bearer `sk-` e contrato OpenAPI

> Backlog TDD-ready derivado 1:1 da `SPEC.md`. Teste antes da implementação. Esta ADR não emite key: o teste positivo usa stub da porta `PublicApiKeyAuthenticator`; a implementação real é da ADR-002. Regra fechada: `sk-` exato, sem fallback.

| ID | RF/NFR | Prioridade | Risco | Tipo | Depende de |
|---|---|---|---|---|---|
| KEY01 | NFR1 | P0 | Médio | teste | — |
| KEY02 | RF1, RF2 | P0 | Alto | filtro | KEY01 |
| KEY03 | RF3 | P0 | Médio | contrato/OpenAPI | KEY02 |
| KEY04 | NFR1..4 | P0 | Médio | evidência | KEY03 |

---

### KEY01 — Baseline de autenticação atual

- **RF/NFR:** NFR1 · **Prioridade:** P0 · **Risco:** Médio · **Tipo:** teste
- **Arquivos:** nenhum de produto; ITs existentes de auth/cota/contrato.
- **Teste primeiro:** registrar 200 com JWT atual e 401 com token inválido no chat; registrar os esquemas atuais do `/v3/api-docs` (`bearer-key`).
- **Cenários:**
  - Sucesso: baseline verde idêntico ao do PRD anterior.
  - Falha: divergência prévia → parar.
  - Borda: contrato de erro, duplo `/v1`, `@HasPermission` e `ChatTrace` com `turnId`/`clientId` preservados.
- **DoD:** baseline documentado; nenhum teste removido.

### KEY02 — Discriminação no filtro M2M

- **RF/NFR:** RF1, RF2 · **Prioridade:** P0 · **Risco:** Alto · **Tipo:** filtro
- **Arquivos:** `publicapi/api` (porta `PublicApiKeyAuthenticator` + record de resultado), `security/impl` (`PublicApiSecurityFilter` via `ObjectProvider`; pom com `hive-publicapi-api`), testes unitários/IT com stub da porta.
- **Teste primeiro:** `sk-` exato ⇒ somente key; demais valores ⇒ somente JWT; falha em qualquer caminho ⇒ 401 imediato sem fallback.
- **Cenários:**
  - Sucesso: key (stub) e JWT retornam 200; key preenche `publicapi.clientId`/`pcode`/`plock`/`plim`, authorities e `TenantContext` com a mesma semântica do JWT.
  - Falha: key cair no validador de JWT (ou vice-versa), ou fallback após falha → corrigir.
  - Borda: `sk-` vazio e sem bean → 401; JWT sintético `sk-eyJ...` → 401; `SK-` maiúsculo segue JWT e falha → 401; `x-goog-api-key` presente é ignorado.
- **DoD:** ITs verdes; zero diff em chain humana, emissão/cota/telemetria e no token endpoint.

### KEY03 — OpenAPI com dois esquemas Bearer

- **RF/NFR:** RF3 · **Prioridade:** P0 · **Risco:** Médio · **Tipo:** contrato/OpenAPI
- **Arquivos:** `OpenApiConfig` (somar `apiKeyAuth`, **não renomear** `bearer-key`), `PublicChatControllerV1` (`@SecurityRequirements` com os dois nomes na operação de chat).
- **Teste primeiro:** `/v3/api-docs` declara só `bearer-key` — o novo esquema ainda não existe.
- **Cenários:**
  - Sucesso: `bearer-key` e `apiKeyAuth` presentes, ambos HTTP bearer, como alternativas na operação de chat; token endpoint e group `public` inalterados.
  - Falha: `bearer-key` renomeado, esquema duplicado ou `x-*` → corrigir.
  - Borda: nenhuma mudança de status/código de erro nem de paths.
- **DoD:** spec validada com os dois esquemas; clientes gerados existentes mantêm o método de `bearer-key`.

### KEY04 — Evidência e fechamento

- **RF/NFR:** NFR1..4 · **Prioridade:** P0 · **Risco:** Médio · **Tipo:** evidência
- **Arquivos:** nenhum de produto; relatório H1–H6.
- **Teste primeiro:** suíte completa e `git diff --name-only` filtrado.
- **Cenários:**
  - Sucesso: 0 falhas, build 0, review sem 🔴, `VERIFY.md` após merge.
  - Falha: loop de correção, máx. 3 iterações.
  - Borda: zero diff em ADRs arquivadas, PRD, RESEARCH, MFA, `workflow-state.json`, UI, emissão/rotação/revogação, cota, telemetria e token endpoint; portas por PID via lsof+kill, nunca `pkill -f`.
- **DoD:** brain_store + harness report; sem commit antes da autorização.
