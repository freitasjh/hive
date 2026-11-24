# TASKS — Grants por checkbox na UI admin da API pública

**Data:** 2026-11-23 · **Atualização:** 2026-11-24 · **Origem:** UI da ADR-003, arquivada em `.spec/archive/20261123-prd-public-chat-api/` (não editar) · **Fase:** BugCompleted (R1/R2/R3/R4 concluídos).

> Este documento é a análise (R3). Código só após HITL B1→B2 e reprodução real (R1). Mudança de UI + validação de contrato.

## 1. Campo livre de grants e ausência de validação no backend

- **Tipo:** bugfix de usabilidade + validação · **Responsável:** frontend-engineer (UI) + backend-engineer (validação) · **Prioridade:** P1
- **Causa (arquivo:linha):**
  - `frontend/src/modules/hive/admin/public-clients/form.ts:16-25` — `parseGrants` aceita qualquer texto separado por vírgula/newline; `:43` valida somente "não vazio".
  - `PublicClientsView.tsx:239-245` — input textual com label "Grants separados por vírgula".
  - `PublicApiClientAccessWriter.java:41-52` — deduplica e salva qualquer string como `permission`, sem validar contra catálogo.
  - `PublicApiClientAccessWriter.java:23` — único grant útil: `PUBLIC_CHAT_COMPLETE`, checado em `PublicChatControllerV1.java:61`.
- **Efeito:** usuário comum não sabe quais grants existir; strings inventadas viram claim `permissions` no JWT e não autorizam nada. Não há feedback de erro.
- **Solução:**
  - Frontend: substituir o input por lista de checkboxes com o catálogo constante (`PUBLIC_CHAT_COMPLETE`); state deixa de ser `grantsText` e passa a `grants: string[]` (ou manter `grantsText` derivado, desde que não haja texto livre). Manter payload `grants: string[]`.
  - Backend: validar no DTO/writer que todo `permission` pertence ao catálogo; erro 400 com mensagem clara. Não apagar nem reescrever grants já persistidos.
- **Arquivos previstos:** `form.ts`, `state.ts`, `PublicClientsView.tsx`, `i18n.ts`, testes/stories do diretório `public-clients/`; `PublicApiClientAccessWriter.java`, DTO de create/update de grants, ITs de contrato admin.
- **Impacto:** apenas criação/edição de grants de clients públicos; prompts, pin, cota, TTL e limites intactos.
- **Testes:** Vitest/RTL para checkbox (selecionar/desselecionar, submissão com `grants`), IT de rejeição de grant desconhecido (400), suíte completa, H3 375/1280.
- **Riscos:** quebrar clients já provisionados com strings arbitrárias se backend passar a rejeitar na leitura — por isso validar **somente na escrita**; i18n/a11y do checkbox; duplicidade de catálogo frontend/backend.
- **R1 — reproduzido (2026-11-24):** `POST /v1/public-api/clients` com `grants:["GRANT_INEXISTENTE"]` → **201** com o grant inválido; JWT do client com `permissions=[GRANT_INEXISTENTE]`; chamada ao chat negada por `AccessDeniedException` e convertida pelo handler genérico em **500 `internal_error`** (dívida pré-existente, fora deste bug).

---

## Portões

- **R1 (Harness-first):** concluído — evidência real registrada acima, antes da edição.
- **R2:** encerrar portas por `lsof -ti :PORTA` → conferir dono → `kill` por PID; nunca `pkill -f`.
- **R3:** esta análise; comandos e paths devem vir de `harness.config.json`/`AGENTS.md`.
- **R4:** `brain_store` obrigatório ao fechar.
- **B1→B2:** aprovado pelo operador em 2026-11-24; backend e frontend implementados, review e QA verdes.
- **H6:** suíte 0 falhas, H3 verde, `code-reviewer` sem 🔴, Brain registrado.
