# CODE-REVIEW — ADR-002: Emissão, hash, rotação e revogação da API key

**Data:** 2026-11-24 · **Branch:** `adr/public-api-key-complementar/02-emissao-rotacao` (HEAD `3d29f44`) · **Árvore:** sem commit no momento do veredito.

## Veredito

**APPROVED** — 0 🔴 · 0 🟠 · 0 🟡 · 0 🔵 · `must_fix_before_done: []`. `reviewer_session`: `ses_dffa34effffeZMrCoNwL2t4pqv`. Duas rodadas: 1 🟠 encontrado e corrigido, re-review limpo (iteração 1 de 3).

## Guardas verificadas

| # | Guarda | Resultado |
|---|---|---|
| 1 | V3: `key_id UNIQUE`, `previous_key_id` + índice, `CHECK` do par `previous_*`, sem `DROP`/`repair()`, nomes `key_*` explícitos, compatível com V1/V2 | ✅ |
| 2 | Um único `@Service`; `authenticate → null` na falha; BCrypt custo 10 do `sk-...` completo; janela fixa de 10 min com tempo controlado no teste | ✅ |
| 3 | Controller em `/v1/public-api/clients` com `AGENT_MANAGE`; plaintext só em create/rotate; list/get sem plaintext; cross-tenant 404 e 403; `POST .../revoke` com `revoked_at` + set Redis `hive:public:revoked-keys` | ✅ |
| 4 | `plim` m/c/t/r inalterado via `policyFor`; zero diff em `ChatTurnGateway`/`ChatTrace`/`TurnOutcomeMapper`/ADR-001/PRD/RESEARCH/arquivadas/MFA/estado/AGENTS/skills/configs/frontend/cota/telemetria | ✅ |
| 5 | Fallback O(N) eliminado: `findAll()` removido; `findByKeyIdOrPreviousKeyId` indexada, 1 query e no máximo 1 BCrypt por request, inclusive key inventada | ✅ |
| 6 | Evidência: 411 front + 530 backend (526 + 4 testes do autenticador) + 150 ITs, build 0, `git diff --check` limpo, `.flattened-pom.xml` sem diff, portas por PID sem `pkill -f`, R4 registrado | ✅ |

## Correção da iteração 1

O reviewer encontrou 🟠 em `PublicApiKeyAuthenticatorImpl.java:57-59`: quando `findByKeyId` falhava, `findAll()` rodava BCrypt em todas as keys — DoS em superfície pública, antes do rate limit do chat. Corrigido com `previous_key_id` indexado na V3, gravação antes da troca de `key_id` em `rotate`, query `findByKeyIdOrPreviousKeyId` e escolha do hash pelo campo que casou. Re-review limpo.

## Risco residual

Plano do `OR` em `findByKeyIdOrPreviousKeyId` não medido com `EXPLAIN` em banco real; índices existem em ambos os campos. Em tabela pequena o otimizador pode escolher seq scan — inofensivo; medir se volume crescer. Janela depende de timestamp do DB; divergência de relógio encurta/alonga a janela — fora desta ADR.

## Bloqueio de âncora

O bloqueio real é a âncora B do `gate.js` lendo `code_review_status`. Este artefato e o registro escopado no `workflow-state.json` tornam o veredito verificável sem alterar `current_phase` nem o registro da Fase 4 MFA.
