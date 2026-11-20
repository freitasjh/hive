"use client"

import { LoaderCircle, Lock } from "lucide-react"
import type { PromptTemplateDTO } from "@/services/admin.types"
import { useChatI18n } from "../i18n"

export interface PromptSelectorProps {
  prompts: PromptTemplateDTO[]
  value: string | null
  onChange: (id: string) => void
  loading?: boolean
  error?: string | null
  /** Sessão aberta: o prompt dela é autoritativo e o seletor não muda mais. */
  locked?: boolean
  disabled?: boolean
}

export function PromptSelector({
  prompts,
  value,
  onChange,
  loading = false,
  error = null,
  locked = false,
  disabled = false,
}: PromptSelectorProps) {
  const { t } = useChatI18n()
  const isDisabled = loading || disabled || locked

  return (
    <div className="flex flex-col gap-1.5">
      <label htmlFor="chat-prompt" className="text-xs font-medium text-muted-foreground">
        {t("chat.prompt.label")}
      </label>
      <div className="relative">
        <select
          id="chat-prompt"
          data-testid="prompt-select"
          aria-label={t("chat.prompt.label")}
          aria-busy={loading}
          disabled={isDisabled}
          value={value ?? ""}
          onChange={(event) => onChange(event.target.value)}
          className="h-10 w-full appearance-none rounded-md border border-input bg-background px-3 pr-9 text-sm text-foreground outline-none focus-visible:ring-[3px] focus-visible:ring-ring/50 disabled:cursor-not-allowed disabled:opacity-60"
        >
          <option value="">{t("chat.prompt.placeholder")}</option>
          {prompts.map((prompt) => (
            <option key={prompt.id} value={prompt.id}>
              {prompt.name}
            </option>
          ))}
        </select>
        {locked ? (
          <Lock
            className="pointer-events-none absolute right-3 top-1/2 size-4 -translate-y-1/2 text-muted-foreground"
            aria-hidden="true"
          />
        ) : null}
      </div>
      {loading ? (
        <p data-testid="prompt-loading" aria-busy="true" className="flex items-center gap-2 text-xs text-muted-foreground">
          <LoaderCircle className="size-3.5 animate-spin" aria-hidden="true" />
          {t("chat.common.loading")}
        </p>
      ) : null}
      {error ? (
        <p role="alert" className="text-xs text-destructive">{error}</p>
      ) : null}
      {locked ? (
        <p role="status" className="text-xs text-muted-foreground">{t("chat.prompt.locked")}</p>
      ) : null}
    </div>
  )
}

export default PromptSelector
