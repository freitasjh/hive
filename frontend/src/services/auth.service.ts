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

/** Fase 4: login é discriminado — ou sessão com token, ou desafio MFA sem token. */
export interface AuthenticatedResult {
  kind: "authenticated"
  userId: string
  accessToken: string
  type: string
  /**
   * Onboarding opcional: true somente quando o usuario autenticado AINDA nao
   * tem MFA. Campo ausente (backend antigo) => false: nunca oferece convite
   * por inferencia. Nao e segredo — apenas sinalizador de UI, nunca persistido.
   */
  mfaSetupAvailable: boolean
}

/**
 * Estado do desafio em MEMÓRIA — nunca em localStorage/sessionStorage/URL.
 *
 * `tenantId` vem da resposta do desafio (backend Fase 4) e é a única posse
 * disponível em um browser fresco: o segundo fator autentica-se por cookie +
 * X-Tenant-ID, sem Bearer e sem tenant de sessão anterior.
 */
export interface MfaChallengeState {
  challengeId: string
  tenantId: string
  expiresIn: number
}

export interface MfaRequiredResult extends MfaChallengeState {
  kind: "mfa-required"
}

export type AuthResult = AuthenticatedResult | MfaRequiredResult

/** Alias de compatibilidade: o ramo autenticado do resultado discriminado. */
export type LoginResult = AuthenticatedResult

/** Resultado do segundo fator — mesmo contrato de token do login. */
export interface MfaVerifyResult {
  userId: string
  accessToken: string
  type: string
}

/** `setup` expõe segredo/códigos uma única vez; nunca entram em storage/URL/log. */
export interface MfaSetupData {
  secret: string
  qrDataUri: string
  recoveryCodes: string[]
}

interface RawLoginResponse {
  userId?: unknown
  accessToken?: unknown
  type?: unknown
  mfaSetupAvailable?: unknown
  mfaRequired?: unknown
  challengeId?: unknown
  tenantId?: unknown
  expiresIn?: unknown
}

/**
 * Formato canônico RFC 4122 §3 (8-4-4-4-12 hex, case-insensitive).
 * Fonte: https://www.rfc-editor.org/info/rfc4122/ — consultado em 2026-11-20.
 * Nenhuma biblioteca nova: validação local, antes de qualquer uso do valor.
 */
const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i

function isUuid(value: unknown): value is string {
  return typeof value === "string" && UUID_PATTERN.test(value)
}

interface RawMfaSetupResponse {
  secret?: unknown
  qrDataUri?: unknown
  recoveryCodes?: unknown
}

export type LoginErrorCode =
  | "INVALID_CREDENTIALS"
  | "UNAVAILABLE"
  | "NETWORK"
  | "MFA_INVALID"
  | "MFA_TOO_MANY"
  | "MFA_TENANT_MISSING"

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
  /**
   * Autentica. Com MFA ativo devolve ONLY o desafio — nenhum token/tenant/challenge
   * é persistido neste ramo (o challenge vive em memória no App). Sem MFA, mantém
   * o contrato anterior: salva token + tenant e devolve o resultado autenticado.
   */
  async login(credentials: LoginCredentials): Promise<AuthResult> {
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

    // Ramo MFA: o backend não emite token nem cookie. Nada é salvo aqui —
    // challengeId e tenantId viajam somente no estado em memória do App.
    if (payload?.mfaRequired === true) {
      const challengeId = payload.challengeId
      if (typeof challengeId !== "string" || !challengeId.trim()) {
        throw new LoginError("UNAVAILABLE")
      }
      const tenantId = payload.tenantId
      if (!isUuid(tenantId)) throw new LoginError("UNAVAILABLE")
      return {
        kind: "mfa-required",
        challengeId: challengeId.trim(),
        tenantId: tenantId.trim(),
        expiresIn:
          typeof payload.expiresIn === "number" && payload.expiresIn > 0
            ? payload.expiresIn
            : 120,
      }
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
      kind: "authenticated",
      userId: typeof payload.userId === "string" ? payload.userId : "",
      accessToken,
      type: typeof payload.type === "string" ? payload.type : "Bearer",
      // Estritamente true: campo ausente/falso (backend antigo ou usuario com
      // MFA ativo) nunca dispara onboarding. Nao entra em storage nem na URL.
      mfaSetupAvailable: payload.mfaSetupAvailable === true,
    }
  },

  isAuthenticated: hasToken,
  logout,
}

/** Erros do segundo fator são semânticos: nenhum payload do servidor vaza para a UI. */
function mapMfaError(error: unknown): LoginError {
  if (error instanceof ApiError) {
    if (error.status === 401) return new LoginError("MFA_INVALID")
    if (error.status === 429) return new LoginError("MFA_TOO_MANY")
    if (error.status === 0) return new LoginError("NETWORK")
  }
  return new LoginError("UNAVAILABLE")
}

