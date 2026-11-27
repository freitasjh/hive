---
prd_id: streaming-reasoning
research_version: 1
type: technical-research
title: "Pesquisa — transporte SSE, parse de thinking e retry em streaming (langchain4j 1.19.0 fixo)"
dono_da_decisao: architect (RESEARCH apenas fornece insumo)
dono_do_valor: product-manager
agente: engineer (md-only)
data: 2026-11-26
escopo: "PRD aprovado (.spec/prd/streaming-reasoning/PRD.md §16, prd_version 2); sem código, sem ADR, sem SPEC/TASKS"
insumos:
  - ".spec/prd/streaming-reasoning/PRD.md (§16 aprovado em 2026-11-26)"
  - "evidência de deepsearch 2026-11-26 registrada no PRD §15 e reaberta neste lote"
esteira: "PRD (aprovado) → RESEARCH (este arquivo) → ADR-INDEX → ADR → branch → SDD"
status: insumo-entregue
---

# RESEARCH — Streaming + reasoning no chat

> **Natureza.** Insumo técnico. **Não decide arquitetura** e não autoriza implementação. Veredito é do `architect` na esteira ADR-INDEX → ADR (`.agents/rules/prd-adr-pipeline.md`; `PRD.md:60`).
> **Este lote.** Artefato único: este arquivo. Nenhum `ADR-INDEX.md`, `ADR.md`, `SPEC.md`, `TASKS.md`, `workflow-state.json`, código, config ou teste criado/alterado. Diffs pré-existentes no `git status` pertencem a outros agentes.
> **Convenção de evidência.** Toda afirmação de versão/API = `afirmação — URL — consulta em 2026-11-26 — versão aplicável`. Fontes `raw.githubusercontent.com/.../1.19.0/...` são fixadas na tag. Javadoc `docs.spring.io/.../current/...` é **não versionado** (serviu `Spring Framework 7.1.0` nesta data) e precisa de checagem contra a versão do projeto no ADR. Onde não há fonte aberta, o texto diz explicitamente **inferência (sem fonte aberta nesta data)**. Nenhuma assinatura de API foi aceita de memória.

---

## 1. Pergunta de pesquisa e critérios

**Pergunta.** Dado `langchain4j.version = 1.19.0` fixo e `AiServices.builder` manual sem starter (`PRD.md:25,31`), quais opções minimizam custo/acoplamento para (a) transporte SSE nos dois endpoints, (b) separação de thinking em `delta.reasoning_content` e (c) retry em streaming com cancelamento no disconnect — **sem** `Flux`, **sem** bump de versão e **sem** vazar texto de pensamento para log/trace/JPA?

**Critérios explícitos** (mesmos cinco do padrão do repo; a ponderação é do `architect`, não deste insumo):

| Critério | Pergunta operacional |
|---|---|
| **Custo** | Quantos módulos/classes e quantas ITs de caracterização? |
| **Acoplamento** | Cria aresta nova entre módulos ou muda ciclo de beans? `ai/**` deve continuar folha (`PRD.md:86`). |
| **Testabilidade** | Dá para asserir canal de thinking, chunk terminal, cancel e concorrência em teste? |
| **Segurança** | Thinking fora de log/`ChatTrace`/JPA; redator continua valendo nos spans (`PRD.md:53`). |
| **Aderência** | Compatível com Java/Spring MVC + langchain4j 1.19.0 + `AiServices.builder` manual, sem dependência nova (`PRD.md:21,25`). |

**Restrição de fronteira (não negociável neste estudo):** `streaming_not_supported` vira streaming, mas formato de erro OpenAI, duplo `/v1` e `TurnReply` sem texto permanecem (`PRD.md:52,55,60,71`). Nenhuma alternativa propõe moderação por chunk, persistência de thinking, `Flux` ou upgrade.

---

## 2. Fontes e deepsearch adicional obrigatório

### 2.1 Tabela S1..S16 — o que cada fonte respondeu e o que ficou em aberto

Todas consultadas em **2026-11-26**.

