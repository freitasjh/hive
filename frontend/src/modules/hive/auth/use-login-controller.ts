import { useCallback, useEffect, useRef, useState } from "react"
import {
  LoginError,
  getToken,
  loginService,
  type LoginErrorCode,
  type MfaChallengeState,
} from "@/services/auth.service"
import type { AuthMessageKey } from "./i18n"

export type LoginStatus = "idle" | "pending" | "error" | "success"

const ERROR_KEYS: Record<LoginErrorCode | "UNKNOWN", AuthMessageKey> = {
  INVALID_CREDENTIALS: "auth.login.error.invalid",
  UNAVAILABLE: "auth.login.error.unavailable",
  NETWORK: "auth.login.error.network",
  MFA_INVALID: "auth.mfa.error.invalid",
  MFA_TOO_MANY: "auth.mfa.error.tooMany",
  MFA_TENANT_MISSING: "auth.mfa.error.tenantMissing",
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
 * Resultado discriminado: sessão com token chama `onAuthenticated` com o flag
 * `mfaSetupAvailable` (onboarding opcional); desafio MFA chama `onMfaRequired`
 * com challengeId/tenantId/expiresIn — tudo em memória, sem persistir challenge
 * nem tenant do desafio. O flag NUNCA vaza do ramo autenticado.
 *
 * Já autenticado em `#login` => segue direto para a view autenticada; sem isso
 * um usuário com token válido ficaria preso no formulário.
 */
export function useLoginController(
  onAuthenticated?: (mfaSetupAvailable?: boolean) => void,
  onMfaRequired?: (challenge: MfaChallengeState) => void
): LoginController {
  const [status, setStatus] = useState<LoginStatus>("idle")
  const [errorKey, setErrorKey] = useState<AuthMessageKey | null>(null)
  const onAuthenticatedRef = useRef(onAuthenticated)
  const onMfaRequiredRef = useRef(onMfaRequired)

  useEffect(() => {
    onAuthenticatedRef.current = onAuthenticated
    onMfaRequiredRef.current = onMfaRequired
  }, [onAuthenticated, onMfaRequired])

  useEffect(() => {
    // Redirect de token salvo nao tem resposta de login: sem sinal de onboarding.
    if (getToken()) onAuthenticatedRef.current?.(false)
  }, [])

  const submit = useCallback<LoginController["submit"]>(
    async (credentials) => {
      setStatus("pending")
      setErrorKey(null)
      try {
        const result = await loginService.login(credentials)
        setStatus("success")
        if (result.kind === "mfa-required") {
          onMfaRequiredRef.current?.({
            challengeId: result.challengeId,
            tenantId: result.tenantId,
            expiresIn: result.expiresIn,
          })
        } else {
          onAuthenticatedRef.current?.(result.mfaSetupAvailable)
        }
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
