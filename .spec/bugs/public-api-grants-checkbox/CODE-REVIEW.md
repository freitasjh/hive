# CODE-REVIEW — Bugfix grants por checkbox

**Data:** 2026-11-24 · **Branch:** `fix/public-api-grants-checkbox` (de `main` @ `a9cfd7f`) · **Árvore:** sem commit no momento do veredito.

## Veredito

**APPROVED** — 0 🔴 · 0 🟠 · 0 🟡 · 0 🔵 · `must_fix_before_done: []`. `reviewer_session`: `ses_dffa34effffeZMrCoNwL2t4pqv`.

## Guardas verificadas

| # | Guarda | Resultado |
|---|---|---|
| 1 | R1 documentada antes da edição (201 com `GRANT_INEXISTENTE` → claim inútil → 500 no chat); `validateGrants` com catálogo fechado e 400; update inválido não apaga grants persistidos | ✅ |
| 2 | Sem input livre; `PUBLIC_GRANTS` no front; `grants: string[]` em state/payload (contrato HTTP inalterado); checkbox com `Label htmlFor`, `fieldset/legend`, 44px, foco visível, i18n pt/en | ✅ |
| 3 | H3 com fixture + chrome-devtools: 1280/375, console sem ERROR/WARN, network só 200/201, POST 201 com grant correto, secret one-shot, 3 screenshots, portas encerradas por PID | ✅ |
| 4 | 411→413 testes front, 530 backend, 152→155 ITs, build 0, `git diff --check` limpo, nenhum teste apagado/`@Disabled` | ✅ |
| 5 | Zero diff em `PublicChatControllerV1`, prompt/pin/cota/TTL/limites, ADRs, `.spec/archive/**`, MFA, `workflow-state.json`, AGENTS.md, skills, configs | ✅ |
| 6 | Riscos separados e não tocados: catálogo duplicado front/back e 500 pré-existente do chat para `AccessDeniedException` | ✅ |

## Riscos residuais

- Catálogo duplicado: `form.ts:5` e `PublicApiClientAccessWriter.java:23`. Se o backend adicionar grant, o front precisa acompanhar manualmente.
- `PublicChatControllerV1` converte `AccessDeniedException` em 500 `internal_error` (encontrado na R1) — pré-existente, fora deste bug, registrado como dívida separada.

## Bloqueio de âncora

Parecer é parecer; bloqueio é a âncora B lendo `code_review_status`. Registro escopado no estado + este artefato tornam o veredito verificável.
