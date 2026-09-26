# Spec Developed — Hive AI

Lista cronológica de funcionalidades desenvolvidas e etapas concluídas.

## 2026-08-26 — mvp-chat: Chat Persistente + Multi-Turn + Session CRUD (P0)

- **Tipo**: Feature
- **Módulo**: `agent` (api/impl/dbupdate/web) + `frontend` (Vue 3 PrimeVue)
- **Status**: ✅ Completed (Verify APPROVED WITH DEBT → Archive)
- **SPEC**: `.spec/archive/20260826-mvp-chat/SPEC.md` 831 linhas (RF1-RF8, RN1-RN10, CA1-CA10)
- **PLAN**: `.spec/archive/20260826-mvp-chat/PLAN.md` 596 linhas (4 batches)
- **TASKS**: `.spec/archive/20260826-mvp-chat/TASKS.md` 665 linhas (21 tasks: 14 BE + 5 FE + 2 INFRA)
- **VERIFY**: `.spec/archive/20260826-mvp-chat/VERIFY.md` 220 linhas (PASS WITH DEBT / PARTIAL security)
- **ADR**: `docs/adr/ADR_007_Chat_Persistence.md`
- **Delta**: `docs/product/SYSTEM_SPECIFICATION.md` §4.1
- **Batches**:
  - Batch1 Fundação (VOs, entities, repos, mappers domain, V5, 7 mapper tests) — review APPROVED
  - Batch2 Services (ChatSession/MessageService, TX split window 20, 41 BE tests) — review APPROVED
  - Batch3 API (DTOs, mappers DTO, 3 controllers 7 endpoints, HasPermissionAspect, 41 BE tests) — review APPROVED
  - Batch4 Frontend (services, AgentSelector, ChatSidebar, Chat.vue, 37 FE tests 424 modules) — review APPROVED
- **Testes**: 79 total (41 BE + 1 web + 37 FE) 0 failures; builds SUCCESS (mvn 35 modules, npm 424 modules)
- **Harness**: FE UP 5173, BE DOWN infra ${revision} flatten (override P4->P5 aprovado), screenshot /tmp/harness-fase4-screenshot.png, i18n PT-BR OK
- **Débitos D1-D8**: integration-test módulo, RBAC bypass, aspect teste, flatten plugin, findById tenant-blind, listAgents teste, updateAt null, vite proxy
- **Workflow**: workflow-state.json current_phase=Completed, overrides=[P4->P5]

## 2026-08-30 — admin-prompt-agent: Administração de Prompts e Agentes + Agente Padrão (P0)

- **Tipo**: Feature composta (prompt-admin + agent-admin + default-agent)
- **Módulos**: `agent` (api/impl/dbupdate/web) + `frontend` (admin views) + `commons` (RBAC/RFC7807) + `security` (JWT)
- **Status**: ✅ Completed (Verify APPROVED → Archive)
- **SPEC**: `.spec/archive/20260830-admin-prompt-agent/SPEC.md` 1549 linhas (RF1-RF8, RN1-RN12, CA1-CA8+2)
- **PROPOSAL**: `.spec/archive/20260830-admin-prompt-agent/PROPOSAL.md` 341 linhas
- **PLAN**: `.spec/archive/20260830-admin-prompt-agent/PLAN.md` 574 linhas (4 batches)
- **TASKS**: `.spec/archive/20260830-admin-prompt-agent/TASKS.md` 1097 linhas (29 tasks: 3 INFRA + 20 BE + 6 FE)
- **VERIFY**: `.spec/archive/20260830-admin-prompt-agent/VERIFY.md` 425 linhas (3 dimensões + 14 security + harness 16/16)
- **UX-SPEC**: `.spec/archive/20260830-admin-prompt-agent/frontend/UX-SPEC.md` 5 telas I1-I8
- **ADR**: `docs/adr/ADR_008_Admin_Prompt_Agent_Default.md`
- **Delta**: `docs/product/SYSTEM_SPECIFICATION.md` §4.2
- **Batches**: Batch1 prompt backend 53 tests, Batch2 agent default 54 tests, Batch3 web 79 tests, Batch4 frontend 93 tests — 4 reviews APPROVED após fixes
- **Testes**: 209 total (116 BE + 93 FE) 0 failures, harness 16/16 E2E PASS, 0 console errors, 201/400/409/422, partial unique, i18n/responsive OK
- **Workflow**: workflow-state.json Completed, archived_path .spec/archive/20260830-admin-prompt-agent

