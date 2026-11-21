---
prd_id: langchain4j-ai-services
adr_id: ADR-003
unit: legacy
status: todo
title: "Manter histórico JPA; nenhuma ChatMemory e nenhum @MemoryId"
data: 2026-11-21
decisor: architect
---

# ADR-003 — Manter histórico JPA; nenhuma ChatMemory e nenhum @MemoryId

> Unidade de decisão: 1 ADR = 1 branch = 1 PR (merge humano). Decisão apenas.

- **Decisão**: nenhuma `ChatMemory`, nenhum `chatMemoryProvider` e nenhum `@MemoryId` entram no backend neste PRD. O histórico integral continua em `chat_message`/`ChatSession` (JPA). A alternativa C fica como decisão futura, condicionada.
- **Motivo**: responder #3 e #5 (`PRD.md:187,189`) com fonte primária; "memória não é histórico" (A12) e chamada concorrente no mesmo `@MemoryId` corrompe memória sem proteção nativa (A8/D2.5), num sistema cujo tenant é `ScopedValue` que não propaga (K9).
- **Alternativas descartadas**: C — `ChatMemoryProvider`/`ChatMemoryStore` sobre JPA — fonte: https://docs.langchain4j.dev/tutorials/chat-memory — 2026-11-21 (**não versionada**).
- **depende-de**: ADR-002
- **Interface congelada**: JPA é fonte de verdade; builder sem memória; sem `@MemoryId`; se reaberto, `sessionId` (UUID) e nunca `turnId`.
- **Fora desta decisão**: migração de dados, store assíncrono, ciclo MFA.
- **Consequências se estiver errado**: perda do que a UI mostra e da base das ITs se memória virar fonte de verdade (K1) — bloqueado por esta decisão.
- **Status**: todo

## Decisão detalhada

**Pergunta #3 — "ChatMemory em memória, store sobre JPA ou nenhuma? `@MemoryId` = sessionId ou turnId?"**
**Veredito: nenhuma.** Afirmação — LangChain4j oferece apenas "memory"; histórico integral deve ser mantido manualmente — https://docs.langchain4j.dev/tutorials/chat-memory — 2026-11-21 (**não versionada**, A12).Histórico atual: `ChatSession.java:11`, `ChatMessageServiceImpl.java:68-74` (`findLast20`), `ChatServiceImpl.java:210-212` (`TECH-BRIEF.md:120`). Substituí-lo apagaria o que a UI exibe e a base das ITs (K1). Se o tema for reaberto em ADR futuro: `@MemoryId = sessionId` (UUID), **nunca** `turnId`.

**Pergunta #5 — "Política de concorrência por sessão, dado A8?"**
**Veredito: stateless por construção.** Não há `@MemoryId` compartilhado, logo a restrição de concorrência não se aplica neste escopo. Afirmação — AI Service não deve ser chamado simultaneamente para o mesmo `@MemoryId` (corrompe `ChatMemory`) e não há mecanismo nativo de proteção — https://raw.githubusercontent.com/langchain4j/langchain4j/1.19.0/langchain4j/src/main/java/dev/langchain4j/service/AiServices.java — 2026-11-21 (A8/D2.5); repetida em https://docs.langchain4j.dev/tutorials/ai-services — 2026-11-21 (**não versionada**). O cliente já é criado por turno (`LangChain4jTurnGateway.java:80-82`), sem estado conversacional mutável no proxy.

Fontes de tipo/identidade (insumo para a reabertura futura, não decisão de implementação):
- Afirmação — parâmetro `@MemoryId` aceita qualquer tipo desde que implemente corretamente `equals()`/`hashCode()`; `ChatMemoryProvider` é obrigatório quando `@MemoryId` é usado — https://raw.githubusercontent.com/langchain4j/langchain4j/1.19.0/langchain4j/src/main/java/dev/langchain4j/service/MemoryId.java — 2026-11-21 (D2.1).
- Afirmação — `ChatMemoryProvider.get(Object)` é chamado para cada memoryId ainda não visto e a instância é retida pelo AiService — https://raw.githubusercontent.com/langchain4j/langchain4j/1.19.0/langchain4j/src/main/java/dev/langchain4j/service/AiServices.java — 2026-11-21 (D2.2). **Inferência a partir de D2.2**: estabilidade do identificador define continuidade; `turnId` muda a cada turno, `sessionId` não — não é fato verificado em execução.
- `java.util.UUID` satisfaz `equals`/`hashCode` pelo contrato padrão do tipo. Sobre a entidade `ChatSession`: **inferência (sem fonte consultada); verificar em `ChatSession.java:11-26` antes de qualquer `@MemoryId` com entidade** (`RESEARCH.md:84,267`, D2.3).

## depende-de

ADR-002 — o proxy manual é a condição para decidir se memória entra ou não.

## Interface congelada

