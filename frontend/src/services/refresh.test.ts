import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { API_BASE, ApiError, apiRequest, isAuthRequest, refreshAccess } from './api'
import {
  TENANT_STORAGE_KEY,
  TOKEN_STORAGE_KEY,
  getTenantId,
  getToken,
  setToken,
} from './token.storage'

const TENANT = 'aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee'
const OLD_TOKEN = 'jwt-expirado'
const NEW_TENANT = '11111111-2222-4333-8444-555555555555'

function b64url(input: string): string {
  return btoa(input).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '')
}

function makeAccessToken(tenantId: string = TENANT): string {
  const payload = b64url(JSON.stringify({ sub: 'freitasjh', 'tenant-id': tenantId }))
  return `${b64url('{"alg":"HS256","typ":"JWT"}')}.${payload}.assinatura`
}

const NEW_TOKEN = makeAccessToken(NEW_TENANT)

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'content-type': 'application/json' },
  })
}

function storedKeys(): string[] {
  const keys: string[] = []
  for (let i = 0; i < localStorage.length; i += 1) keys.push(localStorage.key(i) ?? '')
  return keys
}

function seedSession(token: string = OLD_TOKEN, tenant: string | null = TENANT): void {
  localStorage.clear()
  setToken(token, tenant ?? undefined)
}

