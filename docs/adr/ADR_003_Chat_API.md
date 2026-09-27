# ADR 003: Contrato da API de Chat

## Status
Proposto

> ### ⚠️ Supersedido em 2026-09-27 — decisão `scope-assistant-sem-rag`
>
> Este ADR **não foi reescrito**: ele é registro do raciocínio da época, e reescrever seria apagar a
> lição. O que aconteceu depois está em
> `arquitetura/projetos/hive/decisoes/scope-assistant-sem-rag` (Brain).
>
> O produto virou um **Assistant**: chat simples, sem agente, sem tools, sem base de conhecimento.
> O contrato descrito aqui (`POST /v1/agent/chat` com `agentId`) não existe mais. Hoje é
`POST /v1/chat` com `promptTemplateId` **apenas no primeiro turno**: a partir da segunda mensagem a
sessão é a autoridade e o id do cliente é ignorado de propósito. A porta `ChatTurnGateway` do
ADR-012 sobrevive e é onde o prompt entra no `TurnRequest`.


## Contexto
Necessidade de padronizar a comunicação entre o Frontend e o motor de IA do Hive Agent.

## Decisão
O endpoint de chat seguirá o padrão REST para a versão MVP, com planos futuros para SSE (Server-Sent Events) para streaming de respostas.

### Endpoint: `POST /v1/agent/chat`

**Request Body (`ChatRequestDTO`):**
```json
{
  "agentId": "uuid",
  "sessionId": "uuid (opcional)",
  "message": "string"
}
```

**Response Body (`ChatResponseDTO`):**
```json
{
  "sessionId": "uuid",
  "message": "string",
  "role": "ASSISTANT",
  "timestamp": "iso-date"
}
```

## Consequências
- Permite rastreabilidade de conversas via `sessionId`.
- Facilita a implementação de histórico no frontend.
