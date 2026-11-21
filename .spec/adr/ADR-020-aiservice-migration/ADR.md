---
adr_id: ADR-020
unit: adr
status: in-progress
tipo: adr-sem-prd
data: 2026-11-21
decisor: architect
---

# ADR-020 — Migrar o gateway para Ai Services com memória por sessão e `AssistantDefault`

> **Ciclo de execução aberto em 2026-11-21** (aprovação humana): `unit: adr` + `status: in-progress`. Ciclo único cobrindo ADR-001 + ADR-020. SPEC/TASKS nascem neste diretório com marcador `<!-- scope: adr/ADR-020 -->` no TASKS (F4). Código/pom/config/teste/migration só na delegação ao `backend-engineer`. `workflow-state.json`, branch/PR e ADRs revisados seguem fora deste ciclo.
> Amparo: `.agents/rules/prd-adr-pipeline.md:43-45` (arquitetural com valor de produto limitado à organização interna ⇒ ADR sem PRD).
> Teto de 5 ADRs do PRD `langchain4j-ai-services` está gasto; este ADR não entra naquele ADR-INDEX.

- **depende-de**: ADR-001 (revisado), ADR-002 (revisado), ADR-003 (revisado), ADR-017 (history), ADR-019 (assistant).
- **Sequenciamento**: ciclo único cobrindo ADR-001 + ADR-020 (decisão humana 2026-11-21). A unificação da conversão (K11) e a migração AiService são implementadas juntas; `PublicChatServiceImpl` passa a chamar o mesmo proxy que o caminho humano. IT de caracterização dos 2 chamadores é obrigatória.
- **Status**: in-progress

## 1. Decisão

Substituir a montagem manual de `List<ChatMessage>` e a chamada `client.chat(prompt)` no gateway por um **AI Service** do LangChain4j 1.19.0, com:

1. **Interface `AssistantDefault`** — ai service declarado com `@SystemMessage({{...}})` e `@UserMessage`, trazendo **o mesmo texto do prompt do seed** (`V6__seed_default_prompt.sql:65-67`) como constante Java. É a única fonte do prompt padrão.
2. **Remoção do seed** — migration V8+ apaga as linhas `code='default_general'` de `prompt_template` dos tenants (decisão humana: tenants passam a depender 100% de `AssistantDefault`). V6 permanece no histórico Flyway (imutável) — a V8+ é que desfaz o dado.
3. **Gateway via classe abstrata** — `AbstractLangChain4jTurnGateway` (ou nome equivalente) contém o proxy AiService injetado e expõe um método abstrato `execute(TurnRequest)`. A concreta mantém retry, criação de cliente, `TurnReply` e métricas — o que hoje está em `assistant/impl/.../LangChain4jTurnGateway.java:51-130` é dividido entre abstract + concreta, sem perder responsabilidade.
4. **Memória via provider** — `AiServices.builder(...).chatMemoryProvider(...)` com `@MemoryId = sessionId (UUID)`. Isso **revisa ADR-003**: `ChatMemory`/`@MemoryId` entram, com os riscos documentados e IT de corrida obrigatória.
5. **Sem starter** — construção manual via `AiServices.builder`, sem `langchain4j-spring-boot-starter` (ADR-002:33 mantém sua restrição quanto ao starter).

## 2. Motivo

- O gateway hoje monta `SystemMessage` + histórico na mão (`LangChain4jTurnGateway.java:120-129`) e chama `client.chat(prompt)` (`:83`) — exatamente o boilerplate que Ai Services eliminam (docs oficiais AI Services, 2026-11-21).
- O prompt padrão vive em SQL (seed V6), tornando-o invisível ao código e dependente de migration. `AssistantDefault` o torna explícito e testável.
- O gateway é complexo de entender — a abstract + concreta separam "contrato do ai service" de "política de turno" (retry, métricas).

## 3. Fatos de API verificados (URL + data 2026-11-21)

