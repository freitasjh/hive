"use client"

import { AlertCircle, ChevronLeft, ChevronRight, Plus, Trash2 } from "lucide-react"
import { Badge } from "@/components/shadcn/ui/badge"
import { Button } from "@/components/shadcn/ui/button"
import { Card, CardContent, CardHeader } from "@/components/shadcn/ui/card"
import { ScrollArea } from "@/components/shadcn/ui/scroll-area"
import { Separator } from "@/components/shadcn/ui/separator"
import { Skeleton } from "@/components/shadcn/ui/skeleton"
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from "@/components/shadcn/ui/tooltip"
import { cn } from "@/lib/cn"
import type { ChatSessionDTO } from "@/services/chat.types"
import { useChatI18n } from "../i18n"

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
}: SessionSidebarProps) {
  const { t } = useChatI18n()

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

          {!loading && !error && sessions.length === 0 ? (
            <div className="flex flex-col gap-1 py-4 text-center">
              <p className="text-sm text-foreground">{t("chat.sessions.empty")}</p>
              <p className="text-xs text-muted-foreground">{t("chat.sessions.emptyHint")}</p>
            </div>
          ) : null}

          {!loading && !error && sessions.length > 0 ? (
            <ScrollArea className="min-h-0 flex-1">
              <ul className="flex flex-col gap-1 pr-2">
                {sessions.map((session) => (
                  <li key={session.id} className="flex items-center gap-1">
                    <button
                      type="button"
                      aria-current={selectedId === session.id ? "true" : undefined}
                      onClick={() => onSelect(session.id)}
                      className={cn(
                        "flex min-h-10 min-w-0 flex-1 items-center truncate rounded-md px-2 text-left text-sm outline-none hover:bg-accent hover:text-accent-foreground focus-visible:ring-[3px] focus-visible:ring-ring/50 aria-[current=true]:bg-primary/10 aria-[current=true]:font-medium"
                      )}
                    >
                      {session.title || session.id}
                    </button>
                    <TooltipProvider>
                      <Tooltip>
                        <TooltipTrigger asChild>
                          <Button
                            type="button"
                            variant="ghost"
                            size="icon"
                            aria-label={t("chat.sessions.delete")}
                            onClick={() => onDelete(session.id)}
                            className="shrink-0 text-muted-foreground hover:text-destructive"
                          >
                            <Trash2 className="size-4" aria-hidden="true" />
                          </Button>
                        </TooltipTrigger>
                        <TooltipContent>{t("chat.sessions.delete")}</TooltipContent>
                      </Tooltip>
                    </TooltipProvider>
                  </li>
                ))}
              </ul>
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
    </aside>
  )
}

export default SessionSidebar
