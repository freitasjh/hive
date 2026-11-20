"use client"

import { AlertCircle, LoaderCircle, Send } from "lucide-react"
import { Button } from "@/components/shadcn/ui/button"
import { Card, CardContent } from "@/components/shadcn/ui/card"
import { cn } from "@/lib/cn"
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
      className="flex shrink-0 flex-col gap-2"
      onSubmit={(event) => {
        event.preventDefault()
        submit()
      }}
    >
      <Card className="gap-0 rounded-xl py-0">
        <CardContent className="flex flex-col gap-1.5 px-3 py-2">
          <label htmlFor="chat-message" className="sr-only">
            {t("chat.composer.label")}
          </label>
          <textarea
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
            className={cn(
              "flex min-h-14 w-full min-w-0 resize-none rounded-md border border-input bg-transparent px-3 py-2 text-sm shadow-xs outline-none transition-[color,box-shadow] placeholder:text-muted-foreground disabled:cursor-not-allowed disabled:opacity-50",
              "focus-visible:border-ring focus-visible:ring-[3px] focus-visible:ring-ring/50"
            )}
          />
          <div className="flex items-center justify-between gap-2">
            <div className="flex min-w-0 flex-col">
              <p className="truncate text-[11px] leading-tight text-muted-foreground">{t("chat.composer.hint")}</p>
              <p title={t("chat.disclaimer")} className="line-clamp-2 text-[11px] leading-tight text-muted-foreground">
                {t("chat.disclaimer")}
              </p>
            </div>
            <Button type="submit" data-testid="chat-send" disabled={!canSend} className="min-h-9">
              {sending ? (
                <LoaderCircle className="size-4 animate-spin" aria-hidden="true" />
              ) : (
                <Send className="size-4" aria-hidden="true" />
              )}
              {sending ? t("chat.composer.sending") : t("chat.composer.send")}
            </Button>
          </div>
        </CardContent>
      </Card>
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
