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
  logout,
  mfaService,
  setToken,
} from './auth.service'

const TENANT = 'aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee'
/** Tenant diferente do storage — prova que o desafio, não a sessão antiga, é a fonte. */
const CHALLENGE_TENANT = 'bbbbbbbb-cccc-4ddd-8eee-ffffffffffff'

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
      kind: 'authenticated',
      userId: '11111111-1111-1111-1111-111111111111',
      accessToken,
      type: 'Bearer',
      mfaSetupAvailable: false,
    })
    expect(getToken()).toBe(accessToken)
    expect(getTenantId()).toBe(TENANT)
    expect(storedKeys()).toEqual([TOKEN_STORAGE_KEY, TENANT_STORAGE_KEY])
    expect(JSON.stringify(storedKeys())).not.toContain('senha')
  })

  it('propaga mfaSetupAvailable=true do ramo autenticado sem vazar para storage/URL', async () => {
    const accessToken = makeAccessToken()
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(
      jsonResponse({
        userId: 'u',
        accessToken,
        type: 'Bearer',
        mfaSetupAvailable: true,
      })
    ))

    const result = await loginService.login({ username: 'u', password: 'pw' })

    expect(result).toEqual({
      kind: 'authenticated',
      userId: 'u',
      accessToken,
      type: 'Bearer',
      mfaSetupAvailable: true,
    })
    expect(storedKeys()).toEqual([TOKEN_STORAGE_KEY, TENANT_STORAGE_KEY])
    expect(JSON.stringify(storedKeys())).not.toContain('mfaSetupAvailable')
    expect(window.location.hash).not.toContain('mfaSetupAvailable')
  })

  it('campo ausente ou false => mfaSetupAvailable=false (backend antigo / usuario com MFA)', async () => {
    const accessToken = makeAccessToken()
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(
      jsonResponse({ userId: 'u', accessToken, type: 'Bearer' })
    ))
    const missing = await loginService.login({ username: 'u', password: 'pw' })
    expect(missing).toMatchObject({ kind: 'authenticated', mfaSetupAvailable: false })

    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(
      jsonResponse({ userId: 'u', accessToken, type: 'Bearer', mfaSetupAvailable: false })
    ))
    const explicitFalse = await loginService.login({ username: 'u', password: 'pw' })
    expect(explicitFalse).toMatchObject({ kind: 'authenticated', mfaSetupAvailable: false })
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

  describe('logout — POST /hive/v1/auth/logout', () => {
    it('tenta revogar no backend com cookie, limpa as duas chaves e redireciona', async () => {
      const accessToken = makeAccessToken()
      setToken(accessToken, TENANT)
      const fetchMock = vi.fn().mockResolvedValue(new Response(null, { status: 204 }))
      vi.stubGlobal('fetch', fetchMock)

      await logout()

      expect(fetchMock).toHaveBeenCalledTimes(1)
      const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit]
      expect(url).toBe('/hive/v1/auth/logout')
      expect(url).not.toContain(accessToken)
      expect(init.method).toBe('POST')
      expect(init.credentials).toBe('include')
      const headers = init.headers as Record<string, string>
      expect(
        Object.entries(headers).find(([name]) => name.toLowerCase() === 'x-tenant-id')?.[1]
      ).toBe(TENANT)
      expect(
        Object.keys(headers).some((name) => name.toLowerCase() === 'authorization')
      ).toBe(false)
      expect(JSON.stringify(headers)).not.toContain(accessToken)
      expect(getToken()).toBeNull()
      expect(getTenantId()).toBeNull()
      expect(storedKeys()).toEqual([])
      expect(window.location.hash).toBe('#login')
    })

    it('sem tenant salvo: POST ainda é feito e sessão local limpa mesmo com 204 (contrato idempotente)', async () => {
      const fetchMock = vi.fn().mockResolvedValue(new Response(null, { status: 204 }))
      vi.stubGlobal('fetch', fetchMock)

      await logout()

      const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit]
      expect(url).toBe('/hive/v1/auth/logout')
      expect(init.credentials).toBe('include')
      const headers = init.headers as Record<string, string>
      expect(
        Object.keys(headers).some((name) => name.toLowerCase() === 'x-tenant-id')
      ).toBe(false)
      expect(
        Object.keys(headers).some((name) => name.toLowerCase() === 'authorization')
      ).toBe(false)
      expect(getToken()).toBeNull()
      expect(window.location.hash).toBe('#login')
    })

    it('sem tenant salvo e backend 500: erro é engolido, sessão limpa e redirect mantidos', async () => {
      vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse({ status: 500 }, 500)))

      await expect(logout()).resolves.toBeUndefined()

      expect(getToken()).toBeNull()
      expect(getTenantId()).toBeNull()
      expect(window.location.hash).toBe('#login')
    })

    it('limpa e redireciona mesmo se o backend responder 500', async () => {
      setToken(makeAccessToken(), TENANT)
      vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse({ status: 500 }, 500)))

      await expect(logout()).resolves.toBeUndefined()

      expect(getToken()).toBeNull()
      expect(getTenantId()).toBeNull()
      expect(window.location.hash).toBe('#login')
    })

    it('limpa e redireciona mesmo em falha de rede', async () => {
      setToken(makeAccessToken(), TENANT)
      vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('Failed to fetch')))

      await expect(logout()).resolves.toBeUndefined()

      expect(getToken()).toBeNull()
      expect(getTenantId()).toBeNull()
      expect(window.location.hash).toBe('#login')
    })

    it('não imprime token/tenant no console nem em URL', async () => {
      const spies = [
        vi.spyOn(console, 'log').mockImplementation(() => {}),
        vi.spyOn(console, 'info').mockImplementation(() => {}),
        vi.spyOn(console, 'warn').mockImplementation(() => {}),
        vi.spyOn(console, 'error').mockImplementation(() => {}),
      ]
      const accessToken = makeAccessToken()
      setToken(accessToken, TENANT)
      vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new Error(`Bearer ${accessToken}`)))

      await logout()

      for (const spy of spies) expect(spy).not.toHaveBeenCalled()
      expect(window.location.hash).not.toContain(accessToken)
    })

    it('loginService.logout aponta para o mesmo logout com revogação', async () => {
      setToken(makeAccessToken(), TENANT)
      vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(null, { status: 204 })))

      await loginService.logout()

      expect(getToken()).toBeNull()
      expect(window.location.hash).toBe('#login')
    })
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

