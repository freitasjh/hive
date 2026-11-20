import { useCallback, useEffect, useRef, useState } from "react"
import {
  LoginError,
  mfaService,
  type LoginErrorCode,
  type MfaChallengeState,
} from "@/services/auth.service"
import type { AuthMessageKey } from "./i18n"

export type MfaMode = "code" | "recovery"
export type MfaVerifyStatus = "idle" | "pending" | "error" | "success"

/** Códigos semânticos → chaves i18n. Nenhum payload do backend chega à UI. */
const ERROR_KEYS: Record<LoginErrorCode | "UNKNOWN", AuthMessageKey> = {
  INVALID_CREDENTIALS: "auth.mfa.error.invalid",
  UNAVAILABLE: "auth.mfa.error.unavailable",
  NETWORK: "auth.mfa.error.network",
  MFA_INVALID: "auth.mfa.error.invalid",
  MFA_TOO_MANY: "auth.mfa.error.tooMany",
  MFA_TENANT_MISSING: "auth.mfa.error.tenantMissing",
  UNKNOWN: "auth.mfa.error.unknown",
}

export interface MfaVerifyController {
  status: MfaVerifyStatus
  isPending: boolean
  errorKey: AuthMessageKey | null
  mode: MfaMode
  code: string
  remainingAttempts: number
  secondsLeft: number
  isExpired: boolean
  setMode: (mode: MfaMode) => void
  setCode: (value: string) => void
  submit: () => Promise<void>
}

const MAX_ATTEMPTS = 5

/**
 * Estado do desafio MFA em memória. `challengeId`/`tenantId`/`expiresIn` chegam
 * pelo compositor de rotas (vindos do login) e nunca são persistidos. O tenant
 * do desafio é repassado ao serviço como X-Tenant-ID, sem aparecer em UI/URL/log.
 * Contagem de tentativas e expiração são locais à tela; 5 erros/expiração
 * bloqueiam o submit.
 */
export function useMfaVerifyController(
  challenge: MfaChallengeState,
  onAuthenticated?: () => void
): MfaVerifyController {
  const [status, setStatus] = useState<MfaVerifyStatus>("idle")
  const [errorKey, setErrorKey] = useState<AuthMessageKey | null>(null)
  const [mode, setModeState] = useState<MfaMode>("code")
  const [code, setCode] = useState("")
  const [remainingAttempts, setRemainingAttempts] = useState(MAX_ATTEMPTS)
  const [secondsLeft, setSecondsLeft] = useState(() =>
    Number.isFinite(challenge.expiresIn) && challenge.expiresIn >= 0 ? challenge.expiresIn : 120
  )
  const authenticatedRef = useRef(onAuthenticated)

  useEffect(() => {
    authenticatedRef.current = onAuthenticated
  }, [onAuthenticated])

  const isExpired = secondsLeft <= 0 || remainingAttempts <= 0

  useEffect(() => {
    if (isExpired) return
    const timer = window.setInterval(() => {
      setSecondsLeft((current) => Math.max(0, current - 1))
    }, 1000)
    return () => window.clearInterval(timer)
  }, [isExpired])

  const setMode = useCallback((next: MfaMode) => {
    setModeState(next)
    setCode("")
    setErrorKey(null)
    setStatus("idle")
  }, [])

  const submit = useCallback(async () => {
    const value = code.trim()
    if (!value || isExpired || status === "pending") return
    if (mode === "code" && !/^\d{6}$/.test(value)) {
      setErrorKey("auth.mfa.code.invalidLength")
      setStatus("error")
      return
    }

    setStatus("pending")
    setErrorKey(null)
    try {
      await (mode === "code"
        ? mfaService.verify(challenge.challengeId, value, challenge.tenantId)
        : mfaService.recovery(challenge.challengeId, value, challenge.tenantId))
      setStatus("success")
      setCode("")
      authenticatedRef.current?.()
    } catch (error) {
      const errorCode = error instanceof LoginError ? error.code : "UNKNOWN"
      setErrorKey(ERROR_KEYS[errorCode])
      setStatus("error")
      setCode("")
      if (errorCode === "MFA_TOO_MANY") setRemainingAttempts(0)
      else if (errorCode === "MFA_INVALID") {
        setRemainingAttempts((current) => Math.max(0, current - 1))
      }
    }
  }, [challenge.challengeId, challenge.tenantId, code, isExpired, mode, status])

  return {
    status,
    isPending: status === "pending",
    errorKey,
    mode,
    code,
    remainingAttempts,
    secondsLeft,
    isExpired,
    setMode,
    setCode,
    submit,
  }
}
