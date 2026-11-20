/**
 * Armazenamento da sessão — chaves canônicas do frontend Hive.
 *
 * Contrato verificado na fonte:
 * - `hive_token` é a chave usada por `services/api.ts` (Bearer) e pelo legado
 *   `bkp/front_bkp/src/stores/auth.store.ts:5,12`.
 * - `hive_tenant_id` é a chave própria do tenant, extraída do claim `tenant-id`
 *   do access token (SecurityTokenServiceImpl.java:42,189). O backend exige o
 *   header `X-Tenant-ID` no refresh (SecurityTokenServiceImpl.java:66-71), então
 *   o tenant precisa sobreviver ao ciclo de vida do token.
 * - `LoginResponseDTO.accessToken` (LoginResponseDTO.java:7-10) é o único valor
 *   de credencial persistido. Senha, payload de login e cookie refreshToken
 *   (HttpOnly — CookieUtil.java:15-22) nunca entram em localStorage/URL/log.
 *
 * Este módulo é a definição canônica; `api.ts` e `auth.service.ts` reexportam
 * os helpers para evitar duas implementações das mesmas chaves.
 */

export const TOKEN_STORAGE_KEY = "hive_token"
export const TENANT_STORAGE_KEY = "hive_tenant_id"

export function getToken(): string | null {
  try {
    return localStorage.getItem(TOKEN_STORAGE_KEY)
  } catch {
    return null
  }
}

export function setToken(token: string, tenantId?: string): void {
  if (!token || !token.trim()) return
  try {
    localStorage.setItem(TOKEN_STORAGE_KEY, token.trim())
    if (tenantId && tenantId.trim()) {
      localStorage.setItem(TENANT_STORAGE_KEY, tenantId.trim())
    }
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

export function getTenantId(): string | null {
  try {
    return localStorage.getItem(TENANT_STORAGE_KEY)
  } catch {
    return null
  }
}

export function setTenantId(tenantId: string): void {
  if (!tenantId || !tenantId.trim()) return
  try {
    localStorage.setItem(TENANT_STORAGE_KEY, tenantId.trim())
  } catch {
    /* armazenamento bloqueado: sessão não persistida */
  }
}

export function clearTenantId(): void {
  try {
    localStorage.removeItem(TENANT_STORAGE_KEY)
  } catch {
    /* armazenamento bloqueado: nada a limpar */
  }
}

/** Falha de refresh / logout / 401 sem recuperação: apaga as duas chaves. */
export function clearSession(): void {
  clearToken()
  clearTenantId()
}
