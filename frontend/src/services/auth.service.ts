/**
 * Serviço de autenticação — POST /hive/v1/auth/login.
 *
 * Contrato verificado na fonte (nenhuma assinatura veio de memória):
 * - LoginControllerV1.java:33 — `@RequestMapping(RestPath.V1 + "/auth")`;
 *   :44-60 — `@PostMapping("/login")` recebe `@Valid LoginDTO`, devolve
 *   `ResponseEntity<LoginResponseDTO>` e 401 para credenciais inválidas.
 * - LoginDTO.java:6-8 — `record LoginDTO(String username, String password)`.
 * - LoginResponseDTO.java:7-10 — `record LoginResponseDTO(UUID userId,
 *   String accessToken, String type)`.
 * - RestControllerTool.java:22-24 — `ResponseEntity.ok(object)`: resposta crua,
 *   sem envelope `{ data }`.
 *
 * - Logout: `POST /auth/logout` com cookie `refreshToken` (`credentials:
 *   'include'`) e header `X-Tenant-ID` — mesmo par de posse do refresh, usado
 *   pelo backend para revogar no Redis. Resposta 204, sem envelope.
 * - Refresh: `POST /auth/refresh-token` (LoginControllerV1.java:63-80) exige
 *   header `X-Tenant-ID` (SecurityTokenServiceImpl.java:66-71) e o cookie
 *   HttpOnly `refreshToken` (CookieUtil.java:15-22). A resposta é crua
 *   `{accessToken}` (RefreshTokenResponseDTO.java:3). O engine de refresh com
 *   single-flight vive em `api.ts` para evitar dependência circular; aqui só
 *   é reexportado.
 *
 * Segurança: body com senha trafega apenas no POST; nunca é logado, persistido
 * ou colocado em URL. Tenant é extraído do claim `tenant-id` do access token.
 */

import { ApiError, apiRequest, refreshAccess } from "./api"
import { decodeTenantId } from "./jwt"
import { clearSession, getTenantId, hasToken, setToken } from "./token.storage"

export {
  TENANT_STORAGE_KEY,
  TOKEN_STORAGE_KEY,
  clearSession,
  clearTenantId,
  clearToken,
  getTenantId,
  getToken,
  hasToken,
  setTenantId,
  setToken,
} from "./token.storage"
export { decodeTenantId } from "./jwt"
export { refreshAccess } from "./api"

/** Renova o access token (delega ao single-flight em `api.ts`). */
export function refresh(): Promise<string> {
  return refreshAccess()
}

export const refreshService = { refresh }

const LOGIN_HASH = "#login"

/**
 * Encerra a sessão. Tenta revogar o refresh token no backend (`POST
 * /auth/logout`, cookie HttpOnly via `credentials: 'include'` + header
 * `X-Tenant-ID` com a chave `hive_tenant_id`) — `/auth/*` nunca dispara
 * refresh/redirect em `api.ts`, então não há loop. Sem tenant salvo a chamada
 * ainda é feita (backend responde 204 idempotente). A falha do servidor é
 * engolida sem log de segredo: o `finally` garante limpeza local e redirect
 * mesmo assim, para que o cliente nunca fique com uma sessão local órfã.
 *
 * `omitAuthorization` é mandatório: o logout autentica-se por OUTRA posse
 * (cookie + X-Tenant-ID). Enviar o Bearer faria o SecurityFilter validá-lo em
 * `/v1/**` e devolver 401 antes do controller, sem revogar o refresh no Redis
 * — mesmo motivo do refresh (api.ts, `performRefresh`).
 */
export async function logout(): Promise<void> {
  try {
    const tenantId = getTenantId()
    await apiRequest<void>("/auth/logout", {
      method: "POST",
      credentials: "include",
      omitAuthorization: true,
      ...(tenantId ? { headers: { "X-Tenant-ID": tenantId } } : {}),
    })
  } catch {
    /* revogação indisponível: a sessão local ainda é encerrada no finally. */
  } finally {
    clearSession()
    window.location.hash = LOGIN_HASH
  }
}

export interface LoginCredentials {
  username: string
  password: string
}

export interface LoginResult {
  userId: string
  accessToken: string
  type: string
}

interface RawLoginResponse {
  userId?: unknown
  accessToken?: unknown
  type?: unknown
}

export type LoginErrorCode = "INVALID_CREDENTIALS" | "UNAVAILABLE" | "NETWORK"

/** Erro semântico: `code` viaja para a camada de UI, payload nunca. */
export class LoginError extends Error {
  readonly code: LoginErrorCode

  constructor(code: LoginErrorCode) {
    super(code)
    this.name = "LoginError"
    this.code = code
  }
}

export const loginService = {
  /** Autentica e persiste accessToken + tenantId extraído do JWT. */
  async login(credentials: LoginCredentials): Promise<LoginResult> {
    let payload: RawLoginResponse
    try {
      payload = await apiRequest<RawLoginResponse>("/auth/login", {
        method: "POST",
        body: { username: credentials.username, password: credentials.password },
      })
    } catch (error) {
      if (error instanceof ApiError && error.status === 401) {
        throw new LoginError("INVALID_CREDENTIALS")
      }
      if (error instanceof ApiError && error.status === 0) {
        throw new LoginError("NETWORK")
      }
      throw new LoginError("UNAVAILABLE")
    }

    if (!payload || typeof payload.accessToken !== "string" || !payload.accessToken.trim()) {
      throw new LoginError("UNAVAILABLE")
    }

    const accessToken = payload.accessToken
    // Tenant é mandatório no refresh (SecurityTokenServiceImpl.java:69-71).
    const tenantId = decodeTenantId(accessToken)
    if (!tenantId) throw new LoginError("UNAVAILABLE")

    setToken(accessToken, tenantId)
    return {
      userId: typeof payload.userId === "string" ? payload.userId : "",
      accessToken,
      type: typeof payload.type === "string" ? payload.type : "Bearer",
    }
  },

  isAuthenticated: hasToken,
  logout,
}
