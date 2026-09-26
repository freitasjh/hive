# UX-SPEC — Refactor cadastro de Provider (vinculante p/ implementação)

> **Fonte**: parecer do subagente `ux-designer` (Fase 1, skill `ui-ux-pro-max`) — fonte da verdade visual/UX.
> **Base**: Design System Hive (`docs/design/DESIGN_SYSTEM.md`) — Deep Indigo & Amber, PrimeVue 4, PrimeIcons, PT-BR.

## Decisões vinculantes

1. **Seção "Provedor de IA"** = 2ª do accordion (`AgentEditorView`); accordion inicia com só a 1ª seção aberta (progressive disclosure).
2. **Sem campo apiKey no editor** (decisão 3b: chave por tenant) → **badge de status da chave**: `configurada ✅` / `faltante ⚠️` + link para `/settings/providers`.
3. **`ProviderSelect.vue`** (novo): logo+nome+descrição+hint de custo; card expandido; `aria-label`, keyboard.
4. **Modelo = `AutoComplete`** (sugestões por provider + valor livre + chips de populares).
5. **`ConnectionTester.vue`** (novo): estados idle|testing|ok|fail, `POST /v1/agents/validate`, timeout 5s, `aria-live="polite"`.
6. **Nova `CredentialsView.vue`** (`/settings/providers`, `MainLayout`, gate `AGENT_MANAGE`): tabela providers×status + diálogo upsert (password show/hide, validação de prefixo inline, DELETE com confirmação).
7. **Validação inline no blur** (não no keystroke, não só no submit); save com **warning** (não bloqueio) se teste falha.
8. **Edição de credencial**: exibir `sk-••••` + "editar para alterar"; vazio no update = mantém.
9. Tokens, dark mode (`.p-dark`), responsivo (2col ≥1024px, 1col tablet, sticky save mobile), contraste WCAG AA, PT-BR.
10. Ollama: sem apiKey; `baseUrl` editável + banner "sem auth — restrinja rede".

## Componentes/arquivos FE

- Novos: `ProviderSelect.vue`, `ConnectionTester.vue`, `CredentialsView.vue` (+ `ApiKeyInput.vue` reaproveitado na tela de credenciais).
- Alterados: `AgentEditorView.vue` (seção LLM → "Provedor de IA"), `types/agent-admin.ts`, `services/agent-admin.service.ts` (`validate()`, `credentials()`), `AdminViews.spec.ts`, `AgentEditorDialog.spec.ts`, `agent-admin.service.spec.ts`.