/**
 * Emite o token após o segundo fator. Mesma posse do refresh/logout: cookie
 * HttpOnly + X-Tenant-ID, SEM Bearer (o SecurityFilter validaria o Bearer antes
 * do controller permitAll e devolveria 401 — atlas-cobranca D8 / brain).
 *
 * `challengeTenantId` (estado em memória do desafio) tem PREFERÊNCIA: é a única
 * posse disponível em browser fresco. O tenant salvo é fallback exclusivo de
 * compatibilidade com backend antigo (que ainda não devolve tenantId no desafio).
 * O tenant do desafio NUNCA é semeado em storage — a persistência posterior
 * usa somente o claim `tenant-id` do JWT recém-emitido (ou o tenant salvo).
 * Sem nenhuma das duas fontes, falha orientada sem abrir a requisição.
 */
async function issueMfaToken(
  path: "/auth/mfa/verify" | "/auth/mfa/recovery",
  body: { challengeId: string } & ({ code: string } | { recoveryCode: string }),
  challengeTenantId?: string
): Promise<MfaVerifyResult> {
  const storedTenantId = getTenantId()
  const tenantId =
    challengeTenantId && isUuid(challengeTenantId) ? challengeTenantId.trim() : storedTenantId
  if (!tenantId) throw new LoginError("MFA_TENANT_MISSING")

  let payload: RawLoginResponse
  try {
    payload = await apiRequest<RawLoginResponse>(path, {
      method: "POST",
      credentials: "include",
      omitAuthorization: true,
      headers: { "X-Tenant-ID": tenantId },
      body,
    })
  } catch (error) {
    throw mapMfaError(error)
  }

  const accessToken = payload?.accessToken
  if (typeof accessToken !== "string" || !accessToken.trim()) {
    throw new LoginError("UNAVAILABLE")
  }
  // Persistência: claim do novo JWT prevalece; fallback é o tenant JÁ salvo.
  // `challengeTenantId` nunca entra aqui — desafio não semeia storage.
  setToken(accessToken, decodeTenantId(accessToken) ?? storedTenantId ?? undefined)
  return {
    userId: typeof payload.userId === "string" ? payload.userId : "",
    accessToken,
    type: typeof payload.type === "string" ? payload.type : "Bearer",
  }
}

/** Gerenciamento de MFA: exige Bearer válido — não usa omitAuthorization. */
async function managementPost(path: string, body?: unknown): Promise<void> {
  try {
    await apiRequest<void>(path, { method: "POST", ...(body === undefined ? {} : { body }) })
  } catch {
    throw new LoginError("UNAVAILABLE")
  }
}

export const mfaService = {
  /** Segundo fator por código TOTP — sem Bearer, tenant do desafio em memória. */
  verify(challengeId: string, code: string, tenantId?: string): Promise<MfaVerifyResult> {
    return issueMfaToken("/auth/mfa/verify", { challengeId, code }, tenantId)
  },

  /** Segundo fator por recovery code de uso único — sem Bearer. */
  recovery(challengeId: string, recoveryCode: string, tenantId?: string): Promise<MfaVerifyResult> {
    return issueMfaToken("/auth/mfa/recovery", { challengeId, recoveryCode }, tenantId)
  },

  /** Gera secret + QR + 8 códigos (exibidos uma única vez). Exige Bearer. */
  async setup(): Promise<MfaSetupData> {
    let payload: RawMfaSetupResponse
    try {
      payload = await apiRequest<RawMfaSetupResponse>("/auth/mfa/setup", { method: "POST" })
    } catch {
      throw new LoginError("UNAVAILABLE")
    }
    if (
      !payload ||
      typeof payload.secret !== "string" ||
      !payload.secret.trim() ||
      typeof payload.qrDataUri !== "string" ||
      !Array.isArray(payload.recoveryCodes)
    ) {
      throw new LoginError("UNAVAILABLE")
    }
    return {
      secret: payload.secret,
      qrDataUri: payload.qrDataUri,
      recoveryCodes: payload.recoveryCodes.filter(
        (code): code is string => typeof code === "string" && code.length > 0
      ),
    }
  },

  /** Confirma o código TOTP do setup. Exige Bearer. */
  async enable(code: string): Promise<void> {
    await managementPost("/auth/mfa/enable", { code })
  },

  /** Remove secret + recovery codes mediante senha. Exige Bearer. */
  async disable(password: string): Promise<void> {
    await managementPost("/auth/mfa/disable", { password })
  },
}
