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
// SseEmitter do assistant: 120s; 10s de margem para o provider responder.
const STREAM_TIMEOUT_MS = 130_000
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
 *
 * Exceção multi-aba: `REFRESH_TOKEN_NOT_FOUND` (500 com `code` no corpo) não
 * apaga sessão nem redireciona. `SecurityTokenServiceImpl.java:100` revoga o
 * cookie a cada refresh; duas abas no mesmo host compartilham o cookie e a
 * segunda refresh recebe 500. O erro é propagado ao chamador para o 401 original
 * da chamada que disparou o refresh sobreviver em `performRequest`.
 * Caminho: backend/security/impl/src/main/java/br/com/systec/hive/security/impl/service/SecurityTokenServiceImpl.java:100
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
        // Rotação entre abas: token atual ainda é válido; preservar sessão.
        if (!(error instanceof ApiError && error.code === 'REFRESH_TOKEN_NOT_FOUND')) {
          clearSession()
          redirectToLogin()
        }
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

/* -------------------------------------------------------------------------- */
/* SSE — eventos nomeados do assistant (/v1/chat/stream)                       */
/* -------------------------------------------------------------------------- */

export interface SseFrame {
  event: string
  data: string
}

export interface ApiStreamOptions {
  method?: "GET" | "POST"
  body?: unknown
  signal?: AbortSignal
  timeoutMs?: number
  headers?: Record<string, string>
}

/**
 * Parse de frames SSE conforme WHATWG: linhas `event:`/`data:`, `data:` múltiplas
 * concatenadas com `\n`, comentários (`:`) e campos `id`/`retry` ignorados.
 * `flush` devolve o frame pendente quando o stream fecha sem linha em branco.
 * Fonte: https://html.spec.whatwg.org/multipage/server-sent-events.html#event-stream-interpretation
 * (consultado em 2026-11-27).
 */
export function parseSseFrames(
  buffer: string,
  flush = false
): { frames: SseFrame[]; rest: string } {
  const frames: SseFrame[] = []
  let event = ""
  const data: string[] = []
  let cursor = 0
  /** Início do frame ainda não fechado; volta no rest para o próximo chunk. */
  let frameStart = 0

  const dispatch = () => {
    if (event || data.length > 0) {
      frames.push({ event: event || "message", data: data.join("\n") })
    }
    event = ""
    data.length = 0
    frameStart = cursor
  }
  const accept = (line: string) => {
    if (line === "") {
      dispatch()
    } else if (line.startsWith(":")) {
      return // heartbeat/comentário
    } else if (line.startsWith("event:")) {
      event = line.slice("event:".length).trim()
    } else if (line.startsWith("data:")) {
      // SSE remove exatamente um espaço após o dois-pontos.
      data.push(line.slice("data:".length).replace(/^ /, ""))
    }
    // id:/retry:/campos desconhecidos não são usados pelo cliente.
  }

  // Só consome linhas terminadas por \n. O que sobra (inclusive um `event:`
  // ainda sem linha em branco) volta no rest para o próximo chunk — sem isso
  // o nome do evento se perde quando o chunk corta o `data:` no meio.
  while (cursor < buffer.length) {
    const end = buffer.indexOf("\n", cursor)
    if (end === -1) break
    accept(buffer.slice(cursor, end).replace(/\r$/, ""))
    cursor = end + 1
  }

  if (flush) {
    // WHATWG descarta frame incompleto no EOF; o SseEmitter do Spring fecha com
    // linha em branco, então despachar o resto aqui não muda o wire observado.
    if (cursor < buffer.length) accept(buffer.slice(cursor).replace(/\r$/, ""))
    dispatch()
    return { frames, rest: "" }
  }
  return { frames, rest: buffer.slice(frameStart) }
}