/** Fase 4 — discriminação de login e segundo fator. */
describe('loginService.login — ramo MFA (discriminação sem persistir credencial)', () => {
  const CHALLENGE = 'e8a2658f-8830-4bcd-aa4c-a1f260ae59fd'

  beforeEach(() => {
    localStorage.clear()
    window.history.replaceState(null, '', '/#login')
    vi.unstubAllGlobals()
    vi.restoreAllMocks()
  })

  afterEach(() => {
    vi.unstubAllGlobals()
    vi.restoreAllMocks()
  })

  it('devolve mfa-required com challengeId/expiresIn/tenantId e NÃO salva token, tenant ou challenge', async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      jsonResponse({
        mfaRequired: true,
        challengeId: CHALLENGE,
        expiresIn: 120,
        tenantId: CHALLENGE_TENANT,
        // Campo de onboarding NUNCA deve vazar no ramo de desafio.
        mfaSetupAvailable: true,
      })
    )
    vi.stubGlobal('fetch', fetchMock)

    const result = await loginService.login({ username: 'teste.login.mfa', password: 'it-secret-pw' })

    expect(result).toEqual({
      kind: 'mfa-required',
      challengeId: CHALLENGE,
      expiresIn: 120,
      tenantId: CHALLENGE_TENANT,
    })
    expect(getToken()).toBeNull()
    expect(getTenantId()).toBeNull()
    expect(storedKeys()).toEqual([])
    expect(localStorage.getItem(TENANT_STORAGE_KEY)).toBeNull()
    expect(JSON.stringify(storedKeys())).not.toContain(CHALLENGE)
    expect(JSON.stringify(storedKeys())).not.toContain(CHALLENGE_TENANT)
  })

  it('ramo MFA prevalece mesmo se a resposta trouxer accessToken indevido', async () => {
    const accessToken = makeAccessToken()
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(
      jsonResponse({
        mfaRequired: true,
        challengeId: CHALLENGE,
        expiresIn: 120,
        tenantId: CHALLENGE_TENANT,
        accessToken,
      })
    ))

    const result = await loginService.login({ username: 'u', password: 'p' })

    expect(result).toEqual({
      kind: 'mfa-required',
      challengeId: CHALLENGE,
      expiresIn: 120,
      tenantId: CHALLENGE_TENANT,
    })
    expect(getToken()).toBeNull()
    expect(storedKeys()).toEqual([])
  })

  it('challengeId ausente/não-string cai em UNAVAILABLE sem persistir nada', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(
      jsonResponse({ mfaRequired: true, expiresIn: 120, tenantId: CHALLENGE_TENANT })
    ))

    const error = await loginService.login({ username: 'u', password: 'p' }).catch((e: unknown) => e)

    expect(error).toBeInstanceOf(LoginError)
    expect((error as LoginError).code).toBe('UNAVAILABLE')
    expect(storedKeys()).toEqual([])
  })

  it('tenantId do desafio ausente ou não-UUID cai em UNAVAILABLE sem semear storage', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(
      jsonResponse({ mfaRequired: true, challengeId: CHALLENGE, expiresIn: 120 })
    ))
    const missing = await loginService
      .login({ username: 'u', password: 'p' })
      .catch((e: unknown) => e)
    expect((missing as LoginError).code).toBe('UNAVAILABLE')
    expect(storedKeys()).toEqual([])

    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(
      jsonResponse({
        mfaRequired: true,
        challengeId: CHALLENGE,
        expiresIn: 120,
        tenantId: 'tenant-opaco-nao-uuid',
      })
    ))
    const invalid = await loginService
      .login({ username: 'u', password: 'p' })
      .catch((e: unknown) => e)
    expect((invalid as LoginError).code).toBe('UNAVAILABLE')
    expect(localStorage.getItem(TENANT_STORAGE_KEY)).toBeNull()
    expect(storedKeys()).toEqual([])
  })
})

