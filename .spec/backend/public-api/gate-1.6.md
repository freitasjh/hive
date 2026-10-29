# Gate 1.6 — prova de comportamento da extração (Batch 1)

- **Feature:** `public-api-chat` · **Batch:** 1 (extração D4/D5) · **Data:** 2026-10-09
- **Veredito:** ✅ igualdade byte a byte medida pelo fio (proxy), não por teste mock.

## Método

1. Backend real em `:8080` (profile `dev`, datasource sobrescrito por CLI para `localhost:5433`,
   postgres/postgres — `application-dev.properties` **não** editado; está sujo na árvore, pré-existente).
2. Proxy de captura (`node:http`, zero dependências) em `127.0.0.1:11500` → `127.0.0.1:11434`
   (Ollama local): encaminha intacto e grava `sha256` + corpo de cada request. Credencial Ollama
   cadastrada com `baseUrl=http://localhost:11500` — **sem chave externa**.
3. Fluxo completo por HTTP: `POST /user-accounts/create` → `POST /auth/login` →
   `PUT /provider-credentials/OLLAMA` → `PUT /tenant/llm-config` → `POST /prompt-templates` →
   `POST /chat` (sessão nova, 1 turno). Template e mensagem do usuário **fixos** nos dois lados.
4. ANTES: `git stash push -u -- backend/agent` (só os arquivos do batch) → build → rodada.
   DEPOIS: `git stash pop` → build → rodada.

## Medidas

| Medida | ANTES | DEPOIS | Igual |
|---|---|---|---|
| `chatRequestBytes` (corpo ao Ollama) | 359 | 359 | ✅ |
| `chatRequestSha256` | `793153e1a0d8f93b155452a5b4f3f7c0906d6ea523c0230c249b15414cb1d1d8` | idem | ✅ |
| `promptSignature` (msgs+model+options+stream) | `471ef90d55de89d35058f6c9bf14ac90647cf2904ebe10ec8766357fe66aa739` | idem | ✅ |
| `systemChars` (sucessor do `systemBlockChars`, morto com o RAG) | **72** | **72** | ✅ |
| `totalPromptChars` / `promptChars` | 93 / 93 | 93 / 93 | ✅ |
| `promptMessages` / `historySize` / `maxTokens` / `temperature` | 2 / 1 / 512 / 0.7 | idem | ✅ |
| status HTTP do chat | 200 | 200 | ✅ |

Duas rodadas bem-sucedidas em cada lado (a 1ª tentativa de cada lado deu 502 por timeout de carga do
modelo — registrado, não mascarado; rodadas válidas medidas com Ollama quente).

## Diff mecânico bloco a bloco (confirmação independente)

Cada bloco extraído foi diffado (`git show HEAD:…` vs arquivo novo): 3 ramos do outcome,
`recordTurn` + 4 helpers, `toProviderMessages`, `isModelNotFound` + `isProviderModelErrorPayload` +
`MODEL_NOT_FOUND_PATTERN`, 3 leituras do provider — **idênticos** salvo reflow, `elapsedMs` por
parâmetro e `isModelNotFound` pela nova casa. `LangChain4jTurnGateway`: **1 linha** (`+import`).

## Ressalvas registradas

- Cabeçalhos (`Authorization`/sessão) **não** capturados pelo proxy — só corpo+sha. Risco baixo
  (`baseUrl` provado pelo proxy em `:11500`; chave casada por stub).
- Cenário medido: 1 turno, histórico USER-only. Os ramos SYSTEM/nulo/ordem do `toProviderMessages`
  ganharam cobertura depois, no `ProviderMessagesTest` (4 testes, pós-review).
- Evidência bruta: `/tmp/opencode/e2e-result-{before,after}.json`,
  `/tmp/opencode/e2e-provider-calls-{before,after}.jsonl`, logs `/tmp/opencode/e2e-backend-{before,after}.log`.

## Suite

371/75/189 (baseline) → **386/75/189** pós-batch → 211 no módulo `agent/impl` pós-correzões de review.
0 falhas em todos.
