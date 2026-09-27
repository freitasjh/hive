export type PromptStatus = 'DRAFT' | 'ACTIVE' | 'ARCHIVED'

export interface InferenceParamsDTO {
  temperature?: number | null
  topP?: number | null
  maxTokens?: number | null
}

export interface PromptTemplateDTO {
  id: string
  name: string
  code: string
  description?: string | null
  tags?: string[] | null
  template: string
  params?: string[] | null
  inferenceParams?: InferenceParamsDTO | null
  status: PromptStatus
  version: number
  createAt: string
  updateAt: string
}

export interface PromptTemplateCreateDTO {
  name: string
  code: string
  description?: string | null
  tags?: string[] | null
  template: string
  inferenceParams?: InferenceParamsDTO | null
  status?: PromptStatus | null
}

export interface PromptTemplateUpdateDTO {
  name: string
  description?: string | null
  tags?: string[] | null
  template: string
  inferenceParams?: InferenceParamsDTO | null
  status?: PromptStatus | null
}

/** A interface de página paginada mora em `services/page.ts`, junto do normalizador. */
export type { Page } from '../services/page'
