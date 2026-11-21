"use client"

import { useState, type FormEvent } from "react"
import {
  AlertCircle,
  ChevronLeft,
  ChevronRight,
  MessageSquare,
  Pencil,
  Plus,
  Search,
  Trash2,
} from "lucide-react"
import { Badge } from "@/components/shadcn/ui/badge"
import { Button } from "@/components/shadcn/ui/button"
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/shadcn/ui/dialog"
import { InputGroup, InputGroupAddon, InputGroupInput } from "@/components/shadcn/ui/input-group"
import { ScrollArea } from "@/components/shadcn/ui/scroll-area"
import { Separator } from "@/components/shadcn/ui/separator"
import { Skeleton } from "@/components/shadcn/ui/skeleton"
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from "@/components/shadcn/ui/tooltip"
import { cn } from "@/lib/cn"
import type { ChatMessageDTO, ChatSessionDTO } from "@/services/chat.types"
import {
  filterSessionsByQuery,
  formatSessionDate,
  groupSessionsByDate,
  type ChatHistoryGroupKey,
} from "../hooks/chat-history"
import { useChatI18n, type ChatMessageKey } from "../i18n"

/** Teto de resultados de mensagem na sidebar — a busca é local, não varre o servidor. */
const MESSAGE_RESULT_LIMIT = 6

/** Primeiro fold: 5 linhas. Abaixo dele, adia layout/pintura sem sair da árvore de a11y. */
const FIRST_FOLD_ROWS = 5
const DEFERRED_ROW_CLASS = "[content-visibility:auto] [contain-intrinsic-size:auto_none_auto_40px]"

const GROUP_LABEL: Record<ChatHistoryGroupKey, ChatMessageKey> = {
  today: "chat.sessions.group.today",
  yesterday: "chat.sessions.group.yesterday",
  last7: "chat.sessions.group.last7",
  older: "chat.sessions.group.older",
}

function messageRoleLabel(role: string): ChatMessageKey {
  const upper = role.toUpperCase()
  if (upper === "USER") return "chat.thread.you"
  if (upper === "SYSTEM") return "chat.thread.system"
  return "chat.thread.assistant"
}

export interface SessionSidebarProps {
  sessions: ChatSessionDTO[]
  selectedId: string | null
  page: number
  totalPages: number
  onSelect: (id: string) => void
  onNew: () => void
  onDelete: (id: string) => void
  onPageChange: (page: number) => void
  loading?: boolean
  error?: string | null
  /** Termo controlado pelo controller. */
  query?: string
  onQueryChange?: (value: string) => void
  /** Resultado já filtrado pelo controller; ausente ⇒ a sidebar filtra `sessions`. */
  filteredSessions?: ChatSessionDTO[]
  /** Mensagens carregadas da sessão ativa que casam com a busca. */
  matchingMessages?: ChatMessageDTO[]
  /** Confirmação de exclusão vive na UI; controller só executa a ação. */
  onRename?: (id: string, title: string) => boolean | void | Promise<boolean | void>
  onJumpToMessage?: (messageId: string) => void
}

