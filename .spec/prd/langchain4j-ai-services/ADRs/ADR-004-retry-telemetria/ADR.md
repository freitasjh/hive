---
prd_id: langchain4j-ai-services
adr_id: ADR-004
unit: legacy
status: todo
title: "Preservar telemetria com Result e corrigir turnId/clientId da trace"
data: 2026-11-21
decisor: architect
---

# ADR-004 — Preservar telemetria com Result e corrigir turnId/clientId da trace

> Unidade de decisão: 1 ADR = 1 branch = 1 PR (merge humano). Decisão apenas.

- **Decisão**: `Result<T>` é o tipo de retorno do proxy de ADR-002; o gateway lê `tokenUsage()`/`finishReason()`/`finalResponse()` e continua medindo `promptMessages`/`promptChars` no `ChatRequest` efetivo. Retry e classificação de transporte permanecem fora do builder, com um único tradutor puro em `agent/api`. A correção de `turnId`/`clientId` da trace entra como **pré-condição absorvida neste ADR** (não ADR próprio).
- **Motivo**: responder #4 e #8 (`PRD.md:188,192`) e tratar K5/K6/K7 sem estourar o teto de 5 ADRs.
- **Alternativas descartadas**: `AiServiceListener`/`ChatModelListener` como fonte de verdade — fonte: https://raw.githubusercontent.com/langchain4j/langchain4j/1.19.0/langchain4j/src/main/java/dev/langchain4j/service/AiServices.java — 2026-11-21 (A17; fidelidade não verificada).
- **depende-de**: ADR-001
- **Interface congelada**: `Result<T>` no proxy; números medidos no gateway; trace com `turnId`/`clientId` populados; retry na fábrica/`LlmRetryPolicy`; tradutor puro em `agent/api`.
- **Fora desta decisão**: cota/ocupação (K12), limite de `maxTokens` (ADR-005), ciclo MFA.
- **Consequências se estiver errado**: telemetria e faturamento inconsistentes (K5) ou trace não atribuível (K6) — ambos com baseline antes/depois.
- **Status**: todo

## Decisão detalhada

**Pergunta #4 — "Como preservar `promptMessages`, `promptChars`, `TokenUsage`, `FinishReason` com `Result<T>`?"**
**Veredito**: Afirmação — `Result<T>` acessa `TokenUsage`, fontes, `FinishReason` e `ChatResponse` final — https://docs.langchain4j.dev/tutorials/ai-services — 2026-11-21 (**não versionada**) e https://raw.githubusercontent.com/langchain4j/langchain4j/1.19.0/langchain4j/src/main/java/dev/langchain4j/service/AiServices.java — 2026-11-21 (A7). O gateway lê esses valores do `Result` para montar `TurnReply.java:45-113` (`LangChain4jTurnGateway.java:92-108`). `promptMessages`/`promptChars` continuam **medidos** no gateway a partir do `ChatRequest` efetivo e do prompt montado, não reconstruídos de listeners: a fidelidade de `AiServiceListener`/`ChatModelListener` para esses números é **inferência (sem fonte consultada); verificar em `RESEARCH.md:103,268` antes de qualquer troca** (A17).

**Pergunta #8 — "Corrigir `turnId`/`clientId` da trace é pré-condição do ADR de telemetria ou ADR próprio?"**
**Veredito: pré-condição absorvida neste ADR.** Não vira 6º ADR — teto de 5 (`.agents/rules/prd-adr-pipeline.md:46`). Débito pré-existente: `ChatTrace.java:47-49` tem os campos, mas `TurnOutcomeMapper.java:181-189` nunca os popula; `LoggingChatTraceRecorder.java:38-39` imprime `null`; `PublicChatServiceImpl.java:192,200,212` já tem os valores (`TECH-BRIEF.md:184,252`). Corrigir no mesmo ponto de emissão de K5 é mais barato e evita duas linhas de trace divergentes. O parecer antigo dizia que `ChatTrace` não tinha os campos — estava desatualizado (`RESEARCH.md:226`); não herdar essa afirmação.

**Retry e classificação (R3/R4, K7, Brain D5)**: Afirmação — o builder 1.19.0 não expõe retry de transporte — https://raw.githubusercontent.com/langchain4j/langchain4j/1.19.0/langchain4j/src/main/java/dev/langchain4j/service/AiServices.java — 2026-11-21 (D3.1). Política atual: 2 retries, 500 ms, expoente 1.5, jitter 20% (`LlmRetryPolicy.java:31-35,72-97`); transiente = 429/408/5xx, não transiente = timeout/`NonRetriableException`/`ModelNotFoundException`/`AuthenticationException` (`:119-146`); `MAX_RETRIES = 0` na fábrica (`LangChain4jClientFactory.java:41,115,135,153,169,185`). Débito K7: javadoc em `:53` diz "sem jitter", mas `:55` liga jitter — corrigir o documento, não o comportamento. Classificação hoje tem dois vocabulários: `ModelNotFound.java:42-86` e `FailureClassifier.java:69-89` (`model_not_found` vs `not_found`/`server_error`); `TurnOutcomeMapper.java:134-146` usa ambos e deixa o `evict` no chamador. Decisão: mover os dois para **uma função pura em `agent/api`** sem inventar rótulos novos, preservando `evict` no chamador (Brain `fechamento-d5-d7-d3` D5 — 2026-09-27).

