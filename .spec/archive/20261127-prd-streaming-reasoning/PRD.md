---
prd_id: streaming-reasoning
title: Streaming de resposta e thinking/reasoning no chat (público + assistant)
status: aprovado_humano
prd_status: active
date: 2026-11-26
owner: orchestrator (proposta) — aprovação: operador humano
prd_version: 2
---

# PRD — Streaming + reasoning no chat

## 1. Objetivo e problema

> ❓ Validação: problema e objetivo derivados de fatos verificáveis no código e da decisão do operador.

O Hive responde turnos de forma síncrona: `ChatModel` + `Result<String>`, e `PublicChatServiceImpl:73` rejeita `stream=true` com `streaming_not_supported`. Modelos de reasoning já são usados (`mimo-v2.5`, `glm-5.2`) e seus tokens de pensamento são descartados — `TurnReply` guarda só `finishReason`/usage (`.spec/adr/ADR-020-aiservice-migration/ADR.md:43-49`). Objetivo: entregar a resposta token a token por SSE e expor o pensamento ao cliente final em canal separado, nos dois caminhos de chat.

## 2. Escopo

> ❓ Validação: inclusões e exclusões confirmadas pelo operador no PICCO de 2026-11-26.

**Incluído:** endpoint público OpenAI-compatible com `stream:true`; novo endpoint SSE do assistant; `delta.reasoning_content` (estilo DeepSeek) separado de `delta.content`; `finish_reason` + usage no último chunk + `data: [DONE]`; cancelamento do provider no disconnect; configuração de `returnThinking`/`think` nos 7 providers; contrato de evento único reusado pelos dois endpoints.

**Fora do escopo:** mudança de versão do langchain4j; `langchain4j-reactor`/`Flux`; moderação por chunk; persistência do texto de thinking em banco ou trace; exposição de thinking no caminho não-streaming; UI frontend (outra sessão); H3 do MFA (override registrado); migrações SQL.

## 3. Contexto e estado atual

- `backend/dependencies/pom.xml:37` fixa `langchain4j.version = 1.19.0`.
- `backend/ai/api/.../AssistantDefault.java:54-58` — proxy `@SystemMessage`/`@UserMessage` retornando `Result<String>`.
- `backend/assistant/impl/.../AssistantTurnGateway.java:157-161` — retry + `synchronized(lockFor(sessionId))` + `proxy.chat(...)`.
- `backend/publicapi/impl/.../PublicChatServiceImpl.java:71-225` — preflight (validação, allowlist, cota, prompt, resolve) e chamada única ao gateway.
- `backend/publicapi/web/internal/v1/.../PublicChatControllerV1.java:66-68` — só `application/json`.
- `backend/assistant/web/internal/v1/.../ChatControllerV1.java:41-52` — só `application/json`.
- Verificado na tag 1.19.0: `TokenStream.onPartialThinking` (`TokenStream.java`), `AiMessage.thinking()` (`AiMessage.java`), `Result.finalResponse()` (`Result.java`), `AiServices.builder().streamingChatModel(...)` exclusivo com `.chatModel(...)` (`AiServices.java`). `langchain4j-reactor` **não existe** nessa tag (árvore recursiva da tag, 6233 entradas).

## 4. Stakeholders

Operador (aprovação de §16); backend (provider, `ai`, `publicapi`, `assistant`); frontend/cliente final (consumo do SSE — outra sessão); QA (H1/H3/H6); security (P1/P4).

## 5. Requisitos funcionais

> ❓ Validação: todo RF tem métrica executável na coluna "Como medir".

