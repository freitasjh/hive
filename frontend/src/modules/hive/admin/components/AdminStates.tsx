"use client"

import { AlertCircle, CheckCircle2, Info, LoaderCircle, RotateCw } from "lucide-react"
import { Button } from "@/components/shadcn/ui/button"
import { Card, CardContent } from "@/components/shadcn/ui/card"
import { Skeleton } from "@/components/shadcn/ui/skeleton"
import { useAdminI18n } from "../i18n"

export interface ErrorBannerProps {
  message: string
  onRetry?: () => void
  testId?: string
}

export function ErrorBanner({ message, onRetry, testId }: ErrorBannerProps) {
  const { t } = useAdminI18n()
  return (
    <div
      role="alert"
      data-testid={testId}
      className="flex flex-wrap items-center gap-3 rounded-lg border border-destructive/40 bg-destructive/10 px-4 py-3 text-sm text-destructive"
    >
      <AlertCircle className="size-4 shrink-0" aria-hidden="true" />
      <span className="min-w-0 flex-1">{message}</span>
      {onRetry ? (
        <Button type="button" variant="outline" size="sm" onClick={onRetry} className="shrink-0">
          <RotateCw className="size-3.5" aria-hidden="true" />
          {t("admin.common.retry")}
        </Button>
      ) : null}
    </div>
  )
}

export interface NoticeBannerProps {
  message: string
  tone?: "success" | "info" | "warn"
  testId?: string
}

export function NoticeBanner({ message, tone = "info", testId }: NoticeBannerProps) {
  const { t } = useAdminI18n()
  const toneClass =
    tone === "success"
      ? "border-emerald-500/40 bg-emerald-500/10 text-emerald-700 dark:text-emerald-400"
      : tone === "warn"
        ? "border-amber-500/40 bg-amber-500/10 text-amber-700 dark:text-amber-400"
        : "border-primary/30 bg-primary/10 text-primary"
  const Icon = tone === "success" ? CheckCircle2 : tone === "warn" ? AlertCircle : Info
  return (
    <div
      role={tone === "warn" ? "alert" : "status"}
      data-testid={testId}
      className={`flex items-start gap-2 rounded-lg border px-4 py-3 text-sm ${toneClass}`}
    >
      <Icon className="mt-0.5 size-4 shrink-0" aria-hidden="true" />
      <span className="min-w-0 flex-1">{message}</span>
      <span className="sr-only">{t("admin.common.dismiss")}</span>
    </div>
  )
}

export function LoadingBlock({ testId, rows = 4 }: { testId: string; rows?: number }) {
  const { t } = useAdminI18n()
  return (
    <div data-testid={testId} aria-busy="true" aria-live="polite" className="flex flex-col gap-3">
      <p className="flex items-center gap-2 text-sm text-muted-foreground">
        <LoaderCircle className="size-4 animate-spin" aria-hidden="true" />
        {t("admin.common.loading")}
      </p>
      {Array.from({ length: rows }, (_, index) => (
        <Skeleton key={index} className="h-12 w-full" />
      ))}
    </div>
  )
}

export interface EmptyStateProps {
  title: string
  description: string
  ctaLabel: string
  onCta: () => void
  testId: string
  ctaTestId: string
}

export function EmptyState({ title, description, ctaLabel, onCta, testId, ctaTestId }: EmptyStateProps) {
  return (
    <Card data-testid={testId}>
      <CardContent className="flex flex-col items-center gap-3 py-10 text-center">
        <Info className="size-6 text-muted-foreground" aria-hidden="true" />
        <div className="flex flex-col gap-1">
          <p className="text-base font-semibold text-foreground">{title}</p>
          <p className="text-sm text-muted-foreground">{description}</p>
        </div>
        <Button type="button" onClick={onCta} data-testid={ctaTestId} className="mt-2">
          {ctaLabel}
        </Button>
      </CardContent>
    </Card>
  )
}
