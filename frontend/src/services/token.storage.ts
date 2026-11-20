/**
 * Armazenamento do token de acesso — chave única do frontend Hive.
 *
 * Contrato verificado na fonte:
 * - `hive_token` é a chave usada por `services/api.ts` (Bearer) e pelo legado
 *   `bkp/front_bkp/src/stores/auth.store.ts:5,12`.
 * - `LoginResponseDTO.accessToken` (LoginResponseDTO.java:7-10) é o único valor
 *   persistido. Senha, payload de login e token nunca entram em URL, log ou
 *   chave própria do localStorage.
 *
 * Este módulo é a definição canônica; `api.ts` e `auth.service.ts` reexportam
 * os helpers para evitar duas implementações da mesma chave.
 */

export const TOKEN_STORAGE_KEY = "hive_token"

export function getToken(): string | null {
  try {
    return localStorage.getItem(TOKEN_STORAGE_KEY)
  } catch {
    return null
  }
}

export function setToken(token: string): void {
  if (!token || !token.trim()) return
  try {
    localStorage.setItem(TOKEN_STORAGE_KEY, token.trim())
  } catch {
    /* armazenamento bloqueado: sessão não persistida */
  }
}

export function clearToken(): void {
  try {
    localStorage.removeItem(TOKEN_STORAGE_KEY)
  } catch {
    /* armazenamento bloqueado: nada a limpar */
  }
}

export function hasToken(): boolean {
  return Boolean(getToken())
}
