<!-- scope: adr/ADR-001 -->
# TASKS — ADR-001: Contrato tipado e especificação OpenAPI da API pública

> Backlog TDD-ready derivado 1:1 da `SPEC.md`. Teste antes da implementação em toda tarefa. Gate de spec vem primeiro: sem ele, nenhuma anotação é escrita.

| ID | RF/NFR | Prioridade | Risco | Tipo | Depende de |
|---|---|---|---|---|---|
| CT01 | NFR1, AC-1 | P0 | Alto | infra (pom) | — |
| CT02 | RF3, AC-1 | P0 | Alto | config | CT01 |
| CT03 | RF1, AC-3 | P0 | Alto | contrato | CT02 |
| CT04 | RF2, RF4, RF5, AC-2 | P0 | Médio | refactor | CT03 |
| CT05 | NFR2, NFR3, AC-4, AC-5 | P0 | Alto | qualidade | CT04 |

---

### CT01 — Upgrade springdoc e gate de spec

- **RF/NFR:** NFR1 · **Prioridade:** P0 · **Risco:** Alto · **Tipo:** infra (pom)
- **Arquivos:** `backend/dependencies/pom.xml`; nenhum controller neste passo.
- **Teste primeiro (hipótese, não fato):** medir `GET /v3/api-docs` com `2.8.14` em Boot `4.1.0`. Hipótese a verificar no gate: a versão atual **pode** falhar ou devolver paths vazios. Registrar status e paths observados com data antes de qualquer conclusão — não afirmar falha sem medição.
- **Cenários:**
  - Baseline: executar o gate na versão atual e anotar resultado; se 200 com paths preenchidos, registrar hipótese refutada e seguir com o upgrade pela matriz oficial, sem alegar defeito.
  - Sucesso: `3.2.0` resolvido; `/v3/api-docs` → 200 **autenticado na chain humana**, 401 anônimo.
  - Falha: testar `3.1.1`; se ainda falhar, parar e reportar — não refundar o contrato.
  - Borda: nenhuma mudança em `Mfa*`, `workflow-state.json` ou configs de harness.
- **DoD:** versão declarada; gate de spec documentado com data, resultado medido da hipótese e credencial usada; suíte verde.

### CT02 — Group `public` e exposição controlada

- **RF/NFR:** RF3 · **Prioridade:** P0 · **Risco:** Alto · **Tipo:** config
- **Arquivos:** configuração springdoc do módulo bootstrap/web; `backend/security/impl/.../SecurityConfig.java:77-87` (exposição da spec).
- **Teste primeiro:** spec sem group não isola `/v1/public/**`; `/v3/**` hoje é `permitAll` — decidido: autenticar.
- **Cenários:**
  - Sucesso: `?group=public` devolve apenas os dois paths públicos, com sessão humana.
  - Falha: paths internos vazando para o group → ajustar `pathsToMatch`.
  - Borda (decisão já tomada, não reabrir): remover `/v3/**`, `/swagger-resources/**` e `/scalar/**` do `permitAll`; caem em `.anyRequest().authenticated()` (`:88`). Outros padrões do bloco intactos. Sem sessão → 401.
- **DoD:** group verificável por comando autenticado; 401 anônimo em `/v3/api-docs`; decisão de exposição registrada na ADR (Decisão 6) e na SPEC (§4).

### CT03 — DTOs tipados de response

- **RF/NFR:** RF1 · **Prioridade:** P0 · **Risco:** Alto · **Tipo:** contrato
- **Arquivos:** `backend/publicapi/api/src/main/java/.../dto/` (novos DTOs), `PublicChatServiceImpl.java`.
- **Teste primeiro:** IT de caracterização serializa a response atual e congela `choices`, `usage`, `metadata.hive`.
- **Cenários:**
  - Sucesso: serialização idêntica à baseline; `Map` removido do caminho público.
  - Falha: qualquer divergência de JSON → corrigir mapeamento antes de seguir.
  - Borda: `prompt_code` e `metadata.hive` permanecem como extensões explícitas.
- **DoD:** baseline preservado; nenhum campo OpenAI canônico alterado.

### CT04 — DTO duplicado, anotações e erros

- **RF/NFR:** RF2, RF4, RF5 · **Prioridade:** P0 · **Risco:** Médio · **Tipo:** refactor
- **Arquivos:** `backend/publicapi/web/internal/v1/.../dto/ChatCompletionRequestDTO.java` (remoção), controllers, `OpenAiErrorResponse`.
- **Teste primeiro:** `grep` encontra duas classes `ChatCompletionRequestDTO` em `publicapi/**`.
- **Cenários:**
  - Sucesso: uma única classe em `publicapi/api`; controllers importam-na.
  - Falha: erro de compilação por import antigo → corrigir.
  - Borda: `@ApiResponse` nos handlers locais sem criar advice global. No token endpoint, documentar só 400 `invalid_request` e 401 `invalid_client` — **nenhum `@ApiResponse` 429** neste ADR (bugfix separado, ADR-004).
- **DoD:** zero duplicata; envelope de erro idêntico; `unknown_model` e `streaming_not_supported` preservados; contrato do token endpoint congelado como 400 + 401.

### CT05 — Evidência e fechamento do ADR

- **RF/NFR:** NFR2, NFR3 · **Prioridade:** P0 · **Risco:** Alto · **Tipo:** qualidade
- **Arquivos:** nenhum de produto neste passo; relatório de evidência.
- **Teste primeiro:** suíte completa e `git diff --name-only` antes do review.
- **Cenários:**
  - Sucesso: suíte 0 falhas; front build 0 erros; spec com schemas; `code-reviewer` sem 🔴.
  - Falha: qualquer regressão → loop de correção (máx. 3 iterações).
  - Borda: `git diff` confirma nenhum `@ExceptionHandler(QuotaExceededException)` novo em `PublicAuthControllerV1` (429 é bugfix separado, fora deste ADR); `brain_store` + `workflow-state.json` atualizados; portas livres.
- **DoD:** `VERIFY.md` gerado somente após merge; H6 verde antes do merge.
