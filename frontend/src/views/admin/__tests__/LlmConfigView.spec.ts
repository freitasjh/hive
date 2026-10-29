import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import PrimeVue from 'primevue/config'
import ToastService from 'primevue/toastservice'
import LlmConfigView from '../LlmConfigView.vue'
import { agentAdminService } from '../../../services/agent-admin.service'

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: vi.fn() })
}))

vi.mock('../../../services/agent-admin.service', () => ({
  agentAdminService: {
    getLlmConfig: vi.fn(),
    saveLlmConfig: vi.fn(),
    validate: vi.fn(),
    listProviderModels: vi.fn()
  },
  default: {
    getLlmConfig: vi.fn(),
    saveLlmConfig: vi.fn(),
    validate: vi.fn(),
    listProviderModels: vi.fn()
  }
}))

const push = vi.fn()
vi.mock('vue-router', () => ({
  useRouter: () => ({ push: (...args: unknown[]) => push(...args) })
}))

const saved = {
  id: 'cfg-1',
  tenantId: 't1',
  provider: 'OPENCODE_GO' as const,
  model: 'mimo-v2.5',
  createAt: '2026-09-27T00:00:00Z',
  updateAt: '2026-09-27T00:00:00Z',
}

function mountView() {
  return mount(LlmConfigView, {
    global: { plugins: [PrimeVue, ToastService], stubs: { Card: { template: '<div><slot name="content" /></div>' } } }
  })
}

describe('LlmConfigView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(agentAdminService.getLlmConfig).mockResolvedValue(null)
    vi.mocked(agentAdminService.listProviderModels).mockResolvedValue({
      models: ['mimo-v2.5'],
      filteredOut: [],
      cachedAt: new Date().toISOString(),
    })
  })

  it('says the tenant has nothing configured before any error happens', async () => {
    // A tela que o 422 manda o usuário procurar precisa explicar por que ela existe.
    const w = mountView()
    await flushPromises()
    expect(w.text()).toContain('Provedor e modelo do tenant')
    expect(w.text()).toContain('Nenhum provedor e modelo definidos ainda')
    expect(w.text()).toContain('sem esta configuração o chat recusa')
  })

  it('loads an existing configuration into the form', async () => {
    vi.mocked(agentAdminService.getLlmConfig).mockResolvedValue(saved)
    const w = mountView()
    await flushPromises()
    expect((w.vm as unknown as { provider: string }).provider).toBe('OPENCODE_GO')
    expect((w.vm as unknown as { model: string }).model).toBe('mimo-v2.5')
    expect((w.vm as unknown as { configured: boolean }).configured).toBe(true)
  })

  it('resets the model to the new vendor default when the vendor changes', async () => {
    // O modelo anterior quase nunca está no catálogo do vendor novo, e o servidor rejeitaria com
    // um erro sobre um modelo que o usuário não digitou aqui.
    const w = mountView()
    await flushPromises()
    ;(w.vm as unknown as { provider: string }).provider = 'OPENAI'
    ;(w.vm as unknown as { model: string }).model = 'claude-sonnet-4-5'
    ;(w.vm as unknown as { onProviderChanged: () => void }).onProviderChanged()
    expect((w.vm as unknown as { model: string }).model).toBe('gpt-4o-mini')
  })

  it('saves provider and model only — the tenant is never in the payload', async () => {
    vi.mocked(agentAdminService.saveLlmConfig).mockResolvedValue(saved)
    const w = mountView()
    await flushPromises()
    ;(w.vm as unknown as { provider: string }).provider = 'OPENCODE_GO'
    ;(w.vm as unknown as { model: string }).model = '  mimo-v2.5  '
    await (w.vm as unknown as { save: () => Promise<void> }).save()
    await flushPromises()
    // O tenant vem do principal autenticado; mandá-lo no body seria um campo que o servidor ignora.
    expect(agentAdminService.saveLlmConfig).toHaveBeenCalledWith({ provider: 'OPENCODE_GO', model: 'mimo-v2.5' })
  })

  it('surfaces a rejected provider instead of pretending it saved', async () => {
    vi.mocked(agentAdminService.saveLlmConfig).mockRejectedValue(new Error('Provider SKYNET não aceito'))
    const w = mountView()
    await flushPromises()
    ;(w.vm as unknown as { provider: string }).provider = 'OPENAI'
    ;(w.vm as unknown as { model: string }).model = 'gpt-4o-mini'
    await (w.vm as unknown as { save: () => Promise<void> }).save()
    await flushPromises()
    expect(w.text()).toContain('Provider SKYNET não aceito')
  })

  it('probes the vendor and reports the verdict', async () => {
    vi.mocked(agentAdminService.validate).mockResolvedValue({ ok: true, provider: 'OPENAI', model: 'gpt-4o-mini', latencyMs: 120 })
    const w = mountView()
    await flushPromises()
    ;(w.vm as unknown as { provider: string }).provider = 'OPENAI'
    ;(w.vm as unknown as { model: string }).model = 'gpt-4o-mini'
    await (w.vm as unknown as { testConnection: () => Promise<void> }).testConnection()
    await flushPromises()
    expect(w.text()).toContain('Resposta em 120 ms')
  })

  it('explains a failed probe as a key or model problem, not a generic error', async () => {
    vi.mocked(agentAdminService.validate).mockResolvedValue({ ok: false, provider: 'OPENAI', model: 'gpt-4o-mini', latencyMs: 3000, reason: 'timeout' })
    const w = mountView()
    await flushPromises()
    ;(w.vm as unknown as { provider: string }).provider = 'OPENAI'
    ;(w.vm as unknown as { model: string }).model = 'gpt-4o-mini'
    await (w.vm as unknown as { testConnection: () => Promise<void> }).testConnection()
    await flushPromises()
    expect(w.text()).toContain('timeout')
    expect(w.text()).toContain('A chave pode estar errada')
  })

  it('sends the user to the chat from the configured state', async () => {
    vi.mocked(agentAdminService.getLlmConfig).mockResolvedValue(saved)
    const w = mountView()
    await flushPromises()
    ;(w.vm as unknown as { goToChat: () => void }).goToChat()
    expect(push).toHaveBeenCalledWith('/chat')
  })
})
