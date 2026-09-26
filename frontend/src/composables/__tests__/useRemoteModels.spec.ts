import { describe, it, expect, vi, beforeEach } from 'vitest'
import { ref } from 'vue'
import { flushPromises } from '@vue/test-utils'

vi.mock('../../services/agent-admin.service', () => ({
  agentAdminService: { listProviderModels: vi.fn() },
}))
vi.mock('../../services/api', () => ({
  default: { get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn(), interceptors: { request: { use: vi.fn() }, response: { use: vi.fn() } } },
}))

import { agentAdminService } from '../../services/agent-admin.service'
import { useRemoteModels, isRemoteProvider } from '../useRemoteModels'
import type { AiProvider } from '../../types/agent-admin'

describe('useRemoteModels', () => {
  beforeEach(() => vi.clearAllMocks())

  it('fetches remote models for GO on mount', async () => {
    vi.mocked(agentAdminService.listProviderModels).mockResolvedValue({
      models: ['mimo-v2.5'],
      filteredOut: [],
      cachedAt: new Date().toISOString(),
    })
    const provider = ref<AiProvider>('OPENCODE_GO')
    const r = useRemoteModels(provider)
    await flushPromises()
    expect(agentAdminService.listProviderModels).toHaveBeenCalledWith('OPENCODE_GO', false)
    expect(r.models.value).toEqual(['mimo-v2.5'])
    expect(r.loading.value).toBe(false)
    expect(r.error.value).toBeNull()
  })

  it('falls back to catalog on remote error and exposes retry', async () => {
    vi.mocked(agentAdminService.listProviderModels).mockRejectedValueOnce({
      response: { status: 422, data: { code: 'DISCOVERY_FAILED', detail: 'falha remota' } },
    })
    const provider = ref<AiProvider>('OPENCODE_ZEN')
    const r = useRemoteModels(provider)
    await flushPromises()
    expect(r.error.value).toContain('falha remota')
    expect(r.models.value).toContain('claude-sonnet-4-5')
    vi.mocked(agentAdminService.listProviderModels).mockResolvedValue({
      models: ['claude-sonnet-4-5'],
      filteredOut: [],
      cachedAt: new Date().toISOString(),
    })
    await r.refresh()
    expect(r.error.value).toBeNull()
    expect(r.models.value).toEqual(['claude-sonnet-4-5'])
  })

  it('marks stale after 5min and refresh forces reload', async () => {
    const old = new Date(Date.now() - 6 * 60 * 1000).toISOString()
    vi.mocked(agentAdminService.listProviderModels).mockResolvedValue({ models: ['mimo-v2.5'], filteredOut: [], cachedAt: old })
    const provider = ref<AiProvider>('OPENCODE_GO')
    const r = useRemoteModels(provider)
    await flushPromises()
    expect(r.stale.value).toBe(true)
    expect(r.staleMinutes.value).toBeGreaterThanOrEqual(5)
    await r.refresh()
    expect(agentAdminService.listProviderModels).toHaveBeenLastCalledWith('OPENCODE_GO', true)
  })

  it('refetches on provider switch GO -> ZEN -> OPENAI', async () => {
    vi.mocked(agentAdminService.listProviderModels).mockResolvedValue({
      models: ['mimo-v2.5'],
      filteredOut: [],
      cachedAt: new Date().toISOString(),
    })
    const provider = ref<AiProvider>('OPENCODE_GO')
    const r = useRemoteModels(provider)
    await flushPromises()
    expect(agentAdminService.listProviderModels).toHaveBeenCalledWith('OPENCODE_GO', false)
    vi.mocked(agentAdminService.listProviderModels).mockResolvedValue({
      models: ['claude-sonnet-4-5'],
      filteredOut: [],
      cachedAt: new Date().toISOString(),
    })
    provider.value = 'OPENCODE_ZEN'
    await flushPromises()
    expect(agentAdminService.listProviderModels).toHaveBeenLastCalledWith('OPENCODE_ZEN', false)
    expect(r.models.value).toEqual(['claude-sonnet-4-5'])
    provider.value = 'OPENAI'
    await flushPromises()
    expect(r.isRemote.value).toBe(false)
    expect(r.models.value).toContain('gpt-4o-mini')
    expect(r.error.value).toBeNull()
  })

  it('uses static catalog for non-remote providers without fetching', async () => {
    const provider = ref<AiProvider>('OPENAI')
    const r = useRemoteModels(provider)
    await flushPromises()
    expect(agentAdminService.listProviderModels).not.toHaveBeenCalled()
    expect(r.models.value).toContain('gpt-4o-mini')
    expect(r.isRemote.value).toBe(false)
    expect(isRemoteProvider('OPENAI')).toBe(false)
    expect(isRemoteProvider('OPENCODE_GO')).toBe(true)
  })
})
