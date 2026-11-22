import { ApiError } from "@/services/api"
import { apiErrorMessage } from "../error-message"
import type { AdminI18n, AdminMessageKey } from "../i18n"

/**
 * 401/403 ganham mensagem própria e localizada; demais erros usam o `detail`
 * redacionado do problem+json via `apiErrorMessage`. Nunca expõe segredo.
 */
export function publicClientsError(
  error: unknown,
  i18n: AdminI18n,
  fallbackKey: AdminMessageKey
): string {
  if (error instanceof ApiError && error.status === 403) {
    return i18n.t("admin.publicClients.errorForbidden")
  }
  if (error instanceof ApiError && error.status === 401) {
    return i18n.t("admin.publicClients.errorUnauthorized")
  }
  return apiErrorMessage(error, i18n.t(fallbackKey))
}