| ID | Fonte (URL) | Versão aplicável | O que respondeu | O que ficou em aberto |
|---|---|---|---|---|
| S1 | `https://raw.githubusercontent.com/langchain4j/langchain4j/1.19.0/langchain4j/src/main/java/dev/langchain4j/service/AiServices.java` | tag 1.19.0 | Builder expõe `streamingChatModel(...)` como exclusivo com `chatModel(...)`; sem config de retry de transporte no builder | Como o proxy liga o handle de cancel ao `TokenStream` — verificar em ADR |
| S2 | `https://raw.githubusercontent.com/langchain4j/langchain4j/1.19.0/langchain4j/src/main/java/dev/langchain4j/service/TokenStream.java` (reaberta) | tag 1.19.0 | `onPartialResponse`, `onPartialThinking` (Experimental, `@since 1.2.0`), `onPartialResponseWithContext` (Experimental, `@since 1.8.0`), `onPartialThinkingWithContext`, `onUnmappedRawEvent` (`@since 1.17.0`), `onCompleteResponse(Consumer<ChatResponse>)`, `onError`, `start()` | Se os 7 providers emitem o overload *with context* (necessário para cancel — S6) |
| S3 | `https://raw.githubusercontent.com/langchain4j/langchain4j/1.19.0/langchain4j-core/src/main/java/dev/langchain4j/model/chat/response/PartialThinking.java` | tag 1.19.0 | `PartialThinking.text()` existe; tipo é `@Experimental`, `@since 1.2.0`, `ensureNotEmpty` | Estabilidade da API em upgrade futuro (fora de escopo) |
| S4 | `https://raw.githubusercontent.com/langchain4j/langchain4j/1.19.0/langchain4j-core/src/main/java/dev/langchain4j/model/chat/response/TokenUsage.java` | tag 1.19.0 | Só `inputTokenCount`/`outputTokenCount`/`totalTokenCount`; **sem `reasoning_tokens`** | Como expor detalhe de reasoning no chunk OpenAI-compatible — omitir ou mapear de fonte externa (decisão) |
| S5 | `https://raw.githubusercontent.com/langchain4j/langchain4j/1.19.0/langchain4j-core/src/main/java/dev/langchain4j/model/chat/response/PartialResponseContext.java` + `.../StreamingHandle.java` | tag 1.19.0 | `PartialResponseContext.streamingHandle()`; `StreamingHandle.cancel()` e `isCancelled()`, ambos `@Experimental @since 1.8.0` | Verificar na prática qual provider entrega contexto com handle real |
| S6 | `https://raw.githubusercontent.com/langchain4j/langchain4j/1.19.0/langchain4j/src/main/java/dev/langchain4j/service/CancellationUnsupportedStreamingHandle.java` | tag 1.19.0 | `cancel()` lança `UnsupportedFeatureException` quando o modelo **não** chama `onPartialResponse(PartialResponse, PartialResponseContext)`; `isCancelled()` = `false` | RF5 pode falhar em providers que só usam `onPartialResponse(String)` — pré-condição de teste por provider |
| S7 | `https://raw.githubusercontent.com/langchain4j/langchain4j/1.19.0/langchain4j/src/main/java/dev/langchain4j/service/AiServices.java` (S1) + PRD §15 (`OpenAiStreamingChatModel.java`, `AnthropicStreamingChatModel.java`, `OllamaChatRequestParameters.java`, `GeminiStreamingResponseBuilder.java`) | tag 1.19.0 | `returnThinking`/`sendThinking`/`thinkingFieldName` (default `reasoning_content`); `thinkingType`/`thinkingBudgetTokens`; `think(Boolean)`; thought parts → `AiMessage.thinking()` | Matriz efetiva de quais dos 7 providers emitem thinking sem config extra |
| S8 | `https://api.github.com/repos/langchain4j/langchain4j/git/trees/1.19.0?recursive=1` | tag 1.19.0 (6233 entradas) | Nenhum path contém `reactor`; também localizou os paths reais de `TokenUsage` e `Gemini*Parameters` usados em S4/S10 | Nenhuma — usada só como prova de ausência |
| S9 | `https://developers.openai.com/api/reference/resources/chat/subresources/completions/streaming-events/` | doc atual (não versionada) | `chat.completion.chunk`; `choices` **pode ser vazio no último chunk** com `stream_options.include_usage`; `usage` só no último chunk; `completion_tokens_details.reasoning_tokens` existe no schema upstream; `delta` não lista `reasoning_content` | Como o langchain4j 1.19.0 mapeia `reasoning_tokens` (S4 diz que não mapeia) |
| S10 | `https://raw.githubusercontent.com/langchain4j/langchain4j/1.19.0/langchain4j-google-ai-gemini/src/main/java/dev/langchain4j/model/googleai/GoogleAiGeminiChatRequestParameters.java` | tag 1.19.0 | Parâmetros específicos do provider são `aspectRatio`, `imageSize`, `cachedContentName`; **nenhum `thinkingConfig`** | Checar `DefaultChatRequestParameters`/builder do modelo antes de afirmar impossibilidade total — registrado como **não confirmado**, não como "impossível" |
| S11 | `https://developers.openai.com/api/docs/guides/streaming-responses/` | doc atual (não versionada) | Moderação por chunk não disponível no guia de streaming (apoio ao fora de escopo do PRD §21) | Nenhuma relevante a este lote |
| S12 | `https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/web/servlet/mvc/method/annotation/SseEmitter.html` | javadoc **current = 7.1.0** em 2026-11-26 | `send(Object)` = **uma linha `data`** (sem nome); `send(event().name(...).data(...))` = evento nomeado; `event()`; herdados `complete()`, `completeWithError(Throwable)`, `onCompletion(Runnable)`, `onError(Consumer)`, `onTimeout(Runnable)`; construtor `SseEmitter(Long timeout)` | Checar assinaturas contra a versão de Spring do projeto (Spring Boot 4.1.0) no ADR; como emitir literal `data: [DONE]` via converter |
| S13 | `https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/web/servlet/mvc/method/annotation/StreamingResponseBody.html` | javadoc **current = 7.1.0** em 2026-11-26 | `writeTo(OutputStream)` escreve direto sem prender thread do container; recomenda configurar `TaskExecutor` | Detecção de disconnect e montagem manual do framing — inferência sem IT neste lote |
| S14 | `https://platform.claude.com/docs/en/build-with-claude/streaming` | doc oficial atual (`anthropic-version: 2026-10-21`) | SSE **nomeado**: `message_start` → `content_block_start`/`thinking_delta`/`text_delta`/`signature_delta` → `message_delta` → `message_stop`; `thinking_delta` e `text_delta` são campos distintos; `usage` em `message_delta` é **cumulativo**; eventos `error` podem vir dentro do stream; recuperação = capturar parcial + retomar (Claude 4.6+ via mensagem de usuário) | Como normalizar usage cumulativo para o contrato "último chunk" do PRD; se os 7 providers do hive se comportam assim |
| S15 | `https://api-docs.deepseek.com/api/create-chat-completion` | doc oficial atual | `stream:true` → SSE **data-only** terminado por `data: [DONE]`; `stream_options` sem `stream:true` → `400`; `include_usage:true` põe `usage` em todos os chunks (null exceto o último); campo `reasoning_content` existe na mensagem; `completion_tokens_details.reasoning_tokens` existe | A aba "200 (Streaming)" não renderizou o schema do delta no fetch — **não confirmado** o nome exato `delta.reasoning_content` no stream oficial DeepSeek; divergência: DeepSeek diz que usage vem no último chunk **com um choice**, OpenAI (S9) diz `choices: []` |
| S16 | `https://developer.mozilla.org/en-US/docs/Web/API/Server-sent_events/Using_server-sent_events` | MDN, página alterada em 2026-09-03 | MIME `text/event-stream`; campo `event:` nomeia eventos; linhas `data:` consecutivas são concatenadas com `\n`; linha começando com `:` é comentário (keep-alive); `retry:` controla reconexão; `EventSource` reconecta por default e fecha com `.close()`; limite de 6 conexões por browser+domínio fora HTTP/2 | Consumo frontend é outra sessão (`PRD.md:35`): como o cliente vai autenticar e lidar com reconexão automática — fora deste lote |

