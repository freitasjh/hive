import { describe, it, expect, vi, beforeEach } from 'vitest'

vi.mock('../api', () => ({
  default: {
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
    delete: vi.fn(),
  }
}))

import api from '../api'
import { agentAdminService } from '../agent-admin.service'

describe('agent-admin.service', () => {
  beforeEach(() => vi.clearAllMocks())

  it('list calls GET /agents with page/size/q and unwraps page', async () => {
    const page = { content: [{ id: 'a1', name: 'Bot', provider: 'OPENAI', model: 'gpt-4o-mini', isDefault: false, active: true }], totalElements: 1, totalPages: 1, number: 0, size: 20 }
    vi.mocked(api.get).mockResolvedValue({ data: page } as never)
    const result = await agentAdminService.list({ page: 0, size: 20 })
    expect(api.get).toHaveBeenCalledWith('/agents', { params: { page: 0, size: 20, q: undefined } })
    expect(result.content[0].id).toBe('a1')
  })

  it('getById calls GET /agents/:id and unwraps data wrapper', async () => {
    const dto = { id: 'a1', name: 'Bot', provider: 'OLLAMA', model: 'llama3.2', promptTemplateId: 'p1', isDefault: false, active: true }
    vi.mocked(api.get).mockResolvedValue({ data: { data: dto } } as never)
    const result = await agentAdminService.getById('a1')
    expect(api.get).toHaveBeenCalledWith('/agents/a1')
    expect(result.promptTemplateId).toBe('p1')
  })

  it('create sends promptTemplateId (not promptId) with vendor provider + model', async () => {
    const payload = {
      name: 'Agent 1',
      provider: 'OLLAMA' as const,
      model: 'llama3.2',
      promptTemplateId: 'p-uuid-1',
      temperature: 0.7,
      topP: 0.9,
      maxTokens: 2048,
      isDefault: false,
      active: true,
    }
    const created = { id: 'new', ...payload }
    vi.mocked(api.post).mockResolvedValue({ data: created } as never)
    const result = await agentAdminService.create(payload as never)
    expect(api.post).toHaveBeenCalledWith('/agents', payload)
    const sentPayload = vi.mocked(api.post).mock.calls[0][1] as Record<string, unknown>
    expect(sentPayload).toHaveProperty('promptTemplateId', 'p-uuid-1')
    expect(sentPayload).not.toHaveProperty('promptId')
    expect(sentPayload).not.toHaveProperty('providerType')
    expect(sentPayload.provider).toBe('OLLAMA')
    expect(sentPayload.model).toBe('llama3.2')
    expect(result.id).toBe('new')
  })

  it('create supports every AiProvider vendor with catalog model', async () => {
    const cases = [
      { provider: 'OPENAI', model: 'gpt-4o-mini' },
      { provider: 'GEMINI', model: 'gemini-2.0-flash' },
      { provider: 'ANTHROPIC', model: 'claude-3-5-haiku-latest' },
      { provider: 'OLLAMA', model: 'llama3.2' },
      { provider: 'OPENAI_COMPATIBLE', model: 'mimo-v2.5' },
    ] as const
    for (const c of cases) {
      vi.mocked(api.post).mockResolvedValue({ data: { id: 'x', ...c } } as never)
      const result = await agentAdminService.create({ name: 'A', ...c } as never)
      expect(result.model).toBe(c.model)
    }
    expect(api.post).toHaveBeenCalledTimes(5)
  })

  it('contract rejects legacy framework enums SPRING_AI/LANGCHAIN4J and OLLHAMA/OPENCODE models', async () => {
    const { VENDOR_ORDER } = await import('../../types/agent-admin')
    expect(VENDOR_ORDER).toEqual(['OPENAI', 'GEMINI', 'ANTHROPIC', 'OLLAMA', 'OPENCODE_GO', 'OPENCODE_ZEN', 'OPENAI_COMPATIBLE'])
    expect(VENDOR_ORDER).not.toContain('SPRING_AI')
    expect(VENDOR_ORDER).not.toContain('LANGCHAIN4J')
    expect(VENDOR_ORDER).not.toContain('OLLHAMA')
    expect(VENDOR_ORDER).not.toContain('OPENCODE')
  })

  it('update sends promptTemplateId and keeps null for unlink', async () => {
    const payload = { name: 'Bot Updated', provider: 'GEMINI' as const, model: 'gemini-2.0-flash', promptTemplateId: null }
    vi.mocked(api.put).mockResolvedValue({ data: { id: 'a1', ...payload } } as never)
    await agentAdminService.update('a1', payload as never)
    expect(api.put).toHaveBeenCalledWith('/agents/a1', payload)
    const sent = vi.mocked(api.put).mock.calls[0][1] as Record<string, unknown>
    expect(sent.promptTemplateId).toBeNull()
    expect(sent).not.toHaveProperty('promptId')
  })

  it('remove calls DELETE /agents/:id', async () => {
    vi.mocked(api.delete).mockResolvedValue({} as never)
    await agentAdminService.remove('a1')
    expect(api.delete).toHaveBeenCalledWith('/agents/a1')
  })

  it('setDefault calls POST /agents/:id/default', async () => {
    vi.mocked(api.post).mockResolvedValue({ data: { id: 'a1', isDefault: true } } as never)
    const result = await agentAdminService.setDefault('a1')
    expect(api.post).toHaveBeenCalledWith('/agents/a1/default')
    expect(result.isDefault).toBe(true)
  })

  it('unwrapPage handles wrapper { data: Page }', async () => {
    const page = { content: [{ id: 'a1' }], totalElements: 1, totalPages: 1, number: 0, size: 20 }
    vi.mocked(api.get).mockResolvedValue({ data: { data: page } } as never)
    const result = await agentAdminService.list()
    expect(result.content).toHaveLength(1)
  })

  it('validate posts provider/model to /agents/validate with 20s timeout and unwraps', async () => {
    const response = { ok: true, provider: 'OPENAI', model: 'gpt-4o-mini', latencyMs: 120 }
    vi.mocked(api.post).mockResolvedValue({ data: response } as never)
    const result = await agentAdminService.validate({ provider: 'OPENAI', model: 'gpt-4o-mini' })
    expect(api.post).toHaveBeenCalledWith('/agents/validate', { provider: 'OPENAI', model: 'gpt-4o-mini' }, { timeout: 20000 })
    expect(result.ok).toBe(true)
    expect(result.latencyMs).toBe(120)
  })

  it('validate client timeout is greater than the backend timeout so the backend can answer reason:timeout', async () => {
    // Backend corta em 15s e devolve { ok:false, reason:'timeout' }. Com timeout de cliente
    // menor, o axios aborta primeiro (net::ERR_ABORTED) e a orientação some da tela.
    const response = { ok: false, provider: 'OPENCODE_GO', model: 'mimo-v2.5', latencyMs: 15000, reason: 'timeout' }
    vi.mocked(api.post).mockResolvedValue({ data: response } as never)
    await agentAdminService.validate({ provider: 'OPENCODE_GO', model: 'mimo-v2.5' })
    const config = vi.mocked(api.post).mock.calls[0][2] as { timeout: number }
    expect(config.timeout).toBeGreaterThan(15000)
  })

  it('validate forwards baseUrl for OLLAMA/COMPATIBLE and unwraps data wrapper', async () => {
    const response = { ok: false, provider: 'OLLAMA', model: 'llama3.2', latencyMs: 15000, reason: 'timeout' }
    vi.mocked(api.post).mockResolvedValue({ data: { data: response } } as never)
    const result = await agentAdminService.validate({ provider: 'OLLAMA', model: 'llama3.2', baseUrl: 'http://localhost:11434' })
    expect(api.post).toHaveBeenCalledWith(
      '/agents/validate',
      { provider: 'OLLAMA', model: 'llama3.2', baseUrl: 'http://localhost:11434' },
      { timeout: 20000 },
    )
    expect(result.ok).toBe(false)
    expect(result.reason).toBe('timeout')
  })

  it('validate propagates 422 CONFIG_MISSING as typed ApiError', async () => {
    const err = { response: { status: 422, data: { code: 'CONFIG_MISSING', detail: 'Chave não configurada' } } }
    vi.mocked(api.post).mockRejectedValue(err)
    await expect(agentAdminService.validate({ provider: 'OPENAI', model: 'gpt-4o-mini' })).rejects.toMatchObject({
      status: 422,
      code: 'CONFIG_MISSING',
    })
  })

  it('listCredentials calls GET /provider-credentials', async () => {
    const list = [{ provider: 'OPENAI', configured: true, last4: 'abcd', updatedAt: '2026-01-01T00:00:00' }]
    vi.mocked(api.get).mockResolvedValue({ data: list } as never)
    const result = await agentAdminService.listCredentials()
    expect(api.get).toHaveBeenCalledWith('/provider-credentials')
    expect(result[0]).toMatchObject({ provider: 'OPENAI', configured: true, last4: 'abcd' })
    expect(result[0]).not.toHaveProperty('ciphertext')
    expect(result[0]).not.toHaveProperty('apiKey')
  })

  it('upsertCredential calls PUT /provider-credentials/:provider', async () => {
    const status = { provider: 'GEMINI', configured: true, last4: 'wxyz' }
    vi.mocked(api.put).mockResolvedValue({ data: status } as never)
    const result = await agentAdminService.upsertCredential('GEMINI', { apiKey: 'AIza-secret' })
    expect(api.put).toHaveBeenCalledWith('/provider-credentials/GEMINI', { apiKey: 'AIza-secret' })
    expect(result.configured).toBe(true)
  })

  it('upsertCredential propagates 400 on invalid prefix as typed ApiError', async () => {
    const err = { response: { status: 400, data: { detail: 'Prefixo inválido' } } }
    vi.mocked(api.put).mockRejectedValue(err)
    await expect(agentAdminService.upsertCredential('OPENAI', { apiKey: 'bad' })).rejects.toMatchObject({ status: 400 })
  })

  it('deleteCredential calls DELETE /provider-credentials/:provider', async () => {
    vi.mocked(api.delete).mockResolvedValue({} as never)
    await agentAdminService.deleteCredential('OPENAI')
    expect(api.delete).toHaveBeenCalledWith('/provider-credentials/OPENAI')
  })

  it('listProviderModels calls GET /providers/go/models and unwraps', async () => {
    const payload = { models: ['mimo-v2.5'], filteredOut: [{ id: 'x', reason: 'deny' }], cachedAt: '2026-09-25T00:00:00Z' }
    vi.mocked(api.get).mockResolvedValue({ data: payload } as never)
    const result = await agentAdminService.listProviderModels('OPENCODE_GO')
    expect(api.get).toHaveBeenCalledWith('/providers/go/models', { params: {} })
    expect(result.models).toEqual(['mimo-v2.5'])
    expect(result.cachedAt).toContain('2026-09-25')
  })

  it('listProviderModels maps ZEN slug and sends refresh param', async () => {
    const payload = { models: ['claude-sonnet-4-5'], filteredOut: [], cachedAt: '2026-09-25T00:00:00Z' }
    vi.mocked(api.get).mockResolvedValue({ data: { data: payload } } as never)
    const result = await agentAdminService.listProviderModels('OPENCODE_ZEN', true)
    expect(api.get).toHaveBeenCalledWith('/providers/zen/models', { params: { refresh: true } })
    expect(result.models).toEqual(['claude-sonnet-4-5'])
  })

  it('listProviderModels rejects non-remote providers and propagates 422 as ApiError', async () => {
    await expect(agentAdminService.listProviderModels('OPENAI' as never)).rejects.toMatchObject({ code: 'INVALID_PROVIDER', status: 400 })
    try {
      await agentAdminService.listProviderModels('OPENAI' as never)
      expect.unreachable()
    } catch (e) {
      expect(e).toBeInstanceOf(Error)
      expect((e as Error).message).toContain('Discovery remoto')
    }
    const err = { response: { status: 422, data: { code: 'DISCOVERY_FAILED', detail: 'falha remota' } } }
    vi.mocked(api.get).mockRejectedValue(err)
    await expect(agentAdminService.listProviderModels('OPENCODE_GO')).rejects.toMatchObject({ status: 422, code: 'DISCOVERY_FAILED' })
  })
})
