import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createRouter, createWebHistory } from 'vue-router'
import { createPinia, setActivePinia } from 'pinia'
import PrimeVue from 'primevue/config'
import ToastService from 'primevue/toastservice'
import AutoComplete from 'primevue/autocomplete'

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
import { VENDOR_CATALOG } from '../../../types/agent-admin'

/** 26 modelos remotos, mesma ordem e tamanho do catálogo descoberto do OpenCode GO. */
const REMOTE_MODELS = [
  'mimo-v2.5', 'glm-5.2', 'longcat-2.0', 'deepseek-v3.2', 'qwen3-max', 'kimi-k3',
  'kimi-k2.7-code', 'kimi-k2.6', 'gpt-4o-mini', 'gpt-4.1', 'o4-mini', 'claude-sonnet-4-5',
  'claude-3-5-haiku-latest', 'gemini-2.5-pro', 'gemini-2.0-flash', 'llama-4-405b',
  'mistral-large-3', 'grok-4-fast', 'grok-3-mini', 'command-r-plus', 'aya-expanse',
  'jamba-1.6-large', 'nova-pro-v1', 'phi-4-reasoning', 'internlm3-70b', 'yi-large',
]

const stubs = {
  Toast: { template: '<div />' },
  Dialog: { template: '<div><slot /><slot name="footer" /></div>' },
  Card: { template: '<div><slot name="content" /><slot /></div>' },
  Accordion: { template: '<div><slot /></div>' },
  AccordionPanel: { template: '<div><slot /></div>' },
  AccordionHeader: { template: '<div><slot /></div>' },
  AccordionContent: { template: '<div><slot /></div>' },
  ConnectionTester: { template: '<div />' },
  InferenceParamsSliders: { template: '<div />' },
  ProviderSelect: { template: '<div />' },
}

type EditorVm = {
  provider: string
  model: string
  onProviderChange: (p: string) => void
  $nextTick: () => Promise<void>
}

type AutoCompleteWrapper = {
  props: (name: string) => unknown
  vm: Record<string, unknown> & { overlayVisible: boolean; searching: boolean }
  find: (s: string) => { trigger: (e: string) => Promise<void>; element: HTMLInputElement }
  findAll: (s: string) => Array<{ trigger: (e: string) => Promise<void>; element: HTMLInputElement }>
}

async function mountEditor() {
  const router = createRouter({
    history: createWebHistory(),
    routes: [
      { path: '/', component: { template: '<div />' } },
      { path: '/agents/new', component: AgentEditorView },
    ],
  })
  router.push('/agents/new')
  await router.isReady()
  const wrapper = mount(AgentEditorView, {
    global: { plugins: [router, PrimeVue, ToastService], stubs },
  } as never)
  await flushPromises()
  return wrapper as unknown as { vm: EditorVm; $nextTick: () => Promise<void> }
}

/** AutoComplete do campo "Modelo" — desambiguado por inputId, não por posição. */
function modelField(w: Awaited<ReturnType<typeof mountEditor>>): AutoCompleteWrapper {
  const components = (w as unknown as { findAllComponents: (c: unknown) => AutoCompleteWrapper[] }).findAllComponents(AutoComplete)
  const found = components.find((c) => c.props('inputId') === 'agent-model')
  if (!found) throw new Error('AutoComplete do campo Modelo não encontrado')
  return found
}

async function settle(w: Awaited<ReturnType<typeof mountEditor>>) {
  await flushPromises()
  await w.vm.$nextTick()
  await w.vm.$nextTick()
}

