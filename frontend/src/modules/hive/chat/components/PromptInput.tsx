"use client"

import { useRef, type ReactNode } from "react"
import { AlertCircle, ArrowUpIcon, LoaderCircle } from "lucide-react"
import { InputGroup, InputGroupAddon, InputGroupButton, InputGroupTextarea } from "@/components/shadcn/ui/input-group"
import { cn } from "@/lib/cn"

/**
 * Prompt Input no padrão oficial do shadcn/ui AI Elements
 * (InputGroup + InputGroupTextarea + InputGroupAddon block-end com
 * ModelSelector e submit circular ArrowUpIcon).
 * Fonte: https://ui.shadcn.com/docs/ai-elements/base/prompt-input — 2026-11-21.
 *
 * Sem anexos: decisão do operador (o backend do Hive não tem upload). Por isso
 * o ModelSelector entra no addon block-end com `ml-auto` antes do submit.
 */
export interface PromptInputProps {
  value: string
  onChange: (value: string) => void
  onSubmit: () => void
  label: string
  placeholder: string
  sendLabel: string
  sendingLabel: string
  hint?: string
  disclaimer?: string
  warning?: string | null
  disabled?: boolean
  sending?: boolean
  /** Seletor de modelo/provedor injetado no addon block-end. */
  modelSlot?: ReactNode
  className?: string
}

export function PromptInput({
  value,
  onChange,
  onSubmit,
  label,
  placeholder,
  sendLabel,
  sendingLabel,
  hint,
  disclaimer,
  warning = null,
  disabled = false,
  sending = false,
  modelSlot,
  className,
}: PromptInputProps) {
  const formRef = useRef<HTMLFormElement>(null)
  const canSend = value.trim().length > 0 && !disabled

  const submit = () => {
    const form = formRef.current
    if (!form) return
    if (typeof form.requestSubmit === "function") form.requestSubmit()
    else form.dispatchEvent(new Event("submit", { bubbles: true, cancelable: true }))
  }

  return (
    <form
      ref={formRef}
      data-testid="chat-form"
      className={cn("flex w-full shrink-0 flex-col gap-1.5", className)}
      onSubmit={(event) => {
        event.preventDefault()
        if (canSend) onSubmit()
      }}
    >
      {warning ? (
        <p
          role="alert"
          aria-live="assertive"
          className="flex items-start gap-2 rounded-lg border border-destructive/40 bg-destructive/10 px-3 py-2 text-xs text-destructive"
        >
          <AlertCircle className="mt-0.5 size-3.5 shrink-0" aria-hidden="true" />
          <span>{warning}</span>
        </p>
      ) : null}

      <InputGroup className="rounded-2xl border border-border/60 bg-muted/40 transition-[border-color,box-shadow] duration-200 focus-within:border-ring/70 focus-within:ring-[3px] focus-within:ring-ring/20 dark:bg-input/35">
        <label htmlFor="chat-message" className="sr-only">
          {label}
        </label>
        <InputGroupTextarea
          id="chat-message"
          data-testid="chat-input"
          aria-label={label}
          rows={2}
          value={value}
          disabled={disabled}
          placeholder={placeholder}
          onChange={(event) => onChange(event.target.value)}
          onKeyDown={(event) => {
            // Enter confirma a composição IME (CJK); não deve enviar a mensagem.
            if (event.nativeEvent.isComposing) return
            if (event.key === "Enter" && !event.shiftKey) {
              event.preventDefault()
              submit()
            }
          }}
          className="max-h-40 min-h-[52px] w-full px-4 pt-3.5 pb-1 text-sm leading-relaxed placeholder:text-muted-foreground/70"
        />
        <InputGroupAddon align="block-end" className="items-center justify-between gap-2 px-3 pt-1 pb-2.5">
          {hint ? (
            <p className="hidden min-w-0 truncate text-[11px] text-muted-foreground/75 sm:block">
              {hint}
            </p>
          ) : null}
          <div className="flex min-w-0 flex-1 items-center justify-end gap-1.5 sm:flex-initial">
            {modelSlot ? (
              <div className="ml-auto flex min-w-0 items-center gap-1.5">{modelSlot}</div>
            ) : null}
            <InputGroupButton
              type="submit"
              data-testid="chat-send"
              disabled={!canSend}
              size="icon-sm"
              variant="default"
              aria-label={sending ? sendingLabel : sendLabel}
              className="size-8 shrink-0 rounded-full"
            >
              {sending ? (
                <LoaderCircle className="size-4 animate-spin" aria-hidden="true" />
              ) : (
                <ArrowUpIcon className="size-4" aria-hidden="true" />
              )}
              <span className="sr-only">{sending ? sendingLabel : sendLabel}</span>
            </InputGroupButton>
          </div>
        </InputGroupAddon>
      </InputGroup>

      {disclaimer ? (
        <p
          title={disclaimer}
          className="line-clamp-2 px-2 text-center text-[11px] leading-snug text-muted-foreground/80"
        >
          {disclaimer}
        </p>
      ) : null}
    </form>
  )
}

export default PromptInput
