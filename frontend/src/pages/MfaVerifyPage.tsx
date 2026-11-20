"use client"

import type { MfaChallengeState } from "@/services/auth.service"
import { MfaVerifyView } from "@/modules/hive/auth/components/MfaVerifyView"
import { useMfaVerifyController } from "@/modules/hive/auth/use-mfa-verify-controller"

export interface MfaVerifyPageProps {
  /** Desafio em memória — nunca vem de storage/URL. */
  challenge: MfaChallengeState
  onAuthenticated?: () => void
  onCancel?: () => void
}

/** Página de segundo fator: compositor de rota + controller sobre a view pura. */
export function MfaVerifyPage({ challenge, onAuthenticated, onCancel }: MfaVerifyPageProps) {
  const controller = useMfaVerifyController(challenge, onAuthenticated)

  return (
    <MfaVerifyView
      mode={controller.mode}
      code={controller.code}
      status={controller.status}
      errorKey={controller.errorKey}
      remainingAttempts={controller.remainingAttempts}
      secondsLeft={controller.secondsLeft}
      onModeChange={controller.setMode}
      onCodeChange={controller.setCode}
      onSubmit={() => void controller.submit()}
      onBack={() => onCancel?.()}
    />
  )
}
