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

/* -------------------------------------------------------------------------- */
/* ADR-003 — clients da API pública                                            */
/* Espelha os DTOs reais entregues pelo backend em 2026-11-22:                 */
/* PublicApiClientResponseDTO / PageDTO / CreateDTO / CreatedDTO / RotatedDTO / */
/* PromptBindingDTO. Nenhum campo de secret ou hash entra nestes contratos.    */
/* -------------------------------------------------------------------------- */

export type PublicClientPinMode = "FOLLOW_ACTIVE" | "PINNED"

export interface PublicClientPromptBinding {
  promptCode: string
  pinMode: PublicClientPinMode
  pinnedVersion: number | null
}

/**
 * Item seguro de listagem/get. Campos snake_case espelham o JSON do backend.
 * Não existe `client_secret`, `secret_hash`, `previous_secret_hash` nem
 * `rate_limit_per_minute` — cota por client pertence à ADR-004.
 */
export interface PublicApiClient {
  clientId: string
  label: string
  tenantId: string
  grants: string[]
  prompts: PublicClientPromptBinding[]
  token_ttl_seconds: number | null
  max_messages: number | null
  max_message_chars: number | null
  max_total_chars: number | null
  revoked_at: string | null
  expires_at: string | null
  previous_secret_expires_at: string | null
}

export interface PublicApiClientPage {
  content: PublicApiClient[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

/** Request de criação: `clientId`/`tenantId` são do servidor e NÃO são enviados. */
export interface PublicApiClientCreate {
  label: string
  grants: string[]
  prompts: PublicClientPromptBinding[]
  tokenTtlSeconds?: number
  maxMessages?: number
  maxMessageChars?: number
  maxTotalChars?: number
}

/** Plaintext one-shot de rotação — sem o item completo. */
export interface PublicApiClientRotation {
  clientId: string
  secret: string
  previousSecretExpiresAt: string | null
}
