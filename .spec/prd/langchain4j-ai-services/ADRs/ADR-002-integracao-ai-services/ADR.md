---
prd_id: langchain4j-ai-services
adr_id: ADR-002
unit: legacy
status: todo
title: "Integrar Ai Services de forma manual e stateless dentro da porta"
data: 2026-11-21
decisor: architect
---

# ADR-002 — Integrar Ai Services de forma manual e stateless dentro da porta

> Unidade de decisão: 1 ADR = 1 branch = 1 PR (merge humano). Decisão apenas.

- **Decisão**: adotar Ai Services com `AiServices.builder(...)` manual, stateless, **dentro de `agent/impl`**, substituindo apenas a montagem de prompt e o parsing hoje feitos em `LangChain4jTurnGateway.java:66-108`. A porta `ChatTurnGateway` permanece; retry, criação de cliente e mapeamento para `TurnReply` permanecem no gateway. **Não** adotar `langchain4j-spring-boot-starter`.
- **Motivo**: usar API confirmada na tag 1.19.0 sem introduzir starter nem memória; responder às perguntas #1, #2 e #6 (`PRD.md:185-190`) com fonte primária (`RESEARCH.md:131-149,169-187`).
- **Alternativas descartadas**: alternativa D (starter) — fonte: https://docs.langchain4j.dev/tutorials/spring-boot-integration/ — 2026-11-21; alternativa C (com memória) — fonte: https://docs.langchain4j.dev/tutorials/chat-memory — 2026-11-21.
- **depende-de**: ADR-001
- **Interface congelada**: porta em `agent/api`; builder manual em `agent/impl`; sem memória/`@MemoryId`; retry fora do builder; cliente por turno.
- **Fora desta decisão**: memória (ADR-003), telemetria e classificação (ADR-004), fronteira de módulos e limites (ADR-005), ciclo MFA.
- **Consequências se estiver errado**: proxy pode não preservar a ordem das mensagens (K4) ou a fidelidade da resposta (K5) — ambas com IT de caracterização obrigatória.
- **Status**: todo

## Decisão detalhada

