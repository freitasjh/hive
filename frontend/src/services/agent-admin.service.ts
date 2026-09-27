import api from './api'
import type {
  AgentDTO,
  AgentCreateDTO,
  AgentUpdateDTO,
  AiProvider,
  CredentialStatus,
  CredentialUpsert,
  Page,
  ProviderModelsResponse,
  ValidateRequest,
  ValidateResponse,
} from '../types/agent-admin'
import { toApiError } from '../types/agent-admin'

function unwrapPage<T>(data: unknown, fallbackSize: number): Page<T> {
  const d = data as Record<string, unknown>
  if (d && Array.isArray(d.content)) return d as unknown as Page<T>
  if (Array.isArray(d)) return { content: d as T[], totalElements: d.length, totalPages: 1, number: 0, size: fallbackSize } as Page<T>
  if (d && d.data) {
    const inner = d.data as Record<string, unknown>
    if (Array.isArray(inner.content)) return inner as unknown as Page<T>
  }
  return d as unknown as Page<T>
}

function unwrapData<T>(data: unknown): T {
  const d = data as Record<string, unknown>
  if (d && d.data !== undefined) return d.data as T
  return data as T
}

function handleError(e: unknown): never {
  throw e
}

export const agentAdminService = {
  async list(params: { page?: number; size?: number; q?: string } = {}): Promise<Page<AgentDTO>> {
    const { page = 0, size = 20, q } = params
    try {
      const res = await api.get('/agents', { params: { page, size, q } })
      return unwrapPage<AgentDTO>(res.data, size)
    } catch (e) { handleError(e); throw e }
  },

  async getDefault(): Promise<AgentDTO> {
    try {
      const res = await api.get('/agents/default')
      return unwrapData<AgentDTO>(res.data)
    } catch (e) { handleError(e); throw e }
  },

  async getById(id: string): Promise<AgentDTO> {
    try {
      const res = await api.get(`/agents/${id}`)
      return unwrapData<AgentDTO>(res.data)
    } catch (e) { handleError(e); throw e }
  },

  async create(dto: AgentCreateDTO): Promise<AgentDTO> {
    try {
      const res = await api.post('/agents', dto)
      return unwrapData<AgentDTO>(res.data)
    } catch (e) { handleError(e); throw e }
  },

  async update(id: string, dto: AgentUpdateDTO): Promise<AgentDTO> {
    try {
      const res = await api.put(`/agents/${id}`, dto)
      return unwrapData<AgentDTO>(res.data)
    } catch (e) { handleError(e); throw e }
  },

  async remove(id: string): Promise<void> {
    try {
      await api.delete(`/agents/${id}`)
    } catch (e) { handleError(e); throw e }
  },

  async setDefault(id: string): Promise<AgentDTO> {
    try {
      const res = await api.post(`/agents/${id}/default`)
      return unwrapData<AgentDTO>(res.data)
    } catch (e) { handleError(e); throw e }
  },

  /**
   * Substitui o conjunto inteiro de bases de conhecimento do agente (máx. 5; lista vazia desvincula).
   *
   * Separate from create/update on purpose: the agent row is already saved by the time this runs, and
   * folding the links into the same call would mean either losing them on a partial save or
   * persisting an agent with dangling links. The server enforces the limit and the ownership check,
   * so the client only reports what comes back.
   */
  async validate(payload: ValidateRequest): Promise<ValidateResponse> {
    try {
      // 20s > 15s do backend de propósito: o backend corta a chamada upstream em 15s e devolve
      // { ok:false, reason:'timeout' }, que a UI traduz. Com timeout de cliente menor, o axios
      // aborta primeiro (net::ERR_ABORTED) e essa orientação nunca chega ao usuário.
      const res = await api.post('/agents/validate', payload, { timeout: 20000 })
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
}

export default agentAdminService
