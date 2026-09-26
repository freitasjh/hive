import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import PrimeVue from 'primevue/config'

const mockList = vi.fn()

vi.mock('../../services/knowledge.service', () => ({
  knowledgeService: {
    list: (...args: unknown[]) => mockList(...args),
  },
}))
vi.mock('../../services/api', () => ({
  default: { get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn(), interceptors: { request: { use: vi.fn() }, response: { use: vi.fn() } } },
}))

import KbPicker from '../admin/KbPicker.vue'

const seed = [
  { id: 'kb1', name: 'Ajuda', slug: 'ajuda', embeddingModel: 'text-embedding-3-small', dimension: 1536, status: 'ACTIVE' },
  { id: 'kb2', name: 'Local', slug: 'local', embeddingModel: 'nomic-embed-text', dimension: 768, status: 'ACTIVE' },
  { id: 'kb3', name: 'Arq', slug: 'arq', embeddingModel: 'x', dimension: 1536, status: 'ARCHIVED' },
]

function mountPicker(props: Record<string, unknown> = {}) {
  setActivePinia(createPinia())
  return mount(KbPicker as unknown as object, {
    props: { modelValue: [], ...props },
    global: { plugins: [PrimeVue] },
  })
}

describe('KbPicker', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockList.mockResolvedValue({ content: seed, totalElements: 3, totalPages: 1, number: 0, size: 50 })
  })

  it('shows counter "x de 5"', async () => {
    const w = mountPicker({ modelValue: ['kb1'] })
    await flushPromises()
    expect(w.find('[data-testid="kb-picker-count"]').text()).toContain('1 de 5')
  })

  it('only lists ACTIVE bases', async () => {
    const w = mountPicker()
    await flushPromises()
    expect(w.text()).not.toContain('Arq')
  })

  it('blocks mixed dimensions with PT-BR message', async () => {
    const w = mountPicker({ modelValue: ['kb1', 'kb2'] })
    await flushPromises()
    const msg = w.find('[data-testid="kb-picker-dimension-error"]')
    expect(msg.exists()).toBe(true)
    expect(msg.text()).toContain('dimensões diferentes')
  })

  it('no dimension warning for same-dimension selection', async () => {
    const w = mountPicker({ modelValue: ['kb1'] })
    await flushPromises()
    expect(w.find('[data-testid="kb-picker-dimension-error"]').exists()).toBe(false)
  })
})