| ID | Requisito | Como medir |
|---|---|---|
| RF1 | Criar cliente de streaming por provider, com thinking habilitado onde suportado | `createStreamingClient` cobre os 7 `AiProvider`; teste por case |
| RF2 | Proxy de streaming com `TokenStream`, mesmo `@MemoryId`/system/user do proxy síncrono | Teste de caracterização 1:1 com o prompt síncrono |
| RF3 | Chunks SSE separados: `delta.reasoning_content` e `delta.content` | IT assere zero `content` em chunk de thinking e vice-versa |
| RF4 | `finish_reason` e `usage` no penúltimo/último chunk, depois `[DONE]` | IT com fake `ChatResponse` + `TokenUsage` |
| RF5 | Cancelamento do provider no disconnect do cliente | Teste com `onCompletion` → `streamingHandle().cancel()` |
| RF6 | `stream:true` aceito no endpoint público; `stream_options` não causa 400 | IT `POST` com `stream:true` e `stream_options.include_usage` |
| RF7 | Novo endpoint SSE no assistant com eventos `thinking`/`text`/`done`/`error` | IT com permissão `CHAT_SEND` e payloads tipados |
| RF8 | Lock compartilhado entre sync e streaming por `@MemoryId` | Teste de concorrência: um turno de cada tipo na mesma sessão não se sobrepõe |

## 6. Requisitos não-funcionais

> ❓ Validação: restrições de versão, segurança, resiliência e observabilidade derivadas da tag 1.19.0 e do PICCO.

- Compatibilidade: formato de erro OpenAI inalterado; duplo `/v1` preservado.
- Segurança: pensamento não entra em log, `ChatTrace` nem JPA; redator continua valendo nos spans.
- Resiliência: retry apenas antes do primeiro token; timeout 120s por cliente; sem `pkill -f`.
- Observabilidade: `TurnReply` continua sem texto; thinking medido por contagem de chars, nunca conteúdo.
- Versão: sem bump do langchain4j; APIs `@Experimental` marcadas como risco de upgrade.

## 7. Restrições e premissas confirmadas

> ❓ Validação: PICCO confirmado pelo operador; premissa não confirmada marcada como aberta, nunca promovida a fato.

PICCO confirmado pelo operador em 2026-11-26: 1.19.0 fixa; sem starter; estilo DeepSeek entregue ao cliente final; público + assistant; SSE com cancel e usage; 1 ADR = 1 branch = 1 PR; 2 ADRs; zero código sem SPEC/TASKS; H1/H2 antes de delegar; `ai/**` folha; `TurnReply` sem texto. Premissa aberta: `thinkingConfig` do Gemini não confirmado na 1.19.0 — se o provider não emitir thoughts, o comportamento é degradado (sem thinking), nunca erro.

## 8. Alternativas consideradas

`Flux<String>` via `langchain4j-reactor` — descartado: módulo ausente na 1.19.0 (só em 1.24-beta na doc atual). Upgrade para 1.24-beta — descartado: beta em produção e quebra de contrato. Expor thinking concatenado em `content` — descartado: vazamento em histórico/trace. Detalhes em `RESEARCH.md`.

## 9. Critérios de aceite

> ❓ Validação: critérios mensuráveis por comando/IT; nenhum critério subjetivo.

- `POST /v1/public/v1/chat/completions` com `stream:true` → 200 `text/event-stream`; chunks `reasoning_content` e `content` distintos; último chunk com `choices: []` + usage; `data: [DONE]`; `emitter.complete()`.
- `POST /v1/chat/stream` → 200 `text/event-stream`; eventos nomeados; mesmo contrato de turno do assistant.
- Disconnect do cliente cancela o provider (asserção no handle).
- `stream_options` aceito; `unknown=false` do DTO não gera 400.
- Suíte canônica (`harness.config.json:14` → `npm test`) 0 falhas; H3 quando aplicável; `code-reviewer` sem 🔴; `brain_store` + estado atualizados.

## 10. Riscos e mitigações

| Risco | Mitigação |
|---|---|
| `TokenUsage` 1.19.0 não expõe `reasoning_tokens` | usage chunk com input/output/total; `completion_tokens_details` omitido, documentado |
| Gemini sem `thinkingConfig` confirmado | `returnThinking(true)`; ausência de thoughts = degradação silenciosa |
| Lock `synchronized` não cobre callbacks assíncronos | `Semaphore(1, true)` compartilhado por `@MemoryId` (ADR-001/D6/Q5), adquirido antes de `start()` e liberado no terminal por qualquer thread; `ReentrantLock` rejeitado com fonte JDK (owner-bound) |
| `TenantContext` usa `ScopedValue`, não propaga | capturar tenant/usuário/turnId antes de `start()` |
| Thinking é `@Experimental` | isolar em tipos próprios; nenhuma persistência |

## 11. Impacto em módulos e dados

