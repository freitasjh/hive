import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createRouter, createWebHistory } from 'vue-router'
import { createPinia, setActivePinia } from 'pinia'
import PrimeVue from 'primevue/config'
import ToastService from 'primevue/toastservice'

const mockCreate = vi.fn().mockResolvedValue({ id: 'new-id', name: 'Agent B', isDefault: true })
const mockUpdate = vi.fn().mockResolvedValue({ id: 'a1' })
const mockGetDefault = vi.fn().mockResolvedValue({ id: 'a1', name: 'Agent A', isDefault: true })
const mockGetById = vi.fn().mockResolvedValue({ id: 'a1', name: 'Agent A', provider: 'OPENAI', model: 'gpt-4o-mini', isDefault: false, active: true, createAt: new Date().toISOString(), updateAt: new Date().toISOString() })
const mockList = vi.fn().mockResolvedValue({ content: [], totalElements: 0, totalPages: 0, number: 0, size: 20 })

vi.mock('../../../services/prompt.service', () => ({
  promptService: {
    list: vi.fn().mockResolvedValue({ content: [], totalElements: 0, totalPages: 0, number: 0, size: 20 }),
    getById: vi.fn().mockResolvedValue(null),
  }
}))
vi.mock('../../../services/agent-admin.service', () => ({
  agentAdminService: {
    list: (...args: unknown[]) => mockList(...args),
    getById: (...args: unknown[]) => mockGetById(...args),
    create: (...args: unknown[]) => mockCreate(...args),
    update: (...args: unknown[]) => mockUpdate(...args),
    remove: vi.fn().mockResolvedValue(undefined),
    setDefault: vi.fn().mockResolvedValue({ id: 'a1', isDefault: true }),
    getDefault: (...args: unknown[]) => mockGetDefault(...args),
    listCredentials: vi.fn().mockResolvedValue([]),
    listProviderModels: vi.fn().mockResolvedValue({ models: [], filteredOut: [], cachedAt: new Date().toISOString() }),
    validate: vi.fn().mockResolvedValue({ ok: true, provider: 'OPENAI', model: 'gpt-4o-mini', latencyMs: 100 }),
  }
}))
vi.mock('../../../services/api', () => ({
  default: { get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn(), interceptors: { request: { use: vi.fn() }, response: { use: vi.fn() } } }
}))

import AgentEditorView from '../AgentEditorView.vue'
import AgentsView from '../AgentsView.vue'

function createTestRouter(initial = '/agents/new') {
  const router = createRouter({
    history: createWebHistory(),
    routes: [
      { path: '/', component: { template: '<div />' } },
      { path: '/agents', component: AgentsView },
      { path: '/agents/new', component: AgentEditorView },
      { path: '/agents/:id/edit', component: AgentEditorView },
      { path: '/chat', component: { template: '<div>chat</div>' } },
    ]
  })
  router.push(initial)
  return router
}

