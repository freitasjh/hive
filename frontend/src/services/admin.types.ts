/**
 * Contratos de API espelhados dos DTOs reais — verificação em:
 * - CredentialStatusDTO.java / CredentialUpsertDTO.java
 * - ProviderModelsDTO.java / FilteredModelDTO.java / ValidateRequestDTO.java / ValidateResponseDTO.java
 *   (backend/agent/web/internal/v1/src/main/java/br/com/systec/hive/agent/web/internal/v1/dto/)
 * - PromptTemplateDTO.java / PromptTemplateCreateDTO.java / PromptTemplateUpdateDTO.java / InferenceParamsDTO.java
 * - TenantLlmConfigDTO.java / TenantLlmConfigUpsertDTO.java
 *   (backend/tenant/web/internal/v1/src/main/java/br/com/systec/hive/tenant/web/internal/v1/dto/)
 * e de bkp/front_bkp/src/types/{agent-admin,prompt}.ts.
 */

export type AiProvider =
  | "OPENAI"
  | "GEMINI"
  | "ANTHROPIC"
  | "OLLAMA"
  | "OPENCODE_GO"
  | "OPENCODE_ZEN"
  | "OPENAI_COMPATIBLE"

export type PromptStatus = "DRAFT" | "ACTIVE" | "ARCHIVED"

export interface CredentialStatus {
  provider: AiProvider
  configured: boolean
  last4: string | null
  baseUrl?: string | null
  updatedAt?: string | null
}

export interface CredentialUpsert {
  apiKey?: string
  baseUrl?: string
}

export interface ProviderModelsResponse {
  models: string[]
  filteredOut: Array<{ id: string; reason: string }>
  cachedAt: string
}

export interface ValidateRequest {
  provider: AiProvider
  model: string
  baseUrl?: string
}

export interface ValidateResponse {
  ok: boolean
  provider: AiProvider
  model: string
  latencyMs: number
  reason?: string | null
}

export interface TenantLlmConfig {
  id: string
  tenantId: string
  provider: AiProvider
  model: string
  createAt: string
  updateAt: string
}

export interface TenantLlmConfigUpsert {
  provider: AiProvider
  model: string
}

export interface InferenceParamsDTO {
  temperature?: number | null
  topP?: number | null
  maxTokens?: number | null
}

export interface PromptTemplateDTO {
  id: string
  name: string
  code: string
  description?: string | null
  tags?: string[] | null
  template: string
  params?: string[] | null
  inferenceParams?: InferenceParamsDTO | null
  status: PromptStatus
  version: number
  createAt: string
  updateAt: string
}

export interface PromptTemplateCreateDTO {
  name: string
  code: string
  description?: string | null
  tags?: string[] | null
  template: string
  inferenceParams?: InferenceParamsDTO | null
  status?: PromptStatus | null
}

export interface PromptTemplateUpdateDTO {
  name: string
  description?: string | null
  tags?: string[] | null
  template: string
  inferenceParams?: InferenceParamsDTO | null
  status?: PromptStatus | null
}

export function maskKey(last4: string | null | undefined): string {
  return last4 ? `••••${last4}` : "••••"
}
