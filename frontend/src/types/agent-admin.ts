export type AiProvider = 'OPENAI' | 'GEMINI' | 'ANTHROPIC' | 'OLLAMA' | 'OPENCODE_GO' | 'OPENCODE_ZEN' | 'OPENAI_COMPATIBLE'

export const VENDOR_ORDER: AiProvider[] = ['OPENAI', 'GEMINI', 'ANTHROPIC', 'OLLAMA', 'OPENCODE_GO', 'OPENCODE_ZEN', 'OPENAI_COMPATIBLE']

export const LGPD_OPENCODE_MESSAGE =
  '🔒 Privacidade — Seus prompts e dados de conversa trafegam pela API da OpenCode. Consulte a política de privacidade: https://opencode.ai/privacy'

export function modelUnavailableMessage(model: string, provider: AiProvider): string {
  return `Modelo ${model} indisponível no provedor ${provider}. Escolha outro modelo ou atualize a lista.`
}

/**
 * Allowlist (não negação) dos providers cuja base URL pertence ao tenant.
 *
 * Campo readonly de exibição NUNCA pode alimentar o payload: em GO/Zen a base URL é uma
 * constante server-side e a UI a exibe apenas como informação — serializá-la quebrava o PUT
 * com 400. Um provider novo entra aqui por decisão explícita, nunca por omissão.
 */
export const PROVIDERS_WITH_TENANT_BASE_URL: ReadonlySet<AiProvider> = new Set<AiProvider>([
  'OLLAMA',
  'OPENAI_COMPATIBLE',
])

export function acceptsTenantBaseUrl(provider: AiProvider): boolean {
  return PROVIDERS_WITH_TENANT_BASE_URL.has(provider)
}

export interface VendorMeta {
  provider: AiProvider
  label: string
  description: string
  costHint: string
  icon: string
  requiresKey: boolean
  keyPrefix: string | null
  keyPlaceholder: string
  defaultModel: string
  models: string[]
  defaultBaseUrl: string | null
  hiddenFromCreate: boolean
  readOnlyBaseUrl?: boolean
  remoteModels?: boolean
  modelsEndpoint?: string | null
  lgpdWarning?: boolean
}

export const VENDOR_CATALOG: Record<AiProvider, VendorMeta> = {
  OPENAI: {
    provider: 'OPENAI',
    label: 'OpenAI',
    description: 'Modelos GPT — melhor qualidade geral para chat e raciocínio.',
    costHint: 'Pago por uso',
    icon: 'pi pi-bolt',
    requiresKey: true,
    keyPrefix: 'sk-',
    keyPlaceholder: 'sk-...',
    defaultModel: 'gpt-4o-mini',
    models: ['gpt-4o-mini', 'gpt-4o', 'gpt-4', 'gpt-3.5-turbo'],
    defaultBaseUrl: null,
    hiddenFromCreate: false,
  },
  GEMINI: {
    provider: 'GEMINI',
    label: 'Google Gemini',
    description: 'Modelos Gemini — janela de contexto longa e multimodalidade.',
    costHint: 'Pago por uso',
    icon: 'pi pi-google',
    requiresKey: true,
    keyPrefix: 'AIza',
    keyPlaceholder: 'AIza...',
    defaultModel: 'gemini-2.0-flash',
    models: ['gemini-2.0-flash', 'gemini-1.5-pro', 'gemini-1.5-flash'],
    defaultBaseUrl: null,
    hiddenFromCreate: false,
  },
  ANTHROPIC: {
    provider: 'ANTHROPIC',
    label: 'Anthropic Claude',
    description: 'Modelos Claude — destaque em raciocínio, código e escrita.',
    costHint: 'Pago por uso',
    icon: 'pi pi-sparkles',
    requiresKey: true,
    keyPrefix: 'sk-ant-',
    keyPlaceholder: 'sk-ant-...',
    defaultModel: 'claude-3-5-haiku-latest',
    models: ['claude-3-5-haiku-latest', 'claude-3-5-sonnet-latest', 'claude-3-opus-latest'],
    defaultBaseUrl: null,
    hiddenFromCreate: false,
  },
  OLLAMA: {
    provider: 'OLLAMA',
    label: 'Ollama (local)',
    description: 'Modelos locais — sem custo de API. Rode na sua máquina ou rede.',
    costHint: 'Gratuito local',
    icon: 'pi pi-desktop',
    requiresKey: false,
    keyPrefix: null,
    keyPlaceholder: '',
    defaultModel: 'llama3.2',
    models: ['llama3.2', 'llama3.1', 'mistral', 'qwen2.5'],
    defaultBaseUrl: 'http://localhost:11434',
    hiddenFromCreate: false,
  },
  OPENAI_COMPATIBLE: {
    provider: 'OPENAI_COMPATIBLE',
    label: 'Custom (endpoint próprio)',
    description: 'Endpoint próprio compatível com a API OpenAI — base URL e chave configuráveis.',
    costHint: 'Conforme endpoint',
    icon: 'pi pi-server',
    requiresKey: true,
    keyPrefix: null,
    keyPlaceholder: 'chave do endpoint...',
    defaultModel: 'mimo-v2.5',
    models: ['mimo-v2.5'],
    defaultBaseUrl: null,
    hiddenFromCreate: false,
    readOnlyBaseUrl: false,
    remoteModels: false,
    modelsEndpoint: null,
    lgpdWarning: false,
  },
  OPENCODE_GO: {
    provider: 'OPENCODE_GO',
    label: 'OpenCode GO',
    description: 'Assinatura OpenCode GO — catálogo remoto com discovery filtrado.',
    costHint: 'Assinatura US$ 10/mês',
    icon: 'pi pi-cloud',
    requiresKey: true,
    keyPrefix: null,
    keyPlaceholder: 'chave OpenCode GO...',
    defaultModel: 'mimo-v2.5',
    models: ['mimo-v2.5'],
    defaultBaseUrl: 'https://opencode.ai/zen/go/v1',
    hiddenFromCreate: false,
    readOnlyBaseUrl: true,
    remoteModels: true,
    modelsEndpoint: '/providers/go/models',
    lgpdWarning: true,
  },
  OPENCODE_ZEN: {
    provider: 'OPENCODE_ZEN',
    label: 'OpenCode Zen',
    description: 'Pay-as-you-go OpenCode Zen — catálogo remoto com discovery filtrado.',
    costHint: 'Pay-as-you-go',
    icon: 'pi pi-sparkle',
    requiresKey: true,
    keyPrefix: null,
    keyPlaceholder: 'chave OpenCode Zen...',
    defaultModel: 'claude-sonnet-4-5',
    models: ['claude-sonnet-4-5'],
    defaultBaseUrl: 'https://opencode.ai/zen/v1',
    hiddenFromCreate: false,
    readOnlyBaseUrl: true,
    remoteModels: true,
    modelsEndpoint: '/providers/zen/models',
    lgpdWarning: true,
  },
}

