# ADR 003: Contrato da API de Chat

## Status
Proposto

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
