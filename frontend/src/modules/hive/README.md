# hive — feature de dashboard

Aplica a convenção de [`../README.md`](../README.md).

- `index.ts` — API pública consumida pelo `App.tsx`: `OverviewPage`,
  `PlaceholderPage`, `AiAdminPage`, `PromptsAdminPage` (e tipos de props).
- `overview/` — visão geral: `pages/` (rota), `components/HiveOverviewView`
  (View) e `data/overview.data.ts` (constantes estáticas, inclusive
  `VIEW_LABELS`, usado por `OverviewPage` e pelos placeholders; títulos do
  menu vivem em `app-sidebar.tsx`).
- `admin/` — pages de admin e subfeatures `credentials/`, `llm/`, `prompts/`
  (Container + View). `components/AdminStates.tsx` é compartilhado dentro da
  feature; `i18n.ts`, `state.ts`, `error-message.ts` e `vendor-catalog.ts` são
  suporte da feature.
- `shared/` — `PlaceholderPage` e `PlaceholderView` para rotas ainda não
  entregues. Consome `VIEW_LABELS` via barrel de `overview/`.

Serviços HTTP (`agentAdminService`, `promptService`, `api`) e DTOs permanecem em
`src/services/`, fora desta feature.
