import api from './api'
import type {
  KbChunkDTO,
  KbDocumentDTO,
  KbSearchRequest,
  KnowledgeBaseCreateDTO,
  KnowledgeBaseDTO,
  KnowledgeBaseUpdateDTO,
  Page,
} from '../types/knowledge'

function unwrapPage<T>(data: unknown, fallbackSize: number): Page<T> {
  const d = data as Record<string, unknown>
  if (d && Array.isArray(d.content)) return d as unknown as Page<T>
  if (Array.isArray(d)) {
    return { content: d as T[], totalElements: d.length, totalPages: 1, number: 0, size: fallbackSize } as Page<T>
  }
  if (d && d.data) {
    const inner = d.data as Record<string, unknown>
    if (Array.isArray(inner.content)) return inner as unknown as Page<T>
    if (Array.isArray(d.data)) {
      return { content: d.data as T[], totalElements: (d.data as unknown[]).length, totalPages: 1, number: 0, size: fallbackSize } as Page<T>
    }
  }
  return d as unknown as Page<T>
}

function unwrapData<T>(data: unknown): T {
  const d = data as Record<string, unknown>
  if (d && d.data !== undefined) return d.data as T
  return data as T
}

function unwrapList<T>(data: unknown): T[] {
  const d = data as Record<string, unknown>
  if (Array.isArray(d)) return d as T[]
  if (d && Array.isArray(d.data)) return d.data as T[]
  if (d && d.data && Array.isArray((d.data as Record<string, unknown>).content)) {
    return (d.data as unknown as Page<T>).content
  }
  return data as T[]
}

export const knowledgeService = {
  async list(params: { page?: number; size?: number; q?: string } = {}): Promise<Page<KnowledgeBaseDTO>> {
    const { page = 0, size = 20, q } = params
    const res = await api.get('/knowledge-bases', { params: { page, size, q } })
    return unwrapPage<KnowledgeBaseDTO>(res.data, size)
  },

  async getById(id: string): Promise<KnowledgeBaseDTO> {
    const res = await api.get(`/knowledge-bases/${id}`)
    return unwrapData<KnowledgeBaseDTO>(res.data)
  },

  async create(dto: KnowledgeBaseCreateDTO): Promise<KnowledgeBaseDTO> {
    const res = await api.post('/knowledge-bases', dto)
    return unwrapData<KnowledgeBaseDTO>(res.data)
  },

  async update(id: string, dto: KnowledgeBaseUpdateDTO): Promise<KnowledgeBaseDTO> {
    const res = await api.put(`/knowledge-bases/${id}`, dto)
    return unwrapData<KnowledgeBaseDTO>(res.data)
  },

  async remove(id: string, force = false): Promise<void> {
    await api.delete(`/knowledge-bases/${id}`, { params: { force } })
  },

  async uploadDocument(
    id: string,
    file: File,
    onProgress?: (percent: number) => void,
  ): Promise<KbDocumentDTO> {
    const form = new FormData()
    form.append('file', file, file.name)
    const res = await api.post(`/knowledge-bases/${id}/documents:upload`, form, {
      headers: { 'Content-Type': 'multipart/form-data' },
      onUploadProgress: onProgress
        ? (e) => {
            if (e.total) onProgress(Math.round((e.loaded / e.total) * 100))
          }
        : undefined,
    })
    return unwrapData<KbDocumentDTO>(res.data)
  },

  async addUrlDocument(id: string, url: string): Promise<KbDocumentDTO> {
    const res = await api.post(`/knowledge-bases/${id}/documents:url`, { url })
    return unwrapData<KbDocumentDTO>(res.data)
  },

  async listDocuments(id: string): Promise<KbDocumentDTO[]> {
    const res = await api.get(`/knowledge-bases/${id}/documents`)
    return unwrapList<KbDocumentDTO>(res.data)
  },

  async deleteDocument(kbId: string, docId: string): Promise<void> {
    await api.delete(`/knowledge-bases/${kbId}/documents/${docId}`)
  },

  async reindex(id: string): Promise<void> {
    await api.post(`/knowledge-bases/${id}/reindex`)
  },

  async search(id: string, req: KbSearchRequest): Promise<KbChunkDTO[]> {
    const res = await api.post(`/knowledge-bases/${id}/search`, { query: req.query, topK: req.topK })
    return unwrapList<KbChunkDTO>(res.data)
  },

  async listAgentLinks(agentId: string): Promise<string[]> {
    const res = await api.get(`/agents/${agentId}/knowledge-bases`)
    return unwrapList<string>(res.data)
  },

  async patchAgentKnowledge(agentId: string, knowledgeBaseIds: string[]): Promise<void> {
    await api.patch(`/agents/${agentId}`, { knowledgeBaseIds })
  },
}

export default knowledgeService