**Pergunta #1 — "AiService substitui todo o `ChatTurnGateway` ou só montagem/parsing?"**
**Veredito: só montagem/parsing.** O proxy cobre da construção de mensagens ao parsing (`A1/A5/A14`: Afirmação — `AiServices.builder(Class<T>)` produz `AiServices<T>` e `chatRequestTransformer(UnaryOperator<ChatRequest>)` altera a requisição — https://raw.githubusercontent.com/langchain4j/langchain4j/1.19.0/langchain4j/src/main/java/dev/langchain4j/service/AiServices.java — 2026-11-21; Afirmação — AI Services são proxies de interface com `@SystemMessage`/`@UserMessage`/`@V`/`ChatRequestParameters` por chamada — https://docs.langchain4j.dev/tutorials/ai-services — 2026-11-21, **não versionada**). Mas o retry de transporte **não** está no builder (§Alternativas, D3) e a regra de ordem do system é segurança (ADR-001/K4): trocar o gateway todo apagaria a costura onde essas políticas vivem (`LangChain4jTurnGateway.java:55,83,92-108`).

**Pergunta #2 — "`ChatTurnGateway` permanece como porta entre `agent/api` e os 2 chamadores?"**
**Veredito: sim.** A porta está em `ChatTurnGateway.java:29-36`, com dois chamadores (`ChatServiceImpl` humano e `PublicChatServiceImpl` público; `TECH-BRIEF.md:78,87,97`). É a costura que a centralização de ADR-001 precisa e o ponto que evita aprofundar K3. Brain `fechamento-d5-d7-d3` D5 — 2026-09-27: **porta em `agent/api`, adaptador em `agent/impl`**.

**Pergunta #6 — "Adotar `langchain4j-spring-boot-starter`?"**
**Veredito: não neste PRD.** Compatibilidade SB4 verificada **com ressalvas** (`RESEARCH.md:66-78`): Afirmação — convenção `-spring-boot4-starter` para SB4, `-spring-boot-starter` para SB3 — https://docs.langchain4j.dev/tutorials/spring-boot-integration/ — 2026-11-21 (**não versionada**, D1.1); Afirmação — maintainer testou `-spring-boot4-starter:1.19.0-beta29` em SB 4.1.1/Java 25 com bean criado — https://github.com/langchain4j/langchain4j/issues/6236 — 2026-11-21 (D1.3); Afirmação — POM de `1.19.0-beta29` declara core `1.19.0` e `spring-boot-starter:4.0.5` — https://central.sonatype.com/artifact/dev.langchain4j/langchain4j-spring-boot4-starter/1.19.0-beta29 — 2026-11-21 (D1.4); Afirmação — múltiplos beans sem `@Primary` falham ao iniciar; há `wiringMode = EXPLICIT` — https://docs.langchain4j.dev/tutorials/spring-boot-integration/ — 2026-11-21 (**não versionada**, D1.5). O hive já constrói 5 providers na fábrica (`TECH-BRIEF.md:71`; `LangChain4jClientFactory.java:75-105`) — autoconfig colidiria sem necessidade demonstrada pelas 7 regras.

**Risco explícito (registrado, não tratado como fato)**: a combinação exata `starter 1.19.0-beta29 + core 1.19.0 GA + Spring Boot 4.1.0` não é matriz formal publicada; docs oficiais já exemplificam `1.24.0-beta34` (`RESEARCH.md:66-78,266`). Se o starter for revisitado, este ADR deve ser reaberto.

## depende-de

ADR-001 — a função de conversão e a ordem template-primeiro são pré-condição do proxy.

## Interface congelada

1. **Porta**: `ChatTurnGateway` permanece em `agent/api`, com 1 método, exatamente 2 chamadores (`ChatTurnGateway.java:29-36`). Nenhum chamador passa a invocar o proxy diretamente.
2. **Construção**: proxy criado por `AiServices.builder(Classe)...build()` manual em `agent/impl`. Nenhum `langchain4j-spring-boot-starter` em `backend/dependencies/pom.xml`; nenhuma anotação `@AiService`. Estado real hoje: starter e `AiServices` ausentes (`TECH-BRIEF.md:196-198`).
3. **Sem memória**: nenhum `.chatMemory(...)` nem `.chatMemoryProvider(...)` no builder e nenhum parâmetro `@MemoryId` na interface do proxy — ver ADR-003. Afirmação — `chatMemory`/`chatMemoryProvider` existem e não podem ser configurados simultaneamente — https://raw.githubusercontent.com/langchain4j/langchain4j/1.19.0/langchain4j/src/main/java/dev/langchain4j/service/AiServices.java — 2026-11-21 (A3).
4. **Retry fora do builder**: retry permanece em `LlmRetryPolicy.java:29-146` e no `MAX_RETRIES = 0` da fábrica (`LangChain4jClientFactory.java:41,115,135,153,169,185`). Afirmação — o builder 1.19.0 não expõe `maxRetries`/`retryPolicy` — https://raw.githubusercontent.com/langchain4j/langchain4j/1.19.0/langchain4j/src/main/java/dev/langchain4j/service/AiServices.java — 2026-11-21 (`RESEARCH.md:90-96`, D3.1); Afirmação — retry de transporte é parâmetro do builder do modelo — https://github.com/langchain4j/langchain4j/blob/main/docs/docs/integrations/language-models/ollama.md — 2026-11-21 (branch `main`, **não fixada na 1.19.0**, D3.3).
5. **Cliente por turno**: a criação em `LangChain4jTurnGateway.java:80-82` permanece; o proxy é construído sobre esse cliente, não substitui a fábrica.
6. **Fronteira de responsabilidade**: montagem/parsing atrás da porta; `TurnReply`, a ordem do system (ADR-001) e a leitura de métricas (ADR-004) continuam no gateway.

## Alternativas consideradas (com fonte)

- **A — manter `client.chat(List<ChatMessage>)` e só centralizar regras**: pré-condição cumprida por ADR-001, mas insuficiente para o objetivo "usar todo o potencial do LangChain4j" do PICCO. Custo baixo/acoplamento nulo (`RESEARCH.md:112-129`).
- **B — `AiServices.builder` manual stateless**: **ESCOLHIDA**. API confirmada na tag; sem starter; evita K1/K2/K9 (`RESEARCH.md:131-148`).
- **C — B + `ChatMemoryStore` sobre JPA**: não adotada; ver ADR-003. Afirmação — memory ≠ history — https://docs.langchain4j.dev/tutorials/chat-memory — 2026-11-21 (**não versionada**, A12).
- **D — starter com autoconfiguração**: não adotada; fontes D1.1–D1.5 acima. Deixou de estar bloqueada por ausência de fonte, mas permanece sem recomendação por acoplamento e divergência de versão (`RESEARCH.md:197`).

## Consequências e riscos K tratados

- **K10 (starter)**: tratado por verificação com fonte + decisão de não adotar (`RESEARCH.md:66-78,255`).
- **K1/K2/K9**: evitados — sem memória e sem `@MemoryId` (ADR-003).
- **K4**: mitigado só por IT — **inferência (sem fonte consultada)** de que o proxy preserve a lista estruturada de mensagens; verificar `chatRequestTransformer`/`@V` em `AiServices.java` antes de implementar (`RESEARCH.md:270`).
- **K5**: depende de ADR-004 (`Result<T>`).
- **K3/K6/K7/K8/K12/K13**: não tratados aqui.

## Como verificar (IT de caracterização)

1. IT compara `TurnReply` (texto + `promptTokens`/`completionTokens`/`totalTokens` + `finishReason`) antes e depois do proxy, com o mesmo cliente mockado — congela itens 1 e 5.
2. IT de caracterização da ordem das mensagens no proxy (K4), com baseline do ADR-001.
3. Verificação estática: `grep` por `langchain4j-spring-boot-starter`, `@AiService`, `chatMemory`, `@MemoryId` em `backend/**` = zero ocorrências (estado atual em `TECH-BRIEF.md:196-198`).
4. Verificação de retry: `grep` por `maxRetries` no builder = zero; política em `LlmRetryPolicy.java:29-146` intacta.
5. Portão: suíte completa 0 falhas; zero diff em paths MFA.
