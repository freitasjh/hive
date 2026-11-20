import { useCallback, useEffect, useState } from "react"
import { AlertCircle, CheckCircle2, Copy, QrCode, ShieldCheck } from "lucide-react"

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
import { Label } from "@/components/shadcn/ui/label"
import { mfaService, type MfaSetupData } from "@/services/auth.service"
import { useAuthI18n, type AuthMessageKey } from "@/modules/hive/auth/i18n"

export interface MfaSetupDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  onSuccess?: () => void
}

/**
 * Setup MFA: secret/QR/8 códigos exibidos uma única vez, copiáveis via clipboard.
 * `setup` e `enable` usam Bearer normal (mfaService) — nunca omitAuthorization
 * e nunca persistência de segredo em storage/URL/log.
 */
export function MfaSetupDialog({ open, onOpenChange, onSuccess }: MfaSetupDialogProps) {
  const { t } = useAuthI18n()
  const [data, setData] = useState<MfaSetupData | null>(null)
  const [loading, setLoading] = useState(false)
  const [confirming, setConfirming] = useState(false)
  const [errorKey, setErrorKey] = useState<AuthMessageKey | null>(null)
  const [code, setCode] = useState("")
  const [copiedSecret, setCopiedSecret] = useState(false)
  const [copiedCodes, setCopiedCodes] = useState(false)
  const [done, setDone] = useState(false)

  useEffect(() => {
    if (!open || data) return
    let active = true
    setLoading(true)
    setErrorKey(null)
    mfaService
      .setup()
      .then((result) => {
        if (active) setData(result)
      })
      .catch(() => {
        if (active) setErrorKey("auth.mfa.setup.error")
      })
      .finally(() => {
        if (active) setLoading(false)
      })
    return () => {
      active = false
    }
  }, [open, data])

  const copy = useCallback(async (value: string, onCopied: () => void) => {
    try {
      await navigator.clipboard?.writeText(value)
      onCopied()
    } catch {
      /* clipboard indisponível: o valor permanece selecionável na tela. */
    }
  }, [])

  const handleConfirm = useCallback(async () => {
    if (!/^\d{6}$/.test(code.trim()) || confirming) return
    setConfirming(true)
    setErrorKey(null)
    try {
      await mfaService.enable(code.trim())
      setDone(true)
      setCode("")
      onSuccess?.()
    } catch {
      setErrorKey("auth.mfa.setup.error")
    } finally {
      setConfirming(false)
    }
  }, [code, confirming, onSuccess])

  const close = useCallback(
    (next: boolean) => {
      if (!next) {
        setData(null)
        setCode("")
        setErrorKey(null)
        setDone(false)
        setCopiedSecret(false)
        setCopiedCodes(false)
      }
      onOpenChange(next)
    },
    [onOpenChange]
  )

  return (
    <Dialog open={open} onOpenChange={close}>
      <DialogContent className="max-h-[92vh] w-full overflow-y-auto overscroll-contain p-5 sm:max-w-[560px] sm:p-6">
        <DialogHeader>
          <div className="flex items-center gap-2">
            <div className="flex size-8 items-center justify-center rounded-lg bg-primary/10 text-primary">
              <ShieldCheck className="size-4" aria-hidden="true" />
            </div>
            <div>
              <DialogTitle className="text-base font-bold">{t("auth.mfa.setup.title")}</DialogTitle>
              <DialogDescription className="text-xs">
                {t("auth.mfa.setup.description")}
              </DialogDescription>
            </div>
          </div>
        </DialogHeader>

        {loading && (
          <p className="py-6 text-center text-xs text-muted-foreground" role="status">
            {t("auth.mfa.submitting")}
          </p>
        )}

        {errorKey && (
          <p role="alert" className="flex items-start gap-1.5 rounded-lg border border-destructive/30 bg-destructive/10 p-2.5 text-xs text-destructive">
            <AlertCircle className="mt-0.5 size-4 shrink-0" aria-hidden="true" />
            {t(errorKey)}
          </p>
        )}

        {data && !done && (
          <div className="space-y-5 py-2">
            <div className="flex flex-col gap-4 rounded-xl border border-border/80 bg-muted/30 p-4 sm:flex-row sm:items-center">
              <div className="flex size-36 shrink-0 items-center justify-center overflow-hidden rounded-lg border border-border bg-card p-2">
                {data.qrDataUri ? (
                  <img src={data.qrDataUri} alt={t("auth.mfa.setup.qrAlt")} className="size-full object-contain" />
                ) : (
                  <QrCode className="size-20 text-muted-foreground" aria-hidden="true" />
                )}
              </div>
              <div className="min-w-0 w-full flex-1 space-y-2">
                <p className="text-xs font-semibold text-foreground">{t("auth.mfa.setup.manual")}</p>
                <p className="text-[11px] text-muted-foreground">{t("auth.mfa.setup.manualHint")}</p>
                <div className="flex min-w-0 w-full items-center gap-2">
                  <code className="min-w-0 flex-1 overflow-hidden break-all rounded-md border border-border bg-background px-2.5 py-2 font-mono text-xs select-all">
                    {data.secret}
                  </code>
                  <Button
                    type="button"
                    variant="outline"
                    size="sm"
                    className="h-11 shrink-0 gap-1.5"
                    onClick={() => void copy(data.secret, () => setCopiedSecret(true))}
                  >
                    <Copy className="size-3.5" aria-hidden="true" />
                    {copiedSecret ? t("auth.mfa.setup.copied") : t("auth.mfa.setup.copy")}
                  </Button>
                </div>
              </div>
            </div>

            <div className="space-y-2">
              <div className="flex items-center justify-between gap-2">
                <p className="text-xs font-semibold text-foreground">{t("auth.mfa.setup.codes")}</p>
                <Button
                  type="button"
                  variant="ghost"
                  size="sm"
                  className="h-9 gap-1.5 text-xs"
                  onClick={() => void copy(data.recoveryCodes.join("\n"), () => setCopiedCodes(true))}
                >
                  <Copy className="size-3.5" aria-hidden="true" />
                  {copiedCodes ? t("auth.mfa.setup.copied") : t("auth.mfa.setup.copy")}
                </Button>
              </div>
              <p className="text-[11px] text-muted-foreground">{t("auth.mfa.setup.codesHint")}</p>
              <div className="grid grid-cols-2 gap-2 rounded-xl border border-border/80 bg-muted/30 p-3">
                {data.recoveryCodes.map((recoveryCode) => (
                  <code
                    key={recoveryCode}
                    className="rounded-md border border-border/60 bg-background px-2 py-1.5 text-center font-mono text-xs select-all"
                  >
                    {recoveryCode}
                  </code>
                ))}
              </div>
            </div>

            <div className="flex flex-col gap-1.5 border-t border-border/60 pt-4">
              <Label htmlFor="mfa-setup-code">{t("auth.mfa.setup.confirmLabel")}</Label>
              <Input
                id="mfa-setup-code"
                data-testid="mfa-setup-code"
                inputMode="numeric"
                autoComplete="one-time-code"
                maxLength={6}
                value={code}
                disabled={confirming}
                onChange={(event) => setCode(event.target.value.replace(/\D/g, "").slice(0, 6))}
                className="h-11 w-full text-center font-mono text-base tracking-[0.3em]"
              />
            </div>
          </div>
        )}

        {done && (
          <div className="flex items-center gap-2 rounded-lg border border-primary/30 bg-primary/10 p-3 text-xs text-primary" role="status">
            <CheckCircle2 className="size-4 shrink-0" aria-hidden="true" />
            {t("auth.mfa.setup.success")}
          </div>
        )}

        <DialogFooter className="mt-5 flex flex-row items-center justify-end gap-3 sm:space-x-0">
          <Button type="button" variant="outline" onClick={() => close(false)} className="h-11 text-xs">
            {done ? t("auth.mfa.setup.finish") : t("auth.mfa.setup.cancel")}
          </Button>
          {data && !done && (
            <Button
              type="button"
              disabled={!/^\d{6}$/.test(code.trim()) || confirming}
              aria-busy={confirming}
              onClick={() => void handleConfirm()}
              className="h-11 gap-1.5 text-xs"
            >
              <ShieldCheck className="size-4" aria-hidden="true" />
              {confirming ? t("auth.mfa.setup.confirming") : t("auth.mfa.setup.confirm")}
            </Button>
          )}
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
