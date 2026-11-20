"use client"

import { GalleryVerticalEnd, Hexagon } from "lucide-react"

import hiveLoginHero from "@/assets/hive-login-hero.jpg"
import { LoginForm } from "@/components/shadcn/auth/login-form"
import { ModeToggle } from "@/components/shadcn/navigation/mode-toggle"
import type { MfaChallengeState } from "@/services/auth.service"
import { useAuthI18n } from "@/modules/hive/auth/i18n"
import { useLoginController } from "@/modules/hive/auth/use-login-controller"

export interface LoginPageProps {
  /**
   * Navegação para a área autenticada, injetada pelo compositor de rotas.
   * Recebe o sinal de onboarding MFA somente no ramo autenticado do login.
   */
  onAuthenticated?: (mfaSetupAvailable?: boolean) => void
  /** Desafio MFA: o compositor guarda em memória e navega para #mfa. */
  onMfaRequired?: (challenge: MfaChallengeState) => void
}

/**
 * Tela de autenticação — fora do shell Atlas (sem sidebar/header) e sem
 * redirecionamento por reload: o hash-router decide a view.
 */
export function LoginPage({ onAuthenticated, onMfaRequired }: LoginPageProps) {
  const { t } = useAuthI18n()
  const { isPending, errorKey, submit } = useLoginController(onAuthenticated, onMfaRequired)

  return (
    <div className="relative grid min-h-svh bg-background lg:grid-cols-2">
      <div className="absolute right-4 top-4 z-10 rounded-lg border border-border/70 bg-background/95 shadow-xs supports-[backdrop-filter]:bg-background/75 supports-[backdrop-filter]:backdrop-blur-sm">
        <ModeToggle />
      </div>
      <div className="flex flex-col gap-4 p-6 md:p-10">
        <div className="flex justify-center gap-2 md:justify-start">
          <div className="flex items-center gap-2 font-medium">
            <div className="flex size-6 items-center justify-center rounded-md bg-primary text-primary-foreground">
              <GalleryVerticalEnd className="size-4" />
            </div>
            {t("auth.login.brand")}
          </div>
        </div>
        <div className="flex flex-1 items-center justify-center">
          <div className="w-full max-w-sm">
            <div className="relative overflow-hidden rounded-2xl border border-border/70 bg-card p-6 shadow-sm sm:p-8 dark:border-border/60 dark:shadow-none">
              <span
                aria-hidden="true"
                className="pointer-events-none absolute inset-x-0 top-0 h-px bg-gradient-to-r from-transparent via-foreground/15 to-transparent dark:via-white/20"
              />
              <LoginForm
                onSubmit={submit}
                isPending={isPending}
                errorKey={errorKey}
              />
            </div>
          </div>
        </div>
      </div>
      <figure className="relative hidden overflow-hidden border-l border-border/70 bg-muted lg:flex lg:flex-col lg:justify-end">
        <img
          src={hiveLoginHero}
          alt={t("auth.login.hero.alt")}
          className="absolute inset-0 size-full object-cover object-center"
        />
        <figcaption className="relative z-10 m-6 max-w-md rounded-xl border border-border/70 bg-card p-5 text-card-foreground shadow-xs xl:m-10">
          <div className="flex items-center gap-2 text-xs font-medium tracking-wide text-muted-foreground">
            <Hexagon className="size-3.5 text-primary" aria-hidden="true" />
            <span>{t("auth.login.hero.eyebrow")}</span>
          </div>
          <p className="mt-2 text-base font-semibold tracking-tight text-foreground">
            {t("auth.login.hero.title")}
          </p>
          <p className="mt-1 text-xs leading-relaxed text-muted-foreground">
            {t("auth.login.hero.description")}
          </p>
        </figcaption>
      </figure>
    </div>
  )
}
