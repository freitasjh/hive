"use client"

import { useEffect, useMemo, useState } from "react"
import {
  ChevronLeft,
  ChevronRight,
  Copy,
  ExternalLink,
  KeyRound,
  Plus,
  RotateCw,
  ShieldAlert,
  Trash2,
} from "lucide-react"
import { Badge } from "@/components/shadcn/ui/badge"
import { Button } from "@/components/shadcn/ui/button"
import { Checkbox } from "@/components/shadcn/ui/checkbox"
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/shadcn/ui/dialog"
import { Input } from "@/components/shadcn/ui/input"
import { Label } from "@/components/shadcn/ui/label"
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
import type { PublicApiClient, PublicClientPinMode } from "@/services/admin.types"
import { EmptyState, ErrorBanner, LoadingBlock, NoticeBanner } from "../components/AdminStates"
import { useAdminI18n, type AdminMessageKey } from "../i18n"
import type { OneShotSecret, PublicClientsActions, PublicClientsState } from "../state"
import { PUBLIC_GRANTS } from "./form"

export interface PublicClientsViewProps {
  state: PublicClientsState
  actions: PublicClientsActions
  /** Plaintext one-shot. Nunca faz parte de `state` nem é reconsultado. */
  oneShot: OneShotSecret | null
}

function isFuture(value: string | null): boolean {
  if (!value) return false
  const timestamp = new Date(value).getTime()
  return Number.isFinite(timestamp) && timestamp > Date.now()
}