- `AiServices.builder(Class<T>).chatModel(...).chatMemoryProvider(...).build()` — https://raw.githubusercontent.com/langchain4j/langchain4j/1.19.0/langchain4j/src/main/java/dev/langchain4j/service/AiServices.java — 2026-11-21 (tag 1.19.0; A3/RESEARCH.md:62).
- `ChatMemoryProvider.get(Object memoryId)` — https://raw.githubusercontent.com/langchain4j/langchain4j/1.19.0/langchain4j/src/main/java/dev/langchain4j/memory/chat/ChatMemoryProvider.java — 2026-11-21 (tag 1.19.0).
- `@MemoryId` aceita qualquer tipo com `equals`/`hashCode` corretos — https://raw.githubusercontent.com/langchain4j/langchain4j/1.19.0/langchain4j/src/main/java/dev/langchain4j/service/MemoryId.java — 2026-11-21 (tag 1.19.0). `UUID` é elegível (contrato padrão do tipo).
- `@SystemMessage` com variáveis `{{var}}` resolvidas por `@V`; `@SystemMessage` tem precedência sobre `systemMessageProvider` — https://raw.githubusercontent.com/langchain4j/langchain4j/1.19.0/langchain4j/src/main/java/dev/langchain4j/service/SystemMessage.java — 2026-11-21 (tag 1.19.0).
- **Não usar `.chatMemory(...)`** (memória única compartilhada) — apenas `.chatMemoryProvider(...)` (por `@MemoryId`), para isolar sessões.
- **Concorrência**: AI Service não deve ser chamado simultaneamente para o mesmo `@MemoryId` (corrompe `ChatMemory`); sem proteção nativa — https://raw.githubusercontent.com/langchain4j/langchain4j/1.19.0/langchain4j/src/main/java/dev/langchain4j/service/AiServices.java — 2026-11-21 (A8/D2.5).
- **memory ≠ history**: LangChain4j oferece apenas "memory"; histórico integral permanece no JPA/`history` — https://docs.langchain4j.dev/tutorials/chat-memory — 2026-11-21 (**não versionada**, A12). `evict` remove mensagens do store (A13) — mesma URL.

## 4. Revisão de ADRs

| ADR | Efeito |
|---|---|
| **ADR-003** | **Revisado.** De "nenhuma `ChatMemory`/`@MemoryId`" para "memória por sessão via `ChatMemoryProvider`, `@MemoryId = sessionId (UUID)`". JPA/`history` **continua** fonte de verdade do histórico (memory não substitui history). Riscos A8/A12/A13/K2/K9 passam a ser tratados, não evitados. |
| **ADR-002** | **Revisado.** De "só montagem/parsing, stateless" para "proxy AiService com memória, gateway via abstract". Mantém: porta `ChatTurnGateway` (agora em `assistant/api`), retry fora do builder, cliente por turno, sem starter, `TurnReply` com métricas. |
| **ADR-001** | **Revisado no item 1**: a conversão `domínio → mensagens do provider` deixa de ser necessária no caminho interno — o proxy recebe `@UserMessage`/`@SystemMessage` e a memória gerencia o histórico. A ordem "template primeiro" (K4) é preservada por `@SystemMessage`. K11 (conversão pública divergente) deve ser resolvido neste ADR: `PublicChatServiceImpl` passa a chamar o mesmo proxy. |
| **ADR-017 / ADR-019** | Inalterados. `history/` e o rename `assistant` permanecem. |

## 5. Desenho

### 5.1 `AssistantDefault` (ai service)

```java
interface AssistantDefault {
    String chat(@MemoryId UUID sessionId,
                @SystemMessage("{{system}}") @V("system") String system,
                @UserMessage("{{user}}") @V("user") String user);
}
```

- `@SystemMessage` garante system antes de user (K4 preservado).
- `system` = texto do `PromptTemplate` **quando a sessão tiver um selecionado**; caso contrário, `AssistantDefault.PROMPT` (constante com o texto do seed) é o **fallback**. A constante nunca sobrescreve um template escolhido — a sessão é a autoridade (ADR-013:2).
- `user` = texto do turno; o histórico anterior vem da `ChatMemory` do provider, não mais de `TurnRequest.conversation`.
- `@MemoryId = sessionId (UUID)` — estável entre turnos da mesma sessão (D2.2); nunca `turnId`.

### 5.2 Abstract do gateway

```java
abstract class AbstractLangChain4jTurnGateway implements ChatTurnGateway {
    // proxy AssistantDefault injetado
    // criação de cliente por turno (LlmClientFactory)
    // política de retry (LlmRetryPolicy)
    protected abstract AssistantDefault proxyFor(ChatModel client);
    protected abstract TurnReply toReply(...);
}
```

A concreta mantém `@Component`, constrói o proxy via `AiServices.builder(AssistantDefault.class).chatModel(client).chatMemoryProvider(id -> ...).build()`, e implementa `toReply` preservando `TokenUsage`/`FinishReason` medidos (K5).

