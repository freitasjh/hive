"use client"

import type { ModelChoice, ProviderCatalogEntry } from "@/services/chat.types"
import { useChatI18n } from "../i18n"
import { ModelSelector } from "./ModelSelector"
import { PromptInput } from "./PromptInput"

export interface ChatComposerProps {
  value: string
  onChange: (value: string) => void
  onSend: () => void
  disabled?: boolean
  sending?: boolean
  warning?: string | null
  catalog?: ProviderCatalogEntry[]
  selectedModel?: ModelChoice | null
  modelBaseline?: ModelChoice | null
  modelLoading?: boolean
  modelError?: string | null
  onSelectModel?: (choice: ModelChoice | null) => void
}

/**
 * Mantém o contrato público (props + testids) e delega a renderização ao
 * PromptInput oficial do shadcn, sem anexos.
 */
export function ChatComposer({
  value,
  onChange,
  onSend,
  disabled = false,
  sending = false,
  warning = null,
  catalog = [],
  selectedModel = null,
  modelBaseline = null,
  modelLoading = false,
  modelError = null,
  onSelectModel,
}: ChatComposerProps) {
  const { t } = useChatI18n()

  return (
    <PromptInput
      value={value}
      onChange={onChange}
      onSubmit={onSend}
      disabled={disabled}
      sending={sending}
      warning={warning}
      label={t("chat.composer.label")}
      placeholder={t("chat.composer.placeholder")}
      hint={t("chat.composer.hint")}
      disclaimer={t("chat.disclaimer")}
      sendLabel={t("chat.composer.send")}
      sendingLabel={t("chat.composer.sending")}
      modelSlot={
        <ModelSelector
          catalog={catalog}
          value={selectedModel}
          baseline={modelBaseline}
          loading={modelLoading}
          error={modelError}
          disabled={disabled}
          onChange={(choice) => onSelectModel?.(choice)}
        />
      }
    />
  )
}

export default ChatComposer