## 2026-09-25 — provider-vendor: Provider como Vendor Real + LangChain4j único (P0)

- **Tipo**: Refactor (breaking)
- **Módulos**: `provider` (api/langchain4j, remoção spring-ai) + `agent` (api/impl/dbupdate/web) + `commons` (RBAC/RFC7807) + `frontend` (admin)
- **Status**: ✅ Completed (Verify APPROVED + Security 14/14 → Archive)
- **SPEC**: `.spec/archive/20260925-provider-vendor/backend/SPEC.md` (~240 linhas: ADR-009, RBAC, STRIDE, catálogo MVP, V9/V10, testes)
- **PROPOSAL**: `.spec/archive/20260925-provider-vendor/backend/PROPOSAL.md` 86 linhas
- **PLAN**: `.spec/archive/20260925-provider-vendor/backend/PLAN.md` (3 batches)
- **TASKS**: `.spec/archive/20260925-provider-vendor/backend/TASKS.md` (15 tasks: 2 INFRA + 8 BE + 4 FE + 1 DOC)
- **JIRA_BOARD**: `.spec/archive/20260925-provider-vendor/backend/JIRA_BOARD.md` (16 keys, tudo Done)
- **VERIFY**: `.spec/archive/20260925-provider-vendor/backend/VERIFY.md` (3 dimensões ✅ + security pass)
- **UX-SPEC**: `.spec/archive/20260925-provider-vendor/frontend/UX-SPEC.md` (10 decisões vinculantes)
- **ADR**: `docs/adr/ADR_009_LangChain4j_Vendor_Provider.md` (Aceito)
- **Delta**: `docs/product/SYSTEM_SPECIFICATION.md` §4.3 (nova) + ADR-009
- **Batches**: Batch1 fundação (credential AES, CRUD mascarado, 15/15 IT) — review APPROVED pós-fixes A1/A2; Batch2 vendor+validate+V10+remoção spring-ai (28/28 IT) — review APPROVED; Batch3 frontend (114/114 FE, harness E2E) — review APPROVED 10/10 UX
- **Testes**: 268 total (126 unit BE + 28 IT + 114 FE) 0 falhas; harness 12/12 E2E PASS, 0 console errors
- **Segurança**: chave hardcoded removida do código (rotação no provedor = ação do usuário, pendente); SSRF allowlist + throttle validate com ITs
- **Débitos**: DT router-guard permission (P2), executor singleton validate (fase 2), log exception SDK (fase 2), failsafe forkCount (cosmético)
- **Workflow**: workflow-state.json Completed, archived_path .spec/archive/20260925-provider-vendor

## 2026-09-25 — knowledge-base: Base de Conhecimento / RAG com pgvector (P0)

- **Tipo**: Feature (Phase 2 ROADMAP)
- **Módulos**: `knowledge/` NOVO (api/impl/dbupdate/web) + `agent` (chat RAG) + `commons` + `frontend` (KB views)
- **Status**: ✅ Completed (Verify APPROVED + Security pass → Archive)
- **SPEC**: `.spec/archive/20260925-knowledge-base/backend/SPEC.md` (~170 linhas: R1-R18, CA1-CA6, RN-KB1-7)
- **PROPOSAL**: `.../backend/PROPOSAL.md` · **PLAN**: `.../backend/PLAN.md` (2 batches) · **TASKS**: 14 tasks · **JIRA**: 15 keys Done
- **VERIFY**: `.../backend/VERIFY.md` (3 ✅ + security pass) · **UX-SPEC**: `.../frontend/UX-SPEC.md` · **ADR**: `docs/adr/ADR-010-KB-RAG-pgvector-manual.md` (Aceito)
- **Delta**: `docs/product/SYSTEM_SPECIFICATION.md` (seção Knowledge) + `AGENTS.md`
- **Batches**: Batch1 backend (V11/V12/V13, ingestão, retrieval, CRUD, 5 ITs) — review APPROVED pós-fixes 4🔴+6🟡; Batch2 frontend (146/146, harness) — review APPROVED
- **Testes**: 206+ unit + 41 IT + 146 FE, 0 falhas; harness 12/12 E2E
- **Estudo**: Brain `estudos/global/langchain4j/rag-pgvector-completo`
- **Débitos**: DT-SEC-1 rebinding, DT-SEC-2 eviction, M2 auth-me-500 (backlog), router next() (P3)
- **Workflow**: workflow-state.json Completed, archived_path .spec/archive/20260925-knowledge-base

