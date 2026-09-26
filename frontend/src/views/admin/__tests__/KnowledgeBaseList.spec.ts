import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createRouter, createWebHistory } from 'vue-router'
import { createPinia, setActivePinia } from 'pinia'
import PrimeVue from 'primevue/config'
import ToastService from 'primevue/toastservice'
import Tooltip from 'primevue/tooltip'

const mockList = vi.fn()
const mockReindex = vi.fn()
const mockRemove = vi.fn()

vi.mock('../../../services/knowledge.service', () => ({
  knowledgeService: {
    list: (...args: unknown[]) => mockList(...args),
    reindex: (...args: unknown[]) => mockReindex(...args),
    remove: (...args: unknown[]) => mockRemove(...args),
  },
}))
vi.mock('../../../services/api', () => ({
  default: { get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn(), interceptors: { request: { use: vi.fn() }, response: { use: vi.fn() } } },
}))

import KnowledgeBaseList from '../KnowledgeBaseList.vue'

const seed = [
  { id: 'kb1', name: 'Central de Ajuda', slug: 'central', description: 'docs', embeddingModel: 'text-embedding-3-small', dimension: 1536, status: 'ACTIVE' },
  { id: 'kb2', name: 'Base Local', slug: 'local', description: null, embeddingModel: 'nomic-embed-text', dimension: 768, status: 'ACTIVE' },
]

function mountView() {
  setActivePinia(createPinia())
  const router = createRouter({
    history: createWebHistory(),
    routes: [{ path: '/', component: { template: '<div />' } }],
  })
  return mount(KnowledgeBaseList as unknown as object, {
    global: {
      plugins: [router, PrimeVue, ToastService],
      directives: { tooltip: Tooltip },
      stubs: { Toast: { template: '<div />' }, Dialog: { template: '<div><slot /><slot name="footer" /></div>' } },
    },
  })
}

describe('KnowledgeBaseList', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockList.mockResolvedValue({ content: seed, totalElements: 2, totalPages: 1, number: 0, size: 20 })
    mockReindex.mockResolvedValue(undefined)
    mockRemove.mockResolvedValue(undefined)
  })

  it('renders cards with status and model info', async () => {
    const w = mountView()
    await flushPromises()
    expect(mockList).toHaveBeenCalled()
    const text = w.text()
    expect(text).toContain('Central de Ajuda')
    expect(text).toContain('1536d')
    expect(w.find('[data-testid="kb-card-kb1"]').exists()).toBe(true)
    expect(w.find('[data-testid="new-kb-btn"]').exists()).toBe(true)
  })

  it('shows skeleton while loading and empty state when no items', async () => {
    mockList.mockImplementation(() => new Promise(() => {}))
    const w = mountView()
    await w.vm.$nextTick()
    expect(w.find('[data-testid="kb-skeleton"]').exists()).toBe(true)

    mockList.mockResolvedValue({ content: [], totalElements: 0, totalPages: 1, number: 0, size: 20 })
    const w2 = mountView()
    await flushPromises()
    expect(w2.text()).toContain('Nenhuma base de conhecimento encontrada')
  })

  it('reindex action calls service', async () => {
    const w = mountView()
    await flushPromises()
    await w.find('[data-testid="reindex-kb1"]').trigger('click')
    await flushPromises()
    expect(mockReindex).toHaveBeenCalledWith('kb1')
  })

  it('delete with 409 KB_IN_USE shows agents in use and force option', async () => {
    mockRemove.mockRejectedValueOnce({
      response: { status: 409, data: { code: 'KB_IN_USE', agents: ['Suporte', 'Vendas'] } },
    })
    const w = mountView()
    await flushPromises()
    await w.find('[data-testid="delete-kb1"]').trigger('click')
    await w.vm.$nextTick()
    await w.find('[data-testid="confirm-delete"]').trigger('click')
    await flushPromises()
    expect(mockRemove).toHaveBeenCalledWith('kb1', false)
    expect(w.find('[data-testid="confirm-delete-force"]').exists()).toBe(true)
    expect(w.text()).toContain('Suporte')
  })
})