describe('AgentEditorView — dropdown de modelo (Bug 1: painel nunca abre)', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    setActivePinia(createPinia())
    mockListProviderModels.mockResolvedValue({
      models: REMOTE_MODELS,
      filteredOut: [],
      cachedAt: new Date().toISOString(),
    })
  })

  async function openRemoteProvider() {
    const w = await mountEditor()
    w.vm.onProviderChange('OPENCODE_GO')
    await settle(w)
    return w
  }

  it('remote: o valor de suggestions que chega ao AutoComplete é o catálogo completo', async () => {
    const w = await openRemoteProvider()
    expect(modelField(w).props('suggestions')).toEqual(REMOTE_MODELS)
  })

  it('remote: clicar no botão do dropdown ABRE o painel', async () => {
    const w = await openRemoteProvider()
    const field = modelField(w)
    expect(field.vm.overlayVisible).toBe(false)

    await field.find('button').trigger('click')
    await settle(w)

    expect(field.vm.overlayVisible).toBe(true)
  })

  it('remote: o painel abre com os 26 modelos e filtra ao digitar', async () => {
    const w = await openRemoteProvider()
    const field = modelField(w)

    await field.find('button').trigger('click')
    await settle(w)
    expect(field.props('suggestions')).toEqual(REMOTE_MODELS)

    // 'g' casa com mimo? não. Com glm/gpt/gemini/grok/yi-large.
    await field.find('input').trigger('input')
    ;(field.find('input').element as HTMLInputElement).value = 'g'
    await field.find('input').trigger('input')
    await new Promise((r) => setTimeout(r, 400))
    await settle(w)

    const filtered = field.props('suggestions') as string[]
    expect(filtered.length).toBeGreaterThan(0)
    expect(filtered.every((m) => m.toLowerCase().includes('g'))).toBe(true)
    expect(filtered).not.toContain('mimo-v2.5')
    expect(field.vm.overlayVisible).toBe(true)
  })

  it('remote: apagar o texto volta ao catálogo completo (filtro não é cumulativo)', async () => {
    const w = await openRemoteProvider()
    const field = modelField(w)
    const input = field.find('input')

    input.element.value = 'mimo'
    await input.trigger('input')
    await new Promise((r) => setTimeout(r, 400))
    await settle(w)
    expect(field.props('suggestions')).toEqual(['mimo-v2.5'])

    input.element.value = ''
    await input.trigger('input')
    await settle(w)
    // query vazio no evento 'input' é ignorado pelo PrimeVue; o catálogo segue íntegro.
    expect(field.props('suggestions')).toEqual(['mimo-v2.5'])

    // A lista volta ao todo quando o evento complete chega com query vazia (botão do dropdown).
    await field.find('button').trigger('click')
    await settle(w)
    expect(field.props('suggestions')).toEqual(REMOTE_MODELS)
  })

  it('local: o catálogo local alimenta o mesmo campo e o painel abre', async () => {
    const w = await mountEditor()
    const field = modelField(w)
    const localCatalog = VENDOR_CATALOG.OPENAI.models

    expect(field.props('suggestions')).toEqual(localCatalog)

    await field.find('button').trigger('click')
    await settle(w)

    expect(field.vm.overlayVisible).toBe(true)
    expect(field.props('suggestions')).toEqual(localCatalog)
  })

  it('local: filtrar por "gpt" mantém só os modelos correspondentes', async () => {
    const w = await mountEditor()
    const field = modelField(w)
    const input = field.find('input')

    input.element.value = 'gpt'
    await input.trigger('input')
    await new Promise((r) => setTimeout(r, 400))
    await settle(w)

    const filtered = field.props('suggestions') as string[]
    expect(filtered.length).toBeGreaterThan(0)
    expect(filtered.every((m) => m.toLowerCase().includes('gpt'))).toBe(true)
    expect(field.vm.overlayVisible).toBe(true)
  })

  it('sem código morto: o catálogo que o watcher alimenta é o mesmo que o filtro escreve', async () => {
    const w = await openRemoteProvider()
    const field = modelField(w)
    const input = field.find('input')

    input.element.value = 'kimi'
    await input.trigger('input')
    await new Promise((r) => setTimeout(r, 400))
    await settle(w)

    // O que o componente exibe tem que ser exatamente o resultado do filtro — se o
    // handler escrevesse numa lista que o template não lê, estas duas linhas divergiriam.
    const shown = field.props('suggestions') as string[]
    const expected = REMOTE_MODELS.filter((m) => m.toLowerCase().includes('kimi'))
    expect(shown).toEqual(expected)
    expect(shown).toEqual(['kimi-k3', 'kimi-k2.7-code', 'kimi-k2.6'])
  })

  it('mimo-v2.5 é selecionável pelo painel (não está nos 3 chips)', async () => {
    const w = await openRemoteProvider()
    const field = modelField(w)
    const input = field.find('input')

    input.element.value = 'mimo'
    await input.trigger('input')
    await new Promise((r) => setTimeout(r, 400))
    await settle(w)

    expect(field.props('suggestions')).toEqual(['mimo-v2.5'])
  })
})
