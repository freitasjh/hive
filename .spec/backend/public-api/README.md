# README — índice dos artefatos de `public-api`

Planejamento técnico da **API pública de chat** (OpenAI-compatible) do Hive AI, para o ERP / sistema
B2B da própria organização.

**Status:** planejamento **revisado e corrigido (2026-09-27)** — 3 pareceres (security-engineer,
product-manager, architect: APROVADO COM CONDIÇÕES), decisões **D1–D8** aplicadas, P1/P2
**ratificadas** pelo usuário. **Nenhuma implementação feita.** Handoff para `developer-engineer`
após o Batch 0 (baseline).

| Ordem | Artefato | Conteúdo |
|---|---|---|
| 0 | [`EXPLORATION.md`](./EXPLORATION.md) | Problema, decisões fechadas, 9 perguntas de discovery (5 bloqueantes), 7 achados que mudam o desenho |
| 1 | [`CONSTRAINTS.md`](./CONSTRAINTS.md) | 10 restrições de código **medidas**, dívida, harness, compliance |
| 2 | [`DESIGN-DECISIONS.md`](./DESIGN-DECISIONS.md) | Confronto A/B/C + 4ª opção, argumento **contra** a premissa do briefing, 18 decisões |
| 3 | [`ARCHITECTURE.md`](./ARCHITECTURE.md) | Diagrama, 3 tabelas, contrato do token, contrato HTTP, **4** erros, reuso da porta |
| 4 | [`PLAN.md`](./PLAN.md) | ~75 tasks em 14 batches (0, 1, 1B, 2–12), com o que é pré-requisito de segurança. Estimativa 52–66 h |
| — | [`RUNBOOK-prompt-edition.md`](./RUNBOOK-prompt-edition.md) | Procedimento de edição de prompt com credencial `PINNED` (o 409 e como evitá-lo) — criado na revisão (D1) |
| 5 | [`VALIDATION.md`](./VALIDATION.md) | Consistência, riscos, **gate de conexão com o ERP** (10 passos) |

## As 3 coisas que este replanejamento mudou

1. **A API pública é o 2º *chamador* de `ChatTurnGateway`, não a 2ª *implementação*.** Uma 2ª
   implementação reimplementaria as 5 decisões que a porta existe para centralizar, e
   `MODEL_UNAVAILABLE` passaria a divergir entre os dois caminhos. O gatilho 5 do ADR-012 **não**
   dispara.
2. **Apareceu um gate de segurança que o briefing não listava:** token público na rota pública dá
   **500**, não 401 — e a causa foi **corrigida na revisão** (B2): não é `loadUserByUsername:51`
   (inalcançável), é `validateTokenRevoke:49` → `decodeAndVerify` exigindo `aud=hive.web`; e
   `SecurityFilter.java:28` é `@Component`, **auto-registrado fora do `FilterChainProxy`** (B1).
   Solução — `SecurityFilterChain` separada + `FilterRegistrationBean(setEnabled(false))`,
   `SecurityFilter.java` com 0 linhas alteradas — é também o que **permite não pagar o RBAC de
   usuário** que o PM não quer pagar.
3. **A decisão 5 mudou de significado, não de conteúdo.** `PINNED` **não congela** comportamento:
   `update()` muta a linha in-place, então fixar a versão pane a integração e nunca devolve o prompt
   antigo. `PINNED` é uma **asserção de conformidade** (tripwire), e ganhou um 4º erro
   (`prompt_version_not_found`). **Default = `FOLLOW_ACTIVE`** (D1, arbitrado pelo usuário).
4. **O teto de mensagens é dado da credencial** (D2), não constante e não "do proxy" (não há proxy
   no caminho): 3 colunas + claim `plim`, recusa explícita 400/413, **nunca** truncamento.
5. **O provisionamento é CLI, não tela** (D6): `CommandLineRunner` + `ProvisioningPayloadBuilder`.
   `ux-designer` não exigido.

## Onde ler primeiro

`EXPLORATION.md §4` tem os 7 achados, cada um com `file:line`. `DESIGN-DECISIONS.md §2` tem o
argumento contra a premissa do briefing. `VALIDATION.md §4` tem o gate que decide se a integração
está pronta — que é **não** o H3 de UI.
