---
prd_id: langchain4j-ai-services
adr_id: ADR-001
unit: legacy
status: todo
title: "Centralizar conversão de histórico e precedência do system message"
data: 2026-11-21
decisor: architect
---

# ADR-001 — Centralizar conversão de histórico e precedência do system message

> Unidade de decisão: 1 ADR = 1 branch = 1 PR (merge humano). Decisão apenas:
> nenhum código, config ou teste nasce aqui.

- **Decisão**: uma única função pura em `agent/api` converte o histórico de domínio em mensagens do provider para os dois chamadores da porta, e a ordem "SystemMessage do template primeiro, histórico depois" fica congelada como invariante de segurança.
- **Motivo**: duas conversões divergem hoje — `agent/api/.../ProviderMessages.java:48-63` tem ramo `SYSTEM` (`:57-58`) e descarta conteúdo nulo (`:51-54`), enquanto `PublicChatServiceImpl.java:152-167` não faz nem um nem outro (`TECH-BRIEF.md:119-122,257`, K11). A ordem do template em `LangChain4jTurnGateway.java:121-128` é regra de segurança do caminho interno (Brain `arquitetura/projetos/hive/arquitetura/api-publica-chat/fechamento-d5-d7-d3`, seção D3 — 2026-09-27).
- **Alternativas descartadas**: `.systemMessageProvider(...)` do AiService como fonte da ordem — fonte: https://raw.githubusercontent.com/langchain4j/langchain4j/1.19.0/langchain4j/src/main/java/dev/langchain4j/memory/chat/MessageWindowChatMemory.java — 2026-11-21 (vide §Alternativas).
- **depende-de**: —
- **Interface congelada**: conversão pura em `agent/api`; ordem template-primeiro; rejeição de `role=system` no limite público; sem dependência nova para `agent/impl`.
- **Fora desta decisão**: AiService/starter (ADR-002), memória e `@MemoryId` (ADR-003), retry/telemetria (ADR-004), fronteira de módulos e limites (ADR-005), ciclo MFA.
- **Consequências se estiver errado**: mudança da ordem do system message altera comportamento de segurança visível (K4) — reversível só com novo ADR + IT de caracterização.
- **Status**: todo

## Decisão detalhada

Regra atual (evidência `arquivo:linha`, lida em 2026-11-21):

| Aspecto | Regra atual | Evidência |
|---|---|---|
| Conversão canônica | `ASSISTANT → AiMessage`, `SYSTEM → SystemMessage`, resto `→ UserMessage`; descarta conteúdo nulo/blank | `agent/api/.../ProviderMessages.java:48-63`; `TECH-BRIEF.md:119` |
| Conversão pública | converte payload direto, **sem** ramo `SYSTEM` e **sem** descarte de vazio; `history = List.of()` | `PublicChatServiceImpl.java:152-167`; `TECH-BRIEF.md:121` |
| Ordem do system | `new SystemMessage(template)` somente se template não blank, **antes** de `addAll(conversation)` | `LangChain4jTurnGateway.java:120-129`; `TECH-BRIEF.md:107` |
| Rejeição de `system` do cliente | 400 na camada de serviço pública, antes da montagem | `PublicChatServiceImpl.java:69-73`; Brain `fechamento-d5-d7-d3` D3 — 2026-09-27 |
| Histórico de origem | `findLast20` (20 mensagens) convertido uma vez em `TurnRequest.conversation` | `ChatMessageServiceImpl.java:68-74`; `ChatServiceImpl.java:210-212` |

Regra proposta (congelada por este ADR): as duas primeiras linhas da tabela viram **uma** implementação em `agent/api`, reusada pelos dois chamadores; as duas últimas linhas permanecem **inalteradas** como invariantes. Nenhuma `ChatMemory` participa da ordem — ver ADR-003.

## depende-de

Nenhum. Este é o ADR raiz: ADR-002, ADR-004 e ADR-005 dependem dele; ADR-003 depende de ADR-002.

## Interface congelada

Contrato que o implementador futuro não pode mudar sem novo ADR:

