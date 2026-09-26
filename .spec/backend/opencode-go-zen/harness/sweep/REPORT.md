# Relatório de Harness — E2E Sweep GO/Zen

**Data:** 2026-09-26  
**Ambiente:** Backend :8080 (Java 25), Frontend :5173 (Vite), PostgreSQL+pgvector, Redis  
**Credencial:** OPENCODE_GO real (last4 CcgG) — master key P+Em1Utt...  

---

## H1 — Suite Completa

| Item | Status |
|------|--------|
| Backend UP :8080 | ✅ |
| Frontend UP :5173 | ✅ |
| Console errors (total across all screens) | 3 (1x 500 provider-credentials, 1x 500 POST agents, 1x 500 POST chat) |

---

## H3 — E2E Sweep Results

### A. `/agents/new` — Editor de Agente

| Passo | Status | Detalhes |
|-------|--------|----------|
| A1: Vendor GO select | ✅ | Radio GO selecionado, LGPD banner (`role="alert"` ✅), Base URL readonly `https://opencode.ai/zen/go/v1` ✅, costHint "Assinatura US$ 10/mês" ✅ |
| A2: Model dropdown discovery | ⚠️ | `GET /hive/v1/providers/go/models` → 200, 26 modelos, mimo-v2.5 presente ✅. **Porém:** dropdown mostra "No results found" ao digitar "mimo-v2.5" — filtro do AutoComplete não matcha |
| A3: Save agent GO (POST 201) | 🔴 | `POST /hive/v1/agents` → **500** (toast "Erro — Ocorreu um erro inesperado"). Payload correto: `{provider:"OPENCODE_GO", model:"mimo-v2.5"}`. Causa: master key mismatch impede decifra da credencial → 500 genérico |
| A4: Save agent Zen (graceful fail) | ✅ | Zen selecionado, default model "claude-sonnet-4-5", Base URL readonly. Teste de conexão: "Falha na conexão — verifique a chave e o modelo. Salvamento liberado com aviso." (amigável ✅, sem crash) |
| A5: Connection test GO | 🔴 | `POST /v1/agents/validate` → **500** (Performance API). UI mostra "Falha na conexão" (mensagem amigável ✅ mas backend falha) |
| A6: Connection test bad model | ⚠️ | Não testável — GO connection test já falha por master key mismatch |
| A7: Stale badge + discovery refresh | ⚠️ | "Fora do catálogo — verifique o modelo" aparece quando combo filtrado não matcha. Refresh de discovery: ✅ (cache hit no segundo load) |

**Evidências:**
- `step-A1-01-agents-new.png` — Página carregada
- `step-A1-02-provider-expanded.png` — Lista de providers expandida
- `step-A1-03-go-selected.png` — GO selecionado com LGPD banner + URL readonly
- `step-A3-01-save-error.png` — Toast de erro no save
- `step-A4-01-zen-selected.png` — Zen selecionado
- `step-A7-01-agents-list.png` — Lista de agentes

---

### B. `/settings/providers`

| Passo | Status | Detalhes |
|-------|--------|----------|
| B1: Provider rows | 🔴 | Todas as 7 rows aparecem (OpenAI, Gemini, Anthropic, Ollama, GO, Zen, Custom). **Porém:** todas mostram "Nenhuma chave cadastrada" / "Faltante ⚠️" — `GET /hive/v1/provider-credentials` → **500** impede carregamento |
| B2: Custom baseUrl editable | ⚠️ | Dialog de edição do GO abre corretamente ✅. Base URL readonly "https://opencode.ai/zen/go/v1" ✅. Hint "Definida pelo provedor — não editável e não enviada no PUT" ✅. **Porém:** não consegui testar Custom/Ollama porque a lista de credenciais não carrega |

**Evidências:**
- `step-B1-01-providers-settings.png` — Todas as rows como "Faltante"
- `step-B2-01-go-edit-dialog.png` — Dialog de edição GO com hint correto

---

### C. `/chat`

| Passo | Status | Detalhes |
|-------|--------|----------|
| C1: Multi-turn chat | 🔴 | Agente "Agente GO Validação" selecionado ✅. Mensagem enviada ✅. Resposta: **"Desculpe, ocorreu um erro ao processar sua mensagem."** + toast "Request failed with status code 500". `POST /hive/v1/chat` → **500** |
| C2: Session persistence | ⚠️ | Não testável — chat falha |

**Evidências:**
- `step-C1-01-chat-error.png` — Erro de chat com toast 500

---

### D. Cross-provider (regressão)

| Passo | Status | Detalhes |
|-------|--------|----------|
| D1: OPENAI_COMPATIBLE needs baseUrl | NÃO TESTADO | Backend 500 em provider-credentials impede testes de CRUD |
| D2: OLLAMA no apiKey | NÃO TESTADO | idem |

---

### E. Responsivo + Dark + i18n

| Passo | Status | Detalhes |
|-------|--------|----------|
| E1: 375px all screens | ✅ | Chat page: layout responsivo OK, sidebar colapsada, input full-width. Agents page: cards empilhados corretamente |
| E2: Dark mode | ✅ | Chat page: cores escuras aplicadas, contraste adequado, texto legível |
| E3: i18n PT-BR | 🟡 | Strings principais traduzidas ✅. **Hardcoded:** "Home" no sidebar (deveria ser "Início"), "Status:" e "ONLINE" no footer |
| E4: Accessibility (role/aria) | ✅ | LGPD banner: `role="alert"` + `live="assertive"` ✅. Model selector: `live="polite"` ✅. **Porém:** 6 issues de label/form association no console (pré-existentes) |

