"use client"

import { PlugZap, RotateCw } from "lucide-react"
import { Button } from "@/components/shadcn/ui/button"
import { Card, CardContent } from "@/components/shadcn/ui/card"
import { Input } from "@/components/shadcn/ui/input"
import { Label } from "@/components/shadcn/ui/label"
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/shadcn/ui/select"
import type { AiProvider } from "@/services/admin.types"
import { ErrorBanner, LoadingBlock, NoticeBanner } from "../components/AdminStates"
import { useAdminI18n } from "../i18n"
import type { LlmConfigActions, LlmConfigState } from "../state"
import { isRemoteProvider, VENDOR_CATALOG, VENDOR_ORDER } from "../vendor-catalog"

export interface LlmConfigViewProps {
  state: LlmConfigState
  actions: LlmConfigActions
}

export function LlmConfigView({ state, actions }: LlmConfigViewProps) {
  const { t } = useAdminI18n()
  const meta = state.provider ? VENDOR_CATALOG[state.provider] : null
  const remote = isRemoteProvider(state.provider)
  const hasModel = Boolean(state.provider && state.model.trim())
  const canSave = hasModel && !state.saving && !state.loading && !state.remoteLoading
  const canTest = hasModel && !state.testing

  const remoteOptions = Array.from(new Set([...state.remoteModels, ...(state.model ? [state.model] : [])]))

  return (
    <section className="flex flex-col gap-6" aria-labelledby="admin-llm-title">
      <div className="flex flex-col gap-1">
        <h1 id="admin-llm-title" className="text-xl font-bold tracking-tight text-foreground sm:text-2xl">
          {t("admin.llm.title")}
        </h1>
        <p className="text-xs text-muted-foreground">{t("admin.llm.subtitle")}</p>
      </div>

      {state.loadError ? (
        <ErrorBanner message={state.loadError} onRetry={actions.onRetry} testId="llm-load-error" />
      ) : null}

      {state.loading ? (
        <LoadingBlock testId="llm-loading" rows={4} />
      ) : (
        <Card className="max-w-3xl">
          <CardContent className="flex flex-col gap-5">
            {!state.configured ? (
              <NoticeBanner message={t("admin.llm.notConfigured")} tone="info" />
            ) : null}

            <div className="flex flex-col gap-1.5">
              <Label htmlFor="llm-provider">{t("admin.llm.provider")}</Label>
              <Select
                value={state.provider ?? undefined}
                disabled={state.saving}
                onValueChange={(value) => actions.onProviderChange(value as AiProvider)}
              >
                <SelectTrigger id="llm-provider" data-testid="llm-provider" className="h-10 w-full">
                  <SelectValue placeholder={t("admin.llm.chooseProvider")} />
                </SelectTrigger>
                <SelectContent>
                  {VENDOR_ORDER.map((provider) => {
                    const vendor = VENDOR_CATALOG[provider]
                    return (
                      <SelectItem key={provider} value={provider}>
                        {t(vendor.labelKey)} — {t(vendor.descriptionKey)}
                      </SelectItem>
                    )
                  })}
                </SelectContent>
              </Select>
            </div>

            <div className="flex flex-col gap-1.5">
              <div className="flex items-center justify-between gap-2">
                <Label htmlFor="llm-model">{t("admin.llm.model")}</Label>
                {remote ? (
                  <Button
                    type="button"
                    variant="ghost"
                    size="icon-xs"
                    aria-label={t("admin.common.retry")}
                    data-testid="llm-refresh-models"
                    onClick={actions.onRefreshModels}
                    disabled={state.remoteLoading}
                  >
                    <RotateCw aria-hidden="true" />
                  </Button>
                ) : null}
              </div>

              {remote && !state.remoteError && remoteOptions.length > 0 ? (
                <Select
                  value={state.model || undefined}
                  disabled={state.saving || state.remoteLoading}
                  onValueChange={actions.onModelChange}
                >
                  <SelectTrigger
                    id="llm-model"
                    data-testid="llm-model-remote"
                    className="h-10 w-full"
                  >
                    <SelectValue placeholder={t("admin.llm.chooseModel")} />
                  </SelectTrigger>
                  <SelectContent>
                    {remoteOptions.map((model) => (
                      <SelectItem key={model} value={model}>
                        {model}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              ) : (
                <Input
                  id="llm-model"
                  data-testid="llm-model-manual"
                  value={state.model}
                  placeholder={t("admin.llm.manualPlaceholder")}
                  disabled={state.saving}
                  onChange={(event) => actions.onModelChange(event.target.value)}
                  className="h-10 w-full font-mono"
                />
              )}

              {state.remoteLoading ? (
                <small data-testid="llm-remote-loading" className="text-muted-foreground">
                  {t("admin.llm.loadingModels")}
                </small>
              ) : null}
              {state.remoteError ? (
                <NoticeBanner
                  message={t("admin.llm.remoteError", { detail: state.remoteError })}
                  tone="warn"
                  testId="llm-model-error"
                />
              ) : (
                <small className="text-muted-foreground">
                  {t("admin.llm.catalog", { provider: meta ? t(meta.labelKey) : "—" })}{" "}
                  {meta?.models.length
                    ? meta.models.join(", ")
                    : t("admin.llm.catalogRemote")}
                </small>
              )}
            </div>

            {state.testResult ? (
              <NoticeBanner
                tone={state.testResult.ok ? "success" : "warn"}
                testId="llm-test-result"
                message={
                  state.testResult.ok
                    ? t("admin.llm.testOk", { ms: state.testResult.latencyMs })
                    : t("admin.llm.testFail", { reason: state.testResult.reason ?? "—" })
                }
              />
            ) : null}
            {state.saveError ? (
              <ErrorBanner message={state.saveError} testId="llm-save-error" />
            ) : null}
            {state.notice ? <NoticeBanner message={state.notice} tone="success" /> : null}

            <div className="flex flex-wrap items-center gap-2">
              <Button
                type="button"
                data-testid="llm-save"
                disabled={!canSave}
                onClick={actions.onSave}
              >
                {state.configured ? t("admin.llm.save") : t("admin.llm.saveAndUse")}
              </Button>
              <Button
                type="button"
                variant="outline"
                data-testid="llm-test"
                disabled={!canTest}
                onClick={actions.onTest}
              >
                <PlugZap aria-hidden="true" />
                {state.testing ? t("admin.llm.testing") : t("admin.llm.test")}
              </Button>
            </div>
          </CardContent>
        </Card>
      )}
    </section>
  )
}

/** Alias semântico para o nome usado pela tela legada. */
export const LlmConfigPanel = LlmConfigView
