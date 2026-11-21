"use client"

import { useState, type FormEvent } from "react"
import { AlertCircle, ChevronLeft, ChevronRight, Pencil, Plus, Search, Trash2 } from "lucide-react"
import { Badge } from "@/components/shadcn/ui/badge"
import { Button } from "@/components/shadcn/ui/button"
import { Card, CardContent, CardHeader } from "@/components/shadcn/ui/card"
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
    <aside aria-label={t("chat.sessions.title")} className="h-full min-h-0">
      <Card className="flex h-full min-h-0 gap-0 overflow-hidden rounded-xl py-0">
        <CardHeader className="flex flex-row items-center justify-between gap-2 px-3 py-2">
          <div className="flex min-w-0 items-center gap-2">
            <h2 className="truncate text-sm leading-none font-semibold">{t("chat.sessions.title")}</h2>
            <Badge variant="secondary" className="tabular-nums">{sessions.length}</Badge>
          </div>
          <Button type="button" variant="outline" size="sm" onClick={onNew} className="min-h-9 shrink-0" data-testid="chat-new">
            <Plus className="size-4" aria-hidden="true" />
            {t("chat.sessions.new")}
          </Button>
        </CardHeader>

        <Separator />

        <div className="flex flex-col gap-1 px-2 pt-2">
          <InputGroup className="h-9 min-h-9 shrink-0">
            <InputGroupAddon align="inline-start">
              <Search className="size-4" aria-hidden="true" />
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
            />
          </InputGroup>
          <p className="text-xs text-muted-foreground">{t("chat.sessions.search.limit")}</p>
        </div>

        <CardContent className="flex min-h-0 flex-1 flex-col px-2 py-2">
          {loading ? (
            <div data-testid="sessions-loading" aria-busy="true" className="flex flex-col gap-2">
              <p className="text-xs text-muted-foreground">{t("chat.sessions.loading")}</p>
              {[0, 1, 2].map((item) => (
                <Skeleton key={item} className="h-9 w-full rounded-md" />
              ))}
            </div>
          ) : null}

          {!loading && error ? (
            <p role="alert" className="flex items-start gap-2 text-xs text-destructive">
              <AlertCircle className="mt-0.5 size-3.5 shrink-0" aria-hidden="true" />
              <span>{error}</span>
            </p>
          ) : null}

          {!loading && !error && sessions.length === 0 && !term ? (
            <div className="flex flex-col gap-1 py-4 text-center">
              <p className="text-sm text-foreground">{t("chat.sessions.empty")}</p>
              <p className="text-xs text-muted-foreground">{t("chat.sessions.emptyHint")}</p>
            </div>
          ) : null}

          {!loading && !error && noResults ? (
            <p role="status" className="py-4 text-center text-xs text-muted-foreground">
              {t("chat.sessions.search.noResults")}
            </p>
          ) : null}

          {!loading && !error && (groups.length > 0 || results.length > 0) ? (
            <ScrollArea className="min-h-0 flex-1">
              <div className="flex flex-col gap-3 pr-2">
                {groups.map((group) => {
                  const label = t(GROUP_LABEL[group.key])
                  return (
                    <section key={group.key} aria-label={label} className="flex flex-col gap-1">
                      <h3 className="px-1 text-xs font-medium text-muted-foreground">{label}</h3>
                      <ul className="flex flex-col gap-1">
                        {group.sessions.map((session) => {
                          const rowLabel = session.title || session.id
                          return (
                          <li key={session.id} className="flex items-center gap-0.5">
                            <button
                              type="button"
                              aria-current={selectedId === session.id ? "true" : undefined}
                              onClick={() => onSelect(session.id)}
                              className={cn(
                                "flex min-h-9 min-w-0 flex-1 items-center gap-2 truncate rounded-md px-2 text-left text-sm outline-none hover:bg-accent hover:text-accent-foreground focus-visible:ring-[3px] focus-visible:ring-ring/50 aria-[current=true]:bg-primary/10 aria-[current=true]:font-medium"
                              )}
                            >
                              <span className="min-w-0 flex-1 truncate">{rowLabel}</span>
                              {group.key === "older" ? (
                                <span className="shrink-0 text-xs text-muted-foreground">
                                  {formatSessionDate(session.updateAt, locale)}
                                </span>
                              ) : null}
                            </button>
                            <TooltipProvider>
                              <Tooltip>
                                <TooltipTrigger asChild>
                                  <Button
                                    type="button"
                                    variant="ghost"
                                    size="icon"
                                    aria-label={`${t("chat.sessions.rename")}: ${rowLabel}`}
                                    onClick={() => openRename(session)}
                                    data-testid="session-rename"
                                    className="min-h-9 shrink-0 text-muted-foreground hover:text-foreground"
                                  >
                                    <Pencil className="size-4" aria-hidden="true" />
                                  </Button>
                                </TooltipTrigger>
                                <TooltipContent>{t("chat.sessions.rename")}</TooltipContent>
                              </Tooltip>
                            </TooltipProvider>
                            <TooltipProvider>
                              <Tooltip>
                                <TooltipTrigger asChild>
                                  <Button
                                    type="button"
                                    variant="ghost"
                                    size="icon"
                                    aria-label={`${t("chat.sessions.delete")}: ${rowLabel}`}
                                    onClick={() => setDeleting(session)}
                                    data-testid="session-delete"
                                    className="min-h-9 shrink-0 text-muted-foreground hover:text-destructive"
                                  >
                                    <Trash2 className="size-4" aria-hidden="true" />
                                  </Button>
                                </TooltipTrigger>
                                <TooltipContent>{t("chat.sessions.delete")}</TooltipContent>
                              </Tooltip>
                            </TooltipProvider>
                          </li>
                          )
                        })}
                      </ul>
                    </section>
                  )
                })}

                {results.length > 0 ? (
                  <section aria-label={t("chat.sessions.messagesFound")} className="flex flex-col gap-1 border-t pt-2">
                    <h3 className="px-1 text-xs font-medium text-muted-foreground">
                      {t("chat.sessions.messagesFound")}
                    </h3>
                    <ul className="flex flex-col gap-1">
                      {results.map((result) => (
                        <li key={result.id}>
                          <button
                            type="button"
                            data-testid="session-message-result"
                            aria-label={`${t("chat.sessions.jumpMessage")}: ${result.content.slice(0, 120)}`}
                            onClick={() => onJumpToMessage(result.id)}
                            className="flex min-h-9 w-full items-center gap-2 truncate rounded-md px-2 text-left text-xs outline-none hover:bg-accent hover:text-accent-foreground focus-visible:ring-[3px] focus-visible:ring-ring/50"
                          >
                            <span className="shrink-0 font-medium text-muted-foreground">
                              {t(messageRoleLabel(result.role))}
                            </span>
                            <span className="min-w-0 flex-1 truncate">{result.content}</span>
                          </button>
                        </li>
                      ))}
                    </ul>
                  </section>
                ) : null}
              </div>
            </ScrollArea>
          ) : null}
        </CardContent>

        {/* Paginador só com totalPages > 1 — sem unwrapPage, totalPages é undefined e some. */}
        {totalPages > 1 ? (
          <>
            <Separator />
            <div className="flex items-center justify-between gap-1 px-2 py-1">
              <Button
                type="button"
                variant="ghost"
                size="icon"
                aria-label={t("chat.sessions.prev")}
                disabled={page <= 0}
                onClick={() => onPageChange(page - 1)}
              >
                <ChevronLeft className="size-4" aria-hidden="true" />
              </Button>
              <span className="text-xs text-muted-foreground tabular-nums">
                {t("chat.sessions.page", { n: page + 1, total: totalPages })}
              </span>
              <Button
                type="button"
                variant="ghost"
                size="icon"
                aria-label={t("chat.sessions.next")}
                disabled={page + 1 >= totalPages}
                onClick={() => onPageChange(page + 1)}
              >
                <ChevronRight className="size-4" aria-hidden="true" />
              </Button>
            </div>
          </>
        ) : null}
      </Card>

      <Dialog open={!!renaming} onOpenChange={(open) => { if (!open) setRenaming(null) }}>
        <DialogContent data-testid="session-rename-dialog">
          <DialogHeader>
            <DialogTitle>{t("chat.sessions.rename")}</DialogTitle>
            <DialogDescription>{t("chat.sessions.rename.title")}</DialogDescription>
          </DialogHeader>
          <form onSubmit={submitRename} className="flex flex-col gap-4">
            <InputGroup className="h-9 min-h-9">
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
            <DialogFooter>
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
        <DialogContent data-testid="session-delete-dialog">
          <DialogHeader>
            <DialogTitle>{t("chat.sessions.delete.confirm.title")}</DialogTitle>
            <DialogDescription>{t("chat.sessions.delete.confirm.body")}</DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button
              type="button"
              variant="outline"
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
  )
}

export default SessionSidebar
