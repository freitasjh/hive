import api from './api'
import type { Page, PromptTemplateDTO, PromptTemplateCreateDTO, PromptTemplateUpdateDTO } from '../types/prompt'

function unwrapPage<T>(data: unknown, fallbackSize: number): Page<T> {
  const d = data as Record<string, unknown>
  if (d && Array.isArray(d.content)) return d as unknown as Page<T>
  if (Array.isArray(d)) return { content: d as T[], totalElements: d.length, totalPages: 1, number: 0, size: fallbackSize } as Page<T>
  // handle Spring success wrapper { success:true, data: Page }
  if (d && d.data) {
    const inner = d.data as Record<string, unknown>
    if (Array.isArray(inner.content)) return inner as unknown as Page<T>
    if (Array.isArray(d.data)) return { content: d.data as T[], totalElements: (d.data as unknown[]).length, totalPages: 1, number: 0, size: fallbackSize } as Page<T>
  }
  return d as unknown as Page<T>
}

function unwrapData<T>(data: unknown): T {
  const d = data as Record<string, unknown>
  if (d && d.data !== undefined) return d.data as T
  if (d && d.success !== undefined && d.data !== undefined) return d.data as T
  return data as T
}

function handleError(e: unknown): never {
  const err = e as { response?: { status?: number; data?: unknown } }
  const status = err?.response?.status
  if (status === 400 || status === 409 || status === 422) {
    throw err
  }
  throw err
}

export const promptService = {
  async list(params: { page?: number; size?: number; q?: string; status?: string } = {}): Promise<Page<PromptTemplateDTO>> {
    const { page = 0, size = 20, q, status } = params
    try {
      const res = await api.get('/prompt-templates', { params: { page, size, q, status } })
      return unwrapPage<PromptTemplateDTO>(res.data, size)
    } catch (e) { handleError(e); throw e }
  },

  async getById(id: string): Promise<PromptTemplateDTO> {
    try {
      const res = await api.get(`/prompt-templates/${id}`)
      return unwrapData<PromptTemplateDTO>(res.data)
    } catch (e) { handleError(e); throw e }
  },

  async create(dto: PromptTemplateCreateDTO): Promise<PromptTemplateDTO> {
    try {
      const res = await api.post('/prompt-templates', dto)
      return unwrapData<PromptTemplateDTO>(res.data)
    } catch (e) { handleError(e); throw e }
  },

  async update(id: string, dto: PromptTemplateUpdateDTO): Promise<PromptTemplateDTO> {
    try {
      const res = await api.put(`/prompt-templates/${id}`, dto)
      return unwrapData<PromptTemplateDTO>(res.data)
    } catch (e) { handleError(e); throw e }
  },

  async remove(id: string): Promise<void> {
    try {
      await api.delete(`/prompt-templates/${id}`)
    } catch (e) { handleError(e); throw e }
  },
}

export default promptService