/** Abrir o stream com a mesma política de credenciais de `performRequest`. */
async function streamFetch(
  path: string,
  options: ApiStreamOptions,
  controller: AbortController
): Promise<Response> {
  const { method = "POST", body } = options
  const headers: Record<string, string> = {
    Accept: "text/event-stream",
    "Cache-Control": "no-cache",
  }
  const token = getToken()
  if (token && !isAuthRequest(path)) headers.Authorization = `Bearer ${token}`
  const tenantId = getTenantId()
  if (tenantId) headers["X-Tenant-ID"] = tenantId
  if (body !== undefined) headers["Content-Type"] = "application/json"
  for (const [key, value] of Object.entries(options.headers ?? {})) {
    if (PROTECTED_HEADERS.has(key.toLowerCase())) continue
    headers[key] = value
  }

  return fetch(buildUrl(path), {
    method,
    headers,
    body: body === undefined ? undefined : JSON.stringify(body),
    credentials: "include", // cookie HttpOnly do refresh
    cache: "no-store",
    signal: controller.signal,
  })
}

/**
 * Consome `text/event-stream` com `fetch` + `getReader()`. `EventSource` é
 * proibido (não envia Authorization) e token nunca entra em query string.
 * 401 usa o MESMO refresh single-flight de `apiRequest`, com retry único.
 * Abort externo encerra sem erro; timeout gera ApiError(TIMEOUT). O controller
 * é abortado ao final para cancelar o emitter no backend.
 */
export async function* apiStreamFrames(
  path: string,
  options: ApiStreamOptions = {}
): AsyncGenerator<SseFrame> {
  const controller = new AbortController()
  let timedOut = false
  let onAbort: (() => void) | undefined
  if (options.signal) {
    if (options.signal.aborted) controller.abort()
    else {
      onAbort = () => controller.abort()
      options.signal.addEventListener("abort", onAbort, { once: true })
    }
  }
  const timer =
    options.timeoutMs === 0
      ? undefined
      : setTimeout(() => {
          timedOut = true
          controller.abort()
        }, options.timeoutMs ?? STREAM_TIMEOUT_MS)

  let reader: ReadableStreamDefaultReader<Uint8Array> | undefined
  // Credencial usada no primeiro envio — congela antes do fetch, como performRequest.
  const sentToken = getToken()
  try {
    let response = await streamFetch(path, options, controller)
    if (response.status === 401 && !isAuthRequest(path)) {
      const current = getToken()
      if (!current) {
        clearSession()
        redirectToLogin()
      } else if (current !== sentToken) {
        // Outra aba rotacionou enquanto o stream aguardava o 401: repetir com o
        // token vigente, sem nova refresh (blacklistaria o JTI recém-emitido).
        if (!options.signal?.aborted) response = await streamFetch(path, options, controller)
      } else {
        try {
          await refreshAccess()
        } catch {
          // refreshAccess já limpou sessão e redirecionou; mantém o 401 original.
        }
        if (!options.signal?.aborted) response = await streamFetch(path, options, controller)
      }
    }
    if (!response.ok) {
      const { detail, code } = failureMessage(response.status, undefined)
      throw new ApiError(detail, response.status, code, detail)
    }
    if (!response.body) throw new ApiError("Stream indisponível.", 0, "STREAM_UNAVAILABLE")

    reader = response.body.getReader()
    const decoder = new TextDecoder()
    let buffer = ""
    while (true) {
      const { done, value } = await reader.read()
      if (done) break
      const parsed = parseSseFrames(buffer + decoder.decode(value, { stream: true }))
      buffer = parsed.rest
      for (const frame of parsed.frames) yield frame
    }
    const tail = parseSseFrames(buffer + decoder.decode(), true)
    for (const frame of tail.frames) yield frame
  } catch (error) {
    if (options.signal?.aborted) return
    if (timedOut) throw new ApiError("Tempo limite atingido. Tente novamente.", 0, "TIMEOUT")
    if (error instanceof Error && error.name === "AbortError") return
    if (error instanceof ApiError) throw error
    const message = error instanceof Error ? error.message : "Falha de rede."
    throw new ApiError(redactSecrets(message || "Falha de rede."), 0, "NETWORK_ERROR")
  } finally {
    if (options.signal && onAbort) options.signal.removeEventListener("abort", onAbort)
    if (timer) clearTimeout(timer)
    if (reader) reader.releaseLock()
    controller.abort() // cancela o emitter quando o consumidor encerra antes do fim
  }
}