**Deepsearch adicional exigido pelo task: SIM — 13 fontes abertas além das já coletadas** (S4, S5, S6, S10, S13, S14, S15, S16, mais reabertura integral de S2/S12 e checagem da árvore S8 via API oficial). Nenhuma recomendação abaixo vem de memória.

### 2.2 Fatos negativos registrados explicitamente (exigência do task)

1. **`langchain4j-reactor` ausente na tag 1.19.0.** Árvore recursiva oficial `https://api.github.com/repos/langchain4j/langchain4j/git/trees/1.19.0?recursive=1` (2026-11-26): nenhum path com `reactor`. `Flux` fica fora — alternativa descartada em §3.3, **não recomendada**.
2. **`TokenUsage` 1.19.0 sem `reasoning_tokens`.** `dev.langchain4j.model.output.TokenUsage` em `https://raw.githubusercontent.com/langchain4j/langchain4j/1.19.0/langchain4j-core/src/main/java/dev/langchain4j/model/output/TokenUsage.java` (2026-11-26) expõe só input/output/total. Enquanto isso, o schema OpenAI (S9) e a doc DeepSeek (S15) têm `completion_tokens_details.reasoning_tokens`. Gap de versão, não de contrato.
3. **`thinkingConfig` do Gemini não confirmado.** `GoogleAiGeminiChatRequestParameters` na tag 1.19.0 (S10) não tem tal campo; `GeminiStreamingResponseBuilder` (S7) parseia *thought parts* para `AiMessage.thinking()`. Não foi encontrada fonte que confirme config de thinking habilitável nessa tag. **Comportamento degradado (sem thinking), nunca erro** (`PRD.md:60,79`) — premissa aberta, não resolvida por palpite.

