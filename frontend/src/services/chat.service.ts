import { apiRequest, unwrapData } from "./api"
import type { AiProvider } from "./admin.types"
import type { ChatRequestDTO, ChatResponseDTO } from "./chat.types"

// Backend LangChain4jClientFactory.java:33 = 120s; 10s de margem para o provider responder.
const CHAT_TIMEOUT_MS = 130_000

export interface ChatPayloadDefaults {
  provider?: AiProvider | null
  model?: string | null
}

/**
 * Regra do turno (bkp/front_bkp/src/services/chat.service.ts:4-15 + decisão canônica):
 * o prompt é parâmetro do PRIMEIRO turno, mas opcional: ausente, o backend
 * resolve o `system_fallback` do tenant. Depois que a sessão existe, ela é
 * autoritativa — reenviar o template de um cliente com seleção velha trocaria
 * a conversa silenciosamente. Mensagem vazia não vira requisição.
 *
 * Provider/model entram no body SOMENTE quando o par escolhido difere do par
 * efetivo (tenant ou sessão). Fonte: ui.shadcn.com/docs/ai-elements/base/model-selector
 * ("Send the model with the message"), consultado em 2026-11-20; contrato backend
 * ChatRequestDTO.java:15-21 (override opcional).
 */
export function buildChatPayload(
  input: ChatRequestDTO,
  defaults?: ChatPayloadDefaults | null
): Record<string, unknown> | null {
  const message = typeof input.message === "string" ? input.message.trim() : ""
  if (!message) return null
  const payload: Record<string, unknown> = input.sessionId
    ? { sessionId: input.sessionId, message }
    : input.promptTemplateId
      ? { promptTemplateId: input.promptTemplateId, message }
      : { message }

  // Sessão existente é autoritativa no backend (ChatServiceImpl.java:182-188):
  // override enviado seria descartado e o seletor ficaria mentindo. Nunca vai na wire.
  if (input.sessionId) return payload

  const changed =
    (!!input.provider && input.provider !== defaults?.provider) ||
    (!!input.model && input.model !== defaults?.model)
  if (!changed) return payload

  // O par é atômico na UI: um modelo só faz sentido com o provedor que o oferece.
  // Enviar os dois evita que o backend valide um modelo órfão contra o tenant default.
  if (input.provider) payload.provider = input.provider
  if (input.model) payload.model = input.model
  return payload
}

export const chatService = {
  async sendMessage(
    input: ChatRequestDTO,
    defaults?: ChatPayloadDefaults | null
  ): Promise<ChatResponseDTO> {
    const payload = buildChatPayload(input, defaults)
    if (!payload) {
      throw new Error("Informe uma mensagem antes de enviar.")
    }
    const body = await apiRequest<unknown>("/chat", {
      method: "POST",
      body: payload,
      timeoutMs: CHAT_TIMEOUT_MS,
    })
    return unwrapData<ChatResponseDTO>(body)
  },
}

export default chatService
