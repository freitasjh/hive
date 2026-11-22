<!-- scope: adr/ADR-003 -->
# TASKS — ADR-003: Gestão de credenciais com UI admin

> Backlog TDD-ready derivado 1:1 da `SPEC.md`. Teste antes da implementação em toda tarefa. H3 é gate desta ADR (há UI). Decisões do parecer 2026-11-22 já aplicadas: permissão `AGENT_MANAGE`, path `/v1/public-api/clients`, paginação `page`/`size`, `client_id` gerado no servidor.

| ID | RF/NFR | Prioridade | Risco | Tipo | Depende de |
|---|---|---|---|---|---|
| ADM01 | RF1, NFR1 | P0 | Alto | contrato | — |
| ADM02 | RF2, RF5 | P0 | Alto | serviço | ADM01 |
| ADM03 | RF3, RF4 | P0 | Alto | correção/serviço | ADM02 |
| ADM04 | RF6, NFR2 | P0 | Alto | frontend | ADM03 |
| ADM05 | NFR1..4 | P0 | Alto | segurança/qualidade | ADM04 |
| ADM06 | NFR3, NFR4 | P0 | Médio | evidência | ADM05 |

---

### ADM01 — Contrato da API admin

- **RF/NFR:** RF1, NFR1 · **Prioridade:** P0 · **Risco:** Alto · **Tipo:** contrato
- **Arquivos:** `backend/publicapi/web/internal/v1/.../controller/PublicApiClientControllerV1.java` + DTOs; `@RequestMapping(RestPath.V1 + "/public-api/clients")`.
- **Teste primeiro:** IT afirma anônimo → 401/403, portador sem `AGENT_MANAGE` → 403, e que o path não casa `securityMatcher("/v1/public/**")` (`SecurityConfig:45,76`).
- **Cenários:**
  - Sucesso: `GET /`, `GET /{clientId}`, `POST /`, `POST /{clientId}/rotate`, `POST /{clientId}/revoke`, `PUT /{clientId}/grants`, `PUT /{clientId}/prompts` — todos com `@HasPermission(SecurityConstants.AGENT_MANAGE)`.
  - Falha: uso de `/v1/public/**` (chain M2M) ou `/manager/**` (`permitAll`, `:85`) → parar.
  - Borda: lista com `?page=0&size=20` (teto 100); contrato da ADR-001 inalterado.
- **DoD:** permissão e path registrados na ADR (Decisão 3); IT verde.

### ADM02 — Criar, listar e gerenciar grants/pin

- **RF/NFR:** RF2, RF5 · **Prioridade:** P0 · **Risco:** Alto · **Tipo:** serviço
- **Arquivos:** `publicapi/impl` (serviço de gestão), 3 repositórios existentes + `findByTenantId(tenantId, Pageable)`; regra de grant/prompt extraída de `PublicApiProvisioningRunner:104-124`.
- **Teste primeiro:** `client_id` gerado no servidor e imutável; `label` repetido no mesmo tenant falha (`V1:29`); listagem paginada não contém secret nem hash.
- **Cenários:**
  - Sucesso: criação com grants/pin; CLI e UI usam o mesmo método de escrita de grant/prompt.
  - Falha: `client_id` fornecido pelo cliente ou troca de tenant → rejeitar.
  - Borda: `rate_limit_per_minute` não é exposto; `previousSecretExpiresAt` é timestamp, sem hash.
- **DoD:** ITs verdes; nenhuma migration nova.

### ADM03 — Rotação e revogação

- **RF/NFR:** RF3, RF4 · **Prioridade:** P0 · **Risco:** Alto · **Tipo:** correção/serviço
- **Arquivos:** `PublicApiClientService` (assinatura), `PublicApiClientServiceImpl:73-85`, serviço de gestão, ITs de token.
- **Teste primeiro:** `rotateSecret` **devolve o plaintext** (hoje é `void` e descarta — `:84`); token com secret anterior dentro da janela de 10 min ainda emite; revogação gera 401 imediato.
- **Cenários:**
  - Sucesso: janela `previous_secret_hash`/`previous_secret_expires_at` preservada; revogação Redis+DB já implementada (`PublicClientTokenIssuer:182-191`) — não reimplementar.
  - Falha: secret anterior aceito fora da janela → corrigir; plaintext em log → bloquear.
  - Borda: plaintext de secret nunca em listagem/get/log; nenhum hash exposto.
- **DoD:** ITs verdes; Decisão 7 da ADR-001 intacta.

### ADM04 — UI admin

- **RF/NFR:** RF6, NFR2 · **Prioridade:** P0 · **Risco:** Alto · **Tipo:** frontend
- **Arquivos:** `frontend/src/modules/hive/admin/public-clients/` (View, Container, `.stories.tsx`, `.test.tsx`), `frontend/src/services/public-api-admin.service.ts`, tipos em `admin.types.ts`, chaves em `admin/i18n.ts`, export em `admin/index.ts`. **Não** reutilizar `admin/credentials/` (providers LLM).
- **Teste primeiro:** View renderiza estados vazio/carregando/erro; secret aparece uma vez no create/rotate (diálogo copiar/dispensar) e nunca em listagem; badge da janela usa `previousSecretExpiresAt`; revoked desabilita ações.
- **Cenários:**
  - Sucesso: criar/listar(paginado)/rotacionar/revogar pela UI.
  - Falha: secret ou hash em listagem/get → bloquear.
  - Borda: 375px e 1280px, foco visível, i18n sem literal solto, `apiRequest("/public-api/clients")` (base `/hive/v1`).
- **DoD:** Vitest verde; H3 com console/network limpos e screenshots.

### ADM05 — Segurança e não-regressão

- **RF/NFR:** NFR1..4 · **Prioridade:** P0 · **Risco:** Alto · **Tipo:** segurança/qualidade
- **Arquivos:** nenhum de produto neste passo; evidência.
- **Teste primeiro:** `git diff --name-only` e suíte completa.
- **Cenários:**
  - Sucesso: zero diff em `Mfa*`, `workflow-state.json`, migrations e contrato ADR-001; `AGENT_MANAGE` verificado nos dois sentidos; secret/hash vazado em log → reprovar.
  - Falha: qualquer regressão → loop de correção, máx. 3 iterações.
  - Borda: nenhuma credencial nova em fixtures além do padrão pré-existente.
- **DoD:** security review registrado; code-reviewer sem 🔴.

### ADM06 — Evidência e fechamento

- **RF/NFR:** NFR3, NFR4 · **Prioridade:** P0 · **Risco:** Médio · **Tipo:** evidência
- **Arquivos:** nenhum de produto; relatório H1–H6.
- **Teste primeiro:** suíte 0 falhas, build 0, H3 verde, git diff limpo.
- **Cenários:**
  - Sucesso: `VERIFY.md` após merge; índice `merged`.
  - Falha: reverificar e corrigir antes de prosseguir.
  - Borda: portas encerradas por PID via lsof + kill; nunca `pkill -f`.
- **DoD:** brain_store + harness report; sem commit antes da autorização.
