# UX-SPEC — GO/Zen + Custom (vinculante)

> Fonte: `ux-designer` Fase 1. Base: Design System Hive.

## Decisões vinculantes
1. `VENDOR_ORDER`: `[OPENAI, GEMINI, ANTHROPIC, OLLAMA, OPENCODE_GO, OPENCODE_ZEN, OPENAI_COMPATIBLE]`; COMPATIBLE rotulado "Custom (endpoint próprio)", `hiddenFromCreate=false`.
2. Metadata: `readOnlyBaseUrl`, `remoteModels`, `modelsEndpoint`, `lgpdWarning` (+ costHints GO/Zen).
3. Dropdown remoto: loading skeleton / empty+retry / error+retry / staleness 5min ("há Xmin" + Atualizar); `useRemoteModels` composable.
4. CredentialsView: linhas GO/ZEN (baseUrl read-only + costHint) + custom editável; dialog com banner LGPD.
5. Badge "fora do catálogo" (warn, não-bloqueante) em agentes stale.
6. LGPD `Message info` no editor (GO/ZEN) + dialog credencial.
7. aria-live no seletor, `role="alert"` no LGPD, `aria-busy` no loading, PT-BR, dark, 44px targets, responsivo.
8. Teste de conexão mandatório p/ GO/ZEN/custom (fail-closed na ativação).

## Arquivos FE
Alterar: `types/agent-admin.ts`, `ProviderSelect.vue`, `AgentEditorView.vue`, `CredentialsView.vue`. Criar: `composables/useRemoteModels.ts`.
