"use client"

import { AlertCircle, ChevronLeft, ChevronRight, Plus, Trash2 } from "lucide-react"
import { Button } from "@/components/shadcn/ui/button"
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
    <aside
      aria-label={t("chat.sessions.title")}
      className="flex min-h-0 flex-col gap-3 rounded-lg border border-border/70 bg-card px-3 py-3 text-card-foreground"
    >
      <div className="flex items-center justify-between gap-2">
        <h2 className="text-sm font-semibold text-foreground">{t("chat.sessions.title")}</h2>
        <Button type="button" variant="outline" size="sm" onClick={onNew} className="min-h-9" data-testid="chat-new">
          <Plus className="size-4" aria-hidden="true" />
          {t("chat.sessions.new")}
        </Button>
      </div>

      {loading ? (
        <p data-testid="sessions-loading" aria-busy="true" className="text-xs text-muted-foreground">
          {t("chat.sessions.loading")}
        </p>
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
        <ul className="flex min-h-0 flex-col gap-1 overflow-y-auto">
          {sessions.map((session) => (
            <li key={session.id} className="flex items-center gap-1">
              <button
                type="button"
                aria-current={selectedId === session.id ? "true" : undefined}
                onClick={() => onSelect(session.id)}
                className="min-h-10 min-w-0 flex-1 truncate rounded-md px-2 text-left text-sm outline-none hover:bg-accent hover:text-accent-foreground focus-visible:ring-[3px] focus-visible:ring-ring/50 aria-[current=true]:bg-primary/10 aria-[current=true]:font-medium"
              >
                {session.title || session.id}
              </button>
              <button
                type="button"
                aria-label={t("chat.sessions.delete")}
                onClick={() => onDelete(session.id)}
                className="flex size-9 shrink-0 items-center justify-center rounded-md text-muted-foreground outline-none hover:bg-destructive/10 hover:text-destructive focus-visible:ring-[3px] focus-visible:ring-ring/50"
              >
                <Trash2 className="size-4" aria-hidden="true" />
              </button>
            </li>
          ))}
        </ul>
      ) : null}

      {/* Paginador só when totalPages > 1 — sem unwrapPage, totalPages é undefined e some. */}
      {totalPages > 1 ? (
        <div className="flex items-center justify-between gap-1 border-t border-border/70 pt-2">
          <Button
            type="button"
            variant="ghost"
            size="icon-sm"
            aria-label={t("chat.sessions.prev")}
            disabled={page <= 0}
            onClick={() => onPageChange(page - 1)}
          >
            <ChevronLeft className="size-4" aria-hidden="true" />
          </Button>
          <span className="text-xs text-muted-foreground">
            {t("chat.sessions.page", { n: page + 1, total: totalPages })}
          </span>
          <Button
            type="button"
            variant="ghost"
            size="icon-sm"
            aria-label={t("chat.sessions.next")}
            disabled={page + 1 >= totalPages}
            onClick={() => onPageChange(page + 1)}
          >
            <ChevronRight className="size-4" aria-hidden="true" />
          </Button>
        </div>
      ) : null}
    </aside>
  )
}

export default SessionSidebar
