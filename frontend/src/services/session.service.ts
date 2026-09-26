import api from './api'
import type { ChatSessionDTO, ChatMessageDTO, Page } from '../types/session'

export const sessionService = {
  listSessions(page = 0, size = 20): Promise<Page<ChatSessionDTO>> {
    return api.get('/chat/sessions', { params: { page, size } }).then(r => r.data as Page<ChatSessionDTO>)
  },

  createSession(agentId: string, title?: string): Promise<ChatSessionDTO> {
    return api.post('/chat/sessions', { agentId, title }).then(r => r.data as ChatSessionDTO)
  },

  getSession(id: string): Promise<ChatSessionDTO> {
    return api.get(`/chat/sessions/${id}`).then(r => r.data as ChatSessionDTO)
  },

  getMessages(sessionId: string, page = 0, size = 50): Promise<Page<ChatMessageDTO>> {
    return api.get(`/chat/sessions/${sessionId}/messages`, { params: { page, size } }).then(r => r.data as Page<ChatMessageDTO>)
  },

  deleteSession(id: string): Promise<void> {
    return api.delete(`/chat/sessions/${id}`).then(() => undefined)
  },

  updateSession(id: string, title: string): Promise<ChatSessionDTO> {
    return api.patch(`/chat/sessions/${id}`, { title }).then(r => r.data as ChatSessionDTO)
  }
}

export default sessionService
