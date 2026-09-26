import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createRouter, createWebHistory } from 'vue-router'
import { createPinia, setActivePinia } from 'pinia'
import PrimeVue from 'primevue/config'
import ToastService from 'primevue/toastservice'

const mockLinkKnowledgeBases = vi.fn()
const mockUpdate = vi.fn()
const mockCreate = vi.fn()
const mockListAgentLinks = vi.fn()
const mockToastAdd = vi.fn()

// useToast() injects a PrimeVue symbol and can only run inside setup, so the toast the component
// receives is replaced at the module boundary. The ToastService plugin stays installed for the
// template.
vi.mock('primevue/usetoast', () => ({
  useToast: () => ({ add: (...args: unknown[]) => mockToastAdd(...args) }),
}))

vi.mock('../../../services/prompt.service', () => ({
  promptService: {
    list: vi.fn().mockResolvedValue({ content: [], totalElements: 0, totalPages: 0, number: 0, size: 20 }),
    getById: vi.fn().mockResolvedValue(null),
  },
}))
vi.mock('../../../services/knowledge.service', () => ({
  knowledgeService: {
    list: vi.fn().mockResolvedValue({ content: [], totalElements: 0, totalPages: 0, number: 0, size: 20 }),
    listAgentLinks: (...args: unknown[]) => mockListAgentLinks(...args),
  },
}))
vi.mock('../../../services/agent-admin.service', () => ({
  agentAdminService: {
    list: vi.fn().mockResolvedValue({ content: [], totalElements: 0, totalPages: 0, number: 0, size: 20 }),
    getById: vi.fn(),
    create: (...args: unknown[]) => mockCreate(...args),
    update: (...args: unknown[]) => mockUpdate(...args),
    remove: vi.fn(),
    setDefault: vi.fn(),
    linkKnowledgeBases: (...args: unknown[]) => mockLinkKnowledgeBases(...args),
    getDefault: vi.fn().mockRejectedValue({ response: { status: 404 } }),
    listCredentials: vi.fn().mockResolvedValue([]),
    validate: vi.fn().mockResolvedValue({ ok: true, provider: 'OPENCODE_GO', model: 'mimo-v2.5', latencyMs: 100 }),
    listProviderModels: vi.fn().mockResolvedValue({ models: ['mimo-v2.5'], filteredOut: [] }),
  },
}))
vi.mock('../../../services/api', () => ({
  default: { get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn(), interceptors: { request: { use: vi.fn() }, response: { use: vi.fn() } } },
}))

import AgentEditorView from '../AgentEditorView.vue'

const stubs = {
  Toast: { template: '<div />' },
  Dialog: { template: '<div><slot /><slot name="footer" /></div>' },
  Card: { template: '<div><slot name="content" /><slot /></div>' },
  Accordion: { template: '<div><slot /></div>' },
  AccordionPanel: { template: '<div><slot /></div>' },
  AccordionHeader: { template: '<div><slot /></div>' },
  AccordionContent: { template: '<div><slot /></div>' },
  KbPicker: {
    props: ['modelValue'],
    emits: ['update:modelValue'],
    template: '<div data-testid="kb-picker-stub" />',
  },
}

type EditorVm = {
  name: string
  provider: string
  model: string
  linkedKbIds: string[]
  save: () => Promise<void>
  $nextTick: () => Promise<void>
}

function createTestRouter(path = '/agents/new', agentId?: string) {
  const router = createRouter({
    history: createWebHistory(),
    routes: [
      { path: '/', component: { template: '<div />' } },
      { path: '/agents/new', component: AgentEditorView },
      { path: `/agents/${agentId ?? ':id'}`, component: AgentEditorView },
      { path: '/agents', component: { template: '<div />' } },
    ],
  })
  router.push(path)
  return router
}

