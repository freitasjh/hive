import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createRouter, createWebHistory } from 'vue-router'
import { createPinia, setActivePinia } from 'pinia'
import PrimeVue from 'primevue/config'
import ToastService from 'primevue/toastservice'

const mockListProviderModels = vi.fn()

vi.mock('../../../services/prompt.service', () => ({
  promptService: {
    list: vi.fn().mockResolvedValue({ content: [], totalElements: 0, totalPages: 0, number: 0, size: 20 }),
    getById: vi.fn().mockResolvedValue(null),
  },
}))
vi.mock('../../../services/knowledge.service', () => ({
  knowledgeService: {
    listAgentLinks: vi.fn().mockResolvedValue([]),
    patchAgentKnowledge: vi.fn().mockResolvedValue(undefined),
  },
}))
vi.mock('../../../services/agent-admin.service', () => ({
  agentAdminService: {
    list: vi.fn().mockResolvedValue({ content: [], totalElements: 0, totalPages: 0, number: 0, size: 20 }),
    getById: vi.fn(),
    create: vi.fn(),
    update: vi.fn(),
    remove: vi.fn(),
    setDefault: vi.fn(),
    getDefault: vi.fn().mockRejectedValue({ response: { status: 404 } }),
    listCredentials: vi.fn().mockResolvedValue([]),
    validate: vi.fn().mockResolvedValue({ ok: true, provider: 'OPENCODE_GO', model: 'mimo-v2.5', latencyMs: 100 }),
    listProviderModels: (...args: unknown[]) => mockListProviderModels(...args),
  },
}))
vi.mock('../../../services/api', () => ({
  default: { get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn(), interceptors: { request: { use: vi.fn() }, response: { use: vi.fn() } } },
}))

import AgentEditorView from '../AgentEditorView.vue'

function createTestRouter(initial = '/agents/new') {
  const router = createRouter({
    history: createWebHistory(),
    routes: [
      { path: '/', component: { template: '<div />' } },
      { path: '/agents/new', component: AgentEditorView },
    ],
  })
  router.push(initial)
  return router
}

const stubs = {
  Toast: { template: '<div />' },
  Dialog: { template: '<div><slot /><slot name="footer" /></div>' },
  Card: { template: '<div><slot name="content" /><slot /></div>' },
  Accordion: { template: '<div><slot /></div>' },
  AccordionPanel: { template: '<div><slot /></div>' },
  AccordionHeader: { template: '<div><slot /></div>' },
  AccordionContent: { template: '<div><slot /></div>' },
}

describe('AgentEditorView GO/ZEN', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    setActivePinia(createPinia())
    mockListProviderModels.mockResolvedValue({
      models: ['mimo-v2.5'],
      filteredOut: [{ id: 'grok-x', reason: 'denylist' }],
      cachedAt: new Date().toISOString(),
    })
  })

  async function mountEditor() {
    const router = createTestRouter()
    await router.isReady()
    const wrapper = mount(AgentEditorView, {
      global: { plugins: [router, PrimeVue, ToastService], stubs },
    } as never)
    await flushPromises()
    await (wrapper.vm as unknown as { $nextTick: () => Promise<void> }).$nextTick()
    return wrapper as unknown as {
      vm: Record<string, never> & {
        provider: string
        model: string
        modelTouched: boolean
        onProviderChange: (p: string) => void
        $nextTick: () => Promise<void>
      }
      text: () => string
      find: (s: string) => { exists: () => boolean }
    }
  }

  it('shows LGPD banner and read-only baseUrl when GO selected', async () => {
    const w = await mountEditor()
    w.vm.onProviderChange('OPENCODE_GO')
    await w.vm.$nextTick()
    await flushPromises()
    expect(mockListProviderModels).toHaveBeenCalledWith('OPENCODE_GO', false)
    expect(w.find('[data-testid="lgpd-banner"]').exists()).toBe(true)
    expect(w.find('[data-testid="input-base-url-readonly"]').exists()).toBe(true)
    expect(w.text()).toContain('https://opencode.ai/privacy')
    expect(w.find('[data-testid="kb-external-note"]').exists()).toBe(true)
  })

  it('shows off-catalog badge for stale model without blocking save', async () => {
    const w = await mountEditor()
    w.vm.onProviderChange('OPENCODE_GO')
    await w.vm.$nextTick()
    await flushPromises()
    w.vm.model = 'modelo-removido-xyz'
    w.vm.modelTouched = true
    await w.vm.$nextTick()
    expect(w.find('[data-testid="model-offcatalog-badge"]').exists()).toBe(true)
    expect(w.find('[data-testid="save-btn"]').exists()).toBe(true)
  })

  it('shows stale badge with refresh after 5min', async () => {
    mockListProviderModels.mockResolvedValue({
      models: ['mimo-v2.5'],
      filteredOut: [],
      cachedAt: new Date(Date.now() - 6 * 60 * 1000).toISOString(),
    })
    const w = await mountEditor()
    w.vm.onProviderChange('OPENCODE_ZEN')
    await w.vm.$nextTick()
    await flushPromises()
    expect(w.find('[data-testid="models-stale"]').exists()).toBe(true)
    expect(w.find('[data-testid="models-refresh-btn"]').exists()).toBe(true)
    expect(w.text()).toContain('há 6min')
  })

  it('shows error with retry when discovery fails', async () => {
    mockListProviderModels.mockRejectedValue({
      response: { status: 422, data: { code: 'DISCOVERY_FAILED', detail: 'falha remota' } },
    })
    const w = await mountEditor()
    w.vm.onProviderChange('OPENCODE_GO')
    await w.vm.$nextTick()
    await flushPromises()
    expect(w.find('[data-testid="models-error"]').exists()).toBe(true)
    expect(w.find('[data-testid="models-retry-btn"]').exists()).toBe(true)
  })
})