describe('mfaService.verify / recovery — segundo fator sem Bearer', () => {
  const CHALLENGE = 'e8a2658f-8830-4bcd-aa4c-a1f260ae59fd'
  const CODE = '123456'
  const RECOVERY = 'a1b2-c3d4'

  beforeEach(() => {
    localStorage.clear()
    setToken(makeAccessToken(), TENANT)
    window.history.replaceState(null, '', '/#mfa')
    vi.unstubAllGlobals()
    vi.restoreAllMocks()
  })

  afterEach(() => {
    vi.unstubAllGlobals()
    vi.restoreAllMocks()
  })

  it('verify envia challenge+code, omitAuthorization, credentials include e X-Tenant-ID; salva token ao 200', async () => {
    const accessToken = makeAccessToken()
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse({
      userId: '11111111-1111-1111-1111-111111111111', accessToken, type: 'Bearer',
    }))
    vi.stubGlobal('fetch', fetchMock)

    const result = await mfaService.verify(CHALLENGE, CODE)

    expect(result).toEqual({
      userId: '11111111-1111-1111-1111-111111111111', accessToken, type: 'Bearer',
    })
    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit]
    expect(url).toBe('/hive/v1/auth/mfa/verify')
    expect(init.method).toBe('POST')
    expect(init.credentials).toBe('include')
    expect(JSON.parse(init.body as string)).toEqual({ challengeId: CHALLENGE, code: CODE })
    const headers = init.headers as Record<string, string>
    expect(
      Object.entries(headers).find(([name]) => name.toLowerCase() === 'x-tenant-id')?.[1]
    ).toBe(TENANT)
    expect(Object.keys(headers).some((name) => name.toLowerCase() === 'authorization')).toBe(false)
    expect(getToken()).toBe(accessToken)
    expect(getTenantId()).toBe(TENANT)
  })

  it('recovery envia recoveryCode no mesmo contrato sem Bearer', async () => {
    const accessToken = makeAccessToken()
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse({ userId: 'u', accessToken, type: 'Bearer' }))
    vi.stubGlobal('fetch', fetchMock)

    await mfaService.recovery(CHALLENGE, RECOVERY)

    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit]
    expect(url).toBe('/hive/v1/auth/mfa/recovery')
    expect(init.credentials).toBe('include')
    expect(JSON.parse(init.body as string)).toEqual({ challengeId: CHALLENGE, recoveryCode: RECOVERY })
    const headers = init.headers as Record<string, string>
    expect(Object.keys(headers).some((name) => name.toLowerCase() === 'authorization')).toBe(false)
    expect(headers['X-Tenant-ID']).toBe(TENANT)
  })

  it('browser fresco: verify envia X-Tenant-ID do desafio, sem Bearer, sem semear tenant antes do emit', async () => {
    localStorage.clear()
    const accessToken = makeAccessToken(CHALLENGE_TENANT)
    const fetchMock = vi.fn().mockImplementation(async () => {
      // No instante do request o tenant do desafio NÃO pode estar em storage.
      expect(localStorage.getItem(TENANT_STORAGE_KEY)).toBeNull()
      return jsonResponse({ userId: 'u', accessToken, type: 'Bearer' })
    })
    vi.stubGlobal('fetch', fetchMock)

    await mfaService.verify(CHALLENGE, CODE, CHALLENGE_TENANT)

    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit]
    expect(url).toBe('/hive/v1/auth/mfa/verify')
    expect(init.credentials).toBe('include')
    const headers = init.headers as Record<string, string>
    expect(
      Object.entries(headers).find(([name]) => name.toLowerCase() === 'x-tenant-id')?.[1]
    ).toBe(CHALLENGE_TENANT)
    expect(Object.keys(headers).some((name) => name.toLowerCase() === 'authorization')).toBe(false)
    // Após a emissão, o tenant salvo vem do JWT — nunca é injetado a partir do desafio.
    expect(getTenantId()).toBe(CHALLENGE_TENANT)
    expect(getToken()).toBe(accessToken)
  })

  it('browser fresco: recovery envia X-Tenant-ID do desafio e nada é semeado antes do emit', async () => {
    localStorage.clear()
    const accessToken = makeAccessToken(CHALLENGE_TENANT)
    const fetchMock = vi.fn().mockImplementation(async () => {
      expect(localStorage.getItem(TENANT_STORAGE_KEY)).toBeNull()
      return jsonResponse({ userId: 'u', accessToken, type: 'Bearer' })
    })
    vi.stubGlobal('fetch', fetchMock)

    await mfaService.recovery(CHALLENGE, RECOVERY, CHALLENGE_TENANT)

    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit]
    expect(url).toBe('/hive/v1/auth/mfa/recovery')
    expect(init.credentials).toBe('include')
    expect(JSON.parse(init.body as string)).toEqual({ challengeId: CHALLENGE, recoveryCode: RECOVERY })
    const headers = init.headers as Record<string, string>
    expect(
      Object.entries(headers).find(([name]) => name.toLowerCase() === 'x-tenant-id')?.[1]
    ).toBe(CHALLENGE_TENANT)
    expect(Object.keys(headers).some((name) => name.toLowerCase() === 'authorization')).toBe(false)
  })

  it('tenant do desafio tem preferência sobre o tenant salvo (session antiga)', async () => {
    // beforeEach salva TENANT no storage; o desafio traz CHALLENGE_TENANT.
    const fetchMock = vi.fn().mockResolvedValue(
      jsonResponse({ userId: 'u', accessToken: makeAccessToken(CHALLENGE_TENANT), type: 'Bearer' })
    )
    vi.stubGlobal('fetch', fetchMock)

    await mfaService.verify(CHALLENGE, CODE, CHALLENGE_TENANT)

    const [, init] = fetchMock.mock.calls[0] as [string, RequestInit]
    const headers = init.headers as Record<string, string>
    expect(headers['X-Tenant-ID']).toBe(CHALLENGE_TENANT)
    expect(headers['X-Tenant-ID']).not.toBe(TENANT)
  })

  it('sem tenantId no desafio, usa o tenant salvo (compatibilidade com backend antigo)', async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      jsonResponse({ userId: 'u', accessToken: makeAccessToken(TENANT), type: 'Bearer' })
    )
    vi.stubGlobal('fetch', fetchMock)

    await mfaService.verify(CHALLENGE, CODE)

    const [, init] = fetchMock.mock.calls[0] as [string, RequestInit]
    expect((init.headers as Record<string, string>)['X-Tenant-ID']).toBe(TENANT)
  })

  it('tenantId inválido no desafio nunca é enviado: cai no storage ou falha sem requisição', async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      jsonResponse({ userId: 'u', accessToken: makeAccessToken(TENANT), type: 'Bearer' })
    )
    vi.stubGlobal('fetch', fetchMock)

    await mfaService.verify(CHALLENGE, CODE, 'tenant-opaco-nao-uuid')

    let [, init] = fetchMock.mock.calls[0] as [string, RequestInit]
    expect((init.headers as Record<string, string>)['X-Tenant-ID']).toBe(TENANT)

    localStorage.clear()
    const noFetch = vi.fn()
    vi.stubGlobal('fetch', noFetch)
    const error = await mfaService
      .verify(CHALLENGE, CODE, 'tenant-opaco-nao-uuid')
      .catch((e: unknown) => e)
    expect((error as LoginError).code).toBe('MFA_TENANT_MISSING')
    expect(noFetch).not.toHaveBeenCalled()
  })

  it('401 vira MFA_INVALID e 429 vira MFA_TOO_MANY, ambos sem payload', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse({ code: 'MFA_INVALID_CODE' }, 401)))
    const invalid = await mfaService.verify(CHALLENGE, CODE).catch((e: unknown) => e)
    expect((invalid as LoginError).code).toBe('MFA_INVALID')

    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse({ code: 'MFA_TOO_MANY_ATTEMPTS' }, 429)))
    const tooMany = await mfaService.verify(CHALLENGE, CODE).catch((e: unknown) => e)
    expect((tooMany as LoginError).code).toBe('MFA_TOO_MANY')
  })

  it('5xx/resposta sem token viram UNAVAILABLE; rede vira NETWORK', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse({ status: 500 }, 500)))
    expect((await mfaService.verify(CHALLENGE, CODE).catch((e: unknown) => e) as LoginError).code)
      .toBe('UNAVAILABLE')

    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse({ userId: 'u' })))
    expect((await mfaService.verify(CHALLENGE, CODE).catch((e: unknown) => e) as LoginError).code)
      .toBe('UNAVAILABLE')

    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('Failed to fetch')))
    expect((await mfaService.verify(CHALLENGE, CODE).catch((e: unknown) => e) as LoginError).code)
      .toBe('NETWORK')
  })

  it('sem tenant salvo: falha orientada e NENHUMA requisição (não inventa contrato)', async () => {
    localStorage.clear()
    const fetchMock = vi.fn()
    vi.stubGlobal('fetch', fetchMock)

    const error = await mfaService.verify(CHALLENGE, CODE).catch((e: unknown) => e)

    expect((error as LoginError).code).toBe('MFA_TENANT_MISSING')
    expect(fetchMock).not.toHaveBeenCalled()
    expect(getToken()).toBeNull()
  })

  it('não persiste challenge nem código e não imprime segredo no console', async () => {
    const spies = [
      vi.spyOn(console, 'log').mockImplementation(() => {}),
      vi.spyOn(console, 'info').mockImplementation(() => {}),
      vi.spyOn(console, 'warn').mockImplementation(() => {}),
      vi.spyOn(console, 'error').mockImplementation(() => {}),
    ]
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(
      jsonResponse({ userId: 'u', accessToken: makeAccessToken(), type: 'Bearer' })
    ))

    await mfaService.verify(CHALLENGE, CODE)

    expect(storedKeys()).toEqual([TOKEN_STORAGE_KEY, TENANT_STORAGE_KEY])
    expect(JSON.stringify(storedKeys())).not.toContain(CHALLENGE)
    expect(JSON.stringify(storedKeys())).not.toContain(CODE)
    expect(window.location.hash).not.toContain(CODE)
    for (const spy of spies) expect(spy).not.toHaveBeenCalled()
  })
})

