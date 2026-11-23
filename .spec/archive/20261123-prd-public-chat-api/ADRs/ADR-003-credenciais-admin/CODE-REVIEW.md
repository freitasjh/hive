# CODE-REVIEW — ADR-003 (gestão de credenciais com UI admin)

**Data:** 2026-11-22 · **Branch:** `adr/public-chat-api/03-credenciais-admin` · **Árvore:** sem commit no momento do veredito.

## Veredito

**APPROVED** — 0 🔴 · 0 🟠 · 0 🟡 · 0 🔵 · `must_fix_before_done: []`. `reviewer_session`: `ses_dffa34effffeZMrCoNwL2t4pqv`. Cobriu backend (22 arquivos) e frontend (11 arquivos).

## Guardas verificadas

| # | Guarda | Resultado |
|---|---|---|
| 1 | Contrato HTTP: GET paginado, POST 201 com `client_secret` one-shot, rotate 200 com plaintext e `previous_secret_expires_at`, revoke 204, PUT grants/prompts, 401/403/404, `problem+json`; zero secret/hash em list/get/log | ✅ |
| 2 | `rotateSecret` devolve plaintext e preserva janela; CLI e API usam `PublicApiClientAccessWriter`; revogação Redis+DB reaproveitada; issuer inalterado | ✅ |
| 3 | `AGENT_MANAGE` nos 7 endpoints; path `/v1/public-api/clients` fora de `/v1/public/**` e `/manager/**`; teto 100; `client_id`/`tenantId` rejeitados; cross-tenant 404 | ✅ |
| 4 | Frontend com allowlist anti-vazamento, plaintext one-shot isolado em `useState`, sem storage/URL/log; estados completos; i18n/a11y; H3 preparada | ✅ |
| 5 | ADR-001/002 intactas (Decisão 7 preservada); zero diff em `Mfa*`/`workflow-state.json`/migrations/`.flattened-pom.xml`; nenhum teste apagado ou `@Disabled` | ✅ |
| 6 | Evidência declarada consistente: front 378 → 411, ITs 119 → 129, build 0, `validate.mjs` com 112 bloqueios pré-existentes e zero nos arquivos novos. Divergência de contagem backend (523 declarado vs +1 teste no diff) a ser confirmada pelo QA na reexecução | ⚠️ observação |

## Desvios avaliados e aceitos

Rota `#providers/public-clients` (ADR não fixou rota); `apiRequest` em vez de axios (não existe axios no repo); `updateGrants`/`updatePrompts` sem botão na UI (RF6/ADM04 cobrem criar/listar/rotacionar/revogar). Nenhum descumpre ADR/SPEC.

## Débito registrado (não bloqueante)

`PublicApiClientAdminServiceImpl.list()` faz 1+2N queries (41 com `size=20`; 201 com `size=100`). Otimização sugerida: buscar grants e prompts em lote com `findByClientRefIn` e agrupar por `clientRef` antes de montar o DTO. `@EntityGraph` não se aplica (associações por `clientRef`, sem `@OneToMany`). Não há requisito de performance na ADR; tratar em ADR futura ou sprint de débito.

## Bloqueio de âncora

O bloqueio real é a âncora B do `gate.js` lendo `code_review_status`. Este artefato e o registro escopado `workflow-state.json.code_reviews_by_adr.ADR-003` tornam o veredito verificável sem alterar `current_phase` nem o registro da Fase 4 MFA.