`provider/api`, `provider/langchain4j`, `ai/api`, `ai/impl`, `publicapi/api`, `publicapi/impl`, `publicapi/web/internal/v1`, `assistant/api`, `assistant/impl`, `assistant/web/internal/v1`. Grafo: `ai/**` continua folha (só `provider/api` + langchain4j); nenhuma aresta nova `impl → impl`. Zero migration SQL.

## 12. Estratégia de testes e evidência

Testes unitários de callbacks → emitter; ITs de SSE com `MockMvc` async/WebClient; teste de concorrência sync×stream; `npm test` como comando canônico; security review em P1 e verify em P4; portão H6 com 0 falhas, review sem 🔴, Brain + estado e portas livres.

## 13. Sequenciamento

- ADR-001 — provider de streaming + gateway `ai` (depende de —).
- ADR-002 — endpoints SSE público e assistant (depende de ADR-001).
Teto de 5 ADRs respeitado (2 usados).

## 14. Débitos registrados (fora do PRD)

Override H3 do MFA (`workflow-state.json.overrides`, VERIFY em `.spec/archive/20261126-mfa-onboarding-opcional/`); arquivos publicapi/frontend de outra sessão fora do commit; `reasoning_tokens` e `thinkingConfig` como lacunas de versão.

## 15. Fontes consultadas

<task>
Consolidar, verificar e citar fontes oficiais que sustentam as decisões do PRD: API de streaming do langchain4j 1.19.0 (`AiServices`, `TokenStream`, `PartialThinking`, `AiMessage`, `Result`), knobs de thinking por provider, formato de chunk SSE OpenAI-compatible e API `SseEmitter` do Spring. Toda afirmação de assinatura/versão deve trazer URL e data da consulta.
</task>
<context>
PRD `streaming-reasoning`, aprovado em 2026-11-26 (§16, `prd_version: 2`). Estudo prévio: deepsearch de 2026-11-26 com fontes versionadas na tag 1.19.0 e documentação oficial OpenAI/Spring. Stack: Java/Spring, langchain4j 1.19.0 fixo, `AiServices.builder` manual, sem starter e sem módulo `langchain4j-reactor` nessa tag.
</context>

Todas consultadas em 2026-11-26, via `raw.githubusercontent.com/langchain4j/langchain4j/1.19.0/...`:

- `AiServices.java` (`streamingChatModel` exclusivo), `TokenStream.java` (`onPartialThinking`), `PartialThinking.java`, `ChatResponse.java`, `Result.java` (`finalResponse`), `AiMessage.java` (`thinking()`).
- `OpenAiStreamingChatModel.java` (`reasoningEffort`, `returnThinking`, `sendThinking`, `thinkingFieldName`), `AnthropicStreamingChatModel.java` (`thinkingType`, `thinkingBudgetTokens`, `returnThinking`), `OllamaChatRequestParameters.java` (`think`), `GeminiStreamingResponseBuilder.java` (`returnThinking`).
- `api.github.com/repos/langchain4j/langchain4j/git/trees/1.19.0?recursive=1` (sem `reactor`).
- `developers.openai.com/api/reference/resources/chat/subresources/completions/streaming-events/` (chunk, usage, `reasoning_tokens`).
- `docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/web/servlet/mvc/method/annotation/SseEmitter.html` (`send`, `onCompletion`, `onError`, `completeWithError`).
- `developers.openai.com/api/docs/guides/streaming-responses/` (moderação por chunk indisponível).

## 16. Aprovação humana

> ❓ Validação: veredito, data e `prd_version` presentes; assinatura é ato humano registrado em 2026-11-26.

Veredito: APROVADO

**Status: APPROVED — aprovação humana registrada em 2026-11-26.**

- [X] Aprovado por: operador (sessão de 2026-11-26)
- [X] Data: 2026-11-26
- [X] Escopo aprovado: 2 ADRs; público + assistant; thinking ao cliente final
- [X] Contrato SSE aprovado: DeepSeek `reasoning_content`, usage + `[DONE]`, cancelamento
- [X] `prd_version: 2` — linhas `in-progress`/`merged` exigem `unit: adr` com `SPEC.md` + `TASKS.md` e `VERIFY.md` após merge
