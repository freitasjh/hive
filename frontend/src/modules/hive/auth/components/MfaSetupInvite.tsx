import { ShieldCheck } from "lucide-react"

import { Button } from "@/components/shadcn/ui/button"
import { useAuthI18n } from "@/modules/hive/auth/i18n"

export interface MfaSetupInviteProps {
  /** Abre o MfaSetupDialog existente (QR + segredo + 8 códigos via Bearer). */
  onConfigure: () => void
  /** Dispensa somente em memória da sessão — nunca grava storage. */
  onSkip: () => void
}

/**
 * Convite opcional de MFA exibido após login de usuário sem segundo fator.
 *
 * NÃO é Dialog: nenhum overlay, backdrop, focus-trap ou `pointer-events: none`
 * no body. A overview continua totalmente interativa enquanto o convite está
 * visível. O estado de "dispensado" vive apenas no useState do App — recarregar
 * a página não o persiste (intencional, ver App.tsx).
 */
export function MfaSetupInvite({ onConfigure, onSkip }: MfaSetupInviteProps) {
  const { t } = useAuthI18n()

  return (
    <aside
      role="region"
      aria-label={t("auth.mfa.invite.label")}
      data-testid="mfa-setup-invite"
      className="fixed bottom-4 right-4 z-50 w-[min(24rem,calc(100vw-2rem))] rounded-xl border border-primary/30 bg-card p-4 text-card-foreground shadow-sm sm:p-5"
    >
      <div className="flex items-start gap-3">
        <div className="flex size-9 shrink-0 items-center justify-center rounded-lg bg-primary/10 text-primary">
          <ShieldCheck className="size-5" aria-hidden="true" />
        </div>
        <div className="min-w-0 flex-1 space-y-1">
          <div className="flex items-center gap-2">
            <h2 className="text-sm font-bold tracking-tight text-foreground">
              {t("auth.mfa.invite.title")}
            </h2>
            <span className="rounded-md border border-border/70 bg-muted/30 px-1.5 py-0.5 text-[10px] font-medium text-muted-foreground">
              {t("auth.mfa.invite.optional")}
            </span>
          </div>
          <p className="text-xs leading-relaxed text-muted-foreground">
            {t("auth.mfa.invite.description")}
          </p>
        </div>
      </div>
      <div className="mt-4 flex flex-row items-center justify-end gap-3 border-t border-border/60 pt-3">
        <Button type="button" variant="ghost" onClick={onSkip} className="h-11 text-xs">
          {t("auth.mfa.invite.skip")}
        </Button>
        <Button type="button" onClick={onConfigure} className="h-11 gap-1.5 text-xs">
          <ShieldCheck className="size-4" aria-hidden="true" />
          {t("auth.mfa.invite.configure")}
        </Button>
      </div>
    </aside>
  )
}
