import api from './api'
import type { ChatSessionDTO, ChatMessageDTO } from '../types/session'
import { unwrapPage, type Page } from './page'

/**
 * Histórico de conversas.
 *
 * As duas listagens passam por `unwrapPage`, e isso não é Tidiness: sem o normalizador, o
 * `totalElements`/`totalPages` do Spring 3+ (que vem aninhado em `page`) chega `undefined` para o
 * componente, e um `undefined > 1` é simplesmente falso. O efeito é silencioso e é o pior tipo: o
 * `ChatSidebar` **nunca renderiza o paginador**, então num tenant com 58 conversas o usuário vê 20 e
 * não tem como chegar às outras 38. Achar conversa antiga é função principal de um produto de chat,
 * não conveniência.
 *
 * Um `res.data as Page<T>` aqui é uma afirmação de que o servidor manda a forma que a gente espera.
 * Ele já mandou outra uma vez, sem avisar.
 */
export const sessionService = {
  listSessions(page = 0, size = 20): Promise<Page<ChatSessionDTO>> {
    return api
      .get('/chat/sessions', { params: { page, size } })
      .then(r => unwrapPage<ChatSessionDTO>(r.data, size))
  },

  /**
   * The prompt is chosen HERE, when the conversation is opened — it is a property of the
   * conversation, and there is no default prompt. After this the session keeps it for its whole life,
   * which is why the chat header locks the selector.
   */
  createSession(promptTemplateId: string, title?: string): Promise<ChatSessionDTO> {
    return api.post('/chat/sessions', { promptTemplateId, title }).then(r => r.data as ChatSessionDTO)
  },

  getSession(id: string): Promise<ChatSessionDTO> {
    return api.get(`/chat/sessions/${id}`).then(r => r.data as ChatSessionDTO)
  },

  getMessages(sessionId: string, page = 0, size = 50): Promise<Page<ChatMessageDTO>> {
    return api
      .get(`/chat/sessions/${sessionId}/messages`, { params: { page, size } })
      .then(r => unwrapPage<ChatMessageDTO>(r.data, size))
  },

  deleteSession(id: string): Promise<void> {
    return api.delete(`/chat/sessions/${id}`).then(() => undefined)
  },

  updateSession(id: string, title?: string): Promise<ChatSessionDTO> {
    return api.patch(`/chat/sessions/${id}`, { title }).then(r => r.data as ChatSessionDTO)
  }
}

export default sessionService
