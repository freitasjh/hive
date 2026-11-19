import { apiRequest, unwrapData } from "./api"
import { unwrapPage, type Page } from "./page"
import type {
  PromptTemplateCreateDTO,
  PromptTemplateDTO,
  PromptTemplateUpdateDTO,
} from "./admin.types"

/** Porta de bkp/front_bkp/src/services/prompt.service.ts:21-62. Rota: PromptTemplateControllerV1.java:39. */
export interface PromptListParams {
  page?: number
  size?: number
  q?: string
  status?: string
  /** Listagem aplica 10s por default para nunca ficar presa em carregamento. */
  timeoutMs?: number
}

export const promptService = {
  async list(params: PromptListParams = {}): Promise<Page<PromptTemplateDTO>> {
    const { page = 0, size = 20, q, status, timeoutMs = 10_000 } = params
    const body = await apiRequest<unknown>("/prompt-templates", {
      query: { page, size, q, status },
      timeoutMs,
    })
    return unwrapPage<PromptTemplateDTO>(body, size)
  },

  async getById(id: string): Promise<PromptTemplateDTO> {
    const body = await apiRequest<unknown>(`/prompt-templates/${id}`)
    return unwrapData<PromptTemplateDTO>(body)
  },

  async create(dto: PromptTemplateCreateDTO): Promise<PromptTemplateDTO> {
    const body = await apiRequest<unknown>("/prompt-templates", { method: "POST", body: dto })
    return unwrapData<PromptTemplateDTO>(body)
  },

  async update(id: string, dto: PromptTemplateUpdateDTO): Promise<PromptTemplateDTO> {
    const body = await apiRequest<unknown>(`/prompt-templates/${id}`, { method: "PUT", body: dto })
    return unwrapData<PromptTemplateDTO>(body)
  },

  async remove(id: string): Promise<void> {
    await apiRequest<void>(`/prompt-templates/${id}`, { method: "DELETE" })
  },
}

export default promptService
