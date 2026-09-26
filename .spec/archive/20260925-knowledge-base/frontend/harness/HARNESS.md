# Relatório de Harness — knowledge-base (Batch 2/2, FE-04)

Data: 2026-09-25 · Executor: developer-engineer · Backend `:8081` (build atual + `--enable-preview`, profile dev) · Frontend `:5174` (`HIVE_API_PROXY=http://localhost:8081/hive`) · Chrome DevTools.

> Desvios justificados: sem `DROP DATABASE` (sessão paralela ativa usa o mesmo PG em `:8080`/`:5173`, intocados); isolamento por tenant novo (`hkb*`) em vez de wipe. Portas próprias 8081/5174, sem conflito.

## H1 — Suite Completa
- `npm run build` (vue-tsc + vite): ✅ zero erros.
- `npm run test`: ✅ 22 files, **146 passed / 0 failed** (inclui 15 service + 4 picker + 4 list + 5 detail + nav/breadcrumb).

## H3 — E2E (API + UI)
| Passo | Resultado |
|---|---|
| Register + login tenant novo (via API) | ✅ 204 → token com `KNOWLEDGE_MANAGE, KNOWLEDGE_READ` |
| Criar KB `Base Harness` (nomic-embed-text → 768) | ✅ 201 (após fix BE-1) |
| Upload `reembolso.txt` | ✅ 202 PENDING → **READY** (após fix BE-2 + reindex) |
| `POST .../search {como solicito reembolso?, 5}` | ✅ 1 chunk, score 0.74, `docName=reembolso.txt` |
| Criar agente Ollama + `PATCH ... {knowledgeBaseIds}` | ✅ 200, vínculo listado |
| Chat com vínculo | ✅ `ragUsed:true` + citation score 0.80 |
| Desvínculo (`[]`) + chat | ✅ links `[]`, `ragUsed:false`, citations `[]` (sem contaminação) |
| Cross-tenant GET/search (2º tenant) | ✅ 404 + 404 |
| UI login → `/knowledge` (cards, ACTIVE, 768d) | ✅ + `step-01-list-1280.png` |
| UI edit (breadcrumb PT-BR, tabs, doc READY, copies snapshot/escaneado) | ✅ + `step-02-detail-docs-1280.png` |
| UI playground (pergunta → chunk 0.80 + fonte) | ✅ + `step-03-playground-1280.png` |
| UI picker no editor (contador, S4 mix bloqueado + toast) | ✅ + `step-04-agent-picker-1280.png`, `step-05-picker-mixed-1280.png` |
| Criar KB default via UI (`/knowledge/new` → 1536) | ✅ redirect p/ edit da nova base |
| Mobile 375 (cards empilham, menu) | ✅ + `step-06-list-375.png` |
- Network (xhr/fetch): 9 reqs, todas 200, sem `/api/api/`, Bearer presente.
- Console: **zero ERROR**; 2 warns (1 pré-existente `next()` router-guard; 1 breadcrumb UUID sem rota — corrigido via `to: undefined` p/ UUID).

## H4 — Avaliação Contínua
- Telas: ✅ MainLayout/sidebar/breadcrumb PT-BR; Tag ACTIVE; empty/skeleton/retry; dark-mode via tokens; aria-labels PT-BR; foco visível PrimeVue.
- Negócio: ✅ max 5 (selectionLimit + contador); mix-dimensão bloqueado com mensagem PT-BR; 409 `KB_IN_USE` com lista + force; >10MB bloqueado client + 413 server.
- Lógica: ✅ vínculo server-side (PATCH ids, GET links); playground = mesmo `POST .../search` do chat; tenant isolation 404.
- Performance: ✅ busca debounced 400ms; lista paginada + "Carregar mais"; rotas lazy.
- Débitos P1: nenhum. P2 (fora do batch, p/ code-review): warn `next()` pré-existente em `router/index.ts`.

## H5 — Brain / achados
- Fix BE-1 (desbloqueio E2E): `KnowledgeBaseServiceImpl.create` derivava `dimension=0` → 400 ao informar `embeddingModel`; agora deriva do model (nomic/768 → 768, demais → 1536).
- Fix BE-2 (desbloqueio E2E): BOM marcava `langchain4j-pgvector` e `langchain4j-document-parser-apache-tika` como `provided` → `NoClassDefFoundError` no parse; removido `provided` (uso exclusivo do módulo knowledge).
- Ambos compilam (`mvn -q install -pl dependencies,knowledge/impl` OK) e serão sinalizados ao orchestrator p/ review formal do Batch 1.

## Encerramento (R2)
- `:8081` e `:5174` finalizados pelo executor; `:8080`/`:5173` (outra sessão) intocados.
- Screenshots: `step-01-list-1280.png`, `step-02-detail-docs-1280.png`, `step-03-playground-1280.png`, `step-04-agent-picker-1280.png`, `step-05-picker-mixed-1280.png`, `step-06-list-375.png`.
