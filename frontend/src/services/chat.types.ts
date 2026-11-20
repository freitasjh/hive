/**
 * DTOs espelhando o contrato HTTP — sem reinterpretar o backend.
 *
 * Fonte verificada em 2026-11-19:
 * - ChatControllerV1.java:29,41-44 → POST /v1/chat
 * - ChatSessionControllerV1.java:34,53-124 → POST/GET sessions, GET /{id}/messages,
 *   DELETE, PATCH
 * - dto/ChatRequestDTO.java, ChatResponseDTO.java, ChatSessionDTO.java,
 *   ChatMessageDTO.java, CreateSessionDTO.java, UpdateSessionDTO.java
 * - RestControllerTool.buildSuccessResponse → ResponseEntity.ok(obj) (sem envelope)
 * - RestPath.V1 = "/v1"; context path "/hive" em services/api.ts:20
 */

export interface ChatRequestDTO {
  /** Somente no PRIMEIRO turno. Turnos seguintes usam sessionId. */
  promptTemplateId?: string
  sessionId?: string
  message: string
}

export interface ChatResponseDTO {
  sessionId: string
  role: string
  message: string
  timestamp: string
}

export interface ChatSessionDTO {
  id: string
  userId: string
  promptTemplateId: string
  title: string
  summary?: string | null
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

export interface CreateSessionDTO {
  promptTemplateId: string
  title?: string
}

export interface UpdateSessionDTO {
  title: string
}
