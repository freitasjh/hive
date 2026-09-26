# ISSUE_008: Redesign UI Premium (Deep Indigo & Amber)

## 🎯 Objetivo
Redesenhar a interface do Hive AI para uma experiência elegante, moderna e premium, focada no Chat e na identidade visual da marca, eliminando o débito técnico de falta de componentização.

## 🎨 Identidade Visual (UX Designer Definition)
- **Cores**: Deep Indigo (Primary) e Amber (Accent).
- **Layout**: Focado em centralização, minimalismo e modularidade.
- **Chat**: Bolhas de conversa modernas com sombras suaves e animações de entrada.

## 🏗️ Estratégia de Componentização (The Blueprint)
Para garantir a escalabilidade e manutenção do código, a interface será modularizada nos seguintes componentes:
1.  **AuthLayout.vue**: Layout compartilhado para Login e Register com background dinâmico.
2.  **ChatSidebar.vue**: Gerenciamento do histórico de conversas e navegação lateral.
3.  **ChatMessage.vue**: Renderização atômica das bolhas de mensagem (suportando diferentes tipos de conteúdo e papéis).
4.  **ChatInput.vue**: Área de entrada de texto modernizada com suporte a multiline e ações rápidas.

## ✅ Critérios de Aceite
1.  **Design Tokens**: Utilizar exclusivamente tokens do PrimeVue 4 (Aura) para garantir suporte nativo a **Dark Mode** (ex: `var(--p-primary-color)`).
2.  **Chat Centralizado**: O contêiner de mensagens deve estar centralizado na tela, com largura máxima otimizada para leitura (max-width: 850px).
3.  **Bolhas de Mensagens**:
    - Agente: Fundo `surface-100` ou `surface-800` (dark), bordas arredondadas (20px).
    - Usuário: Fundo `primary`, texto contrastante.
4.  **Tipografia**: Utilizar pesos bem definidos para hierarquia visual, focando em legibilidade.
5.  **Acessibilidade**: Garantir contraste adequado entre cores de fundo e texto (WCAG AA).
6.  **Responsividade**: Layout fluido que se adapta perfeitamente a mobile.
7.  **Animações**: Utilizar PrimeVue `AnimateOnScroll` ou classes de transição suaves.

## 🛠 Tarefas Técnicas
- [ ] Configurar tokens de cores primária e acento no `main.ts` (PrimeVue 4).
- [ ] Implementar `AuthLayout.vue` e refatorar `Login.vue` / `Register.vue`.
- [ ] Extrair lógica de mensagens para `ChatMessage.vue`.
- [ ] Implementar `ChatSidebar.vue` (retrátil).
- [ ] Modernizar a área de entrada em `ChatInput.vue`.
- [ ] Refatorar `Chat.vue` para orquestrar os novos componentes.

## 🧪 Estratégia de Testes
- [ ] Testes unitários para `ChatMessage.vue` (renderização correta por papel).
- [ ] Testes de integração para o fluxo de logout via sidebar.
- [ ] Validação visual manual de contraste e Dark Mode.
- [ ] Comprovar sucesso com `npm run test` e `npm run build`.

## 🛡️ Quality Gate (Definition of Done)
- [ ] Código implementado seguindo a stack Vue 3 (Composition API).
- [ ] Testes unitários e de componentes aprovados.
- [ ] Code Review com veredito `APPROVED` pelo `@frontend-code-reviewer`.
- [ ] Documentação técnica atualizada.
- [ ] **Aceite do Usuário (UAT) confirmado.**
