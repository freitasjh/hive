# ADR-002 — Centralizar conversação, precedência do system message e telemetria

**Status:** in-progress · **Data:** 2026-11-22 · **PRD:** `public-chat-api` · **Branch:** `adr/public-chat-api/02-centralizacao-chat`

**depende-de:** ADR-001 (merged — contrato tipado e spec OpenAPI estáveis).

## Contexto

Revisão contra código em 2026-11-22 corrige o diagnóstico inicial do rascunho:

- **K11 já está fechado.** A conversão canônica não está em `agent/api` — esse módulo foi renomeado para `assistant/api` pela ADR-019. Ela vive em `history/api`, em `ProviderMessages.toProviderMessages(List<ChatMessage>)` (`history/api/.../ProviderMessages.java:48-63`, ADR-017). **Ambos os chamadores já a usam**: `PublicChatServiceImpl.java:161-172` (comentário "ADR-020/K11") e `ChatServiceImpl.java:242`. O `ChatCallerParityIT` já prova paridade byte a byte dos dois prompts finais. O que falta não é criar a função — é **travar a regra com guarda de IT** para impedir regressão.
- **K6 permanece aberto.** `ChatTrace` tem `turnId` e `clientId` (`assistant/api/.../ChatTrace.java:47-49`) e o builder já expõe `.turnId()`/`.clientId()` (`:104-112`), mas `TurnOutcomeMapper.recordTurn` (`:181-189`) nunca os preenche. A linha de trace pública imprime `null` embora `PublicChatServiceImpl` calcule `clientId` em `:81` e `turnId` em `:174`.
- **Ordem do system message não é do conversor.** `history/api` converte e **preserva a ordem de entrada** (`ProviderMessages.java:50-63`; teste `ProviderMessagesTest:71-86`). O `SystemMessage` do template é preposto pelo gateway em `AbstractLangChain4jTurnGateway.telemetryPrompt` (`:67-72`) e garantido na memória por `alwaysKeepSystemMessageFirst(true)` (`:118`). Mover essa responsabilidade para o conversor mudaria o baseline.
- A ADR-021 extraiu `ProviderResolution` e `TurnOutcomeMapper` para **`assistant/impl`**. Esta ADR não reabre essa decisão nem a fronteira de módulos (ADR-005).

Fontes internas: ADR-017 (`history/api` dono da conversão), ADR-019 (rename `agent` → `assistant`), ADR-020 (paridade dos dois chamadores), ADR-021 (`assistant/impl`); `.spec/prd/langchain4j-ai-services/TECH-BRIEF.md:101-124,184-188,249-257`; `.spec/prd/public-chat-api/EXPLORATION.md` §2; `.spec/prd/public-chat-api/ARCHITECTURE.md` §2.2. Nenhuma recomendação de biblioteca nova; nenhuma fonte externa adicional necessária.

## Decisão

1. **Conversão única — manter a existente, sem novo tipo.** A função canônica permanece `history.api.util.ProviderMessages.toProviderMessages(List<history.api.domain.ChatMessage>)` em `history/api` (ADR-017). O **tipo neutro é `history.api.domain.ChatMessage`** (papel `provider.api.ChatRole` + conteúdo em `content`/`message`) — não se cria tipo novo em `assistant/api` nem em `ai/api`. Criá-lo exigiria aresta `ai/api → history/api` (vetada pelo critério de dependência) ou duplicaria a regra, reabrindo K11. Regra congelada: `SYSTEM→SystemMessage`, `ASSISTANT→AiMessage`, demais→`UserMessage`; entrada sem texto nas duas colunas é descartada; **ordem de entrada preservada** (`ProviderMessagesTest:35-86`). A função não conhece DTOs de `publicapi` nem classes de `assistant/impl`.
2. **Um adaptador por chamador, zero montagem local — com guarda.** `PublicChatServiceImpl` e `ChatServiceImpl` já mapeiam entradas para `ChatMessage` e chamam a função (`:161-172` e `:242`); nenhum monta mensagens do provider diretamente. Esta ADR **não reescreve** os chamadores — congela a propriedade com `ChatCallerParityIT` (prompt final idêntico nos dois) e com `grep` proibindo construção local de `SystemMessage`/`UserMessage`/`AiMessage` fora da função.
3. **Ordem do prompt pertence ao gateway, não ao conversor.** `SystemMessage` do template primeiro, conversação depois — garantido por `AbstractLangChain4jTurnGateway.telemetryPrompt:67-72` e `alwaysKeepSystemMessageFirst(true)` (`:118`), já coberto por `ChatCallerParityIT:157-167`. Nenhuma reordenação é introduzida em `history/api`.
4. **Telemetria atribuível (K6).** `TurnOutcomeMapper.resolve(...)` passa a receber `turnId`/`clientId` e a chamá-los no builder (`ChatTrace.java:104-112`). Chamador público fornece `turnId` (calculado em `PublicChatServiceImpl:174`) e `clientId` (`:81`); chamador humano passa **`null`/`null` explicitamente**, conforme o javadoc de `ChatTrace:26-27` ("`null` for the human chat flow") — não há turnId/clientId no fluxo humano. `ChatTurnGateway.execute(TurnRequest):TurnReply` **não muda**: ambos os valores são locais do chamador, não da porta.
5. **Fora desta ADR e contrato inalterado.** Fora: trocar `ChatTurnGateway` por Ai Services; adotar `ChatMemory`/`ChatMemoryStore`; mover `ProviderResolution`/`TurnOutcomeMapper` para `assistant/api` (ADR-005); criar novo tipo neutro; alterar a regra de descarte do conversor (incluir `blank`). Inalterados: envelope OpenAI da ADR-001, códigos de erro, rejeição de `role: system` (400) e duplo `/v1`.