1. **Fonte de verdade**: `chat_message`/`ChatSession` permanecem o histórico integral do chat humano; nenhum ADR deste PRD remove, substitui ou espelha essa persistência em `ChatMemoryStore`.
2. **Builder sem memória**: nenhum `.chatMemory(...)` nem `.chatMemoryProvider(...)`; nenhum `ChatMemoryStore`/`ChatMemoryAccess` novo em `backend/**`. Afirmação — `ChatMemoryStore` expõe `getMessages(Object)`, `updateMessages(Object,List<ChatMessage>)`, `deleteMessages(Object)` — https://raw.githubusercontent.com/langchain4j/langchain4j/1.19.0/langchain4j-core/src/main/java/dev/langchain4j/store/memory/chat/ChatMemoryStore.java — 2026-11-21 (A10); Afirmação — `ChatMemoryAccess.getChatMemory(Object)`/`evictChatMemory(Object)` — https://raw.githubusercontent.com/langchain4j/langchain4j/1.19.0/langchain4j/src/main/java/dev/langchain4j/service/memory/ChatMemoryAccess.java — 2026-11-21 (A9).
3. **Sem `@MemoryId`**: nenhum parâmetro anotado na interface do proxy; sem memoryId de domínio. Afirmação — sem parâmetro `@MemoryId`, o memoryId é a string `"default"` — https://raw.githubusercontent.com/langchain4j/langchain4j/1.19.0/langchain4j/src/main/java/dev/langchain4j/service/AiServices.java — 2026-11-21 (D2.4) — irrelevante enquanto não há memória, mas registrado para não ser confundido com isolamento por sessão.
4. **Concorrência**: cliente por turno, sem estado compartilhado; nenhuma serialização por sessão é necessária **enquanto stateless**. Se C for reaberto: (a) `@MemoryId = sessionId (UUID)`, nunca `turnId`; (b) IT de corrida + política explícita de serialização por sessão antes do merge; (c) fronteira explícita de tenant fora de callbacks — `TenantContext` é `ScopedValue` e não propaga para thread pool (`TenantContext.java:8-16`), `TenantAspect.java:23-35` segue com filtro desligado quando não encontra tenant (K9; `TECH-BRIEF.md:167-172`). Afirmação — mensagens evictadas de `ChatMemory` também saem do store — https://docs.langchain4j.dev/tutorials/chat-memory — 2026-11-21 (**não versionada**, A13) — portanto evict é destrutivo e não deve ser introduzido sem decisão.
5. **Ordem do system permanece a de ADR-001**: nenhuma reordenação por `MessageWindowChatMemory`. Afirmação — system novo com conteúdo diferente vai para o fim salvo `alwaysKeepSystemMessageFirst(true)` — https://raw.githubusercontent.com/langchain4j/langchain4j/1.19.0/langchain4j/src/main/java/dev/langchain4j/memory/chat/MessageWindowChatMemory.java — 2026-11-21 (A11).

## Alternativas consideradas (com fonte)

- **A — nenhuma memória, histórico JPA**: **ESCOLHIDA** (esta decisão). Mantém K1/K2/K9 intocados (`RESEARCH.md:150-167`).
- **B — memória em memória, sem store**: REJEITADA — ainda introduz `@MemoryId` e concorrência (A8), sem ganho sobre o histórico já persistido.
- **C — `ChatMemoryProvider` + store JPA, `@MemoryId = sessionId`**: REJEITADA neste PRD; custo alto, risco grave (A8/A12/A13/K2/K4/K9), não substitui o histórico (`RESEARCH.md:150-167`). Fonte: https://docs.langchain4j.dev/tutorials/chat-memory — 2026-11-21 (**não versionada**).

## Consequências e riscos K tratados

- **K1**: mitigado — JPA continua fonte de verdade.
- **K2**: evitado — sem `@MemoryId`; A8/D2.5 não se aplicam.
- **K9**: evitado — nenhum store assíncrono/offload de memória.
- **K4**: protegido por ADR-001 (ordem congelada; nenhuma reordenação de memória).
- **K3, K5, K6, K7, K8, K12, K13**: não tratados aqui.
- **Se estiver errado**: abrir mão de memória pronta pode custar trabalho se o produto quiser resumo/contexto window no futuro; a reabertura é explícita via novo ADR, não por acidente de implementação.

## Como verificar (IT de caracterização)

1. IT existente de histórico: `findLast20` em `ChatMessageServiceImpl.java:68-74` e conversão em `ChatServiceImpl.java:210-212` permanecem com baseline de 20 mensagens e ordem preservada.
2. Verificação estática: `grep` por `ChatMemory`, `ChatMemoryStore`, `ChatMemoryProvider`, `@MemoryId`, `chatMemory(` em `backend/**` = zero ocorrências (estado atual em `TECH-BRIEF.md:196`).
3. IT de caracterização do caminho humano: histórico persistido continua sendo o que o gateway recebe em `TurnRequest.conversation` (`TurnRequest.java:47-50`).
4. Portão: suíte completa 0 falhas; zero diff em paths MFA.
