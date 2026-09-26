import { describe, it, expect, vi, beforeEach } from 'vitest'

vi.mock('../api', () => ({
  default: {
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
    patch: vi.fn(),
    delete: vi.fn(),
  },
}))

import api from '../api'
import { knowledgeService } from '../knowledge.service'

function httpError(status: number, code = 'ERR', detail = 'erro') {
  return { response: { status, data: { code, detail } } }
}

describe('knowledge.service', () => {
  beforeEach(() => vi.clearAllMocks())

  it('list calls GET /knowledge-bases with page/size/q', async () => {
    const page = { content: [{ id: 'kb1', name: 'KB' }], totalElements: 1, totalPages: 1, number: 0, size: 20 }
    vi.mocked(api.get).mockResolvedValue({ data: page } as never)
    const result = await knowledgeService.list({ page: 0, size: 20, q: 'fin' })
    expect(api.get).toHaveBeenCalledWith('/knowledge-bases', { params: { page: 0, size: 20, q: 'fin' } })
    expect(result.content).toHaveLength(1)
  })

  it('getById calls GET /knowledge-bases/:id and unwraps success wrapper', async () => {
    const kb = { id: 'kb1', name: 'KB', dimension: 1536, status: 'ACTIVE' }
    vi.mocked(api.get).mockResolvedValue({ data: { success: true, data: kb } } as never)
    const result = await knowledgeService.getById('kb1')
    expect(api.get).toHaveBeenCalledWith('/knowledge-bases/kb1')
    expect(result).toEqual(kb)
  })

  it('create calls POST /knowledge-bases', async () => {
    const payload = { name: 'Nova Base', description: 'docs' }
    vi.mocked(api.post).mockResolvedValue({ data: { id: 'kb1', ...payload } } as never)
    const result = await knowledgeService.create(payload)
    expect(api.post).toHaveBeenCalledWith('/knowledge-bases', payload)
    expect(result.id).toBe('kb1')
  })

  it('update calls PUT /knowledge-bases/:id', async () => {
    vi.mocked(api.put).mockResolvedValue({ data: { id: 'kb1', name: 'Novo nome' } } as never)
    await knowledgeService.update('kb1', { name: 'Novo nome' })
    expect(api.put).toHaveBeenCalledWith('/knowledge-bases/kb1', { name: 'Novo nome' })
  })

  it('remove calls DELETE and forwards force flag', async () => {
    vi.mocked(api.delete).mockResolvedValue({} as never)
    await knowledgeService.remove('kb1', true)
    expect(api.delete).toHaveBeenCalledWith('/knowledge-bases/kb1', { params: { force: true } })
  })

  it('uploadDocument posts multipart FormData to :upload', async () => {
    const file = new File(['conteudo'], 'doc.pdf', { type: 'application/pdf' })
    const doc = { id: 'd1', status: 'PENDING' }
    vi.mocked(api.post).mockResolvedValue({ data: doc } as never)
    const result = await knowledgeService.uploadDocument('kb1', file)
    expect(api.post).toHaveBeenCalledWith(
      '/knowledge-bases/kb1/documents:upload',
      expect.any(FormData),
      expect.objectContaining({ headers: { 'Content-Type': 'multipart/form-data' } }),
    )
    expect(result).toEqual(doc)
  })

  it('addUrlDocument posts url to :url', async () => {
    vi.mocked(api.post).mockResolvedValue({ data: { id: 'd2', status: 'PENDING' } } as never)
    await knowledgeService.addUrlDocument('kb1', 'https://example.com/guia')
    expect(api.post).toHaveBeenCalledWith('/knowledge-bases/kb1/documents:url', { url: 'https://example.com/guia' })
  })

  it('listDocuments calls GET documents', async () => {
    vi.mocked(api.get).mockResolvedValue({ data: [{ id: 'd1' }] } as never)
    const result = await knowledgeService.listDocuments('kb1')
    expect(api.get).toHaveBeenCalledWith('/knowledge-bases/kb1/documents')
    expect(result).toHaveLength(1)
  })

  it('reindex calls POST reindex', async () => {
    vi.mocked(api.post).mockResolvedValue({} as never)
    await knowledgeService.reindex('kb1')
    expect(api.post).toHaveBeenCalledWith('/knowledge-bases/kb1/reindex')
  })

  it('search posts query/topK and returns chunks', async () => {
    const chunks = [{ docId: 'd1', docName: 'guia.pdf', chunkId: 'c1', text: 'trecho', score: 0.92 }]
    vi.mocked(api.post).mockResolvedValue({ data: { success: true, data: chunks } } as never)
    const result = await knowledgeService.search('kb1', { query: 'como pagar?', topK: 5 })
    expect(api.post).toHaveBeenCalledWith('/knowledge-bases/kb1/search', { query: 'como pagar?', topK: 5 })
    expect(result[0].score).toBe(0.92)
  })

  it('patchAgentKnowledge calls PATCH /agents/:id with knowledgeBaseIds', async () => {
    vi.mocked(api.patch).mockResolvedValue({ data: {} } as never)
    await knowledgeService.patchAgentKnowledge('ag1', ['kb1', 'kb2'])
    expect(api.patch).toHaveBeenCalledWith('/agents/ag1', { knowledgeBaseIds: ['kb1', 'kb2'] })
  })

  it('listAgentLinks calls GET /agents/:id/knowledge-bases', async () => {
    vi.mocked(api.get).mockResolvedValue({ data: { success: true, data: ['kb1'] } } as never)
    const result = await knowledgeService.listAgentLinks('ag1')
    expect(api.get).toHaveBeenCalledWith('/agents/ag1/knowledge-bases')
    expect(result).toEqual(['kb1'])
  })

  it('propagates 401 errors to caller', async () => {
    vi.mocked(api.get).mockRejectedValue(httpError(401, 'UNAUTHORIZED', 'não autenticado'))
    await expect(knowledgeService.getById('kb1')).rejects.toMatchObject({ response: { status: 401 } })
  })

  it('propagates 403 errors to caller', async () => {
    vi.mocked(api.post).mockRejectedValue(httpError(403, 'FORBIDDEN', 'sem permissão'))
    await expect(knowledgeService.create({ name: 'X' })).rejects.toMatchObject({ response: { status: 403 } })
  })

  it('propagates 404 errors to caller', async () => {
    vi.mocked(api.get).mockRejectedValue(httpError(404, 'KB_NOT_FOUND', 'base não encontrada'))
    await expect(knowledgeService.getById('outro-tenant')).rejects.toMatchObject({ response: { status: 404 } })
  })
})
