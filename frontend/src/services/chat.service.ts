import { apiRequest, unwrapData } from "./api"
import type { ChatRequestDTO, ChatResponseDTO } from "./chat.types"

/**
 * Regra do turno (bkp/front_bkp/src/services/chat.service.ts:4-15 + decisão canônica):
 * o prompt é parâmetro do PRIMEIRO turno. Depois que a sessão existe, ela é
 * autoritativa — reenviar o template de um cliente com seleção velha trocaria
 * a conversa silenciosamente. Mensagem vazia/sem prompt no primeiro turno não
 * vira requisição.
 */
export function buildChatPayload(input: ChatRequestDTO): Record<string, unknown> | null {
  const message = typeof input.message === "string" ? input.message.trim() : ""
  if (!message) return null
  if (input.sessionId) return { sessionId: input.sessionId, message }
  if (!input.promptTemplateId) return null
  return { promptTemplateId: input.promptTemplateId, message }
}

export const chatService = {
  async sendMessage(input: ChatRequestDTO): Promise<ChatResponseDTO> {
    const payload = buildChatPayload(input)
    if (!payload) {
      throw new Error("Selecione um prompt e informe uma mensagem antes de enviar.")
    }
    const body = await apiRequest<unknown>("/chat", { method: "POST", body: payload })
    return unwrapData<ChatResponseDTO>(body)
  },
}

export default chatService
