# CODE-REVIEW — ADR-002 (centralização e telemetria)

**Data:** 2026-11-22 · **Branch:** `adr/public-chat-api/02-centralizacao-chat` · **Árvore:** sem commit no momento do veredito.

## Veredito

**APPROVED** — 0 🔴 · 0 🟠 · 0 🟡 · 0 🔵 · `must_fix_before_done: []`. `reviewer_session`: `ses_dffa34effffeZMrCoNwL2t4pqv`.

## Escopo revisado

6 arquivos: `ChatCallerParityIT`, `ProviderMessagesTest`, `TurnOutcomeMapper`, `ChatServiceImpl`, `PublicChatServiceImpl`, `TurnOutcomeMapperTest`. Fonte: `ADR.md`/`SPEC.md`/`TASKS.md` revisados pelo arquiteto em 2026-11-22.

## Guardas verificadas

| # | Guarda | Resultado |
|---|---|---|
| 1 | `TurnOutcomeMapper.resolve` preenche `turnId`/`clientId` em `EMPTY_RESPONSE`, `FAILED` e `ANSWERED`; público com valores reais, humano `null/null`; `ChatTurnGateway`/`TurnRequest`/`TurnReply` sem diff | ✅ |
| 2 | Nenhum tipo novo; diff de poms zero; `history/api/.../ProviderMessages.java` sem diff | ✅ |
| 3 | CZ02 congela `blank` não descartado no conversor, mas a rejeição pública pré-existente (`trim().isEmpty()`) permanece — comportamento indesejado não foi registrado como aceito | ✅ |
| 4 | CZ01 afirma 4 mensagens e `120/340/460/STOP` nos dois chamadores; stub controla fixture, mas `TurnReply` → trace passa pelo gateway e mapper reais | ✅ |
| 5 | ADR-001 intacta: envelope `{message,type,param,code}`, duplo `/v1`, `model`/`stream`/`role:system`, Decisão 7; zero diff em `Mfa*`/`workflow-state.json`/`.flattened-pom.xml`; nenhum teste apagado ou `@Disabled` | ✅ |
| 6 | Evidência declarada consistente: front 378, backend 520 → 522 (+2 testes), 119 ITs, build 0, `git diff --check` limpo | ✅ |

## Impacto

Nenhuma tarefa CZ01..CZ05 reaberta. Única mudança de código real: assinatura de `TurnOutcomeMapper.resolve` + 2 chamadores + teste. Guardas de não-regressão (CZ01/CZ02/CZ03/CZ05) preservadas sem refactor de conversão.

## Bloqueio de âncora

O bloqueio real é a âncora B do `gate.js` lendo `code_review_status`. Este artefato e o registro escopado `workflow-state.json.code_reviews_by_adr.ADR-002` tornam o veredito verificável sem alterar `current_phase` nem o registro da Fase 4 MFA.