## Alternativas rejeitadas

| Alternativa | Motivo da rejeição |
|---|---|
| Criar novo tipo neutro + função em `assistant/api`/`ai/api` | Exige aresta `ai/api → history/api` (vetada) ou duplica a regra; K11 reabre |
| Mover `ProviderResolution`/`TurnOutcomeMapper` para `assistant/api` nesta rodada | Muda grafo de módulos e mistura refactor load-bearing com a correção; fica na ADR-005 |
| Reordenar `SYSTEM` dentro do conversor de `history/api` | A ordem do prompt é do gateway (`telemetryPrompt`, `alwaysKeepSystemMessageFirst`); mudar aqui altera baseline e responsabilidade |
| Descartar conteúdo `blank` no conversor | Muda comportamento: o caminho público já rejeita branco a montante (`PublicChatServiceImpl:130`); fora de escopo |
| Ai Services + `ChatMemory` | Riscos A8/A11/A12/A13/A15 registrados no TECH-BRIEF; exige deepsearch de compatibilidade e ADR própria |
| Corrigir só a trace (K6) sem guarda de conversão | K11 já está fechado, mas sem IT de paridade a regressão volta silenciosamente |

## Interface congelada

- `ChatTurnGateway.execute(TurnRequest): TurnReply` — assinatura e semântica inalteradas; `TurnRequest`/`TurnReply` sem campos novos.
- `history.api.util.ProviderMessages.toProviderMessages(List<ChatMessage>)` — assinatura e regra congeladas: mapeamento explícito de papel, descarte de conteúdo nulo, ordem de entrada preservada. Conteúdo `blank` **não** é descartado aqui (já rejeitado a montante no caminho público).
- Ordem do prompt final entregue ao provider: `SystemMessage` do template → conversação; responsabilidade do gateway (`telemetryPrompt` + `alwaysKeepSystemMessageFirst`), não do conversor.
- `TurnReply` continua carregando `promptTokens`, `completionTokens`, `totalTokens` e `finishReason` medidos (baseline `120/340/460/STOP`).
- `ChatTrace` continua com `tenantId`, `turnId`, `clientId`; `turnId`/`clientId` preenchidos no caminho público e **explicitamente `null` no caminho humano**.
- Envelope público da ADR-001, códigos de erro e rejeição de `role: system` preservados.
- Nenhuma nova dependência de módulo: nenhuma aresta `ai/api → history/api`, `assistant/api → publicapi/**` ou `assistant/api → assistant/impl`; poms inalterados.

## Consequências

- **Positivas:** regra de conversão já única e agora com guarda de IT; trace atribuível por turno e client; nenhuma mudança de comportamento de conversão.
- **Negativas:** assinatura de `TurnOutcomeMapper.resolve(...)` muda — exige atualizar os 2 chamadores e `TurnOutcomeMapperTest`; baselines precisam ser registrados com data antes de qualquer toque.
- **Neutras:** gateway, política de retry e classificação de `model-not-found` inalterados (ADRs 012/013 permanecem válidos); nenhum pom, entidade ou migration muda.

## Evidência exigida

`ChatCallerParityIT` com baseline de 4 mensagens (`SystemMessage` do template → `UserMessage`/`AiMessage` da conversação) para os dois chamadores, estendido para afirmar `promptTokens=120`, `completionTokens=340`, `totalTokens=460`, `finishReason=STOP` (valores já caracterizados em `AiServiceCharacterizationTest:40-80` e `LangChain4jTurnGatewayTest:157-166`); asserção de `turnId`/`clientId` não nulos no turno público e de `null` no humano; suíte completa sem regressão; `git diff` sem pom novo, sem `backend/security/**/Mfa*`, `identity/**/Mfa*` e `workflow-state.json`; diagnóstico de `model-not-found` idêntico nos dois chamadores.
