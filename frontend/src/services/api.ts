/**
 * Cliente HTTP nativo do Hive React.
 *
 * Contrato verificado no código-fonte:
 * - context path `/hive` + `/v1` — brain `regras/projetos/hive/agents-rules-alignment`
 *   e `RestPath.V1` em
 *   backend/commons/api-tools/src/main/java/br/com/systec/hive/commons/api/tools/controller/RestPath.java:6
 * - Bearer de `localStorage['hive_token']` — bkp/front_bkp/src/stores/auth.store.ts:5,12
 * - 401 FORA de `/auth/login` => limpa token + hash `#login` (sem reload) —
 *   app é hash-router; `window.location.assign('/login')` gerava 404.
 * - corpo de erro `StandardError` com `status`/`code`/`detail`/`title` —
 *   backend/commons/api-tools/.../exceptions/ControllerExceptionHandler.java:36-45
 *
 * O frontend React não possui axios (frontend/package.json). Nenhuma dependência nova foi
 * adicionada: uso `fetch` + `AbortController` com timeout explícito.
 *
 * Segurança: token nunca entra em URL, headers são locais, nada é escrito no console e
 * `detail` recebido do servidor passa por redação antes de virar mensagem de UI.
 */

import { clearToken, getToken } from "./token.storage"

export { TOKEN_STORAGE_KEY, clearToken, getToken } from "./token.storage"

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
}

/** Chamadas da própria rota de autenticação nunca disparam redirect (evita loop). */
export function isAuthRequest(path: string): boolean {
  return /\/auth\/(?:login|refresh-token)(?:\?|$)/.test(path)
}

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

export async function apiRequest<T>(path: string, options: ApiRequestOptions = {}): Promise<T> {
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
  const token = getToken()
  if (token) headers.Authorization = `Bearer ${token}`
  if (body !== undefined) headers["Content-Type"] = "application/json"

  try {
    const response = await fetch(buildUrl(path, query), {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
      signal: controller.signal,
    })
    const payload = await readBody(response)

    if (!response.ok) {
      if (response.status === 401 && !isAuthRequest(path)) {
        clearToken()
        redirectToLogin()
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
