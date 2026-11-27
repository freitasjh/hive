"use client"

import { useEffect, useRef, useState } from "react"
import {
  AlertCircle,
  ArrowDown,
  ArrowUpRight,
  Bot,
  Check,
  Copy,
  Hexagon,
  LoaderCircle,
  RotateCcw,
  User,
} from "lucide-react"
import { Reasoning } from "@/components/shadcn/ai/ai-elements"
import { Avatar, AvatarFallback } from "@/components/shadcn/ui/avatar"
import { Bubble, BubbleContent } from "@/components/shadcn/ui/bubble"
import { Button } from "@/components/shadcn/ui/button"
import {
  Message,
  MessageAvatar,
  MessageContent,
  MessageFooter,
} from "@/components/shadcn/ui/message"
import {
  MessageScroller,
  MessageScrollerButton,
  MessageScrollerContent,
  MessageScrollerItem,
  MessageScrollerProvider,
  MessageScrollerViewport,
  useMessageScroller,
} from "@/components/shadcn/ui/message-scroller"
import { Skeleton } from "@/components/shadcn/ui/skeleton"
import {
  Tooltip,
  TooltipContent,
  TooltipProvider,
  TooltipTrigger,
} from "@/components/shadcn/ui/tooltip"
import { cn } from "@/lib/cn"
import type { ChatMessageDTO } from "@/services/chat.types"
import { STARTER_PROMPTS } from "../data/chat.data"
import { useChatI18n } from "../i18n"
import { ChatMarkdown } from "./ChatMarkdown"

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
  /** Remonta o provider ao trocar de conversa: reopen em `last-anchor` (regra 11). */
  sessionKey?: string
  /** messageId alvo de um salto vindo da busca na sidebar. */
  targetMessageId?: string | null
  /** Avisa o controller para limpar o alvo após o scroll (evita jump repetido). */
  onTargetHandled?: () => void
}

type Role = "USER" | "ASSISTANT" | "SYSTEM"

function roleOf(role: string): Role {
  const upper = role.toUpperCase()
  if (upper === "USER") return "USER"
  if (upper === "ASSISTANT") return "ASSISTANT"
  return "SYSTEM"
}

/**
 * Vive DENTRO do MessageScrollerProvider: `useMessageScroller` exige o contexto.
 * Só salta quando o messageId já está montado — o primitive registra os itens
 * por ref; se a mensagem ainda não chegou, o alvo permanece pendente no
 * controller até o histórico carregar. No @shadcn/react 0.3.1, sem
 * `keepPreviousPeek`, o scrollToMessage entra em `settling-jump` e mantém a
 * posição ancorada na mensagem — sem sequestrar quem scrollou manualmente.
 */
function ScrollToTarget({
  targetMessageId,
  messages,
  onHandled,
}: {
  targetMessageId?: string | null
  messages: ChatMessageDTO[]
  onHandled?: () => void
}) {
  const { scrollToMessage } = useMessageScroller()
  const available = !!targetMessageId && messages.some((item) => item.id === targetMessageId)

  useEffect(() => {
    if (!targetMessageId || !available) return
    scrollToMessage(targetMessageId, { align: "start", behavior: "smooth" })
    onHandled?.()
  }, [targetMessageId, available, scrollToMessage, onHandled])

  return null
}

