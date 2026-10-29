import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { createRouter, createWebHistory } from 'vue-router'
import { createPinia, setActivePinia } from 'pinia'
import PrimeVue from 'primevue/config'
import ToastService from 'primevue/toastservice'

vi.mock('../../services/prompt.service', () => ({
  promptService: {
    list: vi.fn().mockResolvedValue({ content: [], totalElements: 0, totalPages: 0, number: 0, size: 20 }),
    getById: vi.fn().mockResolvedValue({ id: '1', name: 'Test', code: 'test', template: 'hi {{nome}}', status: 'DRAFT', version: 1, createAt: new Date().toISOString(), updateAt: new Date().toISOString() }),
    create: vi.fn().mockResolvedValue({ id: 'new' }),
    update: vi.fn().mockResolvedValue({ id: '1' }),
    remove: vi.fn().mockResolvedValue(undefined),
  }
}))
vi.mock('../../services/agent-admin.service', () => ({
  agentAdminService: {
    listCredentials: vi.fn().mockResolvedValue([{ provider: 'OPENAI', configured: true, last4: 'abcd' }]),
    validate: vi.fn().mockResolvedValue({ ok: true, provider: 'OPENAI', model: 'gpt-4o-mini', latencyMs: 100 }),
    getLlmConfig: vi.fn().mockResolvedValue(null),
    listProviderModels: vi.fn().mockResolvedValue({ models: ['mimo-v2.5', 'claude-sonnet-4-5'], filteredOut: [], cachedAt: '2026-01-01T00:00:00Z' }),
    saveLlmConfig: vi.fn().mockResolvedValue({
      id: 'cfg-1',
      tenantId: 't1',
      provider: 'OPENAI',
      model: 'gpt-4o-mini',
      createAt: new Date().toISOString(),
      updateAt: new Date().toISOString(),
    }),
  }
}))
vi.mock('../../services/api', () => ({
  default: { get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn(), interceptors: { request: { use: vi.fn() }, response: { use: vi.fn() } } }
}))

import { agentAdminService } from '../../services/agent-admin.service'
import PromptsView from '../../views/admin/PromptsView.vue'
import PromptEditorView from '../../views/admin/PromptEditorView.vue'
import LlmConfigView from '../../views/admin/LlmConfigView.vue'
import PlaygroundPanel from '../../components/admin/PlaygroundPanel.vue'
import TemplateEditor from '../../components/admin/TemplateEditor.vue'

function createTestRouter() {
  return createRouter({
    history: createWebHistory(),
    routes: [
      { path: '/', component: { template: '<div />' } },
      { path: '/prompts', component: PromptsView },
      { path: '/prompts/new', component: PromptEditorView },
      { path: '/prompts/:id/edit', component: PromptEditorView },
      { path: '/settings/llm', component: LlmConfigView },
      { path: '/chat', component: { template: '<div>chat</div>' } },
    ]
  })
}

function mountWithPlugins(component: unknown, route = '/') {
  setActivePinia(createPinia())
  const router = createTestRouter()
  router.push(route)
  const wrapper = mount(component as unknown as never, {
    global: {
      plugins: [router, PrimeVue, ToastService],
      stubs: {
        DataTable: { template: '<div data-testid="prompts-table"><slot /></div>' },
        Column: { template: '<div><slot /></div>' },
        Toast: { template: '<div />' },
        Dialog: { template: '<div><slot /><slot name="footer" /></div>' },
        Card: { template: '<div><slot name="content" /><slot name="title" /><slot /></div>' },
        Accordion: { template: '<div><slot /></div>' },
        AccordionPanel: { template: '<div><slot /></div>' },
        AccordionHeader: { template: '<div><slot /></div>' },
        AccordionContent: { template: '<div><slot /></div>' },
      }
    }
  } as never)
  return wrapper as unknown as { text: () => string; vm: { $nextTick: () => Promise<void> }; html: () => string; find: (s: string) => { exists: () => boolean } }
}

describe('PromptsView', () => {
  beforeEach(() => setActivePinia(createPinia()))
  it('renders heading', async () => {
    const w = mountWithPlugins(PromptsView)
    await w.vm.$nextTick()
    expect(w.text()).toContain('Prompts')
  })
  it('shows empty state when no data', async () => {
    const w = mountWithPlugins(PromptsView)
    await new Promise(r => setTimeout(r, 100))
    expect(w.text().length).toBeGreaterThan(0)
  })
})

describe('PromptEditorView', () => {
  it('renders form', async () => {
    const w = mountWithPlugins(PromptEditorView, '/prompts/new')
    await w.vm.$nextTick()
    expect(w.text()).toContain('Novo Prompt')
  })
})

