import { ApiError, apiRequest, unwrapData } from "./api"
import type {
  AiProvider,
  CredentialStatus,
  CredentialUpsert,
  ProviderModelsResponse,
  TenantLlmConfig,
  TenantLlmConfigUpsert,
  ValidateRequest,
  ValidateResponse,
} from "./admin.types"

/**
 * Porta de bkp/front_bkp/src/services/agent-admin.service.ts:34-95.
 * Rotas Conferidas:
 * - POST /v1/providers/validate — ProviderModelsControllerV1.java:61-70
 * - GET  /v1/providers/{go|zen}/models — ProviderModelsControllerV1.java:72-84
 * - GET/PUT/DELETE /v1/provider-credentials — CredentialControllerV1.java:46-74
 * - GET/PUT /v1/tenant/llm-config — TenantLlmConfigControllerV1.java:54-74
 *
 * Timeout de validação: 20s (backend corta upstream em 15s e devolve ok:false/timeout —
 * ver agent-admin.service.ts:37-40 do legado). Timeout do seletor de modelos: 10s.
 */
const VALIDATE_TIMEOUT_MS = 20_000
const MODELS_SELECTOR_TIMEOUT_MS = 10_000

function remoteSlug(provider: AiProvider | "go" | "zen"): "go" | "zen" | null {
  if (provider === "OPENCODE_GO" || provider === "go") return "go"
  if (provider === "OPENCODE_ZEN" || provider === "zen") return "zen"
  return null
}

export const agentAdminService = {
  async validate(payload: ValidateRequest): Promise<ValidateResponse> {
    const body = await apiRequest<unknown>("/providers/validate", {
      method: "POST",
      body: payload,
      timeoutMs: VALIDATE_TIMEOUT_MS,
    })
    return (unwrapData<ValidateResponse | null>(body) ?? {
      ok: false,
      provider: payload.provider,
      model: payload.model,
      latencyMs: 0,
      reason: "Resposta inválida do provedor.",
    }) as ValidateResponse
  },

  async listCredentials(): Promise<CredentialStatus[]> {
    const body = await apiRequest<unknown>("/provider-credentials")
    const data = unwrapData<CredentialStatus[] | null>(body)
    return Array.isArray(data) ? data : []
  },

  async upsertCredential(provider: AiProvider, payload: CredentialUpsert): Promise<CredentialStatus> {
    const body = await apiRequest<unknown>(`/provider-credentials/${provider}`, {
      method: "PUT",
      body: payload,
    })
    return unwrapData<CredentialStatus>(body)
  },

  async deleteCredential(provider: AiProvider): Promise<void> {
    await apiRequest<void>(`/provider-credentials/${provider}`, { method: "DELETE" })
  },

  async listProviderModels(
    provider: AiProvider | "go" | "zen",
    refresh = false,
    timeoutMs = MODELS_SELECTOR_TIMEOUT_MS
  ): Promise<ProviderModelsResponse> {
    const slug = remoteSlug(provider)
    if (!slug) {
      throw new ApiError(
        "Discovery remoto disponível apenas para OpenCode GO/Zen.",
        400,
        "INVALID_PROVIDER"
      )
    }
    const body = await apiRequest<unknown>(`/providers/${slug}/models`, {
      query: refresh ? { refresh: true } : undefined,
      timeoutMs,
    })
    const data = unwrapData<ProviderModelsResponse | null>(body)
    return {
      models: Array.isArray(data?.models) ? data.models : [],
      filteredOut: Array.isArray(data?.filteredOut) ? data.filteredOut : [],
      cachedAt: data?.cachedAt ?? "",
    }
  },

  /** `null` é resposta esperada: tenant ainda sem LLM padrão. */
  async getLlmConfig(): Promise<TenantLlmConfig | null> {
    const body = await apiRequest<unknown>("/tenant/llm-config")
    const data = unwrapData<TenantLlmConfig | null>(body)
    return data && typeof data === "object" && "provider" in data ? data : null
  },

  async saveLlmConfig(payload: TenantLlmConfigUpsert): Promise<TenantLlmConfig> {
    const body = await apiRequest<unknown>("/tenant/llm-config", {
      method: "PUT",
      body: payload,
    })
    return unwrapData<TenantLlmConfig>(body)
  },
}

export default agentAdminService
