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
 * Segurança: body com senha trafega apenas no POST; nunca é logado, persistido
 * ou colocado em URL. Nada de refresh-token automático (cookie HttpOnly, fora
 * do PICCO desta entrega).
 */

import { ApiError, apiRequest } from "./api"
import { clearToken, hasToken, setToken } from "./token.storage"

export { TOKEN_STORAGE_KEY, clearToken, getToken, hasToken, setToken } from "./token.storage"

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
  /** Autentica e persiste somente o accessToken em `hive_token`. */
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
    setToken(accessToken)
    return {
      userId: typeof payload.userId === "string" ? payload.userId : "",
      accessToken,
      type: typeof payload.type === "string" ? payload.type : "Bearer",
    }
  },

  isAuthenticated: hasToken,
  logout: clearToken,
}