---

## 3. Decisão A — transporte SSE

### A1 — `SseEmitter` do Spring MVC (recomendação de insumo)

**Descrição.** Controller `produces = "text/event-stream"` devolvendo `SseEmitter`; `send(Object)` para chunks OpenAI-compatible (linha `data`), `send(event().name(...).data(...))` para os eventos nomeados do assistant (`thinking`/`text`/`done`/`error`); `onCompletion` → `streamingHandle().cancel()`; `onError`/`onTimeout` → terminal; `complete()` no fim.

**Fontes.** S12 (assinaturas), S5 (cancel), PRD §2/§5/§9 (contrato dos dois endpoints).

| Critério | Avaliação de insumo |
|---|---|
| Custo | Baixo — tipo nativo do stack, sem dependência nova; dois controllers, um contrato de evento reusado |
| Acoplamento | Nenhuma aresta nova; `ai/**` continua folha (`PRD.md:86`) |
| Testabilidade | Alta — `MockMvc` async assere chunks, eventos nomeados e `complete()`; `onCompletion` é gancho explícito para testar cancel (RF5) |
| Segurança | `send(Object)` não cria log; thinking passa só pelo mapper de evento; redator nos spans permanece (`PRD.md:53`) |
| Aderência | Alta — Spring MVC + langchain4j 1.19.0; `SseEmitter` é da mesma família de `ResponseBodyEmitter` já usada pelo stack |

**Prós.** Um único tipo cobre os **dois** formatos exigidos: data-only (público) e nomeado (assistant). `onCompletion` casa com RF5 sem callback custom. **Contras.** `send(Object)` é **data-only** — nomear evento exige `event()` (S12), senão RF7 quebra; `completeWithError` gera frame de erro do Spring, não necessariamente o evento `error` tipado do PRD (aberto §7); `data: [DONE]` depende do `HttpMessageConverter` para `String` — verificar por IT. Javadoc consultado é `current = 7.1.0`, não a versão do projeto.

### A2 — `StreamingResponseBody` com escrita manual no `OutputStream`

**Descrição.** Controller devolve `StreamingResponseBody`; `writeTo(OutputStream)` escreve `data: ...\n\n`, `event: ...\n` e o sentinel `[DONE]` byte a byte.

**Fontes.** S13 (assinatura e não bloqueio de thread), S16 (formato do wire).

