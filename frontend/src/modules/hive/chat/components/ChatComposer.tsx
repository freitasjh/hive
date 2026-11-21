"use client"

import { AlertCircle, LoaderCircle, Send } from "lucide-react"
import { InputGroup, InputGroupAddon, InputGroupButton, InputGroupTextarea } from "@/components/shadcn/ui/input-group"
import { cn } from "@/lib/cn"
import type { ModelChoice, ProviderCatalogEntry } from "@/services/chat.types"
import { useChatI18n } from "../i18n"
import { ModelSelector } from "./ModelSelector"

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
  const canSend = value.trim().length > 0 && !disabled

  const submit = () => {
    if (canSend) onSend()
  }

  return (
    <form
      data-testid="chat-form"
      className="flex shrink-0 flex-col gap-2"
      onSubmit={(event) => {
        event.preventDefault()
        submit()
      }}
    >
      <InputGroup className="rounded-xl px-2 py-1.5">
        <label htmlFor="chat-message" className="sr-only">
          {t("chat.composer.label")}
        </label>
        <InputGroupTextarea
          id="chat-message"
          data-testid="chat-input"
          aria-label={t("chat.composer.label")}
          rows={2}
          value={value}
          disabled={disabled}
          placeholder={t("chat.composer.placeholder")}
          onChange={(event) => onChange(event.target.value)}
          onKeyDown={(event) => {
            if (event.key === "Enter" && !event.shiftKey) {
              event.preventDefault()
              submit()
            }
          }}
          className={cn("min-h-14 text-sm")}
        />
        <InputGroupAddon align="block-end" className="justify-between gap-2">
          <div className="flex min-w-0 flex-col">
            <p className="truncate text-[11px] leading-tight text-muted-foreground">{t("chat.composer.hint")}</p>
            <p title={t("chat.disclaimer")} className="line-clamp-2 text-[11px] leading-tight text-muted-foreground">
              {t("chat.disclaimer")}
            </p>
          </div>
          <div className="flex shrink-0 items-center gap-1.5">
            <ModelSelector
              catalog={catalog}
              value={selectedModel}
              baseline={modelBaseline}
              loading={modelLoading}
              error={modelError}
              disabled={disabled}
              onChange={(choice) => onSelectModel?.(choice)}
            />
            <InputGroupButton
              type="submit"
              data-testid="chat-send"
              disabled={!canSend}
              size="icon-sm"
              aria-label={sending ? t("chat.composer.sending") : t("chat.composer.send")}
              className="min-h-9 gap-1.5 px-3"
            >
              {sending ? (
                <LoaderCircle className="size-4 animate-spin" aria-hidden="true" />
              ) : (
                <Send className="size-4" aria-hidden="true" />
              )}
              <span className="hidden sm:inline">{sending ? t("chat.composer.sending") : t("chat.composer.send")}</span>
            </InputGroupButton>
          </div>
        </InputGroupAddon>
      </InputGroup>
      {warning ? (
        <p role="alert" aria-live="assertive" className="flex items-start gap-2 rounded-md border border-destructive/40 bg-destructive/10 px-3 py-2 text-sm text-destructive">
          <AlertCircle className="mt-0.5 size-4 shrink-0" aria-hidden="true" />
          <span>{warning}</span>
        </p>
      ) : null}
    </form>
  )
}

export default ChatComposer
