import type {
  AiProvider,
  CredentialStatus,
  PromptStatus,
  PromptTemplateDTO,
  PublicApiClient,
  PublicClientPinMode,
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

/* ------------------------- ADR-003 / public-clients ------------------------ */

export interface PublicClientPromptDraft {
  promptCode: string
  pinMode: PublicClientPinMode
  pinnedVersion: number | null
}

export interface PublicClientsFormState {
  label: string
  grants: string[]
  prompts: PublicClientPromptDraft[]
  tokenTtlSeconds: string
  maxMessages: string
  maxMessageChars: string
  maxTotalChars: string
}

export interface PublicClientsState {
  items: PublicApiClient[]
  total: number
  page: number
  size: number
  q: string
  loading: boolean
  error: string | null
  actionError: string | null
  notice: string | null
  createOpen: boolean
  form: PublicClientsFormState
  validationError: string | null
  creating: boolean
  revokeTarget: PublicApiClient | null
  revoking: boolean
  rotatingClientId: string | null
}

/**
 * Secret em plaintext one-shot. Vive APENAS num useState do Container e é
 * descartado ao fechar o diálogo — nunca entra em `PublicClientsState`,
 * lista, localStorage ou URL.
 */
export interface OneShotSecret {
  secret: string
  scope: "create" | "rotate"
  clientId: string
  label: string
  previousSecretExpiresAt: string | null
}

export interface PublicClientsActions {
  onQueryChange: (value: string) => void
  onPageChange: (page: number, size: number) => void
  onRetry: () => void
  onOpenCreate: () => void
  onCloseCreate: () => void
  onFormChange: (patch: Partial<PublicClientsFormState>) => void
  onToggleGrant: (grant: string, checked: boolean) => void
  onPromptChange: (index: number, patch: Partial<PublicClientPromptDraft>) => void
  onAddPrompt: () => void
  onRemovePrompt: (index: number) => void
  onCreate: () => void
  onRotate: (client: PublicApiClient) => void
  onAskRevoke: (client: PublicApiClient) => void
  onCancelRevoke: () => void
  onConfirmRevoke: () => void
  onDismissSecret: () => void
}
