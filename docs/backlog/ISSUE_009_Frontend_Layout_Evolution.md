# ISSUE_009: Frontend Layout Evolution (Topbar, Footerbar, and Navigation)

## 🎯 Objetivo
Implementar a estrutura global de layout do frontend, incluindo Topbar, Footerbar e uma estratégia de navegação robusta para suportar as seções de Chat, Prompts e Agentes.

## 🏗️ Estratégia de Implementação
A implementação será dividida em componentes globais reutilizáveis:
1.  **AppTopbar.vue**: Cabeçalho fixo com branding, breadcrumbs e perfil do usuário.
2.  **AppFooter.vue**: Rodapé com informações do sistema e links rápidos.
3.  **AppNavigation.vue**: Menu lateral ou superior para alternar entre Chat, Prompts e Agentes.
4.  **MainLayout.vue**: Layout principal que orquestra os componentes acima e o conteúdo da rota.

## ✅ Critérios de Aceite
1.  **Topbar**:
    - Exibir logo "Hive" e nome da aplicação.
    - Exibir breadcrumbs dinâmicos baseados na rota atual.
    - Exibir avatar do usuário com dropdown contendo "Configurações" e "Sair".
2.  **Footerbar**:
    - Exibir versão da aplicação e ambiente (ex: v1.0.0 - Development).
    - Exibir links para Documentação e Suporte.
    - Exibir aviso de copyright.
3.  **Navegação**:
    - Menu funcional para alternar entre `/chat`, `/prompts` e `/agents`.
    - Indicação visual clara da rota ativa.
    - Responsividade: Menu deve se adaptar para dispositivos móveis (hamburger menu).
4.  **Estilo**:
    - Seguir o esquema de cores "Deep Indigo & Amber" (ISSUE_008).
    - Utilizar componentes PrimeVue (Menubar, Breadcrumb, Avatar, TieredMenu).

## 🛠 Tarefas Técnicas
- [ ] Criar `AppTopbar.vue` com PrimeVue `Menubar`.
- [ ] Implementar lógica de breadcrumbs dinâmicos.
- [ ] Criar `AppFooter.vue`.
- [ ] Criar `AppNavigation.vue` (Sidebar ou Menubar secundário).
- [ ] Criar `MainLayout.vue` para envolver as rotas protegidas.
- [ ] Atualizar `router/index.ts` para utilizar o `MainLayout`.

## 🧪 Estratégia de Testes (Obrigatória)
- [ ] **Unitários**:
    - Testar utilitário de geração de breadcrumbs.
    - Testar lógica de logout no store do Pinia.
- [ ] **Componentes**:
    - `AppTopbar.vue`: Validar renderização de branding e abertura do menu de perfil.
    - `AppFooter.vue`: Validar exibição de versão e links.
    - `AppNavigation.vue`: Validar presença dos links e classe ativa.
- [ ] **Comprovação**: Executar `npm run test` e garantir 100% de sucesso.

## 🛡️ Quality Gate (Definition of Done)
- [ ] Código implementado seguindo a stack Vue 3 (Composition API).
- [ ] Testes unitários e de componentes aprovados.
- [ ] Code Review com veredito `APPROVED` pelo `@frontend-code-reviewer`.
- [ ] Documentação técnica atualizada.
- [ ] **Aceite do Usuário (UAT) confirmado.**
