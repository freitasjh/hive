"use client"

import { Send, LoaderCircle } from "lucide-react"
import { Button } from "@/components/shadcn/ui/button"
import { useChatI18n } from "../i18n"

export interface ChatComposerProps {
  value: string
  onChange: (value: string) => void
  onSend: () => void
  disabled?: boolean
  sending?: boolean
  warning?: string | null
}

export function ChatComposer({
  value,
  onChange,
  onSend,
  disabled = false,
  sending = false,
  warning = null,
}: ChatComposerProps) {
  const { t } = useChatI18n()
  const canSend = value.trim().length > 0 && !disabled

  const submit = () => {
    if (canSend) onSend()
  }

  return (
    <form
      data-testid="chat-form"
      className="flex flex-col gap-2"
      onSubmit={(event) => {
        event.preventDefault()
        submit()
      }}
    >
      <label htmlFor="chat-message" className="sr-only">
        {t("chat.composer.label")}
      </label>
      <textarea
        id="chat-message"
        data-testid="chat-input"
        aria-label={t("chat.composer.label")}
        rows={3}
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
        className="w-full resize-none rounded-md border border-input bg-background px-3 py-2 text-sm text-foreground outline-none placeholder:text-muted-foreground focus-visible:ring-[3px] focus-visible:ring-ring/50 disabled:cursor-not-allowed disabled:opacity-60"
      />
      <div className="flex flex-wrap items-center justify-between gap-2">
        <p className="text-xs text-muted-foreground">{t("chat.composer.hint")}</p>
        <Button type="submit" data-testid="chat-send" disabled={!canSend} className="min-h-10">
          {sending ? (
            <LoaderCircle className="size-4 animate-spin" aria-hidden="true" />
          ) : (
            <Send className="size-4" aria-hidden="true" />
          )}
          {sending ? t("chat.composer.sending") : t("chat.composer.send")}
        </Button>
      </div>
      {warning ? (
        <p role="alert" aria-live="assertive" className="text-sm text-amber-600 dark:text-amber-400">
          {warning}
        </p>
      ) : null}
      <p className="text-xs text-muted-foreground">{t("chat.disclaimer")}</p>
    </form>
  )
}

export default ChatComposer
