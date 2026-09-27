export interface ChatSessionDTO {
  id: string
  userId: string
  agentId: string
  title: string
  summary: string | null
  createAt: string
  updateAt: string
}

export interface ChatMessageDTO {
  id: string
  sessionId: string
  role: string
  content: string
  createAt: string
}

export interface ChatResponseDTO {
  sessionId: string
  role: string
  message: string
  timestamp: string
}

export interface Page<T> {
  content: T[]
  totalElements: number
  totalPages: number
  number: number
  size: number
}

export interface AgentDTO {
  id: string
  name: string
  description?: string
  model?: string
}

export interface AgentPage {
  content: AgentDTO[]
  totalElements: number
  totalPages: number
  number: number
  size: number
}