| Critério | Avaliação de insumo |
|---|---|
| Custo | Médio — framing, flush, heartbeat e escape manuais; sem ganho sobre A1 |
| Acoplamento | Nenhum |
| Testabilidade | Média — é preciso capturar `OutputStream` e reconstruir o stream; menos ergonômico que `MockMvc` + emitter |
| Segurança | Mesma de A1, com mais código manual para acertar (risco de framing/envio de thinking cru) |
| Aderência | Alta — também é Spring MVC |

**Prós.** Controle total do wire; detecção de disconnect via exceção de escrita é direta (**inferência — sem IT aberta neste lote**). **Contras.** Reimplementa o que `SseEmitter` já faz (S12/S16); exige configurar `TaskExecutor` explicitamente (S13); mais superfície para bug de framing.

### A3 — `Flux<ServerSentEvent>` / WebFlux / `langchain4j-reactor` (descartado, **não recomendado**)

**Fontes.** S8 (módulo `reactor` ausente na tag 1.19.0); `PRD.md:21,64` (fora de escopo, sem bump, sem `Flux`).

| Critério | Avaliação de insumo |
|---|---|
| Custo | Alto — exige upgrade de versão + mudança de stack reativo |
| Acoplamento | Alto — muda modelo de execução do backend |
| Testabilidade | — (não avaliada: bloqueada por versão) |
| Segurança | — |
| Aderência | **Nula** — quebra restrição `1.19.0` fixa e introduz módulo inexistente na tag |

**Recomendação de insumo: A1.** A2 é reserva se IT provar que o converter não entrega `data: [DONE]` ou que `onCompletion` não dispara no disconnect. A3 permanece descartada por evidência de tag, não por preferência.

---

## 4. Decisão B — parse de thinking

### B1 — callbacks tipados `onPartialThinking` + `onPartialResponse` (recomendação de insumo)

**Descrição.** Registrar `TokenStream.onPartialThinking(PartialThinking)` → `delta.reasoning_content`; `onPartialResponse(String)` → `delta.content`; `onCompleteResponse(ChatResponse)` → `finish_reason` + usage; mapper único convertendo para o contrato de evento dos dois endpoints. Nenhum parse de JSON de provider.

**Fontes.** S2 (callbacks), S3 (`PartialThinking.text()`), S7 (normalização prévia feita pelo langchain4j: OpenAI `reasoning_content`, Anthropic thinking, Ollama `think`, Gemini thought parts), S14 (providers separam `thinking_delta` de `text_delta`), S9 (schema OpenAI não traz `delta.reasoning_content` — daí mapper próprio).

| Critério | Avaliação de insumo |
|---|---|
| Custo | Baixo — um mapper, sem parsers por provider |
| Acoplamento | Baixo — isolado em `ai/**`/web, sem aresta `impl → impl` nova |
| Testabilidade | Alta — IT assere "zero `content` em chunk de thinking e vice-versa" (RF3) com fake `PartialThinking` |
| Segurança | Alta — thinking não persiste; textos ficam só no payload do chunk; nenhum log de conteúdo |
| Aderência | Alta — API confirmada na tag 1.19.0; sem starter, sem versão nova |

**Prós.** Normalização de 7 providers já feita pela biblioteca; contrato único para público e assistant. **Contras.** `PartialThinking`/`onPartialThinking` são `@Experimental` (S2/S3) — isolar em tipos próprios (`PRD.md:82`); se provider não emitir thinking, degrada para sem thinking (S10, S7). Não resolve `reasoning_tokens` (S4).

### B2 — parse direto de eventos crus por provider (`onUnmappedRawEvent` / cliente SSE próprio)

**Descrição.** Assinar `onUnmappedRawEvent(Consumer<Object>)` (`@Experimental`, `@since 1.17.0` — S2) ou construir clientes SSE por provider e interpretar `thinking_delta`, `signature_delta`, `reasoning_content`, thought parts etc.

**Fontes.** S2, S7, S14, S15.

| Critério | Avaliação de insumo |
|---|---|
| Custo | Alto — um parser por provider, mais testes de formato |
| Acoplamento | Alto — `ai/**` passa a conhecer wire de cada provider |
| Testabilidade | Média — exige fixtures reais de stream por provider |
| Segurança | Pior — mais código tocando texto de pensamento; risco de vazamento em log/trace |
| Aderência | Média — usa API `@Experimental` da própria tag, mas duplica camada já existente |