1. **Localização**: a conversão `domínio → mensagens do provider` é função pura estática em `agent/api` (pacote `util`, mesmo módulo de `ProviderMessages.java:39-64`). Sem collaborators, sem I/O, sem leitura de `TenantContext`.
2. **Mapa de conversão fixo**: `ASSISTANT → dev.langchain4j.data.message.AiMessage`; `SYSTEM → SystemMessage`; qualquer outro papel → `UserMessage`; mensagens com conteúdo nulo ou blank são descartadas. Base: `ProviderMessages.java:48-63` (`TECH-BRIEF.md:119`). Nenhum papel novo é inventado.
3. **Ordem fixa**: `SystemMessage(template)` precede todas as mensagens do histórico; o template só entra se não for blank. Base: `LangChain4jTurnGateway.java:121-128`. Esta é regra de segurança (K4), não detalhe de implementação.
4. **Limite público inalterado**: `role=system` vindo do cliente da API pública continua rejeitado com 400 antes de qualquer montagem; `metadata.hive` permanece ausente na resposta de erro (Brain `parecer-consolidado-2026-09-27` e `fechamento-d5-d7-d3` D3 — 2026-09-27).
5. **Aresta congelada**: a função mora em `agent/api`, que `publicapi/impl` já consome; nenhum import novo de `agent/impl` é introduzido (`publicapi/impl/pom.xml:33-37`, K3).
6. **Tipo das mensagens**: `dev.langchain4j.data.message.*` de `langchain4j-core`, já dependência de `agent/api` (`agent/api/pom.xml:28-40`; `TECH-BRIEF.md:77`).

## Alternativas consideradas (com fonte)

- **B/C — declarar o system via `.systemMessage(...)`/`.systemMessageProvider(...)` do AiService**: REJEITADA neste ADR. Afirmação — `systemMessage(String)`/`systemMessageProvider(Function<Object,String>)` existem e `@SystemMessage` tem precedência — https://raw.githubusercontent.com/langchain4j/langchain4j/1.19.0/langchain4j/src/main/java/dev/langchain4j/service/AiServices.java — 2026-11-21 (A4). Afirmação — em `MessageWindowChatMemory` 1.19.0 o system de conteúdo diferente substitui o anterior e **vai para o fim** salvo `alwaysKeepSystemMessageFirst(true)` — https://raw.githubusercontent.com/langchain4j/langchain4j/1.19.0/langchain4j/src/main/java/dev/langchain4j/memory/chat/MessageWindowChatMemory.java — 2026-11-21 (A11). A ordem não é equivalente à regra atual; como este ADR não adota memória (ADR-003), o mecanismo de reordenação sequer entra, mas a precedência fica explícita aqui para não ser herdada por acidente.
- **Manter duas conversões locais**: REJEITADA — diverge silenciosamente entre os dois chamadores (K11; `TECH-BRIEF.md:257`).
- **Colocar a função em `agent/impl`**: REJEITADA — aprofunda a aresta `publicapi/impl → agent/impl` (K3; `PublicChatServiceImpl.java:9-10`).
- **Aceitar e reescrever `system` do cliente**: REJEITADA — tornaria `prompt_version`/`prompt_fingerprint` mentiras; Brain `fechamento-d5-d7-d3` D3 — 2026-09-27.

## Consequências e riscos K tratados

- **K4 (ordem do system message)**: mitigado por congelamento + IT de caracterização; é regra de segurança.
- **K11 (conversão divergente)**: eliminado — uma função, dois chamadores.
- **K1/K2/K9 (memória/concorrência/tenant)**: intocados — nenhuma memória entra neste ADR.
- **K3 (aresta)**: não agravado; a função fica em `agent/api`.
- **K5/K6/K7/K8/K12**: não tratados aqui — ver ADR-004 e ADR-005.
- **Se estiver errado**: a conversão unificada pode apagar uma distinção intencional entre os caminhos; a IT de caracterização dos dois chamadores é arede de segurança. Reversibilidade: baixa sem IT; alta com baseline congelado.

## Como verificar (IT de caracterização)

1. Baseline existente: captura de prompt em `LangChain4jTurnGatewayTest.java:97` e `ChatServiceImplTest.java:315` (`TECH-BRIEF.md:234`).
2. Nova IT de caracterização da ordem: com template + histórico contendo `SYSTEM`, `ASSISTANT`, `USER` e uma mensagem vazia, afirmar a sequência exata do `List<ChatMessage>` e o descarte do vazio (congela itens 2 e 3 da interface).
3. Nova IT de paridade entre chamadores: o mesmo histórico processado pelo caminho humano e pelo caminho público produz listas idênticas (congela K11).
4. IT de rejeição: `role=system` público → 400 e `metadata.hive` ausente (congela item 4; Brain D3 — 2026-09-27).
5. Portão: suíte completa com 0 falhas; `git diff` com zero linhas em `backend/security/**/Mfa*` e `backend/identity/**/Mfa*`.
