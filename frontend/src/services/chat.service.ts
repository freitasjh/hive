import api from './api'
import type { ChatResponseDTO } from '../types/session'

export const chatService = {
  /**
   * `promptTemplateId` goes only on the FIRST turn of a conversation, when the backend creates the
   * session. After that the session's own template is authoritative and the id in the request is
   * ignored on purpose — a conversation that already has messages must not be re-targeted by a
   * client still holding a stale selection.
   */
  sendMessage(promptTemplateId: string, sessionId: string | null, message: string): Promise<ChatResponseDTO> {
    const payload: Record<string, unknown> = { promptTemplateId, message }
    if (sessionId) payload.sessionId = sessionId
    return api.post('/chat', payload).then(r => r.data as ChatResponseDTO)
  }
}

export default chatService