**Prós.** Acesso a campos que a biblioteca não modela (`signature_delta`, `input_json_delta`, eventos de server-tool — S14). **Contras.** Fora do escopo (`PRD.md:21`: sem moderação/raw events); duplica normalização; nenhum requisito do PRD pede esses campos.

### B3 — extrair thinking heurístico do `content` (marcadores/substring) (descartado)

**Fontes.** `PRD.md:64` (descartado: vazamento em histórico/trace); S9/S15 (não há marcador canônico no schema).

| Critério | Avaliação |
|---|---|
| Custo | Baixo aparente, mas quebra RF3/RF6 e exige limpeza em histórico |
| Acoplamento | Baixo |
| Testabilidade | Baixa — fronteira thinking/texto não determinística |
| Segurança | **Inaceitável** — pensamento entra em `content`, logo em histórico/trace (`PRD.md:53`) |
| Aderência | Baixa — contrato DeepSeek pede canal separado |

**Recomendação de insumo: B1.** B2 só se um ADR futuro provar necessidade de campo raw; B3 é inaceitável por segurança.

---

## 5. Decisão C — strategy de retry em streaming

### C1 — retry **apenas antes do primeiro token** no gateway + terminal explícito (recomendação de insumo)

**Descrição.** Reutilizar `LlmRetryPolicy` existente (`PRD.md:27`; builder do AiServices não tem retry — S1): uma tentativa = criar `TokenStream`, registrar callbacks, `start()`. Se `onError` chegar **antes** de qualquer `onPartialResponse`/`onPartialThinking`, criar stream novo (nada foi emitido ao cliente). Se já houve token, **não** reintentar: emitir evento `error` + encerrar (`complete()`/`completeWithError`), sem duplicar bytes no wire. Timeout 120s por cliente (`PRD.md:55`); capturar `TenantContext`/usuário/turnId antes de `start()` (`PRD.md:81`).

**Fontes.** S1 (sem retry no builder), S2 (`onError`, `start()`), S6 (cancel pode não funcionar em todo provider), S9 (usage final pode não chegar se stream for interrompido/cancelado), S14 (`error` pode vir dentro do stream), PRD §6/§10.

| Critério | Avaliação de insumo |
|---|---|
| Custo | Baixo — reusa política existente; um booleano "já emitiu token" + handler terminal |
| Acoplamento | Baixo — fica no gateway, sem tocar `ai/**` folha |
| Testabilidade | Alta — teste de erro antes do 1º token (retry) × depois do 1º token (sem retry), com emitter fake |
| Segurança | Boa — não reenvia conteúdo de thinking duplicado; sem persistência |
| Aderência | Alta — casa com `PRD.md:54` ("retry apenas antes do primeiro token") e com a API da tag |

**Prós.** Sem duplicação de tokens no cliente; sem re-cobrança silenciosa de turno inteiro; erro rastreável. **Contras.** Se o provider falhar no token 1, o cliente vê o erro após a tentativa extra; não recupera streams longos interrompidos (aceito pelo PRD).

### C2 — capturar parcial + retomar do ponto de falha ("resume")

**Descrição.** Guardar tokens já recebidos e montar nova requisição pedindo continuação, com o parcial no prompt.

**Fontes.** S14 (estratégia oficial Anthropic: capturar parcial; Claude 4.6+ usa mensagem de usuário "continue from where you left off"), S9 (usage final ausente em stream interrompido).

| Critério | Avaliação de insumo |
|---|---|
| Custo | Alto — nova requisição, prompt de continuação por provider, controle de duplicidade |
| Acoplamento | Alto — gateway passa a reconstruir prompt de turno |
| Testabilidade | Média — precisa simular corte em vários pontos |
| Segurança | Pior — parcial (possivelmente thinking) entra em nova mensagem; risco de virar histórico/trace se mal isolado |
| Aderência | Baixa — fora do escopo aprovado (`PRD.md:21,54`) |

**Prós.** Evita reprocessar resposta longa. **Contras.** Estratégia é provider-específica (S14 descreve Anthropic, não os 7 do hive); exige persistir parcial em memória; conflita com "sem texto de thinking em banco/trace".

