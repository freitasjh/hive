"use client"

import { LoaderCircle, Lock } from "lucide-react"
import { Badge } from "@/components/shadcn/ui/badge"
import { Card, CardContent, CardHeader } from "@/components/shadcn/ui/card"
import { cn } from "@/lib/cn"
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
    <Card className="shrink-0 gap-0 rounded-xl py-0">
      <CardHeader className="flex flex-row items-center justify-between gap-2 px-3 py-2">
        <label htmlFor="chat-prompt" className="text-xs leading-none font-medium text-muted-foreground">
          {t("chat.prompt.label")}
        </label>
        {locked ? (
          <Badge role="status" variant="outline" className="gap-1 text-[10px] font-normal">
            <Lock className="size-3" aria-hidden="true" />
            {t("chat.prompt.locked")}
          </Badge>
        ) : null}
      </CardHeader>
      <CardContent className="flex flex-col gap-1 px-3 pb-2">
        <div className="relative">
          <select
            id="chat-prompt"
            data-testid="prompt-select"
            aria-label={t("chat.prompt.label")}
            aria-busy={loading}
            disabled={isDisabled}
            value={value ?? ""}
            onChange={(event) => onChange(event.target.value)}
            className={cn(
              "h-9 w-full min-w-0 appearance-none rounded-md border border-input bg-transparent px-3 pr-9 py-1 text-sm shadow-xs outline-none transition-[color,box-shadow] disabled:cursor-not-allowed disabled:opacity-50",
              "focus-visible:border-ring focus-visible:ring-[3px] focus-visible:ring-ring/50"
            )}
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
      </CardContent>
    </Card>
  )
}

export default PromptSelector
