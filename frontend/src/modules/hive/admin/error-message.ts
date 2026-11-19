import { ApiError, redactSecrets } from "@/services/api"

/** Converte erro de API em texto seguro para a UI — nunca devolve token/chave em claro. */
export function apiErrorMessage(error: unknown, fallback: string): string {
  if (error instanceof ApiError) {
    return error.detail && error.detail.trim() ? redactSecrets(error.detail) : fallback
  }
  if (error instanceof Error) {
    const message = redactSecrets(error.message || "")
    return message || fallback
  }
  return fallback
}
