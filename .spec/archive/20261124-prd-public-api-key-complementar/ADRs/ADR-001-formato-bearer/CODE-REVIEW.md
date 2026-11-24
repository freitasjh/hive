# CODE-REVIEW — ADR-001: Formato Bearer `sk-` e contrato OpenAPI

**Data:** 2026-11-23 · **Branch:** `adr/public-api-key-complementar/01-formato-bearer` (worktree isolada `/tmp/opencode/hive-apikey`) · **Árvore:** sem commit no momento do veredito.

## Veredito

**APPROVED** — 0 🔴 · 0 🟠 · 0 🟡 · 1 🔵 · `must_fix_before_done: []`. `reviewer_session`: `ses_dffa34effffeZMrCoNwL2t4pqv`.

## Guardas verificadas

| # | Guarda | Resultado |
|---|---|---|
| 1 | Prefixo `sk-` exato case-sensitive, sem fallback; `sk-` vazio, `sk-eyJ...` e `SK-` → 401; JWT intacto; `ObjectProvider` com fail-closed; mesmo principal/authorities/attributes/TenantContext | ✅ |
| 2 | Porta `PublicApiKeyAuthenticator` em `publicapi/api`; aresta `security/impl → publicapi/api` explícita; nenhuma outra dependência nova; nenhum bean duplo | ✅ |
| 3 | `bearer-key` preservado + `apiKeyAuth`, ambos Bearer, só na operação de chat; token endpoint inalterado; zero `bearerAuth`/`x-goog-api-key` em produção | ✅ |
| 4 | Baseline 411 front + 526 backend + 133 ITs; pós 411 + 526 + 139 ITs (+6); build 0; `git diff --check` limpo; stub substitui só a porta, filtro real via MockMvc | ✅ |
| 5 | Zero diff em PRD/RESEARCH/ADR-INDEX/ADRs, `.spec/archive/**`, MFA, `workflow-state.json`, AGENTS.md, skills, frontend, emissão/cota/telemetria; nenhum teste apagado/`@Disabled` | ✅ |
| 6 | R2 (5432/DBeaver preservada; 8080/5433/6379 livres; sem `pkill -f`) e R4 (brain_store) presentes | ✅ |

## 🔵 Dívida de ambiente (pré-existente, não bloqueante)

`.gitignore:32` (`*credentials*`) ignora `frontend/src/modules/hive/admin/credentials/`, que existe somente na worktree original; clean checkout falha 2 testes de import. O diff desta ADR não toca frontend nem depende desses arquivos. Correção futura: trocar a regra por `secrets/` com negação explícita, ou adicionar `!frontend/src/modules/hive/admin/credentials/**` após a linha 32. Registrar como dívida de repo, fora desta ADR.

## Risco residual

Em produção, `ObjectProvider.getIfAvailable()` devolve `null` até a ADR-002 registrar o bean — todo `sk-` recebe 401 por desenho. Nenhum outro módulo deve registrar `PublicApiKeyAuthenticator` acidentalmente (um único bean).

## Bloqueio de âncora

O bloqueio real é a âncora B do `gate.js` lendo `code_review_status`. Este artefato e o registro escopado `workflow-state.json.code_reviews_by_adr[...]` tornam o veredito verificável sem alterar `current_phase` nem o registro da Fase 4 MFA.
