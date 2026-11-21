"use client"

import { LoaderCircle, Lock, Sparkles } from "lucide-react"
import { Badge } from "@/components/shadcn/ui/badge"
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/shadcn/ui/select"
import type { PromptTemplateDTO } from "@/services/admin.types"
import { useChatI18n } from "../i18n"

/**
 * Radix não aceita `value=""` em Select.Item (lança erro). Sentinela não vazia
 * representa "sem prompt"; o controller continua recebendo "" como antes.
 */
const EMPTY_VALUE = "__prompt_none__"

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
    <div className="flex shrink-0 flex-col gap-1.5 border-b border-border/60 bg-card/40 px-4 py-2.5">
      <div className="flex flex-wrap items-center gap-2 sm:flex-nowrap">
        <label
          htmlFor="chat-prompt"
          className="flex shrink-0 items-center gap-1.5 text-xs font-medium text-muted-foreground"
        >
          <Sparkles className="size-3.5 text-primary" aria-hidden="true" />
          {t("chat.prompt.label")}
        </label>

        <div className="min-w-0 flex-1 sm:max-w-xs">
          <Select
            value={value ?? EMPTY_VALUE}
            onValueChange={(next) => onChange(next === EMPTY_VALUE ? "" : next)}
            disabled={isDisabled}
          >
            <SelectTrigger
              id="chat-prompt"
              size="sm"
              data-testid="prompt-select"
              aria-label={t("chat.prompt.label")}
              aria-busy={loading}
              className="w-full min-w-0 border-border/70 bg-background text-xs font-medium"
            >
              <SelectValue placeholder={t("chat.prompt.placeholder")} />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value={EMPTY_VALUE}>{t("chat.prompt.placeholder")}</SelectItem>
              {prompts.map((prompt) => (
                <SelectItem key={prompt.id} value={prompt.id}>
                  {prompt.name}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>

        {locked ? (
          <Badge
            role="status"
            variant="secondary"
            className="gap-1 text-[11px] font-normal text-muted-foreground sm:ml-auto"
          >
            <Lock className="size-3" aria-hidden="true" />
            {t("chat.prompt.locked")}
          </Badge>
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
    </div>
  )
}

export default PromptSelector
