import type {
  AiProvider,
  CredentialStatus,
  PromptStatus,
  PromptTemplateDTO,
  ValidateResponse,
} from "@/services/admin.types"

/** Estado observável das telas — mantido fora dos componentes para stories e testes determinísticos. */

export interface CredentialsState {
  statuses: CredentialStatus[]
  loading: boolean
  error: string | null
  editingProvider: AiProvider | null
  apiKey: string
  baseUrl: string
  prefixError: string | null
  saving: boolean
  deleteTarget: AiProvider | null
  deleting: boolean
  notice: string | null
  actionError: string | null
}

export interface CredentialsActions {
  onEdit: (provider: AiProvider) => void
  onApiKeyChange: (value: string) => void
  onBaseUrlChange: (value: string) => void
  onCancelEdit: () => void
  onSave: () => void
  onAskDelete: (provider: AiProvider) => void
  onCancelDelete: () => void
  onConfirmDelete: () => void
  onRetry: () => void
}

export interface LlmConfigState {
  provider: AiProvider | null
  model: string
  configured: boolean
  loading: boolean
  saving: boolean
  testing: boolean
  loadError: string | null
  saveError: string | null
  testResult: ValidateResponse | null
  remoteModels: string[]
  remoteLoading: boolean
  remoteError: string | null
  notice: string | null
}

export interface LlmConfigActions {
  onProviderChange: (provider: AiProvider | null) => void
  onModelChange: (model: string) => void
  onSave: () => void
  onTest: () => void
  onRefreshModels: () => void
  onRetry: () => void
}

export interface PromptsState {
  items: PromptTemplateDTO[]
  total: number
  page: number
  size: number
  q: string
  status: PromptStatus | null
  loading: boolean
  error: string | null
  actionError: string | null
  notice?: string | null
  deleteTarget: PromptTemplateDTO | null
  deleting: boolean
}

export interface PromptsActions {
  onSearch: () => void
  onQueryChange: (value: string) => void
  onStatusChange: (status: PromptStatus | null) => void
  onPageChange: (page: number, size: number) => void
  onNew: () => void
  onEdit: (id: string) => void
  onAskDelete: (row: PromptTemplateDTO) => void
  onCancelDelete: () => void
  onConfirmDelete: () => void
  onRetry: () => void
}

export interface PromptEditorState {
  isEdit: boolean
  promptId: string | null
  name: string
  code: string
  description: string
  tags: string[]
  tagsInput: string
  template: string
  status: PromptStatus
  temperature: number | null
  topP: number | null
  maxTokens: number | null
  loading: boolean
  saving: boolean
  error: string | null
  validationError: string | null
}

export interface PromptEditorActions {
  onNameChange: (value: string) => void
  onCodeChange: (value: string) => void
  onDescriptionChange: (value: string) => void
  onTagsInputChange: (value: string) => void
  onAddTag: () => void
  onRemoveTag: (index: number) => void
  onTemplateChange: (value: string) => void
  onStatusChange: (status: PromptStatus) => void
  onTemperatureChange: (value: number | null) => void
  onTopPChange: (value: number | null) => void
  onMaxTokensChange: (value: number | null) => void
  onSave: () => void
  onBack: () => void
  onRetry: () => void
}
