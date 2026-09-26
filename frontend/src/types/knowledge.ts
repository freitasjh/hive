export type KbStatus = 'ACTIVE' | 'ARCHIVED'

export type KbDocumentStatus = 'PENDING' | 'INDEXING' | 'READY' | 'FAILED'

export type KbSourceType = 'UPLOAD' | 'URL'

export interface KnowledgeBaseDTO {
  id: string
  name: string
  slug: string
  description?: string | null
  embeddingModel: string
  dimension: number
  status: KbStatus
}

export interface KnowledgeBaseCreateDTO {
  name: string
  description?: string | null
  embeddingModel?: string | null
}

export interface KnowledgeBaseUpdateDTO {
  name?: string
  description?: string | null
  status?: KbStatus | null
}

export interface KbDocumentDTO {
  id: string
  kbId: string
  sourceType: KbSourceType
  sourceUri?: string | null
  fileName?: string | null
  status: KbDocumentStatus
  error?: string | null
}

export interface KbChunkDTO {
  docId: string
  docName: string
  chunkId: string
  text: string
  score: number
}

export interface KbSearchRequest {
  query: string
  topK?: number
}

export interface KbAgentLinkDTO {
  kbIds: string[]
}

export interface Page<T> {
  content: T[]
  totalElements: number
  totalPages: number
  number: number
  size: number
}

export interface AgentLinkOption {
  id: string
  name: string
}

export const KB_MAX_LINKS = 5

export const KB_MAX_FILE_SIZE = 10 * 1024 * 1024

export const KB_ALLOWED_EXTENSIONS = ['pdf', 'txt', 'md'] as const

export const KB_MIXED_DIMENSION_MESSAGE =
  'As bases selecionadas usam modelos de embedding com dimensões diferentes (ex.: 1536 e 768). Selecione bases com a mesma dimensão.'

export const KB_MAX_LINKS_MESSAGE = 'Máximo de 5 bases de conhecimento por agente.'

export function kbDisplayName(kb: Pick<KnowledgeBaseDTO, 'name' | 'embeddingModel' | 'dimension'>): string {
  return `${kb.name} · ${kb.embeddingModel} · ${kb.dimension}d`
}

export function dimensionsMixed(dims: number[]): boolean {
  return new Set(dims).size > 1
}