### C3 — repetir o turno inteiro do zero após qualquer erro (descartado)

**Fontes.** Inferência a partir de S2/S9/S14; `PRD.md:54` (retry só antes do primeiro token).

| Critério | Avaliação |
|---|---|
| Custo | Baixo de código, alto de consequência |
| Acoplamento | Baixo |
| Testabilidade | Baixa — cliente recebe prefixo duplicado |
| Segurança | Ruim — reemite pensamento e texto já entregues |
| Aderência | **Nula** — viola restrição de retry do PRD |

**Recomendação de insumo: C1.** C2 é evolução futura, não deste PRD. C3 é inaceitável.

---

## 6. Síntese das recomendações de insumo (não é decisão)

| Decisão | Recomendação | Alternativa de reserva | Motivo curto |
|---|---|---|---|
| A — transporte | **A1 `SseEmitter`** | A2 `StreamingResponseBody` se IT de `[DONE]`/disconnect falhar | Um tipo cobre chunks data-only e eventos nomeados; `onCompletion` dá o cancel (S12/S5) |
| B — parse de thinking | **B1 callbacks tipados** | B2 só se ADR provar necessidade de campo raw | Normalização dos 7 providers já existe na tag 1.19.0 (S2/S3/S7); sem parser por provider |
| C — retry | **C1 pré-primeiro-token** | nenhuma neste PRD | Casa com `PRD.md:54`; evita duplicar conteúdo no wire (S9/S14) |

**Nada aqui autoriza implementação, ADR, SPEC ou TASKS.** `architect` pondera critérios e decide; `product-manager` valida valor/aceite.

---

## 7. Riscos, premissas não validadas e perguntas em aberto

### 7.1 Riscos com fonte

| # | Risco | Evidência | Mitigação sugerida (insumo) |
|---|---|---|---|
| R1 | Cancelamento pode lançar `UnsupportedFeatureException` em providers que só usam `onPartialResponse(String)` | S6 | IT de cancel por provider **antes** de ADR-001; se falhar, degradar para "sem cancel" documentado |
| R2 | `reasoning_tokens` não existe no `TokenUsage` 1.19.0 | S4 vs S9/S15 | Chunk de usage com input/output/total; `completion_tokens_details` omitido e documentado (`PRD.md:78`) |
| R3 | Gemini sem `thinkingConfig` confirmado na tag | S10 + S7 | `returnThinking(true)`; ausência de thoughts = degradação silenciosa, nunca erro |
| R4 | Thinking é `@Experimental` em `TokenStream`/`PartialThinking` | S2/S3 | Isolar em tipos próprios; nenhuma persistência (`PRD.md:82`) |
| R5 | Divergência de formato de chunk final: OpenAI `choices: []` × DeepSeek "último chunk com 1 choice" | S9 vs S15 | Contrato emitido pelo hive segue OpenAI-compatible (`PRD.md:68`); necessita IT de paridade |
| R6 | `usage` final pode não chegar se cliente cancelar | S9 | Não exigir usage em caso de `[DONE]` ausente/cancel; `onCompletion` encerra sem chunk de usage |
| R7 | Javadoc Spring consultado é `current = 7.1.0`, não a versão do projeto | S12/S13 | Confirmar assinaturas contra Spring Boot 4.1.0 no ADR-002 |

### 7.2 Premissas não validadas (levadas ao decisor; **não resolvidas por palpite**)

1. `send(Object)` do `SseEmitter` entrega literalmente `data: [DONE]` com o `HttpMessageConverter` do projeto — **inferência; verificar por IT** (S12).
2. `onCompletion` dispara no disconnect real do cliente (browser/proxy), não só em `complete()` — **inferência; verificar por IT** (S12 + S5).
3. Os 7 providers emitem `PartialThinking`/contexto com handle utilizável — **inferência; verificar por teste de caracterização por provider** (S2/S6/S7).
4. `thinkingConfig` do Gemini existe ou é habilitável na 1.19.0 — **não confirmado** (S10); api de request específica não expõe o campo.
5. Nome exato `delta.reasoning_content` no stream oficial DeepSeek — a aba "200 (Streaming)" da doc não renderizou no fetch (S15); o PRD fixou esse nome como contrato **de saída do hive**, o que é suficiente, mas não prova compatibilidade bidirecional.

