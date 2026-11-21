"use client"

import { useMemo, useState } from "react"
import { ChevronDown, Cpu, LoaderCircle } from "lucide-react"
import { Badge } from "@/components/shadcn/ui/badge"
import { Button } from "@/components/shadcn/ui/button"
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuGroup,
  DropdownMenuLabel,
  DropdownMenuRadioGroup,
  DropdownMenuRadioItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/shadcn/ui/dropdown-menu"
import type { AiProvider } from "@/services/admin.types"
import type { ModelChoice, ProviderCatalogEntry } from "@/services/chat.types"
import { cn } from "@/lib/cn"
import { useChatI18n, type ChatMessageKey } from "../i18n"

const DEFAULT_KEY = "default"
const choiceKey = (choice: ModelChoice) => `${choice.provider}:${choice.model}`

export interface ModelSelectorProps {
  catalog: ProviderCatalogEntry[]
  /** Escolha explícita; `null` = padrão do tenant / da sessão. */
  value: ModelChoice | null
  /** Par efetivo exibido quando não há escolha explícita. */
  baseline?: ModelChoice | null
  disabled?: boolean
  loading?: boolean
  error?: string | null
  onChange: (choice: ModelChoice | null) => void
}

/**
 * Seletor de provedor/modelo sobre DropdownMenuRadioGroup, agrupado por provider.
 * A UI envia o modelo no body da request (deepsearch 2026-11-20,
 * ui.shadcn.com/docs/ai-elements/base/model-selector). Sem AI SDK: o transporte
 * continua sendo chatService do Hive.
 */
export function ModelSelector({
  catalog,
  value,
  baseline = null,
  disabled = false,
  loading = false,
  error = null,
  onChange,
}: ModelSelectorProps) {
  const { t } = useChatI18n()
  const [open, setOpen] = useState(false)

  const groups = useMemo(
    () =>
      [...catalog].sort(
        (a, b) => Number(b.configured) - Number(a.configured) || a.provider.localeCompare(b.provider)
      ),
    [catalog]
  )

  const providerLabel = (provider: AiProvider) =>
    t(`chat.provider.${provider}` as ChatMessageKey)

  const label = value
    ? `${providerLabel(value.provider)} · ${value.model}`
    : baseline?.model
      ? t("chat.model.effective", { model: baseline.model })
      : t("chat.model.default")

  const select = (key: string) => {
    setOpen(false) // closeOnClick: fechar na escolha, sem depender do primitive.
    if (key === DEFAULT_KEY) {
      onChange(null)
      return
    }
    const [provider, ...rest] = key.split(":")
    const model = rest.join(":")
    const entry = catalog.find((item) => item.provider === provider && item.models.includes(model))
    if (provider && model && entry) onChange({ provider: provider as AiProvider, model })
  }

  return (
    <div className="flex min-w-0 items-center gap-1.5">
      <DropdownMenu open={open} onOpenChange={setOpen}>
        <DropdownMenuTrigger asChild>
          <Button
            type="button"
            variant="outline"
            size="sm"
            data-testid="model-select"
            aria-label={t("chat.model.label")}
            aria-busy={loading}
            disabled={disabled || loading}
            className="h-8 max-w-56 justify-between gap-2 rounded-md border-border/70 bg-background px-2.5 text-xs font-medium text-foreground shadow-xs hover:bg-accent dark:bg-input/30 dark:hover:bg-input/50"
          >
            {loading ? (
              <LoaderCircle className="size-3.5 shrink-0 animate-spin text-muted-foreground" aria-hidden="true" />
            ) : (
              <Cpu className="size-3.5 shrink-0 text-muted-foreground" aria-hidden="true" />
            )}
            <span className="min-w-0 truncate">{label}</span>
            <ChevronDown className="size-3.5 shrink-0 opacity-50" aria-hidden="true" />
          </Button>
        </DropdownMenuTrigger>
        <DropdownMenuContent align="end" className="max-h-80 w-72">
          <DropdownMenuRadioGroup
            value={value ? choiceKey(value) : DEFAULT_KEY}
            onValueChange={select}
          >
            <DropdownMenuRadioItem value={DEFAULT_KEY} data-testid="model-select-default">
              {t("chat.model.default")}
            </DropdownMenuRadioItem>
            {groups.map((entry) => (
              <DropdownMenuGroup key={entry.provider}>
                <DropdownMenuSeparator />
                <DropdownMenuLabel className="flex items-center justify-between gap-2">
                  <span>{providerLabel(entry.provider)}</span>
                  {entry.configured ? null : (
                    <Badge variant="outline" className="h-5 px-1.5 text-[10px] font-normal">
                      {t("chat.model.unconfigured")}
                    </Badge>
                  )}
                </DropdownMenuLabel>
                {entry.models.map((model, modelIndex) => (
                  <DropdownMenuRadioItem
                    key={model}
                    value={choiceKey({ provider: entry.provider, model })}
                    data-testid={`model-option-${entry.provider}-${modelIndex}`}
                  >
                    <span className={cn("min-w-0 truncate")}>{model}</span>
                    {model === entry.defaultModel ? (
                      <span className="ms-auto text-[10px] text-muted-foreground">
                        {t("chat.model.default")}
                      </span>
                    ) : null}
                  </DropdownMenuRadioItem>
                ))}
              </DropdownMenuGroup>
            ))}
          </DropdownMenuRadioGroup>
        </DropdownMenuContent>
      </DropdownMenu>
      {error ? (
        <span className="max-w-28 truncate text-[10px] text-muted-foreground" title={error}>
          {error}
        </span>
      ) : null}
    </div>
  )
}

export default ModelSelector
