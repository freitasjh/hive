export interface ChatSessionDTO {
  id: string
  userId: string
  promptTemplateId: string
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

/** A interface de página paginada mora em `services/page.ts`, junto do normalizador. */
export type { Page } from '../services/page'
