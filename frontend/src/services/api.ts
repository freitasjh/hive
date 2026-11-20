/**
 * Cliente HTTP nativo do Hive React.
 *
 * Contrato verificado no código-fonte:
 * - context path `/hive` + `/v1` — brain `regras/projetos/hive/agents-rules-alignment`
 *   e `RestPath.V1` em
 *   backend/commons/api-tools/src/main/java/br/com/systec/hive/commons/api/tools/controller/RestPath.java:6
 * - Bearer de `localStorage['hive_token']` — bkp/front_bkp/src/stores/auth.store.ts:5,12
 * - 401 em rota protegida => refresh transparente (POST `/auth/refresh-token`,
 *   cookie HttpOnly via `credentials: 'include'` + header `X-Tenant-ID`) e retry
 *   único da chamada original; se o refresh falhar ou o retry ainda dar 401,
 *   limpa token + tenant e vai para `#login` (sem reload — app é hash-router).
 * - 401 em `/auth/*` nunca dispara refresh/redirect (anti-loop).
 * - Refresh em single-flight: N chamadas concorrentes compartilham UMA promessa.
 * - corpo de erro `StandardError` com `status`/`code`/`detail`/`title` —
 *   backend/commons/api-tools/.../exceptions/ControllerExceptionHandler.java:36-45
 *
 * O frontend React não possui axios (frontend/package.json). Nenhuma dependência nova foi
 * adicionada: uso `fetch` + `AbortController` com timeout explícito.
 *
 * Segurança: token nunca entra em URL, headers são locais, nada é escrito no console e
 * `detail` recebido do servidor passa por redação antes de virar mensagem de UI.
 */

import { decodeTenantId } from "./jwt"
import {
  clearSession,
  getTenantId,
  getToken,
  setToken,
} from "./token.storage"

export {
  TENANT_STORAGE_KEY,
  TOKEN_STORAGE_KEY,
  clearSession,
  clearTenantId,
  clearToken,
  getTenantId,
  getToken,
  setTenantId,
  setToken,
} from "./token.storage"
export { decodeTenantId } from "./jwt"

export const API_BASE = "/hive/v1"
const DEFAULT_TIMEOUT_MS = 15_000
const LOGIN_HASH = "#login"

export class ApiError extends Error {
  readonly status?: number
  readonly code?: string
  readonly detail?: string

  constructor(message: string, status?: number, code?: string, detail?: string) {
    super(message)
    this.name = "ApiError"
    this.status = status
    this.code = code
    this.detail = detail
  }
}

export interface ApiRequestOptions {
  method?: "GET" | "POST" | "PUT" | "PATCH" | "DELETE"
  body?: unknown
  query?: Record<string, string | number | boolean | null | undefined>
  timeoutMs?: number
  signal?: AbortSignal
  /** Cabeçalhos adicionais. Nunca sobrescrevem `Authorization`, `Accept` ou `Content-Type`. */
  headers?: Record<string, string>
  /** `'include'` envia o cookie HttpOnly `refreshToken` (ver MDN Request.credentials). */
  credentials?: RequestCredentials
  /**
   * Omite o Bearer. O refresh autentica-se por OUTRA posse (cookie + X-Tenant-ID):
   * enviar o access expirado/revogado faz o SecurityFilter do backend devolver 401
   * antes do controller permitAll (SecurityFilter.java:49 / atlas-cobranca D8).
   */
  omitAuthorization?: boolean
}

/** Qualquer rota começando com `/auth/` nunca dispara refresh/redirect (evita loop). */
export function isAuthRequest(path: string): boolean {
  return /^\/auth\//.test(path)
}

/** Cabeçalhos controlados pelo cliente — caller nunca os substitui. */
const PROTECTED_HEADERS = new Set(["authorization", "accept", "content-type"])

