# RODADA 2 — Resposta vazia do modelo · caminho de escrita do vínculo KB · visibilidade na tela

- **Data:** 2026-09-26
- **Decisão:** usuário aprovou (opção A) — 422 para resposta vazia
- **Precondição:** rodada 1 (C+A) entregue e aprovado · 409 unit + 63 IT + 177 FE
- **Prioridade:** 1 (guarda) → 2 (vínculo KB) → 3 (visibilidade) → 4 (🟡 cosmético)

---

## Parte 1 🔴 — Resposta vazia do modelo causa 409 de banco

### Evidência
```
ERROR: null value in column "content" of relation "chat_message" violates not-null constraint
Failing row: (…, ASSISTANT, null, …)  →  409 Data integrity violation
```
Log da mesma requisição: `elapsedMs=30787 completionTokens=2048 finishReason=LENGTH`.
Reproduzido direto no upstream (`mimo-v2.5` via `OPENCODE_GO`):

**Modelo de referência: `mimo-v2.5`** (default do `OPENCODE_GO` — é o que o usuário usa).
Medido direto no upstream, pergunta que provoca raciocínio pesado:

| `max_tokens` | `finish_reason` | `content` | `reasoning` |
|---|---|---|---|
| 32 | `length` | **`NULL`** | 156 chars |
| 64 | `length` | 45 chars | 264 chars |
| 128 | `length` | 5 chars | 542 chars |
| 256 | `length` | 610 chars | 265 chars |

**`mimo-v2.5` é um modelo de raciocínio**: com orçamento pequeno ele consome **todos** os tokens
em `reasoning` e devolve `content: null`. É exatamente o caso que quebra.

Com pergunta longa e orçamento maior, o mesmo modelo **devolve conteúdo** (2048 → 8926 chars),
ou seja: **o gatilho é `maxTokens` pequeno + pergunta que exige raciocínio**, não a pergunta longa.

Referência secundária: `glm-5.2` devolve `content: ''` (vazio, não null) no mesmo cenário —
varia por modelo. **A guarda tem de cobrir `null` E branco**, porque o vazio exato varia.
`chat_message.content` é `NOT NULL` e `ChatAssistantTxService.saveAssistantAndTouch` grava sem
nenhuma guarda → erro de integridade em vez de erro útil.

### Solução
1. **Guarda antes de persistir.** Se `full.aiMessage().text()` for `null` ou em branco:
   - **não** gravar mensagem de assistente;
   - **não** dar `touch` na sessão;
   - lançar exceção de domínio nova, ex.: `ModelEmptyResponseException`, mapeada para
     **HTTP 422** `MODEL_RETURNED_EMPTY` com mensagem pt-BR acionável, no padrão do
     `ModelUnavailableException` já existente:
     *"O modelo {m} não devolveu texto (limite de tokens atingido sem conteúdo). Aumente o
     limite de tokens do agente ou encurte a pergunta."*
   - **A mensagem do usuário já foi salva antes (TX1)** e deve **permanecer** — o usuário
     precisa do contexto para reenviar. Só a mensagem de assistente não é criada.
2. Registrar no `ChatTrace` o motivo (`empty_model_response`) com `finishReason`, `maxTokens`
   e `completionTokens` — sem o texto do modelo (pode ser enorme).
3. **Não** gravar placeholder nem string vazia: poluiria a conversa e enganaria o histórico.
4. Não alterar o `finish_reason=LENGTH` com conteúdo válido: esse caso é sucesso normal
   (resposta truncada) e deve continuar 200.

### Testes
- `ChatServiceImplTest`:
  - `aiMessage().text()` null → `ModelEmptyResponseException`, **nenhuma** linha ASSISTANT salva
  - `text()` vazio/branco → idem
  - `text()` vazio **e** `finishReason=LENGTH` → idem (caso real do `mimo-v2.5` com `max_tokens=32`)
  - `finishReason=LENGTH` **com** texto (o caso normal do `mimo-v2.5` em 2048/256) → **200**, mensagem salva (não regredir)
  - a mensagem **do usuário** permanece na sessão no caso de erro
- IT: simular provider devolvendo `null` (como `mimo-v2.5` em `max_tokens=32`) e devolvendo `""` (como `glm-5.2`) → **422** nos dois `MODEL_RETURNED_EMPTY`, sem
  `chat_message` de assistente, com a de usuário presente.

---

## Parte 2 🔴 — Vínculo KB↔agente não tem caminho de escrita

### Evidência
| Camada | Achado |
|---|---|
| Frontend | `KbPicker` coleta ids, mas o save envia `knowledgeBases: null` (`AgentEditorView.vue:277`) |
| Backend | `AgentServiceImpl:74-80` descarta o campo e loga WARN apontando para `/v1/agents/{id}/knowledge-bases` |
| Endpoint | **não existe** |
| `KbAgentLinkPort` | **só leitura** |

Teste: criar agente com `knowledgeBases:['3b51cdb5…']` → coluna vazia e **nenhuma** linha em
`agent_knowledge_base`.

### Dados verificados (o material está pronto)
- KB `teste` (`3b51cdb5-6912-4c7a-9de8-56be879db65b`), tenant `44059cfc…`, 1 documento `READY`,
  **5 embeddings** em `kb_embeddings_768`, embedding model `nomic-embed-text` (768).
- `nomic-embed-text` disponível no Ollama local.

