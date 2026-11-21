"use client"

import { useEffect } from "react"
import { AlertCircle, ArrowDown, Bot, Copy, LoaderCircle, RotateCcw, Sparkles, User } from "lucide-react"
import { Avatar, AvatarFallback } from "@/components/shadcn/ui/avatar"
import { Badge } from "@/components/shadcn/ui/badge"
import { Bubble, BubbleContent } from "@/components/shadcn/ui/bubble"
import { Button } from "@/components/shadcn/ui/button"
import { Card } from "@/components/shadcn/ui/card"
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
    <div data-testid="thread-loading" aria-busy="true" className="flex flex-col gap-3">
      <div className="flex items-center gap-2 text-sm text-muted-foreground">
        <LoaderCircle className="size-4 animate-spin" aria-hidden="true" />
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

  const copy = (text: string) => {
    try {
      void navigator.clipboard?.writeText(text)
    } catch {
      /* clipboard indisponível: ação é best-effort */
    }
  }

  return (
    <TooltipProvider>
      <div
        data-testid="chat-thread"
        className="flex min-h-0 flex-1 flex-col"
      >
        <Card className="flex min-h-0 flex-1 gap-0 overflow-hidden rounded-xl py-0">
          {deferredHistory ? (
            <div
              role="log"
              aria-label={t("chat.thread.aria")}
              aria-live="polite"
              aria-busy="true"
              className="flex min-h-0 flex-1 flex-col px-4 py-4"
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
                  className="gap-4 px-4 py-4"
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
                    </MessageScrollerItem>
                  ) : null}

                  {canLoadOlder && !loading ? (
                    <MessageScrollerItem messageId="older">
                      <div className="flex justify-center">
                        <Button type="button" variant="ghost" size="sm" onClick={onLoadOlder} className="min-h-9">
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
                          </MessageAvatar>
                          <MessageContent className="items-start group-data-[align=end]/message:items-end">
                            <Badge variant="secondary" className="h-5 gap-1 px-2 text-[10px] font-medium uppercase tracking-wide text-muted-foreground">
                              {t(labelKey)}
                            </Badge>
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
                              <ChatMarkdown
                                content={message.content}
                                isAnimating={generating && index === messages.length - 1}
                              />
                            )}
                            {role === "ASSISTANT" ? (
                              <MessageFooter className="gap-1">
                                <Tooltip>
                                  <TooltipTrigger asChild>
                                    <Button
                                      type="button"
                                      variant="ghost"
                                      size="icon-xs"
                                      aria-label={t("chat.thread.copyMessage")}
                                      onClick={() => copy(message.content)}
                                      data-testid="chat-message-copy"
                                    >
                                      <Copy className="size-3.5" aria-hidden="true" />
                                    </Button>
                                  </TooltipTrigger>
                                  <TooltipContent>{t("chat.thread.copy")}</TooltipContent>
                                </Tooltip>
                                <Tooltip>
                                  <TooltipTrigger asChild>
                                    <Button
                                      type="button"
                                      variant="ghost"
                                      size="icon-xs"
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
        </Card>
      </div>
    </TooltipProvider>
  )
}

export default ChatThread
