# ADR 002: Arquitetura do Frontend MVP

## Status
Proposto

> ### ⚠️ Supersedido em 2026-09-27 — decisão `scope-assistant-sem-rag`
>
> Este ADR **não foi reescrito**: ele é registro do raciocínio da época, e reescrever seria apagar a
> lição. A decisão está em **[ADR-013 — Assistant, sem base de conhecimento e sem agente](ADR-013-Assistant-Sem-Rag-Sem-Agente.md)**.
> O Brain (`arquitetura/projetos/hive/decisoes/scope-assistant-sem-rag`) é registro secundário e
> complementar, não fonte primária.
>
> O produto virou um **Assistant**: chat simples, sem agente, sem tools, sem base de conhecimento.
> A lista de serviços deste ADR cita `agent.service.ts`, que não existe mais. O que o frontend do
Assistant usa: `auth.service.ts`, `chat.service.ts`, `session.service.ts`, `prompt.service.ts`,
`agent-admin.service.ts` (nome histórico: credenciais e catálogo de vendors são coisas de
*provedor*, não de agente) e o seletor de prompt no lugar do antigo `AgentSelector`.


## Contexto
O projeto Hive necessita de um frontend moderno e escalável. A escolha da stack recai sobre Vue 3 e PrimeVue devido à produtividade e conformidade com o design system pretendido.

## Decisão
1. **Stack**: Vue 3, Vite, TypeScript, PrimeVue (v4+), Pinia, Vue Router.
2. **Template**: Baseado no PrimeVue Sakai para acelerar o desenvolvimento de layouts administrativos.
3. **Estrutura de Pastas**:
    - `src/views`: Páginas principais (Login.vue, Register.vue, Chat.vue).
    - `src/components`: Componentes reutilizáveis (MessageItem.vue, ChatInput.vue).
    - `src/services`: Camada de abstração de API (auth.service.ts, agent.service.ts).
    - `src/stores`: Gerenciamento de estado (auth.store.ts).
4. **Segurança**: Interceptor Axios para injetar o JWT no header `Authorization: Bearer ...` e tratar 401.

## Consequências
- Curva de aprendizado baixa para a equipe.
- Facilidade de integração com o backend modular.
- Garantia de tipagem forte em todo o fluxo de dados.