/** `#login` e a forma legada `#/login` apontam para a mesma rota do hash-router. */
export function isLoginHash(hash: string = window.location.hash): boolean {
  const current = typeof hash === "string" ? hash : ""
  return current.replace(/^#\/?/, "").split("/")[0] === "login"
}

/** Navega por hash: o app é um hash-router, então `assign('/login')` daria 404. */
export function redirectToLogin(): void {
  if (isLoginHash()) return
  window.location.hash = LOGIN_HASH
}

/** Remove padrões de segredo antes de qualquer texto chegar à UI. */
export function redactSecrets(value: string): string {
  return value
    .replace(/Bearer\s+[\w.~+/=_-]{4,}/gi, "Bearer ••••")
    .replace(/sk-ant-[A-Za-z0-9_-]{4,}/g, "sk-ant-••••")
    .replace(/sk-[A-Za-z0-9_-]{4,}/g, "sk-••••")
    .replace(/AIza[A-Za-z0-9_-]{4,}/g, "AIza••••")
    .replace(
      /((?:apiKey|api_key|token|secret|password|authorization)["']?\s*[:=]\s*["']?)[^"',}\s]+/gi,
      "$1••••"
    )
}

function buildUrl(path: string, query?: ApiRequestOptions["query"]): string {
  const params = new URLSearchParams()
  for (const [key, value] of Object.entries(query ?? {})) {
    if (value === undefined || value === null || value === "") continue
    params.set(key, String(value))
  }
  const suffix = params.toString()
  return `${API_BASE}${path}${suffix ? `?${suffix}` : ""}`
}

function asRecord(value: unknown): Record<string, unknown> | null {
  return value && typeof value === "object" ? (value as Record<string, unknown>) : null
}

async function readBody(response: Response): Promise<unknown> {
  if (response.status === 204) return undefined
  const contentType = response.headers.get("content-type") ?? ""
  if (!contentType.includes("json")) return undefined
  try {
    return await response.json()
  } catch {
    return undefined
  }
}

function failureMessage(status: number, body: unknown): { detail: string; code?: string } {
  const record = asRecord(body)
  const raw = record?.detail ?? record?.title ?? record?.message
  const fallback = status >= 500 ? "Erro interno no servidor." : "A requisição não pôde ser concluída."
  const detail = redactSecrets(typeof raw === "string" && raw.trim() ? raw : fallback)
  const code = typeof record?.code === "string" ? record.code : undefined
  return { detail, code }
}

/** Envelope opcional `{ data: ... }` usado por alguns endpoints legados. */
export function unwrapData<T>(data: unknown): T {
  const record = asRecord(data)
  if (record && record.data !== undefined) return record.data as T
  return data as T
}

/**
 * Uma única renovação em voo. Promise compartilhada por todas as chamadas que
 * tomarem 401 enquanto o refresh está em andamento (single-flight).
 */
let inFlightRefresh: Promise<string> | null = null

/** Chamada de rede crua — resposta `{accessToken}` sem envelope. */
async function performRefresh(): Promise<string> {
  const tenantId = getTenantId()
  if (!tenantId) {
    throw new ApiError("Sessão inválida.", 401, "REFRESH_UNAVAILABLE")
  }

  const payload = await apiRequest<{ accessToken?: unknown }>("/auth/refresh-token", {
    method: "POST",
    credentials: "include",
    omitAuthorization: true,
    headers: { "X-Tenant-ID": tenantId },
  })

  const accessToken = payload?.accessToken
  if (typeof accessToken !== "string" || !accessToken.trim()) {
    throw new ApiError("Sessão inválida.", 401, "REFRESH_INVALID")
  }

  // Rotação: novo token substitui o antigo; tenant vem do novo payload (ou do
  // anterior como fallback se o JWT vier sem claim).
  setToken(accessToken, decodeTenantId(accessToken) ?? tenantId)
  return accessToken
}

/**
 * Renova o access token. Em caso de falha (401/404/rede/payload inválido) apaga
 * token + tenant e redireciona para `#login`. `/auth/refresh-token` é
 * `isAuthRequest`, então `apiRequest` não recursa nem redireciona por conta própria.
 */
export function refreshAccess(): Promise<string> {
  if (!inFlightRefresh) {
    inFlightRefresh = performRefresh().then(
      (token) => {
        inFlightRefresh = null
        return token
      },
      (error: unknown) => {
        inFlightRefresh = null
        clearSession()
        redirectToLogin()
        throw error
      }
    )
  }
  return inFlightRefresh
}

/** Wrapper público: `retried` é interno e impede loop de 401. */
export function apiRequest<T>(path: string, options: ApiRequestOptions = {}): Promise<T> {
  return performRequest<T>(path, options, false)
}

async function performRequest<T>(
  path: string,
  options: ApiRequestOptions,
  retried: boolean
): Promise<T> {
  const { method = "GET", body, query, timeoutMs = DEFAULT_TIMEOUT_MS, signal } = options
  const controller = new AbortController()
  const timer = timeoutMs > 0 ? setTimeout(() => controller.abort(), timeoutMs) : undefined

  let onAbort: (() => void) | undefined
  if (signal) {
    if (signal.aborted) controller.abort()
    else {
      onAbort = () => controller.abort()
      signal.addEventListener("abort", onAbort, { once: true })
    }
  }

  const headers: Record<string, string> = { Accept: "application/json" }
  // Congelado antes do fetch: identifica qual credencial esta chamada usou.
  const sentToken = getToken()
  if (sentToken && !options.omitAuthorization) headers.Authorization = `Bearer ${sentToken}`
  if (body !== undefined) headers["Content-Type"] = "application/json"
  for (const [key, value] of Object.entries(options.headers ?? {})) {
    if (PROTECTED_HEADERS.has(key.toLowerCase())) continue
    headers[key] = value
  }

  try {
    const response = await fetch(buildUrl(path, query), {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
      credentials: options.credentials,
      signal: controller.signal,
    })
    const payload = await readBody(response)

    if (!response.ok) {
      if (response.status === 401 && !isAuthRequest(path)) {
        const current = getToken()
        if (!retried && current && current !== sentToken) {
          // Outra chamada rotacionou enquanto esta aguardava o 401: repete com o
          // token vigente, sem nova refresh. Uma segunda rotação blacklistaria
          // o JTI que a primeira acabou de emitir (SecurityTokenServiceImpl:103)
          // e o retry cairia em 401 → logout espúrio sob carga.
          return performRequest<T>(path, options, true)
        }
        if (!retried && current) {
          let refreshed = false
          try {
            await refreshAccess()
            refreshed = true
          } catch {
            // refreshAccess já limpou sessão e redirecionou; mantém o 401 original.
          }
          if (refreshed) {
            // Repete a chamada original UMA vez; novo Bearer vem do storage.
            return performRequest<T>(path, options, true)
          }
        } else {
          clearSession()
          redirectToLogin()
        }
      }
      const { detail, code } = failureMessage(response.status, payload)
      throw new ApiError(detail, response.status, code, detail)
    }

    return payload as T
  } catch (error) {
    if (error instanceof ApiError) throw error
    if (error instanceof Error && error.name === "AbortError") {
      throw new ApiError("Tempo limite atingido. Tente novamente.", 0, "TIMEOUT")
    }
    const message = error instanceof Error ? error.message : "Falha de rede."
    throw new ApiError(redactSecrets(message || "Falha de rede."), 0, "NETWORK_ERROR")
  } finally {
    if (signal && onAbort) signal.removeEventListener("abort", onAbort)
    if (timer) clearTimeout(timer)
  }
}
