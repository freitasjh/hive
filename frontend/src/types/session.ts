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

export interface CitationDTO {
  docId: string
  docName: string
  chunkId: string
  score: number
}

export interface ChatResponseDTO {
  sessionId: string
  role: string
  message: string
  timestamp: string
  /** Whether retrieved knowledge was injected into this turn. Optional: older servers omit it. */
  ragUsed?: boolean
  /** Why the context was empty when it was. See `types/chat-messages.ts` for the copy per reason. */
  ragReason?: string
  citations?: CitationDTO[]
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
