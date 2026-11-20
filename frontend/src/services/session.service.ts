import { apiRequest, unwrapData } from "./api"
import { unwrapPage, type Page } from "./page"
import type {
  ChatMessageDTO,
  ChatSessionDTO,
  CreateSessionDTO,
  UpdateSessionDTO,
} from "./chat.types"

/**
 * Histórico de conversas. As duas listagens PASSAM por unwrapPage: sem ele o
 * totalPages do Spring (aninhado em `page`) chega undefined e `undefined > 1`
 * é falso — o paginador nunca renderiza. Ver bkp/.../session.service.ts:5-17.
 */
export const sessionService = {
  async listSessions(page = 0, size = 20): Promise<Page<ChatSessionDTO>> {
    const body = await apiRequest<unknown>("/chat/sessions", { query: { page, size } })
    return unwrapPage<ChatSessionDTO>(body, size)
  },

  /** Prompt escolhido na CRIAÇÃO da conversa; depois a sessão o mantém. */
  async createSession(promptTemplateId: string, title?: string): Promise<ChatSessionDTO> {
    const dto: CreateSessionDTO = { promptTemplateId, title }
    const body = await apiRequest<unknown>("/chat/sessions", { method: "POST", body: dto })
    return unwrapData<ChatSessionDTO>(body)
  },

  async getSession(id: string): Promise<ChatSessionDTO> {
    const body = await apiRequest<unknown>(`/chat/sessions/${id}`)
    return unwrapData<ChatSessionDTO>(body)
  },

  async getMessages(sessionId: string, page = 0, size = 50): Promise<Page<ChatMessageDTO>> {
    const body = await apiRequest<unknown>(`/chat/sessions/${sessionId}/messages`, {
      query: { page, size },
    })
    return unwrapPage<ChatMessageDTO>(body, size)
  },

  async deleteSession(id: string): Promise<void> {
    await apiRequest<void>(`/chat/sessions/${id}`, { method: "DELETE" })
  },

  async updateSession(id: string, title: string): Promise<ChatSessionDTO> {
    const dto: UpdateSessionDTO = { title }
    const body = await apiRequest<unknown>(`/chat/sessions/${id}`, { method: "PATCH", body: dto })
    return unwrapData<ChatSessionDTO>(body)
  },
}

export default sessionService
