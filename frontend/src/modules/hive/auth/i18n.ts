import { useCallback, useState } from "react"

/**
 * i18n leve sem dependência nova (frontend/package.json não tem react-i18next).
 * Nenhum literal visível da tela de login vive fora destes dicionários.
 * Chaves prefixadas por `auth.`; pt-BR é o default, como no restante do projeto.
 */

export type AuthLocale = "pt-BR" | "en-US"

const ptBR = {
  "auth.document.title": "Entrar",
  "auth.login.title": "Entrar na sua conta",
  "auth.login.subtitle": "Informe seu usuário e senha para acessar o painel.",
  "auth.login.username.label": "Usuário",
  "auth.login.username.placeholder": "seu.usuario",
  "auth.login.password.label": "Senha",
  "auth.login.submit": "Entrar",
  "auth.login.submitting": "Entrando…",
  "auth.login.brand": "Atlas",
  "auth.login.error.invalid": "Usuário ou senha incorretos. Confira e tente novamente.",
  "auth.login.error.unavailable": "Não foi possível entrar agora. Tente novamente em instantes.",
  "auth.login.error.network": "Sem conexão com o servidor. Verifique sua rede e tente novamente.",
  "auth.login.error.unknown": "Não foi possível entrar. Tente novamente.",
} as const

export type AuthMessageKey = keyof typeof ptBR

const enUS: Record<AuthMessageKey, string> = {
  "auth.document.title": "Sign in",
  "auth.login.title": "Sign in to your account",
  "auth.login.subtitle": "Enter your username and password to access the dashboard.",
  "auth.login.username.label": "Username",
  "auth.login.username.placeholder": "your.username",
  "auth.login.password.label": "Password",
  "auth.login.submit": "Sign in",
  "auth.login.submitting": "Signing in…",
  "auth.login.brand": "Atlas",
  "auth.login.error.invalid": "Incorrect username or password. Check and try again.",
  "auth.login.error.unavailable": "Could not sign in right now. Try again shortly.",
  "auth.login.error.network": "No connection to the server. Check your network and try again.",
  "auth.login.error.unknown": "Could not sign in. Try again.",
}

const dictionaries: Record<AuthLocale, Record<AuthMessageKey, string>> = {
  "pt-BR": ptBR,
  "en-US": enUS,
}

export function detectAuthLocale(): AuthLocale {
  try {
    const stored = localStorage.getItem("hive_locale")
    if (stored === "pt-BR" || stored === "en-US") return stored
  } catch {
    /* sem storage: default */
  }
  if (typeof document !== "undefined" && document.documentElement.lang.toLowerCase().startsWith("en")) {
    return "en-US"
  }
  return "pt-BR"
}

export interface AuthI18n {
  locale: AuthLocale
  t: (key: AuthMessageKey) => string
}

export function useAuthI18n(): AuthI18n {
  const [locale] = useState<AuthLocale>(detectAuthLocale)
  const t = useCallback<AuthI18n["t"]>(
    (key) => dictionaries[locale][key] ?? ptBR[key] ?? key,
    [locale]
  )
  return { locale, t }
}