describe('AgentEditorView dialog confirm POST', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockCreate.mockResolvedValue({ id: 'new-id', name: 'Agent B', isDefault: true })
    mockGetDefault.mockResolvedValue({ id: 'a1', name: 'Agent A', isDefault: true })
    setActivePinia(createPinia())
  })

  it('confirm dialog triggers POST /agents with isDefault true (auto-swap 201)', async () => {
    const router = createTestRouter('/agents/new')
    await router.isReady()
    const wrapper = mount(AgentEditorView, {
      global: {
        plugins: [router, PrimeVue, ToastService],
        stubs: {
          Toast: { template: '<div />' },
          Dialog: { template: '<div><slot /><slot name="footer" /></div>' },
          Card: { template: '<div><slot name="content" /><slot /></div>' },
          Accordion: { template: '<div><slot /></div>' },
          AccordionPanel: { template: '<div><slot /></div>' },
          AccordionHeader: { template: '<div><slot /></div>' },
          AccordionContent: { template: '<div><slot /></div>' },
        }
      }
    } as never)

    await flushPromises()
    await wrapper.vm.$nextTick()

    // set required name
    const nameInput = wrapper.find('[data-testid="input-name"]')
    if (nameInput.exists()) {
      await nameInput.setValue('Agent B')
    } else {
      // fallback: set via vm directly
      ;(wrapper.vm as any).name = 'Agent B'
    }
    await flushPromises()

    // simulate toggle ON when default exists -> should open dialog
    const vm: any = wrapper.vm
    // hasExistingDefault should be true after getDefault resolves
    expect(mockGetDefault).toHaveBeenCalled()
    vm.onDefaultToggle(true)
    await vm.$nextTick()
    expect(vm.showDefaultDialog).toBe(true)

    // confirm should set isDefault true and call create (POST)
    await vm.confirmDefault()
    await flushPromises()

    expect(vm.isDefault).toBe(true)
    expect(mockCreate).toHaveBeenCalledTimes(1)
    const sent = mockCreate.mock.calls[0][0] as Record<string, unknown>
    expect(sent.isDefault).toBe(true)
    expect(sent.name).toBe('Agent B')
  })

  it('cancel dialog keeps isDefault false and does NOT call POST', async () => {
    const router = createTestRouter('/agents/new')
    await router.isReady()
    const wrapper = mount(AgentEditorView, {
      global: {
        plugins: [router, PrimeVue, ToastService],
        stubs: {
          Toast: { template: '<div />' },
          Dialog: { template: '<div><slot /><slot name="footer" /></div>' },
          Card: { template: '<div><slot name="content" /><slot /></div>' },
          Accordion: { template: '<div><slot /></div>' },
          AccordionPanel: { template: '<div><slot /></div>' },
          AccordionHeader: { template: '<div><slot /></div>' },
          AccordionContent: { template: '<div><slot /></div>' },
        }
      }
    } as never)
    await flushPromises()
    const vm: any = wrapper.vm
    vm.onDefaultToggle(true)
    await vm.$nextTick()
    expect(vm.showDefaultDialog).toBe(true)
    vm.cancelDefault()
    await vm.$nextTick()
    expect(vm.isDefault).toBe(false)
    expect(mockCreate).not.toHaveBeenCalled()
  })

  it('when no existing default, toggle sets isDefault directly without dialog', async () => {
    mockGetDefault.mockRejectedValue({ response: { status: 404 } })
    const router = createTestRouter('/agents/new')
    await router.isReady()
    const wrapper = mount(AgentEditorView, {
      global: {
        plugins: [router, PrimeVue, ToastService],
        stubs: {
          Toast: { template: '<div />' },
          Dialog: { template: '<div><slot /><slot name="footer" /></div>' },
          Card: { template: '<div><slot name="content" /><slot /></div>' },
          Accordion: { template: '<div><slot /></div>' },
          AccordionPanel: { template: '<div><slot /></div>' },
          AccordionHeader: { template: '<div><slot /></div>' },
          AccordionContent: { template: '<div><slot /></div>' },
        }
      }
    } as never)
    await flushPromises()
    const vm: any = wrapper.vm
    expect(vm.hasExistingDefault).toBe(false)
    vm.onDefaultToggle(true)
    await vm.$nextTick()
    expect(vm.showDefaultDialog).toBe(false)
    expect(vm.isDefault).toBe(true)
  })
})

describe('AgentsView star action POST /{id}/default', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    setActivePinia(createPinia())
  })
  it('doToggle calls setDefault and reloads', async () => {
    const mockSetDefault = vi.fn().mockResolvedValue({ id: 'a2', isDefault: true })
    vi.mocked(await import('../../../services/agent-admin.service')).agentAdminService.setDefault = mockSetDefault as never
    const router = createTestRouter('/agents')
    await router.isReady()
    const wrapper = mount(AgentsView, {
      global: {
        plugins: [router, PrimeVue, ToastService],
        stubs: {
          DataTable: { template: '<div><slot /></div>' },
          Column: { template: '<div><slot /></div>' },
          Toast: { template: '<div />' },
          Dialog: { template: '<div><slot /><slot name="footer" /></div>' },
        }
      }
    } as never)
    await flushPromises()
    const vm: any = wrapper.vm
    vm.toggleTarget = { id: 'a2', name: 'Agent B', isDefault: false }
    await vm.doToggle()
    await flushPromises()
    expect(mockSetDefault).toHaveBeenCalledWith('a2')
  })
})