describe('refresh de access token', () => {
  beforeEach(() => {
    localStorage.clear()
    window.history.replaceState(null, '', '/#overview')
    vi.unstubAllGlobals()
    vi.restoreAllMocks()
  })

  afterEach(() => {
    window.history.replaceState(null, '', '/')
    vi.unstubAllGlobals()
    vi.restoreAllMocks()
  })

  it('POST /auth/refresh-token com cookie (credentials include) e X-Tenant-ID; resposta crua {accessToken}', async () => {
    seedSession()
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse({ accessToken: NEW_TOKEN }))
    vi.stubGlobal('fetch', fetchMock)

    await expect(refreshAccess()).resolves.toBe(NEW_TOKEN)

    expect(fetchMock).toHaveBeenCalledTimes(1)
    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit]
    expect(url).toBe(`${API_BASE}/auth/refresh-token`)
    expect(url).not.toContain(NEW_TOKEN)
    expect(init.method).toBe('POST')
    expect(init.credentials).toBe('include')
    const headers = init.headers as Record<string, string>
    expect(headers['X-Tenant-ID']).toBe(TENANT)
    // Refresh autentica-se pelo cookie: Bearer expirado/revogado é omitido para
    // não ser barrado pelo SecurityFilter antes do controller permitAll.
    expect(headers.Authorization).toBeUndefined()
    expect(getToken()).toBe(NEW_TOKEN)
    expect(getTenantId()).toBe(NEW_TENANT)
    expect(storedKeys().sort()).toEqual([TOKEN_STORAGE_KEY, TENANT_STORAGE_KEY].sort())
  })

  it('refresh sem tenant salvo falha rápido, sem requsição, limpa sessão e vai para #login', async () => {
    seedSession(OLD_TOKEN, null)
    const fetchMock = vi.fn()
    vi.stubGlobal('fetch', fetchMock)

    await expect(refreshAccess()).rejects.toBeInstanceOf(ApiError)

    expect(fetchMock).not.toHaveBeenCalled()
    expect(getToken()).toBeNull()
    expect(getTenantId()).toBeNull()
    expect(window.location.hash).toBe('#login')
  })

  it('refresh 401 limpa token + tenant e redireciona sem disparar novo refresh', async () => {
    seedSession()
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse({ status: 401 }, 401))
    vi.stubGlobal('fetch', fetchMock)

    const error = await refreshAccess().catch((e: unknown) => e)

    expect(error).toBeInstanceOf(ApiError)
    expect((error as ApiError).status).toBe(401)
    expect(fetchMock).toHaveBeenCalledTimes(1)
    expect(getToken()).toBeNull()
    expect(getTenantId()).toBeNull()
    expect(window.location.hash).toBe('#login')
  })

  it('refresh 404 limpa sessão e redireciona', async () => {
    seedSession()
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse({ status: 404 }, 404)))

    const error = await refreshAccess().catch((e: unknown) => e)

    expect(error).toBeInstanceOf(ApiError)
    expect((error as ApiError).status).toBe(404)
    expect(getToken()).toBeNull()
    expect(getTenantId()).toBeNull()
    expect(window.location.hash).toBe('#login')
  })

  it('falha de rede no refresh limpa sessão e redireciona', async () => {
    seedSession()
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('Failed to fetch')))

    const error = await refreshAccess().catch((e: unknown) => e)

    expect(error).toBeInstanceOf(ApiError)
    expect((error as ApiError).code).toBe('NETWORK_ERROR')
    expect(getToken()).toBeNull()
    expect(getTenantId()).toBeNull()
    expect(window.location.hash).toBe('#login')
  })

  it('resposta 200 sem accessToken válido é tratada como falha de refresh', async () => {
    seedSession()
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse({ accessToken: '' })))

    const error = await refreshAccess().catch((e: unknown) => e)

    expect(error).toBeInstanceOf(ApiError)
    expect((error as ApiError).code).toBe('REFRESH_INVALID')
    expect(getToken()).toBeNull()
    expect(getTenantId()).toBeNull()
  })

  it('single-flight: 3 chamadas concorrentes disparam UM refresh', async () => {
    seedSession()
    let resolveFetch!: (response: Response) => void
    const fetchMock = vi.fn().mockImplementation(
      () => new Promise<Response>((resolve) => { resolveFetch = resolve })
    )
    vi.stubGlobal('fetch', fetchMock)

    const calls = [refreshAccess(), refreshAccess(), refreshAccess()]
    expect(fetchMock).toHaveBeenCalledTimes(1)

    resolveFetch(jsonResponse({ accessToken: NEW_TOKEN }))
    await expect(Promise.all(calls)).resolves.toEqual([NEW_TOKEN, NEW_TOKEN, NEW_TOKEN])

    expect(fetchMock).toHaveBeenCalledTimes(1)
    expect(getToken()).toBe(NEW_TOKEN)
  })

  it('401 que chega depois da refresh de outra chamada repete com token vigente, sem nova rotação', async () => {
    const TOKEN_B = makeAccessToken()
    seedSession()
    let resolveStale401!: (response: Response) => void
    let getCount = 0

    const fetchMock = vi.fn().mockImplementation((url: string) => {
      if (url.includes('/auth/refresh-token')) {
        return Promise.resolve(jsonResponse({ accessToken: TOKEN_B }))
      }
      getCount += 1
      // 1ª GET (desta chamada): 401 segurado até a refresh da outra terminar.
      if (getCount === 1) return new Promise<Response>((resolve) => { resolveStale401 = resolve })
      // 2ª GET (outra chamada): 401 imediato dispara a única refresh.
      if (getCount === 2) return Promise.resolve(jsonResponse({ status: 401 }, 401))
      // Retries: sucesso.
      return Promise.resolve(jsonResponse({ ok: true }))
    })
    vi.stubGlobal('fetch', fetchMock)

    const stale = apiRequest<{ ok: boolean }>('/provider-credentials')
    const first = apiRequest<{ ok: boolean }>('/provider-credentials')

    await expect(first).resolves.toEqual({ ok: true })
    expect(getToken()).toBe(TOKEN_B)

    resolveStale401(jsonResponse({ status: 401 }, 401))
    await expect(stale).resolves.toEqual({ ok: true })

    const refreshCalls = fetchMock.mock.calls.filter((call) =>
      String(call[0]).includes('/auth/refresh-token')
    )
    expect(refreshCalls).toHaveLength(1)
    expect(window.location.hash).toBe('#overview')
    const retryHeaders = fetchMock.mock.calls[fetchMock.mock.calls.length - 1][1].headers as Record<string, string>
    expect(retryHeaders.Authorization).toBe(`Bearer ${TOKEN_B}`)
  })

  it('single-flight com refresh falhando: 3 chamadas, 1 refresh, todas rejeitadas e sessão limpa', async () => {
    seedSession()
    const fetchMock = vi.fn().mockImplementation((url: string) => {
      if (url.includes('/auth/refresh-token')) {
        return Promise.resolve(jsonResponse({ status: 401 }, 401))
      }
      return Promise.resolve(jsonResponse({ status: 401 }, 401))
    })
    vi.stubGlobal('fetch', fetchMock)

    const errors = await Promise.allSettled([
      apiRequest('/provider-credentials'),
      apiRequest('/provider-credentials'),
      apiRequest('/provider-credentials'),
    ])

    expect(errors.map((e) => e.status)).toEqual(['rejected', 'rejected', 'rejected'])
    for (const e of errors) {
      if (e.status === 'rejected') expect(e.reason).toBeInstanceOf(ApiError)
    }
    const refreshCalls = fetchMock.mock.calls.filter((call) =>
      String(call[0]).includes('/auth/refresh-token')
    )
    expect(refreshCalls).toHaveLength(1)
    expect(getToken()).toBeNull()
    expect(getTenantId()).toBeNull()
    expect(window.location.hash).toBe('#login')
  })

  it('401 em rota protegida renova e repete a chamada original com novo Bearer', async () => {
    seedSession()
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(jsonResponse({ status: 401 }, 401))
      .mockResolvedValueOnce(jsonResponse({ accessToken: NEW_TOKEN }))
      .mockResolvedValueOnce(jsonResponse({ ok: true }))
    vi.stubGlobal('fetch', fetchMock)

    const result = await apiRequest<{ ok: boolean }>('/provider-credentials')

    expect(result).toEqual({ ok: true })
    expect(fetchMock).toHaveBeenCalledTimes(3)
    const urls = fetchMock.mock.calls.map((call) => call[0])
    expect(urls).toEqual([
      `${API_BASE}/provider-credentials`,
      `${API_BASE}/auth/refresh-token`,
      `${API_BASE}/provider-credentials`,
    ])
    const retryHeaders = fetchMock.mock.calls[2][1].headers as Record<string, string>
    expect(retryHeaders.Authorization).toBe(`Bearer ${NEW_TOKEN}`)
    expect(getToken()).toBe(NEW_TOKEN)
    expect(window.location.hash).toBe('#overview')
  })

  it('401 persistente após refresh limpa sessão e redireciona sem loop', async () => {
    seedSession()
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(jsonResponse({ status: 401 }, 401))
      .mockResolvedValueOnce(jsonResponse({ accessToken: NEW_TOKEN }))
      .mockResolvedValueOnce(jsonResponse({ status: 401 }, 401))
    vi.stubGlobal('fetch', fetchMock)

    const error = await apiRequest('/provider-credentials').catch((e: unknown) => e)

    expect(error).toBeInstanceOf(ApiError)
    expect((error as ApiError).status).toBe(401)
    expect(fetchMock).toHaveBeenCalledTimes(3)
    expect(getToken()).toBeNull()
    expect(getTenantId()).toBeNull()
    expect(window.location.hash).toBe('#login')
  })

  it('401 em /auth/* nunca dispara refresh nem redirect', async () => {
    seedSession()
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse({ status: 401 }, 401))
    vi.stubGlobal('fetch', fetchMock)

    await expect(
      apiRequest('/auth/login', { method: 'POST', body: { username: 'u', password: 'p' } })
    ).rejects.toBeInstanceOf(ApiError)

    expect(fetchMock).toHaveBeenCalledTimes(1)
    expect(getToken()).toBe(OLD_TOKEN)
    expect(getTenantId()).toBe(TENANT)
    expect(window.location.hash).toBe('#overview')
  })

  it('401 em outra rota /auth/* também não dispara refresh nem redirect', async () => {
    seedSession()
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse({ status: 401 }, 401))
    vi.stubGlobal('fetch', fetchMock)

    await expect(apiRequest('/auth/permissions')).rejects.toBeInstanceOf(ApiError)

    expect(fetchMock).toHaveBeenCalledTimes(1)
    expect(getToken()).toBe(OLD_TOKEN)
    expect(getTenantId()).toBe(TENANT)
    expect(window.location.hash).toBe('#overview')
  })

  it('401 sem tenant salvo não tenta refresh e limpa ambas as chaves', async () => {
    seedSession(OLD_TOKEN, null)
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse({ status: 401 }, 401))
    vi.stubGlobal('fetch', fetchMock)

    const error = await apiRequest('/provider-credentials').catch((e: unknown) => e)

    expect(error).toBeInstanceOf(ApiError)
    expect(fetchMock).toHaveBeenCalledTimes(1)
    expect(getToken()).toBeNull()
    expect(getTenantId()).toBeNull()
    expect(window.location.hash).toBe('#login')
  })

  it('headers customizados são repassados sem sobrescrever Authorization/Bearer/Accept/Content-Type', async () => {
    seedSession()
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse({ ok: true }))
    vi.stubGlobal('fetch', fetchMock)

    await apiRequest('/provider-credentials', {
      body: { a: 1 },
      headers: {
        'X-Custom': 'valor',
        Authorization: 'Bearer tentativa-de-roubo',
        Accept: 'text/html',
        'Content-Type': 'text/plain',
      },
    })

    const headers = fetchMock.mock.calls[0][1].headers as Record<string, string>
    expect(headers['X-Custom']).toBe('valor')
    expect(headers.Authorization).toBe(`Bearer ${OLD_TOKEN}`)
    expect(headers.Accept).toBe('application/json')
    expect(headers['Content-Type']).toBe('application/json')
    expect(fetchMock.mock.calls[0][1].credentials).toBeUndefined()
  })

  it('isAuthRequest casa somente rotas que começam com /auth/', () => {
    expect(isAuthRequest('/auth/login')).toBe(true)
    expect(isAuthRequest('/auth/refresh-token')).toBe(true)
    expect(isAuthRequest('/auth/permissions?x=1')).toBe(true)
    expect(isAuthRequest('/provider-credentials')).toBe(false)
    expect(isAuthRequest('/foo/auth/bar')).toBe(false)
  })

  it('não imprime token, tenant ou cookie no console durante refresh', async () => {
    seedSession()
    const spies = [
      vi.spyOn(console, 'log').mockImplementation(() => {}),
      vi.spyOn(console, 'info').mockImplementation(() => {}),
      vi.spyOn(console, 'warn').mockImplementation(() => {}),
      vi.spyOn(console, 'error').mockImplementation(() => {}),
    ]
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse({ status: 401 }, 401)))

    await refreshAccess().catch(() => undefined)

    for (const spy of spies) expect(spy).not.toHaveBeenCalled()
  })
})
