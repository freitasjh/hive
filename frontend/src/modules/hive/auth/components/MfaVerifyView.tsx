import { AlertCircle, ArrowLeft, CheckCircle2, Clock, GalleryVerticalEnd, ShieldCheck } from "lucide-react"

import { Button } from "@/components/shadcn/ui/button"
import { Input } from "@/components/shadcn/ui/input"
import { Label } from "@/components/shadcn/ui/label"
import { Tabs, TabsList, TabsTrigger } from "@/components/shadcn/ui/tabs"
import { ModeToggle } from "@/components/shadcn/navigation/mode-toggle"
import { useAuthI18n, type AuthMessageKey } from "@/modules/hive/auth/i18n"
import type { MfaMode, MfaVerifyStatus } from "@/modules/hive/auth/use-mfa-verify-controller"

export interface MfaVerifyViewProps {
  mode: MfaMode
  code: string
  status: MfaVerifyStatus
  errorKey: AuthMessageKey | null
  remainingAttempts: number
  secondsLeft: number
  onModeChange: (mode: MfaMode) => void
  onCodeChange: (value: string) => void
  onSubmit: () => void
  onBack: () => void
}

/** Apresentação pura da tela de desafio — fora do shell Atlas, como o login. */
export function MfaVerifyView({
  mode,
  code,
  status,
  errorKey,
  remainingAttempts,
  secondsLeft,
  onModeChange,
  onCodeChange,
  onSubmit,
  onBack,
}: MfaVerifyViewProps) {
  const { t } = useAuthI18n()
  const isCodeMode = mode === "code"
  const expired = secondsLeft <= 0
  const blocked = expired || remainingAttempts <= 0
  const done = status === "success"
  const pending = status === "pending"

  const visibleError: AuthMessageKey | null = expired ? "auth.mfa.expired" : errorKey
  const minutes = String(Math.floor(Math.max(0, secondsLeft) / 60)).padStart(2, "0")
  const seconds = String(Math.max(0, secondsLeft) % 60).padStart(2, "0")

  return (
    <div className="relative grid min-h-svh place-items-center bg-background p-4">
      <div className="absolute right-4 top-4 z-10 rounded-lg border border-border/70 bg-background/95 shadow-xs supports-[backdrop-filter]:bg-background/75 supports-[backdrop-filter]:backdrop-blur-sm">
        <ModeToggle />
      </div>
      <div className="w-full max-w-sm">
        <div className="flex justify-center gap-2 pb-6">
          <div className="flex items-center gap-2 font-medium">
            <div className="flex size-6 items-center justify-center rounded-md bg-primary text-primary-foreground">
              <GalleryVerticalEnd className="size-4" aria-hidden="true" />
            </div>
            {t("auth.login.brand")}
          </div>
        </div>

        <div className="relative overflow-hidden rounded-2xl border border-border/70 bg-card p-6 text-card-foreground shadow-sm sm:p-8 dark:border-border/60 dark:shadow-none">
          <span
            aria-hidden="true"
            className="pointer-events-none absolute inset-x-0 top-0 h-px bg-gradient-to-r from-transparent via-foreground/15 to-transparent dark:via-white/20"
          />

          <div className="flex flex-col items-center gap-2 text-center">
            <div className="flex size-11 items-center justify-center rounded-xl bg-primary/10 text-primary">
              {done ? (
                <CheckCircle2 className="size-5" aria-hidden="true" />
              ) : (
                <ShieldCheck className="size-5" aria-hidden="true" />
              )}
            </div>
            <h1 className="text-xl font-bold tracking-tight text-foreground">
              {t("auth.mfa.title")}
            </h1>
            <p className="text-xs leading-relaxed text-muted-foreground">
              {done ? t("auth.mfa.success") : t("auth.mfa.subtitle")}
            </p>
          </div>

          {!done && (
            <form
              className="mt-6 space-y-5"
              onSubmit={(event) => {
                event.preventDefault()
                onSubmit()
              }}
            >
              <Tabs value={mode} onValueChange={(value) => onModeChange(value as MfaMode)}>
                <TabsList className="grid w-full grid-cols-2">
                  <TabsTrigger value="code">{t("auth.mfa.tab.code")}</TabsTrigger>
                  <TabsTrigger value="recovery">{t("auth.mfa.tab.recovery")}</TabsTrigger>
                </TabsList>
              </Tabs>

              <div className="flex flex-col gap-1.5">
                <Label htmlFor="mfa-input">
                  {t(isCodeMode ? "auth.mfa.code.label" : "auth.mfa.recovery.label")}
                </Label>
                <Input
                  id="mfa-input"
                  data-testid="mfa-input"
                  aria-label={t(isCodeMode ? "auth.mfa.code.label" : "auth.mfa.recovery.label")}
                  aria-invalid={Boolean(visibleError)}
                  inputMode={isCodeMode ? "numeric" : "text"}
                  autoComplete={isCodeMode ? "one-time-code" : "off"}
                  maxLength={isCodeMode ? 6 : 24}
                  placeholder={t(
                    isCodeMode ? "auth.mfa.code.placeholder" : "auth.mfa.recovery.placeholder"
                  )}
                  value={code}
                  disabled={pending || blocked}
                  onChange={(event) =>
                    onCodeChange(
                      isCodeMode
                        ? event.target.value.replace(/\D/g, "").slice(0, 6)
                        : event.target.value.slice(0, 24)
                    )
                  }
                  className="h-11 w-full text-center font-mono text-base tracking-[0.3em]"
                />
                <p className="text-[11px] leading-relaxed text-muted-foreground">
                  {t(isCodeMode ? "auth.mfa.code.hint" : "auth.mfa.recovery.hint")}
                </p>
              </div>

              <div className="flex items-center justify-between gap-2 rounded-lg border border-border/70 bg-muted/30 px-3 py-2 text-xs text-muted-foreground">
                <span>
                  {t("auth.mfa.attempts")}:{" "}
                  <strong className="font-mono tabular-nums text-foreground">
                    {remainingAttempts}
                  </strong>
                </span>
                <span className="flex items-center gap-1.5">
                  <Clock className="size-3.5" aria-hidden="true" />
                  {t("auth.mfa.expires")}:{" "}
                  <strong className="font-mono tabular-nums text-foreground">
                    {minutes}:{seconds}
                  </strong>
                </span>
              </div>

              <div aria-live="assertive" aria-atomic="true">
                {visibleError && (
                  <p
                    role="alert"
                    className="flex items-start gap-1.5 rounded-lg border border-destructive/30 bg-destructive/10 p-2.5 text-xs text-destructive"
                  >
                    <AlertCircle className="mt-0.5 size-4 shrink-0" aria-hidden="true" />
                    <span>{t(visibleError)}</span>
                  </p>
                )}
              </div>

              <Button
                type="submit"
                disabled={pending || blocked || code.trim().length === 0}
                aria-busy={pending}
                className="h-11 w-full gap-2 text-sm font-semibold"
              >
                <ShieldCheck className="size-4" aria-hidden="true" />
                {pending ? t("auth.mfa.submitting") : t("auth.mfa.submit")}
              </Button>
            </form>
          )}

          <Button
            type="button"
            variant="ghost"
            onClick={onBack}
            className="mt-4 h-11 w-full gap-1.5 text-xs text-muted-foreground"
          >
            <ArrowLeft className="size-3.5" aria-hidden="true" />
            {t("auth.mfa.back")}
          </Button>
        </div>
      </div>
    </div>
  )
}
