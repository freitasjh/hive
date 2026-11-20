import { useCallback, useEffect, useRef, useState } from "react"
import {
  LoginError,
  getToken,
  loginService,
  type LoginErrorCode,
} from "@/services/auth.service"
import type { AuthMessageKey } from "./i18n"

export type LoginStatus = "idle" | "pending" | "error" | "success"

const ERROR_KEYS: Record<LoginErrorCode | "UNKNOWN", AuthMessageKey> = {
  INVALID_CREDENTIALS: "auth.login.error.invalid",
  UNAVAILABLE: "auth.login.error.unavailable",
  NETWORK: "auth.login.error.network",
  UNKNOWN: "auth.login.error.unknown",
}

export interface LoginController {
  status: LoginStatus
  isPending: boolean
  errorKey: AuthMessageKey | null
  submit: (credentials: { username: string; password: string }) => Promise<void>
}

/**
 * Lógica do formulário de login: submete ao serviço, traduz o código de erro
 * para chave i18n (sem payload) e delega a navegação ao compositor da rota.
 *
 * Já autenticado em `#login` => segue direto para a view autenticada; sem isso
 * um usuário com token válido ficaria preso no formulário.
 */
export function useLoginController(onAuthenticated?: () => void): LoginController {
  const [status, setStatus] = useState<LoginStatus>("idle")
  const [errorKey, setErrorKey] = useState<AuthMessageKey | null>(null)
  const onAuthenticatedRef = useRef(onAuthenticated)

  useEffect(() => {
    onAuthenticatedRef.current = onAuthenticated
  }, [onAuthenticated])

  useEffect(() => {
    if (getToken()) onAuthenticatedRef.current?.()
  }, [])

  const submit = useCallback<LoginController["submit"]>(
    async (credentials) => {
      setStatus("pending")
      setErrorKey(null)
      try {
        await loginService.login(credentials)
        setStatus("success")
        onAuthenticatedRef.current?.()
      } catch (error) {
        const code = error instanceof LoginError ? error.code : "UNKNOWN"
        setErrorKey(ERROR_KEYS[code])
        setStatus("error")
      }
    },
    []
  )

  return { status, isPending: status === "pending", errorKey, submit }
}