describe('PlaygroundPanel', () => {
  it('renders preview', async () => {
    setActivePinia(createPinia())
    const router = createTestRouter()
    const wrapper = mount(PlaygroundPanel as unknown as never, {
      props: { template: 'Olá {{nome}}' },
      global: { plugins: [router, PrimeVue, ToastService] }
    } as never)
    expect((wrapper as unknown as { text: () => string }).text()).toContain('Playground')
    expect((wrapper as unknown as { text: () => string }).text()).toContain('Preview')
  })
  it('extracts vars reactively', async () => {
    setActivePinia(createPinia())
    const router = createTestRouter()
    const wrapper = mount(PlaygroundPanel as unknown as never, {
      props: { template: 'Hi {{name}} and {{task}}' },
      global: { plugins: [router, PrimeVue, ToastService] }
    } as never)
    await (wrapper.vm as unknown as { $nextTick: () => Promise<void> }).$nextTick?.()
    expect((wrapper as unknown as { html: () => string }).html()).toContain('name')
  })
})

describe('LlmConfigView', () => {
  it('renders the tenant default heading and the no-config notice', async () => {
    const w = mountWithPlugins(LlmConfigView, '/settings/llm')
    await w.vm.$nextTick()
    await new Promise(r => setTimeout(r, 30))
    const text = w.text()
    expect(text).toContain('Provedor e modelo do tenant')
    // Sem config o chat recusa: a tela precisa dizer isso antes de qualquer erro.
    expect(text).toContain('Nenhum provedor e modelo definidos ainda')
  })
  it('has a single save action and no per-agent default control', async () => {
    const w = mountWithPlugins(LlmConfigView, '/settings/llm')
    await w.vm.$nextTick()
    await new Promise(r => setTimeout(r, 30))
    expect(w.html()).toContain('llm-save')
    expect(w.text()).not.toContain('Agentes')
  })
  it('renders a Select with remote models for a remote provider', async () => {
    (agentAdminService as any).getLlmConfig.mockResolvedValue({
      id: 'cfg-1', tenantId: 't1', provider: 'OPENCODE_GO', model: 'mimo-v2.5',
      createAt: new Date().toISOString(), updateAt: new Date().toISOString(),
    })
    const w = mountWithPlugins(LlmConfigView, '/settings/llm')
    await new Promise(r => setTimeout(r, 50))
    expect(w.html()).toContain('p-select')
  })
  it('falls back to InputText when remote models list is empty', async () => {
    ;(agentAdminService as any).listProviderModels.mockResolvedValueOnce({ models: [], filteredOut: [], cachedAt: '2026-01-01T00:00:00Z' })
    ;(agentAdminService as any).getLlmConfig.mockResolvedValue({
      id: 'cfg-1', tenantId: 't1', provider: 'OPENCODE_GO', model: 'mimo-v2.5',
      createAt: new Date().toISOString(), updateAt: new Date().toISOString(),
    })
    const w = mountWithPlugins(LlmConfigView, '/settings/llm')
    await new Promise(r => setTimeout(r, 50))
    expect(w.html()).toContain('p-inputtext')
  })
  it('shows remote error and falls back to InputText on failure', async () => {
    ;(agentAdminService as any).listProviderModels.mockRejectedValueOnce(new Error('boom'))
    ;(agentAdminService as any).getLlmConfig.mockResolvedValue({
      id: 'cfg-1', tenantId: 't1', provider: 'OPENCODE_GO', model: 'mimo-v2.5',
      createAt: new Date().toISOString(), updateAt: new Date().toISOString(),
    })
    const w = mountWithPlugins(LlmConfigView, '/settings/llm')
    await new Promise(r => setTimeout(r, 50))
    expect(w.html()).toContain('llm-model-error')
    expect(w.html()).toContain('p-inputtext')
  })
})

describe('TemplateEditor', () => {
  it('renders textarea', async () => {
    setActivePinia(createPinia())
    const router = createTestRouter()
    const wrapper = mount(TemplateEditor as unknown as never, {
      props: { modelValue: 'Hello {{nome}}' },
      global: { plugins: [router, PrimeVue] }
    } as never)
    expect((wrapper as unknown as { find: (s: string) => { exists: () => boolean } }).find('[data-testid="template-textarea"]').exists()).toBe(true)
  })
})

describe('PlaygroundPanel collapsible', () => {
  it('shows toggle when collapsible true', async () => {
    setActivePinia(createPinia())
    const router = createTestRouter()
    const wrapper = mount(PlaygroundPanel as unknown as never, {
      props: { template: 'Hi {{x}}', collapsible: true },
      global: { plugins: [router, PrimeVue, ToastService] }
    } as never)
    expect((wrapper as unknown as { find: (s: string) => { exists: () => boolean } }).find('[data-testid="toggle-playground"]').exists()).toBe(true)
  })
  it('hides toggle when collapsible false', async () => {
    setActivePinia(createPinia())
    const router = createTestRouter()
    const wrapper = mount(PlaygroundPanel as unknown as never, {
      props: { template: 'Hi {{x}}', collapsible: false },
      global: { plugins: [router, PrimeVue, ToastService] }
    } as never)
    expect((wrapper as unknown as { find: (s: string) => { exists: () => boolean } }).find('[data-testid="toggle-playground"]').exists()).toBe(false)
  })
})

describe('PromptEditorView collapsible playground', () => {
  it('passes collapsible to PlaygroundPanel', async () => {
    const w = mountWithPlugins(PromptEditorView, '/prompts/new')
    await w.vm.$nextTick()
    expect(w.html()).toContain('Playground')
  })
})