**Evidências:**
- `step-E1-01-chat-mobile.png` — Chat em 375px
- `step-E2-01-chat-dark.png` — Chat em dark mode

---

## Anomalias (por severidade)

### 🔴 Crítico (3)

| # | Passo | Anomalia | Arquivo:linha | Request/Response | Impacto |
|---|-------|----------|---------------|------------------|---------|
| 1 | B1 | `GET /hive/v1/provider-credentials` → **500** | `ProviderCredentialServiceImpl.java:83` (mapper + AES decrypt) | Req: `GET /hive/v1/provider-credentials` — Res: `{"code":"INTERNAL_ERROR",...}` | **Quebra toda a tela de providers** — todas as credenciais aparecem como "Faltante". Master key no env (`P+Em1Utt...`) difere da key usada para cifrar as credenciais (`c76e5fad...`). Decifra falha → 500 |
| 2 | A3 | `POST /hive/v1/agents` → **500** | `AgentControllerV1.java:70` → service chain | Req: `{provider:"OPENCODE_GO", model:"mimo-v2.5", name:"Sweep Test Agent"}` — Res: `500 Internal Server Error` | **Feature de criação de agente GO/Zen completamente inutilizável**. Provavelmente cascata do bug #1 (credencial não decifrada) |
| 3 | C1 | `POST /hive/v1/chat` → **500** | `ChatSessionControllerV1` → LangChain4j client | Req: `POST /hive/v1/chat` com agent GO — Res: `500` | **Chat com agentes GO/Zen completamente inutilizável**. Cascata do bug #1 |

**Causa raiz comum:** Master key de produção/diff environment mismatch. A credencial OPENCODE_GO foi cifrada com uma chave (`c76e5fad...`) mas o backend roda com outra (`P+Em1Utt...`). Qualquer operação que leia a credencial cifrada falha na decifra.

### 🟡 Atenção (3)

| # | Passo | Anomalia | Arquivo | Impacto |
|---|-------|----------|---------|---------|
| 4 | A2 | Model dropdown: "No results found" quando digita "mimo-v2.5" — filtro AutoComplete não matcha modelos do discovery | `ProviderSelect.vue` / `useRemoteModels.ts` | UX confusa — usuário vê "No results" mas o modelo existe no catálogo |
| 5 | A1 | Hint "não editável e não enviada no PUT" **ausente** no editor de agente (apenas no dialog de credenciais) | `ProviderSelect.vue` | Usuário não sabe que baseUrl é fixa |
| 6 | E3 | i18n: "Home" hardcoded no sidebar (deveria "Início"), "Status:" e "ONLINE" no footer | `TheSidebar.vue`, `TheFooterbar.vue` | Inconsistência PT-BR |

### 🟢 Melhoria (2)

| # | Passo | Anomalia | Impacto |
|---|-------|----------|---------|
| 7 | E4 | 6 accessibility issues: label/form field association | P2 — acessibilidade |
| 8 | A2 | Discovery cache funciona bem (26 modelos, mimo-v2.5 presente) — mas dropdown precisa de melhoria no filtro | P3 — UX |

---

## Screenshots

| Arquivo | Descrição |
|---------|-----------|
| `step-A1-01-agents-new.png` | Página /agents/new carregada |
| `step-A1-02-provider-expanded.png` | Lista de providers expandida |
| `step-A1-03-go-selected.png` | GO selecionado: LGPD, URL readonly, modelos |
| `step-A3-01-save-error.png` | Toast erro ao salvar agente GO |
| `step-A4-01-zen-selected.png` | Zen selecionado |
| `step-A7-01-agents-list.png` | Lista de agentes |
| `step-B1-01-providers-settings.png` | Settings providers — todas "Faltante" |
| `step-B2-01-go-edit-dialog.png` | Dialog edição GO com hint |
| `step-C1-01-chat-error.png` | Chat erro 500 |
| `step-E1-01-chat-mobile.png` | Chat responsivo 375px |
| `step-E2-01-chat-dark.png` | Chat dark mode |

---

## H5 — Encerramento

| Item | Status |
|------|--------|
| Processos :8080 finalizados | ✅ Kill -9 enviado, 0 procs restantes |
| Processos :5173 finalizados | ✅ Kill -9 enviado, 0 procs restantes |
| Ollama | NÃO TESTADO (indisponível) |

---

## Veredito

| Passos OK | Passos FALHA | Passos NÃO TESTADO |
|-----------|-------------|-------------------|
| 6 (A1, A4, B2-dialog, E1, E2, E4) | 4 (A3, A5, B1, C1) | 4 (A6, D1, D2, C2) |

**Bloqueador único:** Master key mismatch impede qualquer operação que leia/decifre credenciais de provider. Isso cascata para:
- Criação de agente GO/Zen (POST 500)
- Teste de conexão (POST validate 500)  
- Chat com agentes GO/Zen (POST chat 500)
- Exibição de status de credenciais (GET 500)

**Próximo passo:** Developer-engineer deve resolver o master key mismatch (verificar se a key no env `PROVIDER_MASTER_KEY` corresponde à usada para cifrar as credenciais existentes no banco). Após fix, re-executar esta varredura.