## 2026-09-25 — opencode-go-zen: OpenCode GO/Zen + Provider Custom (P0)

- **Tipo**: Feature (Phase 2 ROADMAP)
- **Módulos**: `provider/api` + `provider/langchain4j` + `agent` (chat, credenciais, 422) + `commons/impl` (PinnedHttpClient, SlidingWindowRateLimiter) + `frontend` (select, editor, credenciais, i18n)
- **Status**: ✅ Completed (Verify 3/3 + 2 code reviews APPROVED → Archive)
- **SPEC**: `.spec/archive/20260925-opencode-go-zen/backend/SPEC.md` · **PROPOSAL**: `.../backend/PROPOSAL.md` · **PLAN**: `.../backend/PLAN.md` (2 batches) · **TASKS**: 11 tasks · **JIRA**: 11 keys Done
- **VERIFY**: `.../backend/VERIFY.md` (Completude/Corretude/Coerência ✅✅✅) · **UX-SPEC**: `.../frontend/UX-SPEC.md` · **ADR**: `docs/adr/ADR-011-Opencode-Go-Zen.md` (Aceito)
- **Delta**: `docs/product/SYSTEM_SPECIFICATION.md` §4.3 (7 correções de divergência documental)
- **Batches**: Batch1 backend (enums, catálogo, discovery pinado, custom, validação, rate limit, 11 ITs) — review APPROVED pós-fixes 2🔴+4🟡; Batch2 frontend (7 vendors, dropdown remoto, credenciais, LGPD, 160/160) — review APPROVED pós-fixes 3🟡
- **Testes**: 301 unit + 55 IT + 160 FE = 516, 0 falhas
- **Bugs 🔴 corrigidos na verificação**: (1) `PinnedHttpClient` com verificação de hostname inativa — `HttpsURLConnection.getDefaultHostnameVerifier()` é stub always-false no JDK; (2) `baseUrl` GO/Zen sem `/v1` → 404 HTML mapeado para falso 422 MODEL_UNAVAILABLE
- **E2E**: CA-GO validado com credencial real — discovery 26 modelos → agente → sessão → chat 200 multi-turn → 5 mensagens persistidas; 6 screenshots
- **Débitos**: CA-Zen sem credencial real (coberto por ITs); teto mensal + alertas (fase 2); LGPD em KB (3ª superfície, cortada); flake vitest pré-existente (P3)
- **Workflow**: workflow-state.json Completed, archived_path .spec/archive/20260925-opencode-go-zen

### Bugfix pós-archive — 2026-09-26 (3 bugs 🔴 em 1 dia)

| # | Bug | Causa | E2E |
|---|---|---|---|
| 1 | `PinnedHttpClient` TLS inativa | `HttpsURLConnection.getDefaultHostnameVerifier()` é stub always-false no JDK | discovery GO quebrado (422) |
| 2 | `baseUrl` GO/Zen sem `/v1` | LangChain4j concatena só `/chat/completions` | 404 HTML → falso 422 "modelo indisponível" |
| 3 | Credencial GO/Zen impossível de salvar pela UI | ref `baseUrl` readonly alimentava o payload | PUT 400 sempre |

**Lição**: os 3 saíram de E2E/chamada real, não de teste unitário. O #3 era meu
(validei o endpoint por `curl` e a UI só "de passagem" no harness) — o teste do
dialog GO/Zen abria e verificava exibição, mas nunca clicava em Salvar.

