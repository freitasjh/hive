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

import type { AiProvider } from "./admin.types"

/**
 * Contrato HTTP espelhado dos DTOs reais — verificação em 2026-11-20:
 * - ChatRequestDTO.java:15-21 → provider/model são override OPCIONAL; sem eles o
 *   tenant usa o default de tenant_llm_config; com sessionId a sessão é autoritativa.
 * - ChatResponseDTO.java:13-20 → provider/model são o par EFETIVO que respondeu.
 * - ChatSessionDTO.java:13-23 → provider/model gravados na conversa (null = default).
 * - ProviderCatalogIT.java:21-60 → GET /v1/providers/catalog devolve ARRAY direto
 *   com { provider, models, defaultModel, configured } (sem wrapper).
 */
export interface ChatRequestDTO {
  /** Somente no PRIMEIRO turno. Turnos seguintes usam sessionId. */
  promptTemplateId?: string
  sessionId?: string
  message: string
  /** Override explícito. Enviar somente quando diferente do par efetivo. */
  provider?: AiProvider
  model?: string
}

export interface ChatResponseDTO {
  sessionId: string
  role: string
  message: string
  timestamp: string
  provider?: AiProvider | null
  model?: string | null
}

export interface ChatSessionDTO {
  id: string
  userId: string
  promptTemplateId: string
  title: string
  summary?: string | null
  createAt: string
  updateAt: string
  provider?: AiProvider | null
  model?: string | null
}

export interface ProviderCatalogEntry {
  provider: AiProvider
  models: string[]
  defaultModel: string
  configured: boolean
}

/** Par provedor/modelo escolhido na UI. `null` = usar o default efetivo. */
export interface ModelChoice {
  provider: AiProvider
  model: string
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