export function PublicClientsView({ state, actions, oneShot }: PublicClientsViewProps) {
  const { t, locale } = useAdminI18n()
  const [copyStatus, setCopyStatus] = useState<"idle" | "ok" | "fail">("idle")

  useEffect(() => {
    setCopyStatus("idle")
  }, [oneShot?.secret])

  const formatWhen = (value: string | null): string => {
    if (!value) return ""
    const date = new Date(value)
    return Number.isNaN(date.getTime()) ? value : date.toLocaleString(locale)
  }

  // Contrato HTTP não tem parâmetro de busca: filtra a página carregada, sem
  // inventar query param. Paginação continua servida pelo backend (page/size).
  const visible = useMemo(() => {
    const query = state.q.trim().toLowerCase()
    if (!query) return state.items
    return state.items.filter(
      (item) =>
        item.label.toLowerCase().includes(query) || item.clientId.toLowerCase().includes(query)
    )
  }, [state.items, state.q])

  const totalPages = Math.max(1, Math.ceil(state.total / Math.max(1, state.size)))
  const canGoPrev = state.page > 0
  const canGoNext = state.page + 1 < totalPages

  async function copySecret(): Promise<void> {
    if (!oneShot) return
    try {
      await navigator.clipboard.writeText(oneShot.secret)
      setCopyStatus("ok")
    } catch {
      // Sem console: H3 exige console limpo. Falha tem mensagem visível e o texto
      // continua selecionável para cópia manual.
      setCopyStatus("fail")
    }
  }

  return (
    <section className="flex flex-col gap-5" aria-labelledby="admin-public-clients-title">
      <div className="flex flex-col gap-1">
        <h1 id="admin-public-clients-title" className="text-xl font-bold tracking-tight text-foreground sm:text-2xl">
          {t("admin.publicClients.title")}
        </h1>
        <p className="text-xs text-muted-foreground">{t("admin.publicClients.subtitle")}</p>
      </div>

      <div className="flex flex-wrap items-center gap-3">
        <div className="flex min-w-0 flex-1 items-center gap-2 sm:max-w-sm">
          <Input
            data-testid="search-q"
            type="search"
            value={state.q}
            aria-label={t("admin.publicClients.searchPlaceholder")}
            placeholder={t("admin.publicClients.searchPlaceholder")}
            onChange={(event) => actions.onQueryChange(event.target.value)}
            className="h-11 w-full"
          />
        </div>
        <Button type="button" variant="outline" asChild className="min-h-11">
          <a
            href="/hive/scalar#public"
            target="_blank"
            rel="noopener noreferrer"
            data-testid="public-docs-link"
          >
            <ExternalLink aria-hidden="true" />
            {t("admin.publicClients.docsLink")}
          </a>
        </Button>
        <Button
          type="button"
          data-testid="new-client-btn"
          onClick={actions.onOpenCreate}
          className="ml-auto min-h-11"
        >
          <Plus aria-hidden="true" />
          {t("admin.publicClients.new")}
        </Button>
      </div>

      {state.error ? <ErrorBanner message={state.error} onRetry={actions.onRetry} testId="public-clients-error" /> : null}
      {state.notice ? <NoticeBanner message={state.notice} tone="success" testId="public-clients-notice" /> : null}
      {state.actionError ? <NoticeBanner message={state.actionError} tone="warn" testId="public-clients-action-error" /> : null}

      {state.loading ? (
        <LoadingBlock testId="public-clients-loading" rows={5} />
      ) : state.items.length === 0 ? (
        <EmptyState
          testId="public-clients-empty"
          ctaTestId="empty-cta"
          title={t("admin.publicClients.emptyTitle")}
          description={t("admin.publicClients.emptyDescription")}
          ctaLabel={t("admin.publicClients.emptyCta")}
          onCta={actions.onOpenCreate}
        />
      ) : (
        <div className="flex flex-col gap-3">
          <div className="w-full overflow-x-auto rounded-lg border border-border/70 bg-card">
            <Table data-testid="public-clients-table">
              <TableHeader>
                <TableRow>
                  <TableHead>{t("admin.publicClients.colLabel")}</TableHead>
                  <TableHead>{t("admin.publicClients.colClient")}</TableHead>
                  <TableHead>{t("admin.publicClients.colTenant")}</TableHead>
                  <TableHead>{t("admin.publicClients.colGrants")}</TableHead>
                  <TableHead>{t("admin.publicClients.colPrompts")}</TableHead>
                  <TableHead>{t("admin.publicClients.colLimits")}</TableHead>
                  <TableHead>{t("admin.publicClients.colStatus")}</TableHead>
                  <TableHead className="text-right">{t("admin.publicClients.colActions")}</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {visible.map((item) => (
                  <PublicClientRow
                    key={item.clientId}
                    item={item}
                    state={state}
                    actions={actions}
                    formatWhen={formatWhen}
                  />
                ))}
              </TableBody>
            </Table>
          </div>

          <div className="flex items-center justify-between gap-3 text-xs text-muted-foreground">
            <span className="font-mono tabular-nums">
              {state.total === 1
                ? t("admin.publicClients.countOne", { n: state.total })
                : t("admin.publicClients.countMany", { n: state.total })}
            </span>
            <div className="flex items-center gap-2">
              <Button
                type="button"
                variant="outline"
                size="icon"
                aria-label={t("admin.publicClients.prevPage")}
                data-testid="prev-page"
                disabled={!canGoPrev}
                onClick={() => actions.onPageChange(state.page - 1, state.size)}
                className="min-h-11 min-w-11"
              >
                <ChevronLeft aria-hidden="true" />
              </Button>
              <span className="font-mono tabular-nums">
                {state.page + 1} / {totalPages}
              </span>
              <Button
                type="button"
                variant="outline"
                size="icon"
                aria-label={t("admin.publicClients.nextPage")}
                data-testid="next-page"
                disabled={!canGoNext}
                onClick={() => actions.onPageChange(state.page + 1, state.size)}
                className="min-h-11 min-w-11"
              >
                <ChevronRight aria-hidden="true" />
              </Button>
            </div>
          </div>
        </div>
      )}

      <Dialog open={state.createOpen} onOpenChange={(open) => !open && actions.onCloseCreate()}>
        <DialogContent
          data-testid="create-dialog"
          className="max-h-[92vh] w-full overflow-y-auto overscroll-contain p-5 sm:max-w-xl sm:p-6"
        >
          <DialogHeader>
            <DialogTitle>{t("admin.publicClients.createTitle")}</DialogTitle>
            <DialogDescription>{t("admin.publicClients.readonlyHint")}</DialogDescription>
          </DialogHeader>

          <div className="flex flex-col gap-3.5">
            <div className="flex flex-col gap-1.5">
              <Label htmlFor="pc-label">{t("admin.publicClients.fieldLabel")}</Label>
              <Input
                id="pc-label"
                name="label"
                data-testid="input-label"
                value={state.form.label}
                onChange={(event) => actions.onFormChange({ label: event.target.value })}
                className="h-11 w-full"
              />
            </div>
            <fieldset className="flex flex-col gap-1.5">
              <legend className="text-sm font-medium text-foreground">
                {t("admin.publicClients.fieldGrants")}
              </legend>
              {PUBLIC_GRANTS.map((grant) => {
                const id = `pc-grant-${grant}`
                return (
                  <Label
                    key={grant}
                    htmlFor={id}
                    className="min-h-11 cursor-pointer rounded-md border border-border/70 px-3 py-2 font-normal"
                  >
                    <Checkbox
                      id={id}
                      data-testid={`checkbox-grant-${grant}`}
                      checked={state.form.grants.includes(grant)}
                      onCheckedChange={(checked) => actions.onToggleGrant(grant, checked === true)}
                    />
                    <span>{t(`admin.publicClients.grant.${grant}` as AdminMessageKey)}</span>
                  </Label>
                )
              })}
            </fieldset>

            <div className="flex flex-col gap-2">
              <div className="flex items-center justify-between gap-3">
                <span className="text-sm font-medium text-foreground">
                  {t("admin.publicClients.fieldPrompts")}
                </span>
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  data-testid="add-prompt"
                  onClick={actions.onAddPrompt}
                  className="min-h-11"
                >
                  <Plus aria-hidden="true" />
                  {t("admin.publicClients.addPrompt")}
                </Button>
              </div>
              {state.form.prompts.map((prompt, index) => (
                <div
                  key={index}
                  data-testid={`prompt-row-${index}`}
                  className="flex flex-col gap-2 rounded-lg border border-border/70 bg-muted/30 p-3 sm:flex-row sm:items-end"
                >
                  <div className="flex min-w-0 flex-1 flex-col gap-1.5">
                    <Label htmlFor={`pc-prompt-code-${index}`}>{t("admin.publicClients.promptCode")}</Label>
                    <Input
                      id={`pc-prompt-code-${index}`}
                      data-testid={`prompt-code-${index}`}
                      value={prompt.promptCode}
                      onChange={(event) => actions.onPromptChange(index, { promptCode: event.target.value })}
                      className="h-11 w-full font-mono"
                    />
                  </div>
                  <div className="flex min-w-0 flex-col gap-1.5 sm:w-44">
                    <Label htmlFor={`pc-pin-mode-${index}`}>{t("admin.publicClients.pinMode")}</Label>
                    <Select
                      value={prompt.pinMode}
                      onValueChange={(value) =>
                        actions.onPromptChange(index, { pinMode: value as PublicClientPinMode })
                      }
                    >
                      <SelectTrigger
                        id={`pc-pin-mode-${index}`}
                        data-testid={`prompt-pin-mode-${index}`}
                        className="h-11 w-full"
                      >
                        <SelectValue />
                      </SelectTrigger>
                      <SelectContent>
                        <SelectItem value="FOLLOW_ACTIVE">{t("admin.publicClients.pinFollow")}</SelectItem>
                        <SelectItem value="PINNED">{t("admin.publicClients.pinPinned")}</SelectItem>
                      </SelectContent>
                    </Select>
                  </div>
                  {prompt.pinMode === "PINNED" ? (
                    <div className="flex min-w-0 flex-col gap-1.5 sm:w-32">
                      <Label htmlFor={`pc-prompt-version-${index}`}>{t("admin.publicClients.pinnedVersion")}</Label>
                      <Input
                        id={`pc-prompt-version-${index}`}
                        data-testid={`prompt-version-${index}`}
                        type="number"
                        min={1}
                        value={prompt.pinnedVersion ?? ""}
                        onChange={(event) =>
                          actions.onPromptChange(index, {
                            pinnedVersion: event.target.value ? Number(event.target.value) : null,
                          })
                        }
                        className="h-11 w-full font-mono tabular-nums"
                      />
                    </div>
                  ) : null}
                  <Button
                    type="button"
                    variant="ghost"
                    size="icon"
                    aria-label={t("admin.publicClients.removePrompt")}
                    data-testid={`remove-prompt-${index}`}
                    onClick={() => actions.onRemovePrompt(index)}
                    className="min-h-11 min-w-11"
                  >
                    <Trash2 className="text-destructive" aria-hidden="true" />
                  </Button>
                </div>
              ))}
            </div>

            <div className="grid grid-cols-1 gap-x-4 gap-y-3.5 sm:grid-cols-2">
              <NumberField
                id="pc-ttl"
                testId="input-ttl"
                label={t("admin.publicClients.tokenTtl")}
                value={state.form.tokenTtlSeconds}
                min={60}
                max={3600}
                onChange={(value) => actions.onFormChange({ tokenTtlSeconds: value })}
              />
              <NumberField
                id="pc-max-messages"
                testId="input-max-messages"
                label={t("admin.publicClients.maxMessages")}
                value={state.form.maxMessages}
                min={1}
                onChange={(value) => actions.onFormChange({ maxMessages: value })}
              />
              <NumberField
                id="pc-max-chars"
                testId="input-max-chars"
                label={t("admin.publicClients.maxMessageChars")}
                value={state.form.maxMessageChars}
                min={1}
                onChange={(value) => actions.onFormChange({ maxMessageChars: value })}
              />
              <NumberField
                id="pc-max-total"
                testId="input-max-total"
                label={t("admin.publicClients.maxTotalChars")}
                value={state.form.maxTotalChars}
                min={1}
                onChange={(value) => actions.onFormChange({ maxTotalChars: value })}
              />
            </div>

            {state.validationError ? (
              <p
                data-testid="validation-error"
                role="alert"
                className="rounded-lg border border-destructive/40 bg-destructive/10 px-3 py-2 text-sm text-destructive"
              >
                {state.validationError}
              </p>
            ) : null}
          </div>

          <DialogFooter className="mt-5 flex flex-row items-center justify-end gap-3 sm:space-x-0">
            <Button type="button" variant="ghost" onClick={actions.onCloseCreate} className="min-h-11">
              {t("admin.common.cancel")}
            </Button>
            <Button
              type="button"
              data-testid="create-submit"
              disabled={state.creating}
              onClick={actions.onCreate}
              className="min-h-11"
            >
              <KeyRound aria-hidden="true" />
              {state.creating ? t("admin.common.saving") : t("admin.common.create")}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <Dialog open={state.revokeTarget !== null} onOpenChange={(open) => !open && actions.onCancelRevoke()}>
        <DialogContent
          data-testid="revoke-confirm"
          className="max-h-[92vh] w-full overflow-y-auto overscroll-contain p-5 sm:max-w-[480px] sm:p-6"
        >
          <DialogHeader>
            <DialogTitle>{t("admin.publicClients.revokeTitle")}</DialogTitle>
            <DialogDescription>
              {t("admin.publicClients.revokeBody", { label: state.revokeTarget?.label ?? "" })}
            </DialogDescription>
          </DialogHeader>
          <DialogFooter className="mt-5 flex flex-row items-center justify-end gap-3 sm:space-x-0">
            <Button type="button" variant="ghost" onClick={actions.onCancelRevoke} className="min-h-11">
              {t("admin.common.cancel")}
            </Button>
            <Button
              type="button"
              variant="destructive"
              data-testid="confirm-revoke"
              disabled={state.revoking}
              onClick={actions.onConfirmRevoke}
              className="min-h-11"
            >
              <Trash2 aria-hidden="true" />
              {state.revoking ? t("admin.publicClients.revoking") : t("admin.publicClients.confirmRevoke")}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <Dialog open={oneShot !== null} onOpenChange={(open) => !open && actions.onDismissSecret()}>
        <DialogContent
          data-testid="secret-dialog"
          className="max-h-[92vh] w-full overflow-y-auto overscroll-contain p-5 sm:max-w-[480px] sm:p-6"
        >
          <DialogHeader>
            <DialogTitle>
              {oneShot?.scope === "rotate"
                ? t("admin.publicClients.secretTitleRotate")
                : t("admin.publicClients.secretTitleCreate")}
            </DialogTitle>
            <DialogDescription>{t("admin.publicClients.secretWarning")}</DialogDescription>
          </DialogHeader>

          <div role="alert" className="flex gap-2 rounded-lg border border-amber-500/40 bg-amber-500/10 p-3 text-xs text-amber-700 dark:text-amber-400">
            <ShieldAlert className="size-4 shrink-0" aria-hidden="true" />
            <span>{t("admin.publicClients.secretWarning")}</span>
          </div>

          <div className="flex min-w-0 w-full items-center gap-2">
            <div className="min-w-0 flex-1 overflow-hidden rounded-lg border border-border/80 bg-muted/40 px-3 py-2">
              <code data-testid="secret-value" className="block break-all font-mono text-sm text-foreground">
                {oneShot?.secret ?? ""}
              </code>
            </div>
            <Button
              type="button"
              variant="outline"
              size="icon"
              aria-label={t("admin.publicClients.copy")}
              data-testid="secret-copy"
              onClick={() => void copySecret()}
              className="min-h-11 min-w-11 shrink-0"
            >
              <Copy aria-hidden="true" />
            </Button>
          </div>
          {oneShot?.previousSecretExpiresAt ? (
            <p className="text-xs text-muted-foreground">
              {t("admin.publicClients.previousWindow", {
                at: formatWhen(oneShot.previousSecretExpiresAt),
              })}
            </p>
          ) : null}
          {copyStatus !== "idle" ? (
            <p role="status" className="text-xs text-muted-foreground">
              {t(copyStatus === "ok" ? "admin.publicClients.copied" : "admin.publicClients.copyFailed")}
            </p>
          ) : null}

          <DialogFooter className="mt-5 flex flex-row items-center justify-end gap-3 sm:space-x-0">
            <Button
              type="button"
              data-testid="secret-dismiss"
              onClick={actions.onDismissSecret}
              className="min-h-11"
            >
              {t("admin.publicClients.dismissSecret")}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </section>
  )
}