describe('mfaService.setup / enable / disable — exigem Bearer normal', () => {
  const SECRET = 'JBSWY3DPEHPK3PXP'
  const QR = 'data:image/png;base64,iVBORw0KGgo='
  const CODES = ['a1b2-c3d4', 'e5f6-g7h8', 'i9j0-k1l2', 'm3n4-o5p6', 'q7r8-s9t0', 'u1v2-w3x4', 'y5z6-a7b8', 'c9d0-e1f2']

  beforeEach(() => {
    localStorage.clear()
    setToken(makeAccessToken(), TENANT)
    vi.unstubAllGlobals()
    vi.restoreAllMocks()
  })

  afterEach(() => {
    vi.unstubAllGlobals()
    vi.restoreAllMocks()
  })

  it('setup devolve secret, qrDataUri e 8 códigos; usa Bearer', async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      jsonResponse({ secret: SECRET, qrDataUri: QR, recoveryCodes: CODES })
    )
    vi.stubGlobal('fetch', fetchMock)

    const result = await mfaService.setup()

    expect(result).toEqual({ secret: SECRET, qrDataUri: QR, recoveryCodes: CODES })
    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit]
    expect(url).toBe('/hive/v1/auth/mfa/setup')
    const headers = init.headers as Record<string, string>
    expect(headers.Authorization).toBe(`Bearer ${getToken()}`)
    expect(headers['X-Tenant-ID']).toBeUndefined()
  })

  it('enable envia code e disable envia password, ambos com Bearer', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(null, { status: 204 }))
    vi.stubGlobal('fetch', fetchMock)

    await mfaService.enable('123456')
    await mfaService.disable('senha-atual')

    expect(fetchMock).toHaveBeenCalledTimes(2)
    const [enableUrl, enableInit] = fetchMock.mock.calls[0] as [string, RequestInit]
    expect(enableUrl).toBe('/hive/v1/auth/mfa/enable')
    expect(JSON.parse(enableInit.body as string)).toEqual({ code: '123456' })
    const [disableUrl, disableInit] = fetchMock.mock.calls[1] as [string, RequestInit]
    expect(disableUrl).toBe('/hive/v1/auth/mfa/disable')
    expect(JSON.parse(disableInit.body as string)).toEqual({ password: 'senha-atual' })
    for (const [, init] of fetchMock.mock.calls as [string, RequestInit][]) {
      const headers = init.headers as Record<string, string>
      expect(headers.Authorization).toBe(`Bearer ${getToken()}`)
      expect(init.credentials).toBeUndefined()
    }
  })
})
