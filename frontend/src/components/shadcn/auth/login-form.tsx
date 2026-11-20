import { useState } from "react"

import { cn } from "@/lib/cn"
import type { LoginCredentials } from "@/services/auth.service"
import { useAuthI18n, type AuthMessageKey } from "@/modules/hive/auth/i18n"

import { Button } from "@/components/shadcn/ui/button"
import { Field, FieldDescription, FieldGroup, FieldLabel } from "@/components/shadcn/ui/field"
import { Input } from "@/components/shadcn/ui/input"

export interface LoginFormProps extends Omit<React.ComponentProps<"form">, "onSubmit"> {
  /** Recebe somente username/password; o serviço decide o que persistir. */
  onSubmit: (credentials: LoginCredentials) => void | Promise<void>
  isPending?: boolean
  disabled?: boolean
  /** Chave i18n do erro semântico — nunca uma mensagem com payload. */
  errorKey?: AuthMessageKey | null
}

export function LoginForm({
  className,
  onSubmit,
  isPending = false,
  disabled = false,
  errorKey = null,
  ...props
}: LoginFormProps) {
  const { t } = useAuthI18n()
  const [username, setUsername] = useState("")
  const [password, setPassword] = useState("")
  const locked = disabled || isPending
  const errorMessage = errorKey ? t(errorKey) : null

  function handleSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (locked) return
    onSubmit({ username, password })
  }

  return (
    <form
      className={cn("flex flex-col gap-6", className)}
      onSubmit={handleSubmit}
      aria-busy={isPending}
      {...props}
    >
      <FieldGroup>
        <div className="flex flex-col items-center gap-1 text-center">
          <h1 className="text-2xl font-bold">{t("auth.login.title")}</h1>
          <p className="text-sm text-balance text-muted-foreground">
            {t("auth.login.subtitle")}
          </p>
        </div>
        <Field data-invalid={Boolean(errorMessage) || undefined}>
          <FieldLabel htmlFor="username">{t("auth.login.username.label")}</FieldLabel>
          <Input
            id="username"
            name="username"
            type="text"
            placeholder={t("auth.login.username.placeholder")}
            autoComplete="username"
            value={username}
            disabled={locked}
            aria-invalid={Boolean(errorMessage)}
            aria-describedby={errorMessage ? "login-error" : undefined}
            onChange={(event) => setUsername(event.target.value)}
            required
          />
        </Field>
        <Field data-invalid={Boolean(errorMessage) || undefined}>
          <FieldLabel htmlFor="password">{t("auth.login.password.label")}</FieldLabel>
          <Input
            id="password"
            name="password"
            type="password"
            autoComplete="current-password"
            value={password}
            disabled={locked}
            aria-invalid={Boolean(errorMessage)}
            aria-describedby={errorMessage ? "login-error" : undefined}
            onChange={(event) => setPassword(event.target.value)}
            required
          />
        </Field>
        {errorMessage ? (
          <FieldDescription
            id="login-error"
            role="alert"
            aria-live="assertive"
            className="text-destructive"
          >
            {errorMessage}
          </FieldDescription>
        ) : null}
        <Field>
          <Button type="submit" disabled={locked} aria-busy={isPending} className="w-full">
            {isPending ? t("auth.login.submitting") : t("auth.login.submit")}
          </Button>
        </Field>
      </FieldGroup>
    </form>
  )
}
