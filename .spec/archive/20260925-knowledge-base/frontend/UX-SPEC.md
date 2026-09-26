# UX-SPEC — Base de Conhecimento / RAG (vinculante)

> Fonte: parecer do `ux-designer` (Fase 1, `ui-ux-pro-max`). Base: Design System Hive.

## Decisões vinculantes
1. **Menu**: item `Bases de Conhecimento` (`pi pi-book`) índice 3 (entre Agentes e Provedores); breadcrumb `Home > Bases de Conhecimento > ...`.
2. **Rotas**: `/knowledge` (lista), `/knowledge/new`, `/knowledge/:id/edit`.
3. **Lista**: cards (não DataTable) + busca debounced + skeleton/empty/retry + Tag PENDING(warn)/INDEXING(info+spinner)/READY(success)/FAILED(danger) + ações editar/reindexar/excluir.
4. **Detalhe**: tabs Documentos|Agentes|Testar; drag-drop FileUpload (PDF/TXT/MD 10MB, ProgressBar individual, validação client-side) + URL (`http(s)://`, erro inline); lista docs com ícone por tipo + linha FAILED vermelha + erro legível + reindexar/remover.
5. **Aba Agentes**: AutoComplete + chips, max 5 (input disabled + tooltip no limite).
6. **Playground**: Textarea + Slider topK (1-10, default 5) + chunk cards (score badge 0.90+/0.70+/abaixo, fonte arquivo+página) + empty/error states.
7. **Picker no editor de agente**: substitui AutoComplete livre por picker real (max 5 + contador "x de 5").
8. Tokens, dark mode, PT-BR, aria-labels, foco visível, contraste AA, responsivo (cards empilham <768px, criar sticky).
9. Validação blur; toasts success/error; delete com Dialog de confirmação.

## Arquivos FE
Criar: `KnowledgeBaseList.vue`, `KnowledgeBaseDetail.vue`, `services/knowledge.service.ts`, `types/knowledge.ts`. Alterar: `router/index.ts` (3 rotas), `MainLayout.vue` (navItem), `AgentEditorView.vue` (picker).
