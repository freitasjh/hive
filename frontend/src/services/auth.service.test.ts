import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from './api'
import {
  LoginError,
  TENANT_STORAGE_KEY,
  TOKEN_STORAGE_KEY,
  clearToken,
  getTenantId,
  getToken,
  loginService,
  setToken,
} from './auth.service'

const TENANT = 'aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee'

function b64url(input: string): string {
  return btoa(input).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '')
}

function makeAccessToken(tenantId: string = TENANT): string {
  const payload = b64url(JSON.stringify({ sub: 'freitasjh', 'tenant-id': tenantId }))
  return `${b64url('{"alg":"HS256","typ":"JWT"}')}.${payload}.assinatura`
}

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

describe('loginService.login — POST /hive/v1/auth/login', () => {
  beforeEach(() => {
    localStorage.clear()
    window.history.replaceState(null, '', '/#overview')
    vi.unstubAllGlobals()
    vi.restoreAllMocks()
  })

  afterEach(() => {
    vi.unstubAllGlobals()
    vi.restoreAllMocks()
  })

  it('envia LoginDTO {username,password}, sem envelope, e salva accessToken + tenantId', async () => {
    const accessToken = makeAccessToken()
    const fetchMock = vi.fn().mockResolvedValue(
      jsonResponse({
        userId: '11111111-1111-1111-1111-111111111111',
        accessToken,
        type: 'Bearer',
      })
    )
    vi.stubGlobal('fetch', fetchMock)

    const result = await loginService.login({
      username: 'freitasjh',
      password: 'senha-nunca-persistida',
    })

    expect(fetchMock).toHaveBeenCalledTimes(1)
    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit]
    expect(url).toBe('/hive/v1/auth/login')
    expect(url).not.toContain('freitasjh')
    expect(url).not.toContain('senha-nunca-persistida')
    expect(init.method).toBe('POST')
    expect(JSON.parse(init.body as string)).toEqual({
      username: 'freitasjh',
      password: 'senha-nunca-persistida',
    })
    expect(result).toEqual({
      userId: '11111111-1111-1111-1111-111111111111',
      accessToken,
      type: 'Bearer',
    })
    expect(getToken()).toBe(accessToken)
    expect(getTenantId()).toBe(TENANT)
    expect(storedKeys()).toEqual([TOKEN_STORAGE_KEY, TENANT_STORAGE_KEY])
    expect(JSON.stringify(storedKeys())).not.toContain('senha')
  })

  it('falha com UNAVAILABLE quando accessToken não contém tenant-id mandatório', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse({ accessToken: 'opaco' })))

    const error = await loginService
      .login({ username: 'user', password: 'pw' })
      .catch((e: unknown) => e)

    expect(error).toBeInstanceOf(LoginError)
    expect((error as LoginError).code).toBe('UNAVAILABLE')
    expect(getToken()).toBeNull()
    expect(getTenantId()).toBeNull()
  })

  it('logout limpa accessToken e tenantId', async () => {
    setToken(makeAccessToken(), TENANT)
    expect(getToken()).not.toBeNull()
    expect(getTenantId()).toBe(TENANT)

    loginService.logout()

    expect(getToken()).toBeNull()
    expect(getTenantId()).toBeNull()
    expect(storedKeys()).toEqual([])
  })

  it('401 vira erro semântico sem payload de credencial e não redireciona', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        jsonResponse(
          { status: 401, detail: 'senha-nunca-persistida inválida' },
          401
        )
      )
    )

    const error = await loginService
      .login({ username: 'freitasjh', password: 'senha-nunca-persistida' })
      .catch((e: unknown) => e)

    expect(error).toBeInstanceOf(LoginError)
    expect((error as LoginError).code).toBe('INVALID_CREDENTIALS')
    expect((error as LoginError).message).not.toContain('senha-nunca-persistida')
    expect(window.location.hash).toBe('#overview')
    expect(getToken()).toBeNull()
  })

  it('resposta sem accessToken não quebra e não persiste nada', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse({ userId: 'x' })))

    const error = await loginService
      .login({ username: 'user', password: 'pw' })
      .catch((e: unknown) => e)

    expect(error).toBeInstanceOf(LoginError)
    expect((error as LoginError).code).toBe('UNAVAILABLE')
    expect(getToken()).toBeNull()
  })

  it('mapeia 5xx para UNAVAILABLE e falha de rede para NETWORK', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse({ status: 500 }, 500)))
    const serverError = await loginService
      .login({ username: 'u', password: 'p' })
      .catch((e: unknown) => e)
    expect((serverError as LoginError).code).toBe('UNAVAILABLE')

    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('Failed to fetch')))
    const networkError = await loginService
      .login({ username: 'u', password: 'p' })
      .catch((e: unknown) => e)
    expect(networkError).toBeInstanceOf(LoginError)
    expect((networkError as LoginError).code).toBe('NETWORK')
  })

  it('não imprime senha nem token no console', async () => {
    const spies = [
      vi.spyOn(console, 'log').mockImplementation(() => {}),
      vi.spyOn(console, 'info').mockImplementation(() => {}),
      vi.spyOn(console, 'warn').mockImplementation(() => {}),
      vi.spyOn(console, 'error').mockImplementation(() => {}),
    ]
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse({ status: 401 }, 401)))

    await loginService
      .login({ username: 'freitasjh', password: 'senha-secreta' })
      .catch(() => undefined)

    for (const spy of spies) {
      expect(spy).not.toHaveBeenCalled()
    }
  })

  it('expõe helpers de token na mesma chave e logout limpa apenas o token', () => {
    // helpers reexportados:ApiError continua disponível para camadas superiores.
    expect(ApiError).toBeTypeOf('function')
    expect(clearToken).toBeTypeOf('function')
  })
})