### Solução
1. **Backend — endpoint de vínculo**
   `PUT /v1/agents/{id}/knowledge-bases` · body `{"kbIds": ["uuid", ...]}` (lista vazia = desvincular)
   · `@HasPermission("AGENT_MANAGE")` · `@Valid` (`@NotNull`, `@Size(max=5)`) ·
   anti-IDOR: 404 se o agente não for do tenant · resposta `200 {agentId, kbIds, kbNames}`.
   Respeitar a regra já documentada de **máximo 5 KBs por agente** (RN-KB4) com mensagem pt-BR.
2. **Port de escrita, mantendo a fronteira** `agent → knowledge/api`:
   - `KbAgentLinkPort` ganha método de escrita (ex.: `replaceLinks(agentId, tenantId, kbIds)`),
     com **validação de tenant** do lado de `knowledge` (cada KB precisa pertencer ao tenant).
   - Implementação transacional: substitui o conjunto de vínculos do agente.
3. **Frontend**
   - `AgentEditorView`: no save, após criar/atualizar, chamar o endpoint com os ids do `KbPicker`.
   - `agent-admin.service`: método `linkKnowledgeBases(agentId, kbIds)`.
   - Estado de erro do vínculo **visível** (toast), sem perder o que já foi salvo.
4. Manter `ignoreLegacyKnowledgeBasesWrite` (o campo continua ignorado no create/update por
   compatibilidade) — mas o WARN passa a apontar para o endpoint **que agora existe**.

### Testes
- **IT de ponta a ponta (o que prova a feature)**: criar KB (ou reaproveitar `3b51cdb5…`) →
  vincular ao agente via endpoint → `linkedKbIds` retorna o id → **chat** com `mimo-v2.5` → `ragUsed=true`
  com `citations` não vazias → `ragOutcome=ok` no log. **Este é o critério de aceite
  principal da rodada**: é o primeiro teste que prova que o RAG funciona de verdade.
- `AgentControllerV1`: 401 sem token, 403 sem permissão, 404 agente de outro tenant (anti-IDOR),
  400 `kbIds` inválido, 200 com e sem vínculo, 422/400 ao exceder 5 KBs.
- `KnowledgeAgentLink` (impl): substitui vínculos, rejeita KB de outro tenant, é transacional
  (falha não deixa vínculo parcial).
- `ChatServiceImplTest`: com `linkedKbIds` mockado, `ragOutcome=ok` e chunks no `systemBlock`.
- Frontend: `CredentialsView`-style spec para o editor — salvar com KB selecionada chama o
  endpoint com os ids; erro de vínculo mostra toast.

---

## Parte 3 🟡 — Tornar o motivo visível na tela (o que o usuário pediu)

A resposta agora traz `ragReason` (`no_link`, `kb_not_found_for_tenant`,
`no_chunk_above_threshold`, `timeout`, `error`, `dimension_mismatch`, `rag_disabled`, `ok`).
**A tela não mostra nada disso** — o usuário vê o modelo afirmar "não recebi contexto".

- `ChatView` (ou onde a resposta é renderizada): quando `ragUsed === false` e `ragReason`
  presente e != `rag_disabled`, exibir um `Message` discreto (severity `info`/`warn`) com
  **texto pt-BR por motivo** (tabela de i18n, sem texto hardcoded).
- `MODEL_RETURNED_EMPTY` (422) também precisa de mensagem na UI no lugar certo — hoje o
  tratamento de erro genérico pode mostrar "Falha ao enviar". Verificar e cobrir.
- **Acessibilidade**: manter `role="status"`/`aria-live` sem duplicar anúncio do banner LGPD.
- Zero string hardcoded (i18n pt-BR, como o resto do projeto).

---

## Parte 4 🟢 — `provider-default` no lugar errado

`LoggingChatTraceRecorder.format()` usa o mesmo helper para `maxTokens`/`topP` e para
`promptTokens`/`completionTokens`/`totalTokens`. Quando o provider não reporta tokens, a linha
diz `promptTokens=provider-default` — texto **errado** para contagem (não existe "default" de
token: ou reportou, ou não reportou).
- Separar: `maxTokens`/`topP` → `provider-default` (correto, é um teto que o agente não mandou).
- Contagens de token → `not_reported`.
- Teste do `format()` cobrindo os dois.

---

## Fora de escopo
- Rotação/reescrita de embeddings; KB de 1536 sem credencial OpenAI.
- `ragReason` em streaming (não há streaming).
- Rate limit e `MODEL_UNAVAILABLE` continuam como estão.

## Critérios de aceite — Rodada 2
- [ ] Resposta vazia → 422 `MODEL_RETURNED_EMPTY`, sem linha de assistente, com a de usuário
- [ ] `finishReason=LENGTH` com texto → continua 200
- [ ] `PUT /v1/agents/{id}/knowledge-bases` funcionando, anti-IDOR, máx. 5
- [ ] Port de escrita com validação de tenant
- [ ] Frontend envia os ids do `KbPicker`
- [ ] **IT E2E: chat com KB vinculada, modelo `mimo-v2.5` → `ragUsed=true` + `citations` + `ragOutcome=ok`**
- [ ] Tela mostra o `ragReason` em pt-BR
- [ ] `not_reported` vs `provider-default` corrigido
- [ ] `mvn clean install -DskipTests` + `mvn test` + `mvn verify -pl integration-test -Pintegration -am` → 0 falhas
- [ ] `npm run test` + `npm run build` → 0 falhas
- [ ] `fullstack-code-reviewer` → zero 🔴
- [ ] Brain (se o MCP voltar; senão deixar em `brain-pending/`)
- [ ] Processos finalizados
