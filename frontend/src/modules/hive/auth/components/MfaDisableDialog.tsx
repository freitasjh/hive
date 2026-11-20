import { useCallback, useState } from "react"
import { AlertCircle, ShieldOff } from "lucide-react"

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
import { mfaService } from "@/services/auth.service"
import { useAuthI18n } from "@/modules/hive/auth/i18n"

export interface MfaDisableDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  onSuccess?: () => void
}

/** Desativa MFA com senha; `disable` exige Bearer normal (mfaService). */
export function MfaDisableDialog({ open, onOpenChange, onSuccess }: MfaDisableDialogProps) {
  const { t } = useAuthI18n()
  const [password, setPassword] = useState("")
  const [pending, setPending] = useState(false)
  const [error, setError] = useState(false)

  const close = useCallback(
    (next: boolean) => {
      if (!next) {
        setPassword("")
        setError(false)
      }
      onOpenChange(next)
    },
    [onOpenChange]
  )

  const handleDisable = useCallback(async () => {
    if (!password || pending) return
    setPending(true)
    setError(false)
    try {
      await mfaService.disable(password)
      setPassword("")
      onSuccess?.()
      close(false)
    } catch {
      setError(true)
    } finally {
      setPending(false)
    }
  }, [password, pending, onSuccess, close])

  return (
    <Dialog open={open} onOpenChange={close}>
      <DialogContent className="max-h-[92vh] w-full overflow-y-auto overscroll-contain p-5 sm:max-w-[480px] sm:p-6">
        <DialogHeader>
          <div className="flex items-center gap-2">
            <div className="flex size-8 items-center justify-center rounded-lg bg-destructive/10 text-destructive">
              <ShieldOff className="size-4" aria-hidden="true" />
            </div>
            <div>
              <DialogTitle className="text-base font-bold">{t("auth.mfa.disable.title")}</DialogTitle>
              <DialogDescription className="text-xs">
                {t("auth.mfa.disable.description")}
              </DialogDescription>
            </div>
          </div>
        </DialogHeader>

        <div className="flex flex-col gap-1.5 py-4">
          <Label htmlFor="mfa-disable-password">{t("auth.mfa.disable.password")}</Label>
          <Input
            id="mfa-disable-password"
            data-testid="mfa-disable-password"
            type="password"
            autoComplete="current-password"
            value={password}
            disabled={pending}
            onChange={(event) => setPassword(event.target.value)}
            className="h-11 w-full"
          />
        </div>

        <div aria-live="assertive" aria-atomic="true">
          {error && (
            <p role="alert" className="flex items-start gap-1.5 rounded-lg border border-destructive/30 bg-destructive/10 p-2.5 text-xs text-destructive">
              <AlertCircle className="mt-0.5 size-4 shrink-0" aria-hidden="true" />
              {t("auth.mfa.disable.error")}
            </p>
          )}
        </div>

        <DialogFooter className="mt-5 flex flex-row items-center justify-end gap-3 sm:space-x-0">
          <Button type="button" variant="outline" onClick={() => close(false)} className="h-11 text-xs">
            {t("auth.mfa.disable.cancel")}
          </Button>
          <Button
            type="button"
            variant="destructive"
            disabled={!password || pending}
            aria-busy={pending}
            onClick={() => void handleDisable()}
            className="h-11 gap-1.5 text-xs"
          >
            <ShieldOff className="size-4" aria-hidden="true" />
            {pending ? t("auth.mfa.disable.confirming") : t("auth.mfa.disable.confirm")}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