### 5.3 Memória

- `ChatMemoryProvider` cria `MessageWindowChatMemory.withMaxMessages(N)` por `sessionId`.
- Store: **em memória por padrão** (sem persistência nova). `history/` JPA continua com o histórico integral da UI.
- `alwaysKeepSystemMessageFirst(true)` — garante K4 mesmo com system message em memória (A11).
- Política de concorrência: serialização por `sessionId` (ex.: `ConcurrentHashMap<UUID, Object>` de locks) — obrigatório antes do merge (A8).

### 5.4 Seed

- Migration V8+ (em `assistant/dbupdate`): `DELETE FROM prompt_template WHERE code='default_general'`.
- `AssistantDefault.PROMPT` é a única fonte do texto **padrão (fallback)** — não substitui templates escolhidos pelo usuário.
- `PromptSeedIT` é reescrito/removido — o seed não existe mais.
- Regra de precedência congelada: `PromptTemplate` da sessão > `AssistantDefault.PROMPT`. A sessão continua a autoridade do prompt (ADR-013:2), inclusive no novo caminho AiService.

## 6. Interface congelada

1. Porta `ChatTurnGateway` em `assistant/api` — 1 método, 2 chamadores. Inalterada.
2. `TurnReply` mantém `Status{ANSWERED, EMPTY_RESPONSE, FAILED}`, exceção desembrulhada e métricas medidas (A7).
3. Ordem system → user/histórico (K4) byte a byte — provada por IT de caracterização.
4. `TokenUsage`/`FinishReason`/`promptChars` idênticos ao baseline (K5) — IT de caracterização antes e depois.
5. `@MemoryId = sessionId (UUID)`, nunca `turnId`.
6. `history/` JPA continua fonte de verdade do histórico; `ChatMemory` é deriva volátil.
7. Sem `langchain4j-spring-boot-starter`, sem `@AiService`, sem `.chatMemory(...)` compartilhada.
8. Zero MFA, zero contrato HTTP novo, zero cota/retry/classificação alterados (retry continua em `LlmRetryPolicy`).

## 7. Riscos

| # | Risco | Evidência | Tratamento |
|---|---|---|---|
| K4 | Ordem do system message | `LangChain4jTurnGateway.java:120-129` | `@SystemMessage` + `alwaysKeepSystemMessageFirst(true)` + IT |
| K5 | Perda de métricas | ADR-002:61 (inferência) | Ler `Result<T>`/`ChatResponse` final; IT de paridade com baseline |
| A8 | Corrupção por concorrência no mesmo `@MemoryId` | AiServices.java javadoc 1.19.0 | Serialização por sessionId + IT de corrida |
| A12/A13 | memory ≠ history; evict destrutivo | docs chat-memory 2026-11-21 | `history/` JPA intacto; store sem evict ou evict controlado |
| K9 | `ScopedValue` não propaga em callbacks | `TenantContext.java:8-16` | Nenhum callback assíncrono; provider síncrono |
| R-seed | Tenants perdem prompt padrão | V6 seed | Migration V8+ apaga linhas; `AssistantDefault` é fallback obrigatório no código |
| R-clean | Abstract mal desenhada reintroduz complexidade | gateway atual | A abstract só injeta proxy + método abstrato; concreta mantém política |

## 8. Como verificar

1. `grep -rE 'ChatMemory|@MemoryId|chatMemoryProvider' backend/**` → presente apenas no provider do gateway e em `AssistantDefault` (não zero, pois ADR-003 foi revisado).
2. `grep -r 'client.chat(prompt)' backend/assistant/**` → zero (chamada direta substituída pelo proxy).
3. IT de caracterização: ordem system→user, `TokenUsage`/`FinishReason`/`promptChars` idênticos ao baseline.
4. IT de corrida: 2 threads no mesmo `sessionId` → sem corrupção de memória.
5. `npm test` EXIT=0; baseline 376/376 front + 62 reports/503 back; 0/0/0.
6. `grep -r 'default_general' backend/**/dbupdate/**` → zero (seed removido); `PromptSeedIT` reescrito.
7. Zero diff MFA, zero contrato HTTP novo, zero `.sql` de criação de tabela (só DELETE da V8+).
8. `code-reviewer` sem 🔴.

## 9. Fora desta decisão

Agent v2 (tools/planner), RAG, starter Spring Boot, mudança de contrato HTTP, cota, classificação de erro, telemetria nova, branch/PR, `workflow-state.json`.
