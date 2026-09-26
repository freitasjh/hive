import api from './api'
import type { ChatResponseDTO } from '../types/session'

export const chatService = {
  sendMessage(agentId: string, sessionId: string | null, message: string): Promise<ChatResponseDTO> {
    const payload: Record<string, unknown> = { agentId, message }
    if (sessionId) payload.sessionId = sessionId
    return api.post('/chat', payload).then(r => r.data as ChatResponseDTO)
  }
}

export default chatService