export function validateKeyPrefix(provider: AiProvider, apiKey: string): boolean {
  const meta = VENDOR_CATALOG[provider]
  if (!meta.requiresKey) return true
  if (!apiKey) return false
  if (!meta.keyPrefix) return true
  return apiKey.startsWith(meta.keyPrefix)
}

export interface AgentDTO {
  id: string
  name: string
  avatarUrl?: string | null
  description?: string | null
  systemPrompt?: string | null
  promptTemplateId?: string | null
  provider: AiProvider
  model: string
  temperature?: number | null
  topP?: number | null
  maxTokens?: number | null
  tools?: string[] | null
  knowledgeBases?: string[] | null
  isDefault: boolean
  active: boolean
  createAt: string
  updateAt: string
}

export interface AgentCreateDTO {
  name: string
  avatarUrl?: string | null
  description?: string | null
  systemPrompt?: string | null
  promptTemplateId?: string | null
  provider: AiProvider
  model: string
  temperature?: number | null
  topP?: number | null
  maxTokens?: number | null
  tools?: string[] | null
  knowledgeBases?: string[] | null
  isDefault?: boolean | null
  active?: boolean | null
}

export interface AgentUpdateDTO {
  name: string
  avatarUrl?: string | null
  description?: string | null
  systemPrompt?: string | null
  promptTemplateId?: string | null
  provider: AiProvider
  model: string
  temperature?: number | null
  topP?: number | null
  maxTokens?: number | null
  tools?: string[] | null
  knowledgeBases?: string[] | null
  isDefault?: boolean | null
  active?: boolean | null
}

export interface CredentialStatus {
  provider: AiProvider
  configured: boolean
  last4: string | null
  baseUrl?: string | null
  updatedAt?: string | null
}

export interface CredentialUpsert {
  apiKey?: string
  baseUrl?: string
}

export interface ValidateRequest {
  provider: AiProvider
  model: string
  baseUrl?: string
}

export interface ValidateResponse {
  ok: boolean
  provider: AiProvider
  model: string
  latencyMs: number
  reason?: string | null
}

export interface FilteredModel {
  id: string
  reason: string
}

export interface ProviderModelsResponse {
  models: string[]
  filteredOut: FilteredModel[]
  cachedAt: string
}

export interface ApiError {
  status?: number
  code?: string
  detail?: string
}

export function toApiError(e: unknown): ApiError {
  const err = e as { response?: { status?: number; data?: { code?: string; detail?: string; title?: string } }; message?: string }
  return {
    status: err.response?.status,
    code: err.response?.data?.code,
    detail: err.response?.data?.detail ?? err.response?.data?.title ?? err.message,
  }
}

export function maskKey(last4: string | null | undefined): string {
  if (!last4) return '••••'
  return `••••${last4}`
}

export interface Page<T> {
  content: T[]
  totalElements: number
  totalPages: number
  number: number
  size: number
}

/**
 * Result of `PUT /agents/{id}/knowledge-bases`. Names come back so the editor can show the current
 * selection without a second request; ids remain the source of truth because names repeat per tenant.
 */
