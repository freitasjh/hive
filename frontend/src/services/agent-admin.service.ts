import api from './api'
import type {
  AiProvider,
  CredentialStatus,
  CredentialUpsert,
  ProviderModelsResponse,
  TenantLlmConfig,
  TenantLlmConfigUpsert,
  ValidateRequest,
  ValidateResponse,
} from '../types/agent-admin'
import { toApiError } from '../types/agent-admin'

/**
 * O nome do arquivo é dívida técnica e não deve ser lido como o que ele faz: sobrou aqui o que era
 * do editor de agente (credenciais do provedor, catálogo de modelos remotos e o probe de
 * conectividade), que são coisas de PROVEDOR, não de agente. Renomear exigiria tocar CredentialsView,
 * ConnectionTester e os testes deles, e renomear arquivo não estava no escopo deste batch.
 *
 * O CRUD de agente, o default e o vínculo com bases de conhecimento saíram daqui junto com a
 * entidade. O que ficou é o que a tela de provedores realmente usa.
 */

function unwrapData<T>(data: unknown): T {
  const d = data as Record<string, unknown>
  if (d && d.data !== undefined) return d.data as T
  return data as T
}

/**
 * Probes a vendor. Used to be `POST /v1/agents/validate`, on a controller that no longer exists; the
 * probe asks a provider whether a model answers, so it moved to the provider route.
 */
export const agentAdminService = {
  async validate(payload: ValidateRequest): Promise<ValidateResponse> {
    try {
      // 20s > 15s do backend de propósito: o backend corta a chamada upstream em 15s e devolve
      // { ok:false, reason:'timeout' }, que a UI traduz. Com timeout de cliente menor, o axios
      // aborta primeiro (net::ERR_ABORTED) e essa orientação nunca chega ao usuário.
      const res = await api.post('/providers/validate', payload, { timeout: 20000 })
      return unwrapData<ValidateResponse>(res.data)
    } catch (e) { throw toApiError(e) }
  },

  async listCredentials(): Promise<CredentialStatus[]> {
    try {
      const res = await api.get('/provider-credentials')
      return unwrapData<CredentialStatus[]>(res.data)
    } catch (e) { throw toApiError(e) }
  },

  async upsertCredential(provider: string, payload: CredentialUpsert): Promise<CredentialStatus> {
    try {
      const res = await api.put(`/provider-credentials/${provider}`, payload)
      return unwrapData<CredentialStatus>(res.data)
    } catch (e) { throw toApiError(e) }
  },

  async deleteCredential(provider: string): Promise<void> {
    try {
      await api.delete(`/provider-credentials/${provider}`)
    } catch (e) { throw toApiError(e) }
  },

  async listProviderModels(provider: AiProvider | 'go' | 'zen', refresh = false): Promise<ProviderModelsResponse> {
    const slug =
      provider === 'OPENCODE_GO' || provider === 'go' ? 'go'
      : provider === 'OPENCODE_ZEN' || provider === 'zen' ? 'zen'
      : null
    if (!slug) throw Object.assign(new Error('Discovery remoto disponível apenas para OpenCode GO/Zen.'), { status: 400, code: 'INVALID_PROVIDER' })
    try {
      const res = await api.get(`/providers/${slug}/models`, { params: refresh ? { refresh: true } : {} })
      return unwrapData<ProviderModelsResponse>(res.data)
    } catch (e) { throw toApiError(e) }
  },

  /**
   * The tenant's one default LLM. `null` is a real, expected answer — it means the tenant has not
   * configured it, which the chat turns into a 422 saying exactly this screen.
   */
  async getLlmConfig(): Promise<TenantLlmConfig | null> {
    try {
      const res = await api.get('/tenant/llm-config')
      const body = unwrapData<TenantLlmConfig | null>(res.data)
      return body ?? null
    } catch (e) { throw toApiError(e) }
  },

  async saveLlmConfig(payload: TenantLlmConfigUpsert): Promise<TenantLlmConfig> {
    try {
      const res = await api.put('/tenant/llm-config', payload)
      return unwrapData<TenantLlmConfig>(res.data)
    } catch (e) { throw toApiError(e) }
  },
}

export default agentAdminService
