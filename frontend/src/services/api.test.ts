import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { API_BASE, ApiError, apiRequest } from './api'

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'content-type': 'application/json' },
  })
}

describe('apiRequest', () => {
  beforeEach(() => {
    localStorage.clear()
    vi.unstubAllGlobals()
  })

  afterEach(() => {
    vi.unstubAllGlobals()
    vi.restoreAllMocks()
  })

  it('usa base /hive/v1, Bearer do localStorage e nunca coloca token na URL', async () => {
    localStorage.setItem('hive_token', 'token-super-secreto')
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse({ ok: true }))
    vi.stubGlobal('fetch', fetchMock)

    await apiRequest<{ ok: boolean }>('/provider-credentials', {
      query: { page: 0, size: 20 },
    })

    expect(fetchMock).toHaveBeenCalledTimes(1)
    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit]
    expect(url).toBe(`${API_BASE}/provider-credentials?page=0&size=20`)
    expect(url).not.toContain('token-super-secreto')
    const headers = init.headers as Record<string, string>
    expect(headers.Authorization).toBe('Bearer token-super-secreto')
    expect(headers.Accept).toBe('application/json')
  })

  it('envia JSON no body de POST/PUT', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse({ ok: true }, 201))
    vi.stubGlobal('fetch', fetchMock)

    await apiRequest('/providers/validate', {
      method: 'POST',
      body: { provider: 'OPENAI', model: 'gpt-4o-mini' },
    })

    const [, init] = fetchMock.mock.calls[0] as [string, RequestInit]
    expect(init.method).toBe('POST')
    expect((init.headers as Record<string, string>)['Content-Type']).toBe('application/json')
    expect(JSON.parse(init.body as string)).toEqual({
      provider: 'OPENAI',
      model: 'gpt-4o-mini',
    })
  })

  it('trata 204 sem corpo como undefined', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(null, { status: 204 })))
    await expect(apiRequest('/provider-credentials/OPENAI', { method: 'DELETE' })).resolves.toBeUndefined()
  })

  it('em 401 limpa o token e redireciona para /login', async () => {
    localStorage.setItem('hive_token', 'token-expirado')
    const assign = vi.fn()
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse({ status: 401 }, 401)))
    vi.stubGlobal('window', { ...globalThis.window, location: { assign } })

    await expect(apiRequest('/provider-credentials')).rejects.toBeInstanceOf(ApiError)

    expect(localStorage.getItem('hive_token')).toBeNull()
    expect(assign).toHaveBeenCalledWith('/login')
  })

  it('expõe status/code/detail do problema e remove segredos do detail', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        jsonResponse(
          {
            status: 422,
            code: 'INVALID_API_KEY',
            detail: 'chave sk-abcdef123456 rejeitada; Bearer tok-secret ',
          },
          422
        )
      )
    )

    const error = await apiRequest('/provider-credentials/OPENAI', { method: 'PUT' }).catch((e: unknown) => e)

    expect(error).toBeInstanceOf(ApiError)
    const apiError = error as ApiError
    expect(apiError.status).toBe(422)
    expect(apiError.code).toBe('INVALID_API_KEY')
    expect(apiError.detail).not.toContain('abcdef123456')
    expect(apiError.detail).not.toContain('tok-secret')
  })

  it('aborta requisição que excede timeoutMs', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockImplementation((_url: string, init: RequestInit) =>
        new Promise((_resolve, reject) => {
          init.signal?.addEventListener('abort', () => {
            const err = new Error('aborted')
            err.name = 'AbortError'
            reject(err)
          })
        })
      )
    )

    const error = await apiRequest('/prompt-templates', { timeoutMs: 10 }).catch((e: unknown) => e)
    expect(error).toBeInstanceOf(ApiError)
    expect((error as ApiError).code).toBe('TIMEOUT')
  })

  it('remove o listener externo de abort ao concluir a requisição', async () => {
    const external = new AbortController()
    const removeSpy = vi.spyOn(external.signal, 'removeEventListener')
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse({ ok: true })))

    await apiRequest('/prompt-templates', { signal: external.signal })

    expect(removeSpy).toHaveBeenCalledWith('abort', expect.any(Function))
  })

  it('não imprime segredos no console', async () => {
    const errorSpy = vi.spyOn(console, 'error').mockImplementation(() => {})
    const warnSpy = vi.spyOn(console, 'warn').mockImplementation(() => {})
    const logSpy = vi.spyOn(console, 'log').mockImplementation(() => {})
    localStorage.setItem('hive_token', 'token-secreto')
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse({ status: 500 }, 500)))

    await apiRequest('/provider-credentials').catch(() => undefined)

    expect(errorSpy).not.toHaveBeenCalled()
    expect(warnSpy).not.toHaveBeenCalled()
    expect(logSpy).not.toHaveBeenCalled()
  })
})
