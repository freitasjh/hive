"use client"

import { ChevronLeft, ChevronRight, Pencil, Plus, Search, Trash2 } from "lucide-react"
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
import { Input } from "@/components/shadcn/ui/input"
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/shadcn/ui/select"
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/shadcn/ui/table"
import type { PromptStatus } from "@/services/admin.types"
import { EmptyState, ErrorBanner, LoadingBlock, NoticeBanner } from "../components/AdminStates"
import { useAdminI18n } from "../i18n"
import type { PromptsActions, PromptsState } from "../state"

export interface PromptsViewProps {
  state: PromptsState
  actions: PromptsActions
}

const STATUSES: readonly PromptStatus[] = ["DRAFT", "ACTIVE", "ARCHIVED"]

export function PromptsView({ state, actions }: PromptsViewProps) {
  const { t } = useAdminI18n()
  const totalPages = Math.max(1, Math.ceil(state.total / Math.max(1, state.size)))
  const canGoPrev = state.page > 0
  const canGoNext = state.page + 1 < totalPages

  return (
    <section className="flex flex-col gap-5" aria-labelledby="admin-prompts-title">
      <div className="flex flex-col gap-1">
        <h1 id="admin-prompts-title" className="text-xl font-bold tracking-tight text-foreground sm:text-2xl">
          {t("admin.prompts.title")}
        </h1>
      </div>

      <div className="flex flex-wrap items-center gap-3">
        <div className="flex min-w-0 flex-1 items-center gap-2 sm:max-w-sm">
          <Input
            data-testid="search-q"
            value={state.q}
            placeholder={t("admin.prompts.searchPlaceholder")}
            onChange={(event) => actions.onQueryChange(event.target.value)}
            onKeyDown={(event) => {
              if (event.key === "Enter") actions.onSearch()
            }}
            className="h-10 w-full"
          />
          <Button
            type="button"
            variant="outline"
            size="icon"
            aria-label={t("admin.common.search")}
            data-testid="search-btn"
            onClick={actions.onSearch}
          >
            <Search aria-hidden="true" />
          </Button>
        </div>
        <Select
          value={state.status ?? "ALL"}
          onValueChange={(value) => actions.onStatusChange(value === "ALL" ? null : (value as PromptStatus))}
        >
          <SelectTrigger data-testid="filter-status" className="h-10 w-full sm:w-44">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="ALL">{t("admin.prompts.allStatuses")}</SelectItem>
            {STATUSES.map((status) => (
              <SelectItem key={status} value={status}>
                {status}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
        <Button type="button" data-testid="new-prompt-btn" onClick={actions.onNew} className="ml-auto">
          <Plus aria-hidden="true" />
          {t("admin.prompts.new")}
        </Button>
      </div>

      {state.error ? <ErrorBanner message={state.error} onRetry={actions.onRetry} testId="prompts-error" /> : null}
      {state.actionError ? <NoticeBanner message={state.actionError} tone="warn" testId="prompts-action-error" /> : null}
      {state.notice ? <NoticeBanner message={state.notice} tone="success" testId="prompts-notice" /> : null}

      {state.loading ? (
        <LoadingBlock testId="prompts-loading" rows={5} />
      ) : state.items.length === 0 ? (
        <EmptyState
          testId="prompts-empty"
          ctaTestId="empty-cta"
          title={t("admin.prompts.emptyTitle")}
          description={t("admin.prompts.emptyDescription")}
          ctaLabel={t("admin.prompts.emptyCta")}
          onCta={actions.onNew}
        />
      ) : (
        <div className="flex flex-col gap-3">
          <div className="rounded-lg border border-border/70 bg-card">
            <Table data-testid="prompts-table">
              <TableHeader>
                <TableRow>
                  <TableHead>{t("admin.prompts.colName")}</TableHead>
                  <TableHead>{t("admin.prompts.colCode")}</TableHead>
                  <TableHead>{t("admin.prompts.colStatus")}</TableHead>
                  <TableHead>{t("admin.prompts.colTags")}</TableHead>
                  <TableHead>{t("admin.prompts.colCreated")}</TableHead>
                  <TableHead className="text-right">{t("admin.prompts.colActions")}</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {state.items.map((row) => (
                  <TableRow key={row.id}>
                    <TableCell className="font-medium">{row.name}</TableCell>
                    <TableCell className="font-mono text-xs">{row.code}</TableCell>
                    <TableCell>
                      <Badge
                        variant={row.status === "ACTIVE" ? "default" : row.status === "DRAFT" ? "secondary" : "outline"}
                      >
                        {row.status}
                      </Badge>
                    </TableCell>
                    <TableCell className="max-w-52 truncate text-muted-foreground">
                      {row.tags?.length ? row.tags.join(", ") : "—"}
                    </TableCell>
                    <TableCell className="font-mono text-xs text-muted-foreground">
                      {new Date(row.createAt).toLocaleDateString()}
                    </TableCell>
                    <TableCell>
                      <div className="flex justify-end gap-1">
                        <Button
                          type="button"
                          variant="ghost"
                          size="icon"
                          aria-label={t("admin.common.edit")}
                          data-testid={`edit-${row.id}`}
                          onClick={() => actions.onEdit(row.id)}
                        >
                          <Pencil aria-hidden="true" />
                        </Button>
                        <Button
                          type="button"
                          variant="ghost"
                          size="icon"
                          aria-label={t("admin.common.delete")}
                          data-testid={`delete-${row.id}`}
                          onClick={() => actions.onAskDelete(row)}
                        >
                          <Trash2 className="text-destructive" aria-hidden="true" />
                        </Button>
                      </div>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </div>

          <div className="flex items-center justify-between gap-3 text-xs text-muted-foreground">
            <span className="font-mono tabular-nums">
              {state.total === 1
                ? t("admin.prompts.countOne", { n: state.total })
                : t("admin.prompts.countMany", { n: state.total })}
            </span>
            <div className="flex items-center gap-2">
              <Button
                type="button"
                variant="outline"
                size="icon-sm"
                aria-label={t("admin.prompts.prevPage")}
                data-testid="prev-page"
                disabled={!canGoPrev}
                onClick={() => actions.onPageChange(state.page - 1, state.size)}
              >
                <ChevronLeft aria-hidden="true" />
              </Button>
              <span className="font-mono tabular-nums">
                {state.page + 1} / {totalPages}
              </span>
              <Button
                type="button"
                variant="outline"
                size="icon-sm"
                aria-label={t("admin.prompts.nextPage")}
                data-testid="next-page"
                disabled={!canGoNext}
                onClick={() => actions.onPageChange(state.page + 1, state.size)}
              >
                <ChevronRight aria-hidden="true" />
              </Button>
            </div>
          </div>
        </div>
      )}

      <Dialog open={state.deleteTarget !== null} onOpenChange={(open) => !open && actions.onCancelDelete()}>
        <DialogContent
          data-testid="prompt-delete-confirm"
          className="w-full max-h-[92vh] overflow-y-auto overscroll-contain p-5 sm:max-w-[480px] sm:p-6"
        >
          <DialogHeader>
            <DialogTitle>{t("admin.prompts.deleteTitle")}</DialogTitle>
            <DialogDescription>
              {t("admin.prompts.deleteBody", { name: state.deleteTarget?.name ?? "" })}
            </DialogDescription>
          </DialogHeader>
          <DialogFooter className="mt-5 flex flex-row items-center justify-end gap-3 sm:space-x-0">
            <Button type="button" variant="ghost" onClick={actions.onCancelDelete}>
              {t("admin.common.cancel")}
            </Button>
            <Button
              type="button"
              variant="destructive"
              data-testid="confirm-delete"
              disabled={state.deleting}
              onClick={actions.onConfirmDelete}
            >
              {state.deleting ? t("admin.common.deleting") : t("admin.prompts.confirmDelete")}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </section>
  )
}

/** Alias semântico para o nome usado pela tela legada. */
export const PromptsPanel = PromptsView