function LoadingSkeleton({ label }: { label: string }) {
  return (
    <div data-testid="thread-loading" aria-busy="true" className="flex w-full flex-col gap-4">
      <div className="flex items-center gap-2 text-xs text-muted-foreground">
        <LoaderCircle className="size-3.5 animate-spin" aria-hidden="true" />
        {label}
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
  )
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
  sessionKey = "new",
  targetMessageId = null,
  onTargetHandled,
}: ChatThreadProps) {
  const { t } = useChatI18n()
  // Histórico de uma sessão recém-aberta chega depois do mount. Montar o provider
  // vazio aplicaria `last-anchor` antes das mensagens existirem e a tela abriria
  // no fim (regra 11 da doc MessageScroller). Enquanto carrega, mostra o skeleton
  // fora do scroller; paginação (mensagens já presentes) não remonta nada.
  const deferredHistory = loading && messages.length === 0 && !generating
  const [copiedId, setCopiedId] = useState<string | null>(null)
  const copyTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null)

  useEffect(() => {
    return () => {
      if (copyTimerRef.current) clearTimeout(copyTimerRef.current)
    }
  }, [])

  const copy = (id: string, text: string) => {
    try {
      void navigator.clipboard?.writeText(text)
    } catch {
      /* clipboard indisponível: ação é best-effort */
    }
    setCopiedId(id)
    if (copyTimerRef.current) clearTimeout(copyTimerRef.current)
    copyTimerRef.current = setTimeout(() => setCopiedId(null), 2000)
  }

  return (
    <TooltipProvider delayDuration={300}>
      <div
        data-testid="chat-thread"
        className="flex min-h-0 flex-1 flex-col"
      >
        <div className="flex min-h-0 flex-1 flex-col overflow-hidden">
          {deferredHistory ? (
            <div
              role="log"
              aria-label={t("chat.thread.aria")}
              aria-live="polite"
              aria-busy="true"
              className="flex min-h-0 flex-1 flex-col px-5 py-5"
            >
              <LoadingSkeleton label={t("chat.thread.loading")} />
            </div>
          ) : (
            <MessageScrollerProvider key={sessionKey} autoScroll defaultScrollPosition="last-anchor">
              <MessageScroller className="flex-1">
                <MessageScrollerViewport
                  role="region"
                  aria-label={t("chat.thread.viewport")}
                >
                  <MessageScrollerContent
                    aria-label={t("chat.thread.aria")}
                    aria-live="polite"
                    aria-busy={generating || loading}
                    className="w-full gap-5 px-5 py-5"
                  >
                    {loading ? (
                      <MessageScrollerItem messageId="history-loading">
                        <LoadingSkeleton label={t("chat.thread.loading")} />
                      </MessageScrollerItem>
                    ) : null}

                    {!loading && error ? (
                      <MessageScrollerItem messageId="history-error">
                        <div role="alert" className="flex items-start gap-2 rounded-lg border border-destructive/40 bg-destructive/10 px-3 py-2 text-sm text-destructive">
                          <AlertCircle className="mt-0.5 size-4 shrink-0" aria-hidden="true" />
                          <span>{error}</span>
                        </div>
                      </MessageScrollerItem>
                    ) : null}

                    {!loading && !error && messages.length === 0 && !generating ? (
                      <MessageScrollerItem messageId="empty">
                        <div className="flex min-h-56 flex-1 flex-col items-center justify-center gap-4 py-8 text-center">
                          <div className="flex size-12 items-center justify-center rounded-2xl border border-border/70 bg-card text-primary shadow-xs">
                            <Hexagon className="size-5" aria-hidden="true" />
                          </div>
                          <div className="flex max-w-md flex-col gap-1">
                            <p className="text-base font-semibold tracking-tight text-foreground">
                              {t("chat.thread.empty")}
                            </p>
                            <p className="text-xs text-muted-foreground">
                              {t("chat.thread.hint")}
                            </p>
                          </div>
                          <div
                            role="group"
                            aria-label={t("chat.thread.starters")}
                            className="grid w-full max-w-xl grid-cols-1 gap-2 sm:grid-cols-2"
                          >
                            {starterPrompts.map((starter) => (
                              <Button
                                key={starter}
                                type="button"
                                variant="outline"
                                size="sm"
                                disabled={!canUseStarter}
                                onClick={() => onStarter(starter)}
                                className="h-auto min-h-10 justify-between gap-2 whitespace-normal rounded-xl border-border/70 bg-card/80 px-3.5 py-2.5 text-left text-xs font-normal text-foreground shadow-none hover:bg-accent"
                              >
                                <span>{starter}</span>
                                <ArrowUpRight className="size-3.5 shrink-0 text-muted-foreground" aria-hidden="true" />
                              </Button>
                            ))}
                          </div>
                        </div>
                      </MessageScrollerItem>
                    ) : null}

                    {canLoadOlder && !loading ? (
                      <MessageScrollerItem messageId="older">
                        <div className="flex justify-center">
                          <Button
                            type="button"
                            variant="outline"
                            size="sm"
                            onClick={onLoadOlder}
                            className="h-8 rounded-full px-3 text-xs text-muted-foreground"
                          >
                            {t("chat.thread.older")}
                          </Button>
                        </div>
                      </MessageScrollerItem>
                    ) : null}

                    {messages.map((message, index) => {
                      const role = roleOf(message.role)
                      const labelKey = role === "USER"
                        ? "chat.thread.you"
                        : role === "SYSTEM"
                          ? "chat.thread.system"
                          : "chat.thread.assistant"
                      return (
                        <MessageScrollerItem
                          key={message.id}
                          messageId={message.id}
                          scrollAnchor={role === "USER"}
                        >
                          <Message
                            align={role === "USER" ? "end" : "start"}
                            aria-label={t(labelKey)}
                            data-chat-role={role}
                          >
                            <MessageAvatar>
                              <Avatar size="sm">
                                <AvatarFallback
                                  className={cn(
                                    role === "USER"
                                      ? "bg-primary/10 text-primary"
                                      : role === "SYSTEM"
                                        ? "bg-destructive/10 text-destructive"
                                        : "border border-border/70 bg-card text-foreground"
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
                            </MessageAvatar>
                            <MessageContent className="gap-2">
                              {role === "SYSTEM" ? (
                                <span className="text-[11px] font-medium text-destructive">
                                  {t(labelKey)}
                                </span>
                              ) : null}
                              {role === "USER" ? (
                                <Bubble variant="muted" align="end" data-chat-bubble="USER">
                                  <BubbleContent>{message.content}</BubbleContent>
                                </Bubble>
                              ) : role === "SYSTEM" ? (
                                <div
                                  data-chat-bubble="SYSTEM"
                                  className="max-w-full rounded-2xl border border-destructive/30 bg-destructive/10 px-4 py-2.5 text-sm text-destructive"
                                >
                                  {message.content}
                                </div>
                              ) : (
                                <>
                                  {message.reasoning ? (
                                    <Reasoning
                                      durationSeconds={0}
                                      steps={message.reasoning.split(/\n+/).filter(Boolean)}
                                      label={t("chat.thread.reasoning")}
                                      showMeta={false}
                                      defaultOpen={generating && index === messages.length - 1}
                                    />
                                  ) : null}
                                  <ChatMarkdown
                                    content={message.content}
                                    isAnimating={generating && index === messages.length - 1}
                                  />
                                </>
                              )}
                              {role === "ASSISTANT" ? (
                                <MessageFooter className="gap-1 px-0">
                                  <Tooltip>
                                    <TooltipTrigger asChild>
                                      <Button
                                        type="button"
                                        variant="ghost"
                                        size="icon-sm"
                                        aria-label={t("chat.thread.copyMessage")}
                                        onClick={() => copy(message.id, message.content)}
                                        data-testid="chat-message-copy"
                                      >
                                        {copiedId === message.id ? (
                                          <Check className="size-3.5 text-emerald-500" aria-hidden="true" />
                                        ) : (
                                          <Copy className="size-3.5" aria-hidden="true" />
                                        )}
                                      </Button>
                                    </TooltipTrigger>
                                    <TooltipContent>
                                      {copiedId === message.id ? t("chat.thread.copied") : t("chat.thread.copy")}
                                    </TooltipContent>
                                  </Tooltip>
                                  <Tooltip>
                                    <TooltipTrigger asChild>
                                      <Button
                                        type="button"
                                        variant="ghost"
                                        size="icon-sm"
                                        aria-label={t("chat.thread.retry")}
                                        onClick={onRetry}
                                        disabled={!retryable || generating}
                                        data-testid="chat-message-retry"
                                      >
                                        <RotateCcw className="size-3.5" aria-hidden="true" />
                                      </Button>
                                    </TooltipTrigger>
                                    <TooltipContent>{t("chat.thread.retry")}</TooltipContent>
                                  </Tooltip>
                                </MessageFooter>
                              ) : null}
                            </MessageContent>
                          </Message>
                        </MessageScrollerItem>
                      )
                    })}

                    {generating ? (
                      <MessageScrollerItem messageId="generating">
                        <div className="mr-auto flex min-w-48 flex-col gap-2" aria-busy="true">
                          <div className="flex items-center gap-2 text-xs text-muted-foreground">
                            <LoaderCircle className="size-3.5 animate-spin text-primary" aria-hidden="true" />
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
                      </MessageScrollerItem>
                    ) : null}

                    {retryable && !generating ? (
                      <MessageScrollerItem messageId="retry">
                        <div className="flex justify-start">
                          <Button type="button" variant="outline" size="sm" onClick={onRetry} className="min-h-9">
                            {t("chat.thread.retry")}
                          </Button>
                        </div>
                      </MessageScrollerItem>
                    ) : null}
                  </MessageScrollerContent>
                </MessageScrollerViewport>
                <MessageScrollerButton>
                  <ArrowDown className="size-4" aria-hidden="true" />
                  <span className="sr-only">{t("chat.thread.jumpToEnd")}</span>
                </MessageScrollerButton>
              </MessageScroller>
              <ScrollToTarget
                targetMessageId={targetMessageId}
                messages={messages}
                onHandled={onTargetHandled}
              />
            </MessageScrollerProvider>
          )}
        </div>
      </div>
    </TooltipProvider>
  )
}

export default ChatThread
