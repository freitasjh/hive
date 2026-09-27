# README — índice dos artefatos de `public-api`

Planejamento técnico da **API pública de chat** (OpenAI-compatible) do Hive AI, para o ERP / sistema
B2B da própria organização.

**Status:** planejamento. **Nenhuma implementação feita.** Handoff para `developer-engineer`.

| Ordem | Artefato | Conteúdo |
|---|---|---|
| 0 | [`EXPLORATION.md`](./EXPLORATION.md) | Problema, decisões fechadas, 9 perguntas de discovery (5 bloqueantes), 7 achados que mudam o desenho |
| 1 | [`CONSTRAINTS.md`](./CONSTRAINTS.md) | 10 restrições de código **medidas**, dívida, harness, compliance |
| 2 | [`DESIGN-DECISIONS.md`](./DESIGN-DECISIONS.md) | Confronto A/B/C + 4ª opção, argumento **contra** a premissa do briefing, 18 decisões |
| 3 | [`ARCHITECTURE.md`](./ARCHITECTURE.md) | Diagrama, 3 tabelas, contrato do token, contrato HTTP, **4** erros, reuso da porta |
| 4 | [`PLAN.md`](./PLAN.md) | 54 tasks em 13 batches, com o que é pré-requisito de segurança. Estimativa 32–44 h |
| 5 | [`VALIDATION.md`](./VALIDATION.md) | Consistência, riscos, **gate de conexão com o ERP** (10 passos) |

## As 3 coisas que este replanejamento mudou

1. **A API pública é o 2º *chamador* de `ChatTurnGateway`, não a 2ª *implementação*.** Uma 2ª
   implementação reimplementaria as 5 decisões que a porta existe para centralizar, e
   `MODEL_UNAVAILABLE` passaria a divergir entre os dois caminhos. O gatilho 5 do ADR-012 **não**
   dispara.
2. **Apareceu um gate de segurança que o briefing não listava:** `SecurityFilter` chama
   `loadUserByUsername` incondicionalmente, então um `sub` = `clientId` produz **500**, não 401. A
   solução — `SecurityFilterChain` separada para `/v1/public/**` — é também o que **permite não pagar
   o RBAC de usuário** que o PM não quer pagar.
3. **A decisão 5 mudou de significado, não de conteúdo.** `PINNED` **não congela** comportamento:
   `update()` muta a linha in-place, então fixar a versão pane a integração e nunca devolve o prompt
   antigo. `PINNED` é uma **asserção de conformidade** (tripwire), e ganhou um 4º erro
   (`prompt_version_not_found`).

## Onde ler primeiro

`EXPLORATION.md §4` tem os 7 achados, cada um com `file:line`. `DESIGN-DECISIONS.md §2` tem o
argumento contra a premissa do briefing. `VALIDATION.md §4` tem o gate que decide se a integração
está pronta — que é **não** o H3 de UI.
