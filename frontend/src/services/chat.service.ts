import { apiRequest, apiStreamFrames, unwrapData } from "./api"
import type { AiProvider } from "./admin.types"
import type { ChatRequestDTO, ChatResponseDTO } from "./chat.types"

// Backend LangChain4jClientFactory.java:33 = 120s; 10s de margem para o provider responder.
const CHAT_TIMEOUT_MS = 130_000

export interface ChatPayloadDefaults {
  provider?: AiProvider | null
  model?: string | null
}

export interface ChatStreamUsage {
  promptTokens: number
  completionTokens: number
  totalTokens: number
}

/** Metadados do terminal `done`: finish_reason/usage e, desde a Emenda 3,
 *  sessionId. timestamp/provider/model vêm quando o endpoint os expõe; o
 *  chamador completa com o estado do turno (ver use-chat-controller). */
export interface ChatStreamMeta {
  type?: string
  finishReason?: string | null
  usage?: ChatStreamUsage
  sessionId?: string | null
  timestamp?: string | null
  provider?: AiProvider | null
  model?: string | null
}

export interface ChatStreamHandlers {
  onThinking: (text: string) => void
  onText: (chunk: string) => void
  onDone: (meta: ChatStreamMeta) => void
  onError: (code: string) => void
}

function frameRecord(raw: string): Record<string, unknown> | null {
  if (!raw.trim() || raw.trim() === "[DONE]") return null
  try {
    const value: unknown = JSON.parse(raw)
    return value && typeof value === "object" ? (value as Record<string, unknown>) : null
  } catch {
    return null
  }
}

/** Código do conjunto fechado do backend; qualquer outra coisa vira stream_failed. */
function safeStreamCode(value: unknown): string {
  return typeof value === "string" && /^[A-Za-z0-9_.-]{1,64}$/.test(value)
    ? value
    : "stream_failed"
}

function streamMeta(record: Record<string, unknown>): ChatStreamMeta {
  const usageRecord =
    record.usage && typeof record.usage === "object"
      ? (record.usage as Record<string, unknown>)
      : null
  const count = (value: unknown) =>
    typeof value === "number" && Number.isFinite(value) ? value : 0
  const meta: ChatStreamMeta = {}
  if (typeof record.type === "string") meta.type = record.type
  if (typeof record.finish_reason === "string") meta.finishReason = record.finish_reason
  if (usageRecord) {
    meta.usage = {
      promptTokens: count(usageRecord.prompt_tokens),
      completionTokens: count(usageRecord.completion_tokens),
      totalTokens: count(usageRecord.total_tokens),
    }
  }
  if (typeof record.sessionId === "string") meta.sessionId = record.sessionId
  if (typeof record.timestamp === "string") meta.timestamp = record.timestamp
  if (typeof record.provider === "string") meta.provider = record.provider as AiProvider
  if (typeof record.model === "string") meta.model = record.model
  return meta
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

  /**
   * Consome os eventos nomeados `thinking`/`text`/`done`/`error` do assistant.
   * Endpoint e payload são os mesmos de `sendMessage`, trocando apenas a rota.
   * `error` e `[DONE]` são terminais: nada depois deles chega à UI.
   */
  async streamMessage(
    input: ChatRequestDTO,
    defaults: ChatPayloadDefaults | null | undefined,
    handlers: ChatStreamHandlers,
    signal?: AbortSignal
  ): Promise<void> {
    const payload = buildChatPayload(input, defaults)
    if (!payload) {
      throw new Error("Informe uma mensagem antes de enviar.")
    }

    for await (const frame of apiStreamFrames("/chat/stream", {
      method: "POST",
      body: payload,
      timeoutMs: CHAT_TIMEOUT_MS,
      signal,
    })) {
      if (frame.data.trim() === "[DONE]") {
        handlers.onDone({})
        return
      }
      const record = frameRecord(frame.data)
      if (!record) continue
      const event = frame.event || (typeof record.type === "string" ? record.type : "message")

      if (event === "thinking" || event === "text") {
        const text = typeof record.text === "string" ? record.text : ""
        if (text) (event === "thinking" ? handlers.onThinking : handlers.onText)(text)
      } else if (event === "error") {
        const body =
          record.error && typeof record.error === "object"
            ? (record.error as Record<string, unknown>)
            : record
        handlers.onError(safeStreamCode(body.code))
        return
      } else if (event === "done") {
        handlers.onDone(streamMeta(record))
        return
      }
    }
  },
}

export default chatService
