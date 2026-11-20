"use client"

import { AlertCircle, LoaderCircle, Sparkles } from "lucide-react"
import { Button } from "@/components/shadcn/ui/button"
import type { ChatMessageDTO } from "@/services/chat.types"
import { STARTER_PROMPTS } from "../data/chat.data"
import { useChatI18n } from "../i18n"

export interface ChatThreadProps {
  messages: ChatMessageDTO[]
  onLoadOlder: () => void
  onRetry: () => void
  onStarter: (text: string) => void
  starterPrompts?: readonly string[]
  loading?: boolean
  error?: string | null
  generating?: boolean
  canLoadOlder?: boolean
  retryable?: boolean
  canUseStarter?: boolean
}

function roleOf(role: string): "USER" | "ASSISTANT" | "SYSTEM" {
  const upper = role.toUpperCase()
  if (upper === "USER") return "USER"
  if (upper === "ASSISTANT") return "ASSISTANT"
  return "SYSTEM"
}

export function ChatThread({
  messages,
  onLoadOlder,
  onRetry,
  onStarter,
  starterPrompts = STARTER_PROMPTS,
  loading = false,
  error = null,
  generating = false,
  canLoadOlder = false,
  retryable = false,
  canUseStarter = false,
}: ChatThreadProps) {
  const { t } = useChatI18n()

  return (
    <section
      role="log"
      aria-live="polite"
      aria-relevant="additions"
      aria-label={t("chat.thread.aria")}
      data-testid="chat-thread"
      className="flex min-h-0 flex-1 flex-col gap-3 overflow-y-auto rounded-lg border border-border/70 bg-card px-4 py-4 text-sm text-card-foreground"
    >
      {loading ? (
        <div data-testid="thread-loading" aria-busy="true" className="flex items-center gap-2 text-muted-foreground">
          <LoaderCircle className="size-4 animate-spin" aria-hidden="true" />
          {t("chat.thread.loading")}
        </div>
      ) : null}

      {!loading && error ? (
        <div role="alert" className="flex items-start gap-2 rounded-md border border-destructive/40 bg-destructive/10 px-3 py-2 text-destructive">
          <AlertCircle className="mt-0.5 size-4 shrink-0" aria-hidden="true" />
          <span>{error}</span>
        </div>
      ) : null}

      {!loading && !error && messages.length === 0 && !generating ? (
        <div className="flex flex-1 flex-col items-center justify-center gap-3 py-10 text-center">
          <div className="flex size-12 items-center justify-center rounded-xl bg-primary/10 text-primary">
            <Sparkles className="size-6" aria-hidden="true" />
          </div>
          <p className="text-base font-semibold text-foreground">{t("chat.thread.empty")}</p>
          <p className="text-xs text-muted-foreground">{t("chat.thread.hint")}</p>
          <div role="group" aria-label={t("chat.thread.starters")} className="flex flex-wrap justify-center gap-2">
            {starterPrompts.map((starter) => (
              <button
                key={starter}
                type="button"
                disabled={!canUseStarter}
                onClick={() => onStarter(starter)}
                className="min-h-9 rounded-full border border-border bg-background px-3 text-xs text-foreground outline-none hover:bg-accent hover:text-accent-foreground focus-visible:ring-[3px] focus-visible:ring-ring/50 disabled:cursor-not-allowed disabled:opacity-50"
              >
                {starter}
              </button>
            ))}
          </div>
        </div>
      ) : null}

      {canLoadOlder && !loading ? (
        <div className="flex justify-center">
          <Button type="button" variant="ghost" size="sm" onClick={onLoadOlder} className="min-h-9">
            {t("chat.thread.older")}
          </Button>
        </div>
      ) : null}

      {messages.map((message) => {
        const role = roleOf(message.role)
        // Erro de sistema NÃO é fala do modelo (regra assistant-fluxo-revisavel §2).
        const labelKey = role === "USER"
          ? "chat.thread.you"
          : role === "SYSTEM"
            ? "chat.thread.system"
            : "chat.thread.assistant"
        return (
          <article
            key={message.id}
            aria-label={t(labelKey)}
            className={`flex max-w-[85%] flex-col gap-1 rounded-lg px-3 py-2 ${
              role === "USER"
                ? "ml-auto bg-primary/10 text-foreground"
                : role === "SYSTEM"
                  ? "mr-auto border border-destructive/30 bg-destructive/10 text-destructive"
                  : "mr-auto bg-muted/60 text-foreground"
            }`}
          >
            <span className="text-[10px] font-medium uppercase tracking-wide text-muted-foreground">
              {t(labelKey)}
            </span>
            <p className="whitespace-pre-wrap break-words">{message.content}</p>
          </article>
        )
      })}

      {generating ? (
        <div className="mr-auto flex min-w-48 flex-col gap-2 rounded-lg border border-border bg-muted/40 px-3 py-2" aria-busy="true">
          <span className="text-xs italic text-muted-foreground">{t("chat.thread.generating")}</span>
          <div
            role="progressbar"
            aria-label={t("chat.thread.generating")}
            aria-valuemin={0}
            aria-valuemax={100}
            className="h-1 w-full overflow-hidden rounded-full bg-muted"
          >
            <div className="h-full w-2/5 animate-pulse rounded-full bg-primary" />
          </div>
        </div>
      ) : null}

      {retryable && !generating ? (
        <div className="flex justify-start">
          <Button type="button" variant="outline" size="sm" onClick={onRetry} className="min-h-9">
            {t("chat.thread.retry")}
          </Button>
        </div>
      ) : null}
    </section>
  )
}

export default ChatThread
