"use client"

import { GalleryVerticalEnd } from "lucide-react"

import { LoginForm } from "@/components/shadcn/auth/login-form"
import { ModeToggle } from "@/components/shadcn/navigation/mode-toggle"
import { useAuthI18n } from "@/modules/hive/auth/i18n"
import { useLoginController } from "@/modules/hive/auth/use-login-controller"

export interface LoginPageProps {
  /** Navegação para a área autenticada, injetada pelo compositor de rotas. */
  onAuthenticated?: () => void
}

/**
 * Tela de autenticação — fora do shell Atlas (sem sidebar/header) e sem
 * redirecionamento por reload: o hash-router decide a view.
 */
export function LoginPage({ onAuthenticated }: LoginPageProps) {
  const { t } = useAuthI18n()
  const { isPending, errorKey, submit } = useLoginController(onAuthenticated)

  return (
    <div className="relative grid min-h-svh bg-background lg:grid-cols-2">
      <div className="absolute right-4 top-4 z-10">
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
      <div
        aria-hidden="true"
        className="relative hidden items-center justify-center bg-muted lg:flex"
      >
        <GalleryVerticalEnd className="size-24 text-muted-foreground/20" />
      </div>
    </div>
  )
}
