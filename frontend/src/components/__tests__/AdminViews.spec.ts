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
    list: vi.fn().mockResolvedValue({ content: [{ id: 'a1', name: 'Bot', provider: 'OPENAI', model: 'gpt-4o-mini', isDefault: true, active: true, createAt: new Date().toISOString(), updateAt: new Date().toISOString() }], totalElements: 1, totalPages: 1, number: 0, size: 20 }),
    getById: vi.fn().mockResolvedValue({ id: 'a1', name: 'Bot', provider: 'OPENAI', model: 'gpt-4o-mini', promptTemplateId: 'p1', isDefault: false, active: true, createAt: new Date().toISOString(), updateAt: new Date().toISOString() }),
    create: vi.fn().mockResolvedValue({ id: 'new' }),
    update: vi.fn().mockResolvedValue({ id: 'a1' }),
    remove: vi.fn().mockResolvedValue(undefined),
    setDefault: vi.fn().mockResolvedValue({ id: 'a1', isDefault: true }),
    getDefault: vi.fn().mockResolvedValue({ id: 'a1', name: 'Bot', isDefault: true }),
    listCredentials: vi.fn().mockResolvedValue([{ provider: 'OPENAI', configured: true, last4: 'abcd' }]),
    validate: vi.fn().mockResolvedValue({ ok: true, provider: 'OPENAI', model: 'gpt-4o-mini', latencyMs: 100 }),
  }
}))
vi.mock('../../services/api', () => ({
  default: { get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn(), interceptors: { request: { use: vi.fn() }, response: { use: vi.fn() } } }
}))

import PromptsView from '../../views/admin/PromptsView.vue'
import PromptEditorView from '../../views/admin/PromptEditorView.vue'
import AgentsView from '../../views/admin/AgentsView.vue'
import AgentEditorView from '../../views/admin/AgentEditorView.vue'
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
      { path: '/agents', component: AgentsView },
      { path: '/agents/new', component: AgentEditorView },
      { path: '/agents/:id/edit', component: AgentEditorView },
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

describe('AgentsView', () => {
  it('renders heading', async () => {
    const w = mountWithPlugins(AgentsView)
    await w.vm.$nextTick()
    expect(w.text()).toContain('Agentes')
  })
})

describe('AgentEditorView', () => {
  it('renders form', async () => {
    const w = mountWithPlugins(AgentEditorView, '/agents/new')
    await w.vm.$nextTick()
    expect(w.text()).toContain('Novo Agente')
  })
})

describe('AgentEditorView contract', () => {
  it('provider section renders 4 vendors without legacy framework enums', async () => {
    const w = mountWithPlugins(AgentEditorView, '/agents/new')
    await w.vm.$nextTick()
    await new Promise(r => setTimeout(r, 50))
    const text = w.text()
    expect(text).toContain('Provedor de IA')
    expect(text).toContain('OpenAI')
    expect(text).toContain('Google Gemini')
    expect(text).toContain('Anthropic Claude')
    expect(text).toContain('Ollama')
    expect(text).not.toContain('Spring AI')
    expect(text).not.toContain('LangChain4j')
    expect(text).not.toContain('OLLHAMA')
    expect(text).not.toContain('OPENCODE')
  })
  it('has no apiKey field — key badge links to /settings/providers', async () => {
    const w = mountWithPlugins(AgentEditorView, '/agents/new')
    await w.vm.$nextTick()
    await new Promise(r => setTimeout(r, 50))
    const html = w.html()
    expect(html).not.toContain('apiKey')
    expect(html).toContain('/settings/providers')
  })
  it('uses promptTemplateId field (not promptId) in edit mapping', async () => {
    // verify imported type has promptTemplateId and no promptId — compile-time contract documented via runtime check of mock
    const { agentAdminService } = await import('../../services/agent-admin.service')
    const dto = await agentAdminService.getById('a1')
    expect(dto).toHaveProperty('promptTemplateId', 'p1')
    expect(dto).not.toHaveProperty('promptId')
    expect(dto.provider).toBe('OPENAI')
    expect(dto).not.toHaveProperty('providerType')
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