function NumberField({
  id,
  testId,
  label,
  value,
  min,
  max,
  onChange,
}: {
  id: string
  testId: string
  label: string
  value: string
  min: number
  max?: number
  onChange: (value: string) => void
}) {
  return (
    <div className="flex flex-col gap-1.5">
      <Label htmlFor={id}>{label}</Label>
      <Input
        id={id}
        data-testid={testId}
        type="number"
        inputMode="numeric"
        min={min}
        max={max}
        value={value}
        onChange={(event) => onChange(event.target.value)}
        className="h-11 w-full font-mono tabular-nums"
      />
    </div>
  )
}

function PublicClientRow({
  item,
  state,
  actions,
  formatWhen,
}: {
  item: PublicApiClient
  state: PublicClientsState
  actions: PublicClientsActions
  formatWhen: (value: string | null) => string
}) {
  const { t } = useAdminI18n()
  const revoked = Boolean(item.revoked_at)
  const rotating = state.rotatingClientId === item.clientId
  const previousWindow = isFuture(item.previous_secret_expires_at)

  return (
    <TableRow data-testid={`row-${item.clientId}`}>
      <TableCell className="font-medium">{item.label}</TableCell>
      <TableCell className="font-mono text-xs text-muted-foreground">{item.clientId}</TableCell>
      <TableCell className="font-mono text-xs text-muted-foreground">{item.tenantId}</TableCell>
      <TableCell className="max-w-52 truncate text-xs">
        {item.grants.length ? item.grants.join(", ") : "—"}
      </TableCell>
      <TableCell className="max-w-52 truncate text-xs text-muted-foreground">
        {item.prompts.length
          ? item.prompts.map((prompt) => `${prompt.promptCode}:${prompt.pinMode}`).join(", ")
          : t("admin.publicClients.noPrompts")}
      </TableCell>
      <TableCell className="font-mono text-xs tabular-nums text-muted-foreground">
        {item.token_ttl_seconds ?? t("admin.publicClients.noLimits")}
        {item.max_messages ? ` / ${item.max_messages}` : ""}
        {item.max_message_chars ? ` / ${item.max_message_chars}` : ""}
        {item.max_total_chars ? ` / ${item.max_total_chars}` : ""}
      </TableCell>
      <TableCell>
        <div className="flex flex-col items-start gap-1">
          <Badge variant={revoked ? "destructive" : "secondary"}>
            {revoked ? t("admin.publicClients.revoked") : t("admin.publicClients.active")}
          </Badge>
          {previousWindow ? (
            <span className="text-xs text-amber-700 dark:text-amber-400">
              {t("admin.publicClients.previousWindow", {
                at: formatWhen(item.previous_secret_expires_at),
              })}
            </span>
          ) : null}
          {item.expires_at ? (
            <span className="text-xs text-muted-foreground">
              {t("admin.publicClients.expiresAt", { at: formatWhen(item.expires_at) })}
            </span>
          ) : null}
        </div>
      </TableCell>
      <TableCell>
        <div className="flex justify-end gap-1">
          <Button
            type="button"
            variant="ghost"
            size="icon"
            aria-label={t("admin.publicClients.rotate")}
            data-testid={`rotate-${item.clientId}`}
            disabled={revoked || rotating || state.revoking}
            onClick={() => actions.onRotate(item)}
            className="min-h-11 min-w-11"
          >
            <RotateCw className={rotating ? "size-4 animate-spin" : "size-4"} aria-hidden="true" />
          </Button>
          <Button
            type="button"
            variant="ghost"
            size="icon"
            aria-label={t("admin.publicClients.revoke")}
            data-testid={`revoke-${item.clientId}`}
            disabled={revoked || state.revoking}
            onClick={() => actions.onAskRevoke(item)}
            className="min-h-11 min-w-11"
          >
            <Trash2 className="size-4 text-destructive" aria-hidden="true" />
          </Button>
        </div>
      </TableCell>
    </TableRow>
  )
}
