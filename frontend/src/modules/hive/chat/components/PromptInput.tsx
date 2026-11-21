"use client"

import { useRef, type ReactNode } from "react"
import { AlertCircle, ArrowUpIcon, LoaderCircle } from "lucide-react"
import { InputGroup, InputGroupAddon, InputGroupButton, InputGroupTextarea } from "@/components/shadcn/ui/input-group"
import { cn } from "@/lib/cn"

/**
 * Prompt Input no padrão oficial do shadcn (InputGroup + InputGroupTextarea +
 * InputGroupAddon block-end + submit rounded-full com ArrowUpIcon).
 * Fonte: https://ui.shadcn.com/docs/ai-elements/base/prompt-input — 2026-11-20.
 *
 * Sem anexos: decisão do operador (o backend do Hive não tem upload). Por isso
 * o ModelSelector entra como primeiro controle do addon e recebe `ml-auto`,
 * no lugar do botão de anexo da doc.
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
      className={cn("flex shrink-0 flex-col gap-2", className)}
      onSubmit={(event) => {
        event.preventDefault()
        if (canSend) onSubmit()
      }}
    >
      <InputGroup className="rounded-xl px-2 py-1.5">
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
          className="min-h-14 p-3 text-sm"
        />
        <InputGroupAddon align="block-end" className="justify-between gap-2">
          {hint || disclaimer ? (
            <div className="flex min-w-0 flex-col">
              {hint ? (
                <p className="truncate text-[11px] leading-tight text-muted-foreground">{hint}</p>
              ) : null}
              {disclaimer ? (
                <p title={disclaimer} className="line-clamp-2 text-[11px] leading-tight text-muted-foreground">
                  {disclaimer}
                </p>
              ) : null}
            </div>
          ) : null}
          <div className="flex shrink-0 items-center gap-1.5">
            {modelSlot ? <div className="ml-auto flex min-w-0 items-center gap-1.5">{modelSlot}</div> : null}
            <InputGroupButton
              type="submit"
              data-testid="chat-send"
              disabled={!canSend}
              size="icon-sm"
              variant="default"
              aria-label={sending ? sendingLabel : sendLabel}
              className="min-h-9 gap-1.5 rounded-full px-3"
            >
              {sending ? (
                <LoaderCircle className="size-4 animate-spin" aria-hidden="true" />
              ) : (
                <ArrowUpIcon className="size-4" aria-hidden="true" />
              )}
              <span className="hidden sm:inline">{sending ? sendingLabel : sendLabel}</span>
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

export default PromptInput
