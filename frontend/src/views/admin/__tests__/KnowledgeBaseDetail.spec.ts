import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createRouter, createWebHistory } from 'vue-router'
import { createPinia, setActivePinia } from 'pinia'
import PrimeVue from 'primevue/config'
import ToastService from 'primevue/toastservice'

const mockGetById = vi.fn()
const mockListDocuments = vi.fn()
const mockSearch = vi.fn()
const mockAddUrl = vi.fn()
const mockUpload = vi.fn()
const mockReindex = vi.fn()
const mockListAgents = vi.fn()
const mockListAgentLinks = vi.fn()

vi.mock('../../../services/knowledge.service', () => ({
  knowledgeService: {
    getById: (...args: unknown[]) => mockGetById(...args),
    listDocuments: (...args: unknown[]) => mockListDocuments(...args),
    search: (...args: unknown[]) => mockSearch(...args),
    addUrlDocument: (...args: unknown[]) => mockAddUrl(...args),
    uploadDocument: (...args: unknown[]) => mockUpload(...args),
    reindex: (...args: unknown[]) => mockReindex(...args),
    listAgentLinks: (...args: unknown[]) => mockListAgentLinks(...args),
    patchAgentKnowledge: vi.fn(),
  },
}))
vi.mock('../../../services/agent-admin.service', () => ({
  agentAdminService: {
    list: (...args: unknown[]) => mockListAgents(...args),
  },
}))
vi.mock('../../../services/api', () => ({
  default: { get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn(), interceptors: { request: { use: vi.fn() }, response: { use: vi.fn() } } },
}))

import KnowledgeBaseDetail from '../KnowledgeBaseDetail.vue'

const kb = { id: 'kb1', name: 'Central', slug: 'central', description: 'd', embeddingModel: 'text-embedding-3-small', dimension: 1536, status: 'ACTIVE' }

async function mountView(path = '/knowledge/kb1/edit') {
  setActivePinia(createPinia())
  const router = createRouter({
    history: createWebHistory(),
    routes: [
      { path: '/knowledge/new', component: KnowledgeBaseDetail },
      { path: '/knowledge/:id/edit', component: KnowledgeBaseDetail },
    ],
  })
  await router.push(path)
  await router.isReady()
  return mount(KnowledgeBaseDetail as unknown as object, {
    global: {
      plugins: [router, PrimeVue, ToastService],
      stubs: {
        Toast: { template: '<div />' },
        Dialog: { template: '<div><slot /><slot name="footer" /></div>' },
        FileUpload: { template: '<div><slot name="empty" /></div>' },
      },
    },
  })
}

describe('KnowledgeBaseDetail', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockGetById.mockResolvedValue(kb)
    mockListDocuments.mockResolvedValue([
      { id: 'd1', kbId: 'kb1', sourceType: 'UPLOAD', fileName: 'guia.pdf', status: 'READY', error: null },
      { id: 'd2', kbId: 'kb1', sourceType: 'UPLOAD', fileName: 'scan.pdf', status: 'FAILED', error: 'PDF sem texto extraível' },
    ])
    mockListAgents.mockResolvedValue({ content: [], totalElements: 0, totalPages: 1, number: 0, size: 50 })
    mockSearch.mockResolvedValue([
      { docId: 'd1', docName: 'guia.pdf', chunkId: 'c1', text: 'trecho relevante', score: 0.92 },
      { docId: 'd1', docName: 'guia.pdf', chunkId: 'c2', text: 'outro trecho', score: 0.65 },
    ])
  })

  it('renders three tabs and failed doc with legible error', async () => {
    const w = await mountView()
    await flushPromises()
    const text = w.text()
    expect(text).toContain('Documentos')
    expect(text).toContain('Agentes')
    expect(text).toContain('Testar')
    expect(w.find('[data-testid="tab-documentos"]').exists()).toBe(true)
    expect(w.find('[data-testid="doc-d2"]').exists()).toBe(true)
    expect(w.find('[data-testid="doc-error-d2"]').text()).toContain('PDF sem texto extraível')
  })

  it('shows snapshot and scanned-pdf copy', async () => {
    const w = await mountView()
    await flushPromises()
    const text = w.text()
    expect(text).toContain('snapshot único')
    expect(text).toContain('PDF escaneado (imagem) não suportado')
  })

  it('rejects invalid URL inline', async () => {
    const w = await mountView()
    await flushPromises()
    const input = w.find('[data-testid="input-kb-url"]')
    await input.setValue('notaurl')
    await w.find('[data-testid="add-url-btn"]').trigger('click')
    await w.vm.$nextTick()
    expect(w.find('[data-testid="url-error"]').exists()).toBe(true)
    expect(mockAddUrl).not.toHaveBeenCalled()
  })

  it('playground searches and renders chunk cards with score badges', async () => {
    const w = await mountView()
    await flushPromises()
    await w.find('[data-testid="play-query"]').setValue('como pagar?')
    await w.find('[data-testid="play-search-btn"]').trigger('click')
    await flushPromises()
    expect(mockSearch).toHaveBeenCalledWith('kb1', { query: 'como pagar?', topK: 5 })
    expect(w.find('[data-testid="chunk-c1"]').exists()).toBe(true)
    expect(w.find('[data-testid="chunk-score-c1"]').text()).toContain('0.92')
    expect(w.text()).toContain('guia.pdf')
  })

  it('playground shows empty state when no chunks', async () => {
    mockSearch.mockResolvedValue([])
    const w = await mountView()
    await flushPromises()
    await w.find('[data-testid="play-query"]').setValue('xyz nada')
    await w.find('[data-testid="play-search-btn"]').trigger('click')
    await flushPromises()
    expect(w.find('[data-testid="play-empty"]').exists()).toBe(true)
  })

  it('does not paint a measured nomic hit as noise', async () => {
    // 0.6219 is what a real nomic-embed-text base scores for a question it answers, and 0.60 is that
    // family's cut-off. With a fixed 0.7 ladder this badge came out grey — the same colour as noise.
    mockGetById.mockResolvedValue({ ...kb, embeddingModel: 'nomic-embed-text', dimension: 768 })
    mockSearch.mockResolvedValue([
      { docId: 'd1', docName: 'runbook.pdf', chunkId: 'c1', text: 'reinicio do servico', score: 0.6219 },
    ])
    const w = await mountView()
    await flushPromises()
    await w.find('[data-testid="play-query"]').setValue('como reinicio o servico?')
    await w.find('[data-testid="play-search-btn"]').trigger('click')
    await flushPromises()

    const badge = w.find('[data-testid="chunk-score-c1"]')
    expect(badge.text()).toContain('0.62')
    expect(badge.classes()).not.toContain('p-tag-secondary')
    expect(badge.classes()).toContain('p-tag-warn')
  })
})
