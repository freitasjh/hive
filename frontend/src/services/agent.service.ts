import api from './api'
import type { AgentPage } from '../types/session'

export const agentService = {
  listAgents(page = 0, size = 50): Promise<AgentPage> {
    return api.get('/agents', { params: { page, size } }).then(r => {
      const data = r.data
      if (data && Array.isArray(data.content)) return data as AgentPage
      if (Array.isArray(data)) return { content: data, totalElements: data.length, totalPages: 1, number: 0, size } as AgentPage
      return data as AgentPage
    })
  }
}

export default agentService
