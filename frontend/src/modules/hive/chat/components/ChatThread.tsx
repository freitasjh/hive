"use client"

import { useLayoutEffect, useRef } from "react"
import { AlertCircle, Bot, LoaderCircle, Sparkles, User } from "lucide-react"
import { Avatar, AvatarFallback } from "@/components/shadcn/ui/avatar"
import { Badge } from "@/components/shadcn/ui/badge"
import { Button } from "@/components/shadcn/ui/button"
import { Card } from "@/components/shadcn/ui/card"
import { ScrollArea } from "@/components/shadcn/ui/scroll-area"
import { Skeleton } from "@/components/shadcn/ui/skeleton"
import { cn } from "@/lib/cn"
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

type Role = "USER" | "ASSISTANT" | "SYSTEM"

function roleOf(role: string): Role {
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
  const rootRef = useRef<HTMLDivElement>(null)
  const viewportRef = useRef<HTMLElement | null>(null)
  const atBottomRef = useRef(true)

  // ScrollArea expõe o viewport interno via data-slot; ref local evita lib nova.
  // useLayoutEffect (declarado antes do efeito de rolagem) garante o viewport
  // disponível já na montagem, mesmo com histórico pré-carregado.
  useLayoutEffect(() => {
    const viewport = rootRef.current?.querySelector<HTMLElement>('[data-slot="scroll-area-viewport"]') ?? null
    viewportRef.current = viewport
    if (!viewport) return
    const onScroll = () => {
      atBottomRef.current = viewport.scrollHeight - viewport.scrollTop - viewport.clientHeight <= 24
    }
    viewport.addEventListener("scroll", onScroll, { passive: true })
    return () => viewport.removeEventListener("scroll", onScroll)
  }, [])

  //nova mensagem só rola o thread se o usuário já estava no fim.
  useLayoutEffect(() => {
    const viewport = viewportRef.current
    if (!viewport || !atBottomRef.current) return
    viewport.scrollTop = viewport.scrollHeight
  }, [messages.length, generating, loading, error])

  return (
    <div
      ref={rootRef}
      role="log"
      aria-live="polite"
      aria-relevant="additions"
      aria-label={t("chat.thread.aria")}
      data-testid="chat-thread"
      className="flex min-h-0 flex-1 flex-col"
    >
      <Card className="flex min-h-0 flex-1 gap-0 overflow-hidden rounded-xl py-0">
        <ScrollArea className="min-h-0 flex-1">
          <div className="flex min-h-full flex-col gap-4 px-4 py-4">
            {loading ? (
              <div data-testid="thread-loading" aria-busy="true" className="flex flex-col gap-3">
                <div className="flex items-center gap-2 text-sm text-muted-foreground">
                  <LoaderCircle className="size-4 animate-spin" aria-hidden="true" />
                  {t("chat.thread.loading")}
                </div>
                <div className="flex items-start gap-2.5">
                  <Skeleton className="size-8 shrink-0 rounded-full" />
                  <Skeleton className="h-16 w-2/3 rounded-2xl" />
                </div>
                <div className="flex flex-row-reverse items-start gap-2.5">
                  <Skeleton className="size-8 shrink-0 rounded-full" />
                  <Skeleton className="h-10 w-1/2 rounded-2xl" />
                </div>
              </div>
            ) : null}

            {!loading && error ? (
              <div role="alert" className="flex items-start gap-2 rounded-lg border border-destructive/40 bg-destructive/10 px-3 py-2 text-sm text-destructive">
                <AlertCircle className="mt-0.5 size-4 shrink-0" aria-hidden="true" />
                <span>{error}</span>
              </div>
            ) : null}

            {!loading && !error && messages.length === 0 && !generating ? (
              <div className="flex min-h-48 flex-1 flex-col items-center justify-center gap-3 py-8 text-center">
                <div className="flex size-12 items-center justify-center rounded-xl bg-primary/10 text-primary">
                  <Sparkles className="size-6" aria-hidden="true" />
                </div>
                <p className="text-base font-semibold text-foreground">{t("chat.thread.empty")}</p>
                <p className="text-xs text-muted-foreground">{t("chat.thread.hint")}</p>
                <div role="group" aria-label={t("chat.thread.starters")} className="flex flex-wrap justify-center gap-2">
                  {starterPrompts.map((starter) => (
                    <Button
                      key={starter}
                      type="button"
                      variant="outline"
                      size="sm"
                      disabled={!canUseStarter}
                      onClick={() => onStarter(starter)}
                      className="min-h-9 rounded-full"
                    >
                      {starter}
                    </Button>
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
                  className={cn("flex items-start gap-2.5", role === "USER" && "flex-row-reverse")}
                >
                  <Avatar size="sm" className="mt-0.5">
                    <AvatarFallback
                      className={cn(
                        role === "USER"
                          ? "bg-primary/10 text-primary"
                          : role === "SYSTEM"
                            ? "bg-destructive/10 text-destructive"
                            : "bg-secondary text-secondary-foreground"
                      )}
                    >
                      {role === "USER" ? (
                        <User className="size-3.5" aria-hidden="true" />
                      ) : role === "SYSTEM" ? (
                        <AlertCircle className="size-3.5" aria-hidden="true" />
                      ) : (
                        <Bot className="size-3.5" aria-hidden="true" />
                      )}
                    </AvatarFallback>
                  </Avatar>
                  <div className={cn("flex max-w-[85%] min-w-0 flex-col gap-1", role === "USER" ? "items-end" : "items-start")}>
                    <Badge variant="secondary" className="h-5 gap-1 px-2 text-[10px] font-medium uppercase tracking-wide text-muted-foreground">
                      {t(labelKey)}
                    </Badge>
                    <div
                      data-chat-bubble={role}
                      className={cn(
                        "whitespace-pre-wrap break-words rounded-2xl px-4 py-2.5 text-sm leading-relaxed",
                        role === "USER"
                          ? "rounded-tr-sm bg-primary text-primary-foreground"
                          : role === "SYSTEM"
                            ? "border border-destructive/30 bg-destructive/10 text-destructive"
                            : "rounded-tl-sm bg-muted text-foreground"
                      )}
                    >
                      {message.content}
                    </div>
                  </div>
                </article>
              )
            })}

            {generating ? (
              <div className="mr-auto flex min-w-48 flex-col gap-2" aria-busy="true">
                <div className="flex items-center gap-2 text-xs italic text-muted-foreground">
                  <LoaderCircle className="size-3.5 animate-spin" aria-hidden="true" />
                  {t("chat.thread.generating")}
                </div>
                <div
                  role="progressbar"
                  aria-label={t("chat.thread.generating")}
                  aria-valuemin={0}
                  aria-valuemax={100}
                  className="h-1.5 w-full overflow-hidden rounded-full bg-muted"
                >
                  <Skeleton className="h-full w-2/5 rounded-full bg-primary" />
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
          </div>
        </ScrollArea>
      </Card>
    </div>
  )
}

export default ChatThread