async function mountEditor(path = '/agents/new') {
  const router = createTestRouter(path)
  await router.isReady()
  const wrapper = mount(AgentEditorView, {
    global: { plugins: [router, PrimeVue, ToastService], stubs },
  } as never)
  await flushPromises()
  await (wrapper.vm as unknown as { $nextTick: () => Promise<void> }).$nextTick()
  return wrapper as unknown as { vm: EditorVm }
}

describe('AgentEditorView — vínculo de bases de conhecimento', () => {
  const AGENT_ID = 'aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee'

  beforeEach(() => {
    vi.clearAllMocks()
    setActivePinia(createPinia())
    mockListAgentLinks.mockResolvedValue([])
    mockLinkKnowledgeBases.mockResolvedValue({ agentId: AGENT_ID, kbIds: [], kbNames: [] })
    mockUpdate.mockResolvedValue({ id: AGENT_ID })
    mockCreate.mockResolvedValue({ id: AGENT_ID })
  })

  it('envia os ids do KbPicker para o endpoint de vínculo ao salvar', async () => {
    const w = await mountEditor()
    w.vm.name = 'Agente RAG'
    w.vm.provider = 'OPENCODE_GO'
    w.vm.model = 'mimo-v2.5'
    w.vm.linkedKbIds = ['3b51cdb5-6912-4c7a-9de8-56be879db65b']

    await w.vm.save()
    await flushPromises()

    expect(mockCreate).toHaveBeenCalledTimes(1)
    expect(mockLinkKnowledgeBases).toHaveBeenCalledWith(AGENT_ID, ['3b51cdb5-6912-4c7a-9de8-56be879db65b'])
  })

  it('mantém knowledgeBases nulo no payload do agente e usa o endpoint separado', async () => {
    const w = await mountEditor()
    w.vm.name = 'Agente RAG'
    w.vm.provider = 'OPENCODE_GO'
    w.vm.model = 'mimo-v2.5'
    w.vm.linkedKbIds = ['kb-1']

    await w.vm.save()
    await flushPromises()

    // O campo legado continua sendo ignorado pelo servidor; a escrita real é o PUT.
    expect(mockCreate.mock.calls[0][0]).toMatchObject({ knowledgeBases: null })
  })

  it('desvincula tudo quando nenhuma base está selecionada e o agente já existia', async () => {
    const w = await mountEditor(`/agents/${AGENT_ID}`)
    w.vm.name = 'Agente RAG'
    w.vm.provider = 'OPENCODE_GO'
    w.vm.model = 'mimo-v2.5'
    w.vm.linkedKbIds = []

    await w.vm.save()
    await flushPromises()

    expect(mockLinkKnowledgeBases).toHaveBeenCalledWith(AGENT_ID, [])
  })

  it('mostra aviso de vínculo e não navega quando a escrita falha, sem perder o agente salvo', async () => {
    mockLinkKnowledgeBases.mockRejectedValue({
      response: { status: 400, data: { code: 'KB_TOO_MANY_LINKS', detail: 'Máximo de 5 bases de conhecimento por agente.' } },
    })
    const router = createTestRouter()
    await router.isReady()
    const wrapper = mount(AgentEditorView, {
      global: { plugins: [router, PrimeVue, ToastService], stubs },
    } as never)
    await flushPromises()
    const w = wrapper as unknown as { vm: EditorVm }
    w.vm.name = 'Agente RAG'
    w.vm.provider = 'OPENCODE_GO'
    w.vm.model = 'mimo-v2.5'
    w.vm.linkedKbIds = ['kb-1', 'kb-2']

    await w.vm.save()
    await flushPromises()

    // O agente foi criado: o aviso tem que dizer isso, senão o usuário refaz o cadastro inteiro.
    expect(mockCreate).toHaveBeenCalledTimes(1)
    expect(mockToastAdd).toHaveBeenCalledWith(
      expect.objectContaining({ severity: 'warn', detail: 'Máximo de 5 bases de conhecimento por agente.' }),
    )
    // A tela fica aberta para o usuário reaplicar a seleção sem perder o que já salvou.
    expect(router.currentRoute.value.path).not.toBe('/agents')
  })
})