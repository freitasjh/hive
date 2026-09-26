import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import PrimeVue from 'primevue/config'

vi.mock('../../services/agent-admin.service', () => ({
  agentAdminService: { validate: vi.fn() },
}))
vi.mock('../../services/api', () => ({
  default: { get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn(), interceptors: { request: { use: vi.fn() }, response: { use: vi.fn() } } },
}))

import { agentAdminService } from '../../services/agent-admin.service'
import ConnectionTester from '../ConnectionTester.vue'

function mountTester(props: Record<string, unknown> = {}) {
  return mount(ConnectionTester, {
    props: { provider: 'OPENAI', model: 'gpt-4o-mini', ...props },
    global: { plugins: [PrimeVue] },
  } as never)
}

describe('ConnectionTester', () => {
  beforeEach(() => vi.clearAllMocks())

  it('starts idle with test button and polite live region', () => {
    const w = mountTester()
    expect(w.find('[data-testid="test-btn"]').exists()).toBe(true)
    expect(w.text()).toContain('Testar conexão')
    const live = w.find('[data-testid="test-status"]')
    expect(live.exists()).toBe(true)
    expect(live.attributes('aria-live')).toBe('polite')
  })

  it('shows ok badge on successful validation and emits tested', async () => {
    vi.mocked(agentAdminService.validate).mockResolvedValue({ ok: true, provider: 'OPENAI', model: 'gpt-4o-mini', latencyMs: 120 })
    const w = mountTester()
    await w.find('[data-testid="test-btn"]').trigger('click')
    expect(w.text()).toContain('Testando')
    await flushPromises()
    expect(agentAdminService.validate).toHaveBeenCalledWith({ provider: 'OPENAI', model: 'gpt-4o-mini', baseUrl: undefined })
    expect(w.text()).toContain('Conexão OK')
    expect(w.find('[data-testid="test-status-ok"]').exists()).toBe(true)
    expect(w.emitted('tested')).toBeTruthy()
  })

  it('shows fail warning on ok:false and emits tested', async () => {
    vi.mocked(agentAdminService.validate).mockResolvedValue({ ok: false, provider: 'OPENAI', model: 'gpt-4o-mini', latencyMs: 5000, reason: 'timeout' })
    const w = mountTester()
    await w.find('[data-testid="test-btn"]').trigger('click')
    await flushPromises()
    expect(w.text()).toContain('Falha')
    expect(w.find('[data-testid="test-status-fail"]').exists()).toBe(true)
    expect(w.emitted('tested')![0][0]).toMatchObject({ ok: false })
  })

  it('shows fail on thrown error without leaking details', async () => {
    vi.mocked(agentAdminService.validate).mockRejectedValue({ status: 422, code: 'CONFIG_MISSING' })
    const w = mountTester()
    await w.find('[data-testid="test-btn"]').trigger('click')
    await flushPromises()
    expect(w.find('[data-testid="test-status-fail"]').exists()).toBe(true)
    expect(w.text()).not.toContain('CONFIG_MISSING')
  })

  it('disables button when model is blank', () => {
    const w = mountTester({ model: '' })
    expect((w.find('[data-testid="test-btn"]').element as HTMLButtonElement).disabled).toBe(true)
  })
})