## depende-de

ADR-001 — a montagem centralizada e a ordem do system são o baseline dos números medidos.

## Interface congelada

1. **Tipo de retorno**: `Result<T>` no proxy de geração; `TurnReply` carrega `promptTokens`/`completionTokens`/`totalTokens` e `finishReason` lidos do `Result` — nunca reconstruídos por exceção ou por listener não verificado.
2. **Métricas de prompt**: `promptMessages`/`promptChars` medidos no gateway a partir do `ChatRequest` efetivo; baseline de `TurnReply.java:45-113` e `TurnOutcomeMapper.java:174-193` preservado.
3. **Trace**: `TurnOutcomeMapper` popula `.turnId(...)` e `.clientId(...)` com os valores já calculados em `PublicChatServiceImpl.java:192,200,212`; `ChatTrace` mantém os campos em `ChatTrace.java:47-49`. Quando não houver `clientId` (caminho humano), omitir o campo da linha — nunca imprimir o literal `null`. Uma linha de trace por turno (`TurnOutcomeMapper.java:174-193`).
4. **Retry**: permanece em `LlmRetryPolicy.java:29-146` + `MAX_RETRIES = 0` na fábrica; nenhuma config de retry no builder do AiService; javadoc de `LlmRetryPolicy.java:53` corrigido para "com jitter" em conformidade com `:55`.
5. **Tradutor de transporte**: função pura de 1 argumento em `agent/api`, sem collaborators; vocabulário existente preservado; `evict` permanece no chamador (`TurnOutcomeMapper.java:136-146`).
6. **Sem texto do provider em log**: `TurnReply.toString()` continua sem imprimir texto do provider (`TurnReply.java:89-113`; `PRD.md:177`).

## Alternativas consideradas (com fonte)

- **`Result<T>`**: **ESCOLHIDA** (A7, fontes acima).
- **`AiServiceListener`/`ChatModelListener`**: REJEITADA como fonte de verdade. Afirmação — `registerListener(AiServiceListener<I>)`/`registerListeners(...)` existem no builder 1.19.0 — https://raw.githubusercontent.com/langchain4j/langchain4j/1.19.0/langchain4j/src/main/java/dev/langchain4j/service/AiServices.java — 2026-11-21 (A17). Fidelidade para `promptMessages`/`promptChars`/`TokenUsage`/`FinishReason`:**inferência (sem fonte consultada); verificar em `RESEARCH.md:103,268`**.
- **Retry translúcido do AiService**: REJEITADO — inexistente no builder (D3.1).
- **ADR próprio para `turnId`/`clientId`**: REJEITADO — estouraria o teto de 5; absorvido aqui.

## Consequências e riscos K tratados

- **K5**: mitigado por `Result<T>` + baseline de números medidos.
- **K6**: mitigado — trace atribuível; débito pré-existente corrigido no mesmo ponto de emissão.
- **K7**: mitigado — javadoc alinhado ao comportamento real.
- **R4/K3 (classificação)**: parcial — tradutor único em `agent/api` (Brain D5) sem mudar rótulos; a aresta `publicapi/impl → agent/impl` permanece como débito do ADR-005.
- **K12 (registrado, não resolvido)**: 2 retries + read timeout 120 s (`LangChain4jClientFactory.java:33`) contra cota 60/min (`PublicChatServiceImpl.java:134`) — cota limita taxa, não ocupação (`TECH-BRIEF.md:258`; Brain `parecer-consolidado-2026-09-27`). Sem palpite de solução aqui.

## Como verificar (IT de caracterização)

1. Baseline antes/depois: mesmo prompt e cliente mockado → mesmos `promptTokens`/`completionTokens`/`totalTokens`/`finishReason` em `TurnReply`, lidos via `Result<T>`.
2. IT de caracterização da trace: uma linha por turno com `turnId`/`clientId` atribuíveis aos valores de `PublicChatServiceImpl.java:192,200,212`; nenhuma linha com `turnId=null clientId=null`.
3. IT de classificação: usar o mesmo par status+corpo nos caminhos humano e público e afirmar o mesmo diagnóstico `model-not-found` (`TECH-BRIEF.md:291(d)`).
4. Verificação estática: `grep` por `.turnId(`/`.clientId(` em `TurnOutcomeMapper.java` ≠ zero; javadoc de `LlmRetryPolicy.java:53` sem "no jitter".
5. Portão: suíte completa 0 falhas; zero diff em paths MFA.