### 2º ciclo de bugs pós-archive — 2026-09-26 (3 bugs: dropdown P0, timeout P1, master key P2)

| # | Bug | Causa | Como apareceu |
|---|---|---|---|
| 4 | Dropdown de modelo nunca abre (remotos) | PrimeVue 4.5.4 só mostra o panel via watcher de `suggestions` que dispara em **troca de referência**; o handler filtrava uma lista diferente da que o template lia | varredura de tela |
| 5 | "Testar conexão" dá timeout | backend 5s vs latência real de 12.42s do `mimo-v2.5` | varredura de tela |
| 6 | Master key divergente sem diagnóstico | 500 genérico; causa só no log | falso positivo de um QA |

**Defeitos de governança corrigidos**: o perfil `integration` do Maven **desliga o surefire**, então
`mvn clean install -Pintegration` rodava **zero** testes unitários e "passava"; e `npm run test:run`
(não existe) aparecia em 10+ portões como se fosse válido. Ambos corrigidos em
`.agents/rules/`, `.agents/agents/`, `.agents/skills/`.

### 3º ciclo — 2026-09-26: `PROVIDER_MASTER_KEY` sem caminho declarativo (🔴 P0, reportado pelo usuário)

**Sintoma:** com o backend iniciado sem `PROVIDER_MASTER_KEY` exportada, `PUT/GET /provider-credentials`
davam **500** e nenhum provider podia ser cadastrado.

**Causa:** `AesGcmAttributeConverter.keySpec()` lia só `System.getenv()` e `System.getProperty()`.
**`System.getProperty` não lê `application.properties`** — não havia caminho declarativo, apenas
exportar a variável no shell. Pior: chave efêmera por shell ⇒ trocar a chave torna credenciais
gravadas irrecuperáveis.

**Fix:** bean `ProviderMasterKey` (env var > propriedade Spring, validado no **boot**,
falha cedo com mensagem acionável), propriedade fixa de dev em `application-dev.properties`,
`application.properties` sem default (produção falha no boot), startup check na mesma fonte.
O Hibernate cria `AttributeConverter` por construtor no-arg e **ignora** beans Spring — a chave
é publicada num ponto de acesso de processo, e `MasterKeyDeclarativePathIT` prova que a
instrução usa a chave do bean.

**Erro meu:** validei o fluxoGO/Zen exportando a chave no meu shell. O usuário não tinha isso.
Terceira vez que valido num ambiente privilegiado que o usuário não tem — regra gravada no Brain:
**toda validação E2E deve reproduzir o ambiente do usuário, não o meu.**

**Verificação:** boot sem env var · PUT 200 · **credencial sobreviveu a restart** · discovery 27
modelos · chat real 200.

### Ciclo RAG/chat — 2026-09-26 (5 bugs, todos encontrados por medição e E2E)

| # | Bug | Sev | Como apareceu |
|---|---|---|---|
| 1 | Chat travava ~90s (timeout 30s + retry 2× + `maxTokens` nunca aplicado) | 🔴 | log |
| 2 | Resposta vazia do modelo → `content NOT NULL` → **409 de banco** | 🔴 | log |
| 3 | Vínculo KB↔agente sem caminho de escrita + **IDOR** no PUT antigo | 🔴 | E2E + achado do dev |
| 4 | Embeddings sem prefixo de tarefa do nomic → **não discriminavam** | 🔴 | medição no pgvector |
| 5 | Threshold único 0.7 para todos os modelos | 🔴 | medição (4 sondas) |

**Resultado:** `ragUsed=true`, `ragReason=ok`, `citations=4`, resposta citando o PDF — com
embedding real do Ollama, não mock. 490 unit + 109 IT + 214 FE, 0 falhas.

**O que a medição mudou:** com o prefixo corrigido, 4 sondas deram relevante-curta **0.6219** e
irrelevante **0.5972**. Threshold 0.60 separa as quatro. A margem é de ~0.02 — e eu quase subi
isso para 0.5 (que teria injetado a pergunta irrelevante) porque não tinha medido o ruído antes.
**Medir a ausência de match antes de escolher o corte** foi o que evitou um bug pior que o
original.
