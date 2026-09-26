# ADR 002: Arquitetura do Frontend MVP

## Status
Proposto

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