export function SessionSidebar({
  sessions,
  selectedId,
  page,
  totalPages,
  onSelect,
  onNew,
  onDelete,
  onPageChange,
  loading = false,
  error = null,
  query = "",
  onQueryChange = () => {},
  filteredSessions,
  matchingMessages = [],
  onRename = () => {},
  onJumpToMessage = () => {},
}: SessionSidebarProps) {
  const { t, locale } = useChatI18n()
  const [renaming, setRenaming] = useState<ChatSessionDTO | null>(null)
  const [renameValue, setRenameValue] = useState("")
  const [renameSaving, setRenameSaving] = useState(false)
  const [deleting, setDeleting] = useState<ChatSessionDTO | null>(null)

  // Fallback local mantém o componente utilizável isolado (stories/testes). Em
  // produção o controller já entrega `filteredSessions`.
  const term = query.trim().toLowerCase()
  const groups = groupSessionsByDate(
    filteredSessions ?? filterSessionsByQuery(sessions, query)
  )
  const results = term ? matchingMessages.slice(0, MESSAGE_RESULT_LIMIT) : []
  const noResults = !!term && groups.length === 0 && results.length === 0

  const openRename = (session: ChatSessionDTO) => {
    setRenameValue(session.title)
    setRenaming(session)
  }

  const submitRename = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (!renaming || !renameValue.trim() || renameSaving) return
    setRenameSaving(true)
    try {
      const ok = await onRename(renaming.id, renameValue.trim())
      if (ok !== false) setRenaming(null)
    } finally {
      setRenameSaving(false)
    }
  }

  const confirmDelete = () => {
    if (!deleting) return
    onDelete(deleting.id)
    setDeleting(null)
  }

  return (
    <TooltipProvider delayDuration={300}>
      <aside aria-label={t("chat.sessions.title")} className="h-full min-h-0">
        <div className="flex h-full min-h-0 flex-col overflow-hidden rounded-xl border border-border/70 bg-card/60">
          {/* Topo: título + ação primária Nova conversa + busca */}
          <div className="flex shrink-0 flex-col gap-2.5 border-b border-border/70 p-3">
            <div className="flex items-center justify-between gap-2">
              <div className="flex min-w-0 items-center gap-2">
                <h2 className="truncate text-xs font-semibold uppercase tracking-wider text-muted-foreground">
                  {t("chat.sessions.title")}
                </h2>
                <Badge variant="secondary" className="h-5 px-1.5 font-mono text-[10px] tabular-nums">
                  {sessions.length}
                </Badge>
              </div>
              <Button
                type="button"
                variant="default"
                size="sm"
                onClick={onNew}
                className="h-8 shrink-0 gap-1.5 rounded-lg px-2.5 text-xs font-medium"
                data-testid="chat-new"
              >
                <Plus className="size-3.5" aria-hidden="true" />
                {t("chat.sessions.new")}
              </Button>
            </div>

            <div className="flex flex-col gap-1">
              <InputGroup className="h-8 min-h-8 shrink-0 rounded-lg border border-border/70 bg-background/80">
                <InputGroupAddon align="inline-start">
                  <Search className="size-3.5 text-muted-foreground" aria-hidden="true" />
                </InputGroupAddon>
                <InputGroupInput
                  id="chat-session-search"
                  name="chat-session-search"
                  type="search"
                  value={query}
                  onChange={(event) => onQueryChange(event.target.value)}
                  placeholder={t("chat.sessions.search.placeholder")}
                  aria-label={t("chat.sessions.search.label")}
                  data-testid="session-search"
                  className="text-xs"
                />
              </InputGroup>
              <p className="px-0.5 text-[10px] leading-snug text-muted-foreground/75">
                {t("chat.sessions.search.limit")}
              </p>
            </div>
          </div>

          {/* Lista de histórico */}
          <div className="flex min-h-0 flex-1 flex-col p-2">
            {loading ? (
              <div data-testid="sessions-loading" aria-busy="true" className="flex flex-col gap-2 p-1">
                <p className="text-xs text-muted-foreground">{t("chat.sessions.loading")}</p>
                {[0, 1, 2, 3].map((item) => (
                  <Skeleton key={item} className="h-9 w-full rounded-lg" />
                ))}
              </div>
            ) : null}

            {!loading && error ? (
              <p role="alert" className="flex items-start gap-2 rounded-lg border border-destructive/30 bg-destructive/10 p-2.5 text-xs text-destructive">
                <AlertCircle className="mt-0.5 size-3.5 shrink-0" aria-hidden="true" />
                <span>{error}</span>
              </p>
            ) : null}

            {!loading && !error && sessions.length === 0 && !term ? (
              <div className="flex flex-1 flex-col items-center justify-center gap-1.5 rounded-lg border border-dashed border-border/70 px-3 py-6 text-center">
                <MessageSquare className="size-5 text-muted-foreground/50" aria-hidden="true" />
                <p className="text-xs font-medium text-foreground">{t("chat.sessions.empty")}</p>
                <p className="text-[11px] text-muted-foreground">{t("chat.sessions.emptyHint")}</p>
              </div>
            ) : null}

            {!loading && !error && noResults ? (
              <p role="status" className="py-6 text-center text-xs text-muted-foreground">
                {t("chat.sessions.search.noResults")}
              </p>
            ) : null}

            {!loading && !error && (groups.length > 0 || results.length > 0) ? (
              <ScrollArea className="min-h-0 w-full flex-1 [&_[data-slot=scroll-area-viewport]>div]:block! [&_[data-slot=scroll-area-viewport]>div]:min-w-0">
                <div className="flex min-w-0 flex-col gap-4 pr-2">
                  {groups.map((group) => {
                    const label = t(GROUP_LABEL[group.key])
                    return (
                      <section key={group.key} aria-label={label} className="flex min-w-0 flex-col gap-1">
                        <h3 className="px-2 pb-0.5 text-[11px] font-medium uppercase tracking-wider text-muted-foreground/75">
                          {label}
                        </h3>
                        <ul className="flex min-w-0 flex-col gap-0.5">
                          {group.sessions.map((session, index) => {
                            const rowLabel = session.title || session.id
                            const isActive = selectedId === session.id
                            return (
                              <li
                                key={session.id}
                                data-active={isActive ? "true" : undefined}
                                className={cn(
                                  "group/row relative flex w-full min-w-0 items-center overflow-hidden rounded-lg transition-colors",
                                  isActive
                                    ? "bg-accent text-accent-foreground"
                                    : "hover:bg-muted/60",
                                  index >= FIRST_FOLD_ROWS && DEFERRED_ROW_CLASS
                                )}
                              >
                                <Tooltip>
                                  <TooltipTrigger asChild>
                                    <button
                                      type="button"
                                      aria-current={isActive ? "true" : undefined}
                                      onClick={() => onSelect(session.id)}
                                      className={cn(
                                        "flex min-h-9 w-full min-w-0 flex-1 cursor-pointer items-center gap-2 overflow-hidden rounded-lg px-2 py-1.5 text-left text-[13px] outline-none transition-colors focus-visible:ring-2 focus-visible:ring-ring/50",
                                        isActive
                                          ? "pr-16 font-medium text-foreground"
                                          : "text-muted-foreground hover:text-foreground"
                                      )}
                                    >
                                      <MessageSquare
                                        className={cn(
                                          "size-3.5 shrink-0",
                                          isActive ? "text-primary" : "text-muted-foreground/60"
                                        )}
                                        aria-hidden="true"
                                      />
                                      <span className="min-w-0 flex-1 truncate">{rowLabel}</span>
                                      {group.key === "older" ? (
                                        <span className="shrink-0 font-mono text-[10px] text-muted-foreground/80 tabular-nums group-hover/row:hidden group-focus-within/row:hidden">
                                          {formatSessionDate(session.updateAt, locale)}
                                        </span>
                                      ) : null}
                                    </button>
                                  </TooltipTrigger>
                                  <TooltipContent side="right" className="max-w-xs break-words">
                                    {rowLabel}
                                  </TooltipContent>
                                </Tooltip>

                                <div
                                  className={cn(
                                    "absolute inset-y-0 right-0 z-10 flex items-center gap-0.5 rounded-r-lg pl-5 pr-1 transition-opacity duration-150",
                                    isActive
                                      ? "bg-gradient-to-l from-accent via-accent/95 to-transparent opacity-100"
                                      : "bg-gradient-to-l from-muted via-muted/95 to-transparent opacity-0 group-hover/row:opacity-100 group-focus-within/row:opacity-100"
                                  )}
                                >
                                  <Tooltip>
                                    <TooltipTrigger asChild>
                                      <Button
                                        type="button"
                                        variant="ghost"
                                        size="icon"
                                        aria-label={`${t("chat.sessions.rename")}: ${rowLabel}`}
                                        onClick={() => openRename(session)}
                                        data-testid="session-rename"
                                        className="size-7 shrink-0 rounded-md text-muted-foreground hover:bg-background/80 hover:text-foreground"
                                      >
                                        <Pencil className="size-3.5" aria-hidden="true" />
                                      </Button>
                                    </TooltipTrigger>
                                    <TooltipContent>{t("chat.sessions.rename")}</TooltipContent>
                                  </Tooltip>
                                  <Tooltip>
                                    <TooltipTrigger asChild>
                                      <Button
                                        type="button"
                                        variant="ghost"
                                        size="icon"
                                        aria-label={`${t("chat.sessions.delete")}: ${rowLabel}`}
                                        onClick={() => setDeleting(session)}
                                        data-testid="session-delete"
                                        className="size-7 shrink-0 rounded-md text-muted-foreground hover:bg-background/80 hover:text-destructive"
                                      >
                                        <Trash2 className="size-3.5" aria-hidden="true" />
                                      </Button>
                                    </TooltipTrigger>
                                    <TooltipContent>{t("chat.sessions.delete")}</TooltipContent>
                                  </Tooltip>
                                </div>
                              </li>
                            )
                          })}
                        </ul>
                      </section>
                    )
                  })}

                  {results.length > 0 ? (
                    <section aria-label={t("chat.sessions.messagesFound")} className="flex flex-col gap-1 border-t border-border/70 pt-3">
                      <h3 className="px-2 pb-0.5 text-[11px] font-medium uppercase tracking-wider text-muted-foreground/75">
                        {t("chat.sessions.messagesFound")}
                      </h3>
                      <ul className="flex flex-col gap-0.5">
                        {results.map((result, index) => (
                          <li
                            key={result.id}
                            className={cn(
                              index >= FIRST_FOLD_ROWS && DEFERRED_ROW_CLASS
                            )}
                          >
                            <button
                              type="button"
                              data-testid="session-message-result"
                              aria-label={`${t("chat.sessions.jumpMessage")}: ${result.content.slice(0, 120)}`}
                              onClick={() => onJumpToMessage(result.id)}
                              className="flex min-h-9 w-full cursor-pointer items-center gap-2 truncate rounded-lg px-2 py-1.5 text-left text-xs outline-none transition-colors hover:bg-muted/60 hover:text-foreground focus-visible:ring-2 focus-visible:ring-ring/50"
                            >
                              <Badge variant="outline" className="h-4 shrink-0 px-1.5 text-[10px] font-normal">
                                {t(messageRoleLabel(result.role))}
                              </Badge>
                              <span className="min-w-0 flex-1 truncate text-muted-foreground">
                                {result.content}
                              </span>
                            </button>
                          </li>
                        ))}
                      </ul>
                    </section>
                  ) : null}
                </div>
              </ScrollArea>
            ) : null}
          </div>

          {/* Paginador só com totalPages > 1 — sem unwrapPage, totalPages é undefined e some. */}
          {totalPages > 1 ? (
            <>
              <Separator />
              <div className="flex shrink-0 items-center justify-between gap-1 px-2 py-1.5">
                <Button
                  type="button"
                  variant="ghost"
                  size="icon"
                  className="size-7"
                  aria-label={t("chat.sessions.prev")}
                  disabled={page <= 0}
                  onClick={() => onPageChange(page - 1)}
                >
                  <ChevronLeft className="size-4" aria-hidden="true" />
                </Button>
                <span className="font-mono text-[11px] text-muted-foreground tabular-nums">
                  {t("chat.sessions.page", { n: page + 1, total: totalPages })}
                </span>
                <Button
                  type="button"
                  variant="ghost"
                  size="icon"
                  className="size-7"
                  aria-label={t("chat.sessions.next")}
                  disabled={page + 1 >= totalPages}
                  onClick={() => onPageChange(page + 1)}
                >
                  <ChevronRight className="size-4" aria-hidden="true" />
                </Button>
              </div>
            </>
          ) : null}
        </div>

        <Dialog open={!!renaming} onOpenChange={(open) => { if (!open) setRenaming(null) }}>
          <DialogContent
            data-testid="session-rename-dialog"
            className="w-full sm:max-w-[440px] max-h-[92vh] overflow-y-auto overscroll-contain p-5 sm:p-6"
          >
            <DialogHeader>
              <DialogTitle>{t("chat.sessions.rename")}</DialogTitle>
              <DialogDescription>{t("chat.sessions.rename.title")}</DialogDescription>
            </DialogHeader>
            <form onSubmit={submitRename} className="flex flex-col gap-4">
              <InputGroup className="h-10 min-h-10 rounded-lg border border-input bg-background">
                <InputGroupInput
                  id="chat-session-rename"
                  name="chat-session-rename"
                  autoFocus
                  value={renameValue}
                  onChange={(event) => setRenameValue(event.target.value)}
                  aria-label={t("chat.sessions.rename.title")}
                  data-testid="session-rename-input"
                />
              </InputGroup>
              <DialogFooter className="mt-2 flex flex-row items-center justify-end gap-3 sm:space-x-0">
                <Button
                  type="button"
                  variant="outline"
                  className="min-h-9"
                  onClick={() => setRenaming(null)}
                >
                  {t("chat.sessions.rename.cancel")}
                </Button>
                <Button type="submit" className="min-h-9" disabled={!renameValue.trim() || renameSaving}>
                  {t("chat.sessions.rename.save")}
                </Button>
              </DialogFooter>
            </form>
          </DialogContent>
        </Dialog>

        <Dialog open={!!deleting} onOpenChange={(open) => { if (!open) setDeleting(null) }}>
          <DialogContent
            data-testid="session-delete-dialog"
            className="w-full sm:max-w-[440px] max-h-[92vh] overflow-y-auto overscroll-contain p-5 sm:p-6"
          >
            <DialogHeader>
              <DialogTitle>{t("chat.sessions.delete.confirm.title")}</DialogTitle>
              <DialogDescription>{t("chat.sessions.delete.confirm.body")}</DialogDescription>
            </DialogHeader>
            <DialogFooter className="mt-4 flex flex-row items-center justify-end gap-3 sm:space-x-0">
              <Button
                type="button"
                variant="outline"
                autoFocus
                className="min-h-9"
                onClick={() => setDeleting(null)}
              >
                {t("chat.sessions.delete.confirm.cancel")}
              </Button>
              <Button
                type="button"
                variant="destructive"
                className="min-h-9"
                onClick={confirmDelete}
                data-testid="session-delete-confirm"
              >
                {t("chat.sessions.delete.confirm.action")}
              </Button>
            </DialogFooter>
          </DialogContent>
        </Dialog>
      </aside>
    </TooltipProvider>
  )
}

export default SessionSidebar
