/**
 * Copy estática do chat. Sem conceito de agente, tools, RAG ou base de
 * conhecimento — o modelo do produto é só prompt + conversa (decisão
 * `scope-assistant-sem-rag`, 2026-09-26).
 */
export const STARTER_PROMPTS = [
  "Resuma este texto",
  "Gere um checklist",
  "Traduza para inglês",
  "Explique este conceito",
] as const

export const CHAT_VIEW_ID = "chat" as const