### 7.3 Perguntas que exigem decisão de `product-manager` / `architect` (HITL se bloqueante)

| # | Pergunta | Dono sugerido | Insumo disponível |
|---|---|---|---|
| Q1 | Erro terminal no público: evento `error` nomeado ou `completeWithError(Throwable)` (formato de erro OpenAI inalterado — `PRD.md:52`)? | product-manager + architect | S12 (ambos existem), S14 (provider pode mandar `error` no stream) |
| Q2 | Aceitar `stream_options` desconhecido sem 400 e confirmar `choices: []` + usage no penúltimo chunk? | product-manager | S9 (OpenAI), S15 (DeepSeek divergente), PRD §9 |
| Q3 | Se nenhum provider emitir thinking, manter canal vazio ou omitir chunks de thinking? (degradação silenciosa) | product-manager | S7/S10, PRD §7/§10 |
| Q4 | A ordem de emissão thinking → texto é garantida ou o mapper deve ordenar por callback? | architect | S2 (callbacks), S14 (blocos com `index`) |
| Q5 | Lock compartilhado sync×stream por `@MemoryId` — `ReentrantLock` adquirido antes de `start()` e liberado no terminal cobre callbacks assíncronos? | architect | PRD §10, S2 (`start()` assíncrono) |

---

## 8. Matriz de rastreabilidade

| RF (PRD §5) | Alternativa recomendada | Fontes de apoio |
|---|---|---|
| RF1 cliente por provider com thinking | B1 | S7, S10 |
| RF2 proxy com mesmo `@MemoryId`/system/user | B1 + C1 | S1, S2 |
| RF3 canais separados | B1 | S3, S7, S14 |
| RF4 `finish_reason` + usage + `[DONE]` | A1 + B1 | S9, S12, S15, S16 |
| RF5 cancel no disconnect | A1 | S5, S6, S12 |
| RF6 `stream:true` aceito | A1 | S9, S15 |
| RF7 eventos nomeados no assistant | A1 | S12, S16 |
| RF8 lock compartilhado | C1 | S2, PRD §10 |

| Alternativa | ADR-001 (provider + gateway `ai`) | ADR-002 (endpoints SSE) |
|---|---|---|
| A1 | pré-condição (handle de cancel) | **núcleo** |
| A2 | — | reserva |
| B1 | **núcleo** (mapper) | contrato de evento |
| B2 | — | fora de escopo |
| C1 | **núcleo** (política de retry) | handler terminal |

---

## 9. Encerramento

**Co-assinatura técnica (engineer).** Insumo entregue: este `RESEARCH.md`. Contribuição nova sobre o PRD: (1) `TokenUsage` 1.19.0 lido na fonte — sem `reasoning_tokens` (S4); (2) `StreamingHandle.cancel()` confirmado, **com `UnsupportedFeatureException` documentada para modelos sem overload com contexto (S5/S6)** — risco direto para RF5; (3) `thinkingConfig` ausente em `GoogleAiGeminiChatRequestParameters` da tag (S10); (4) contrato de chunk final divergente entre OpenAI e DeepSeek (S9/S15) e `usage` cumulativo vs último chunk (S14/S9); (5) `send(Object)` do `SseEmitter` é data-only — eventos nomeados exigem `event()` (S12).

**Ressalva:** `engineer` fornece insumo — **não decide arquitetura**. Decisão é do `architect` em ADR-INDEX → ADR; valor/aceite é do `product-manager` (`.agents/rules/prd-adr-pipeline.md`).

**Próximo passo da esteira (não executado neste lote):** `ADR-INDEX.md` → 2 ADRs (`PRD.md:93-96`) → branch → SPEC/TASKS → implementação → VERIFY. Nenhuma implementação autorizada por este arquivo.

Skipped: nenhum ADR, nenhuma IT executada, nenhuma checagem da versão exata de Spring do projeto (fora do escopo md-only). Risk: R1 (cancel por provider) pode derrubar RF5 se algum dos 7 providers não emitir contexto — verificar antes do ADR-001.
