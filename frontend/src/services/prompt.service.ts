import api from './api'
import type { PromptTemplateDTO, PromptTemplateCreateDTO, PromptTemplateUpdateDTO } from '../types/prompt'
import { unwrapPage, type Page } from './page'

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
  async list(params: { page?: number; size?: number; q?: string; status?: string; timeoutMs?: number } = {}): Promise<Page<PromptTemplateDTO>> {
    const { page = 0, size = 20, q, status, timeoutMs } = params
    try {
      // `timeoutMs` explícito e opcional: o default do axios é esperar indefinidamente, e uma
      // listagem pendurada é o tipo de coisa que deixa uma UI em estado de carregamento para
      // sempre. Quem chama decide (o seletor passa 10s; a tela de prompts usa o default).
      const res = await api.get('/prompt-templates', {
        params: { page, size, q, status },
        ...(timeoutMs ? { timeout: timeoutMs } : {})
      })
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
