# Débito técnico — agent-delete-cascade

Registrado durante o bugfix `agent-delete-cascade` (2026-09-26). **NÃO corrigir neste batch** —
foi deliberadamente adiado para não misturar escopo. Origem: `fullstack-code-reviewer`.

## P2 — o workspace não é repositório git
`git revparse` → `fatal: not a git repository`. Consequência: **toda** afirmação "pré-existente"
num code review foi verificada por **leitura do código circundante**, não por diff. O review não
tinha baseline — o que ele instancia é o dev ter asserted, e eu não ter como refutar.

**Ação:** `git init` + primeiro commit, antes da próxima rodada de bugfix. Sem isso, cada review
gasta tokens reconduzindo "isso já existia ou eu que introduzi?".

## P3 — `StandardError` serializa o mesmo campo 3 vezes
`getDetail()` / `getDetailMessage()` / `getMsg()` — todos sobre o mesmo valor, todos serializados
pelo Jackson → 9 campos JSON por erro, 2 redundantes.
- `detailMessage` **tem consumidor**: `frontend/src/views/Register.vue:339-340` → não pode remover.
- `msg` tem **zero** consumidores no FE (busca por `.msg` = 0 hits) → peso morto.
**Ação:** remover `getMsg()`, manter `getDetailMessage()`, documentar `detail` como canônico.
Sem impacto de segurança (mesmo valor, não mais).

## P3 — branch morto `sole_default_agent` no `ControllerExceptionHandler`
`ControllerExceptionHandler:218-221`: não existe trigger nem constraint com esse nome em nenhum
schema. A guarda do default único é **application-level** (`AgentServiceImpl:338-343`).
**Ação:** remover o branch ou commenting que é legado.

## P3 — `lower.contains` sem qualificar a constraint
`ControllerExceptionHandler:209-217`: os ramos de prompt citam a constraint inteira
(`uk_prompt_template_tenant_code_version`), os de identity/USERS citam só `email`/`username`/
`federal_id`. Substring solto casa com o texto da mensagem de outra constraint.
**Ação:** qualificar todos com o nome da constraint.

## P3 — 3 classes Tailwind mortas em `AgentsView.vue`
Linhas 116 (`text-2xl font-bold text-900`), 154 (`font-medium text-900`), 160
(`text-sm text-surface-600`). **O projeto não tem Tailwind** — só `primeflex`. Essas classes não
renderizam nada. Pré-existente; o bugfix do diálogo de exclusão **não** ampliou (verificado pelo
review: o código novo usa só `mt-3`, `m-0 mb-2`, `m-0 pl-4`, todas primeflex válidas).
**Ação:** converter para utilitário primeflex ou remover.

## P3 — rate limit ausente em admin CRUD
`SlidingWindowRateLimiter` existe mas está aplicado só a chat send, discovery de provider e
validate. **Nenhum** endpoint de admin CRUD é rate-limited. `DELETE /agents` não ter um é
**consistente** com o projeto, não é regressão — mas delete em loop é destrutivo e agora mais fácil
de executar (204 em vez de 409).
**Ação:** avaliar rate limit em `DELETE`/`PUT` de admin.

## Regra nova para `.agents/rules/db-migration-flyway.md`
Ao criar migration que **introduz** FK cross-module, exigir `@DependsOn` explícita entre os beans
Flyway. Hoje a ordem entre os 4 módulos é **acidental** (dois `@Bean(initMethod="migrate")` sem
`@Order`). A dependência `knowledge → agent` já existia (V13 faz `FROM agent a`) e agora está
formalizada com `@DependsOn("agentFlyway")` no `KnowledgeDbUpdateConfig`. As outras arestas foram
auditadas e não existem — mas a regra precisa existir para a próxima.
