import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from './api'
import { publicApiAdminService } from './public-api-admin.service'
import type { PublicApiClient, PublicApiClientCreate } from './admin.types'

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'content-type': 'application/json' },
  })
}

const rawClient = {
  clientId: 'cli-123',
  label: 'Chat do site',
  tenantId: 'tenant-abc',
  grants: ['PUBLIC_CHAT_COMPLETE', 7],
  prompts: [
    { promptCode: 'greeting', pinMode: 'PINNED', pinnedVersion: 3 },
    { promptCode: 'bad', pinMode: 'NOPE', pinnedVersion: null },
  ],
  token_ttl_seconds: 900,
  max_messages: 20,
  max_message_chars: 4000,
  max_total_chars: 20000,
  revoked_at: null,
  expires_at: '2026-12-01T00:00:00Z',
  previous_secret_expires_at: '2026-11-22T00:10:00Z',
}

const payload: PublicApiClientCreate = {
  label: 'Chat do site',
  grants: ['PUBLIC_CHAT_COMPLETE'],
  prompts: [{ promptCode: 'greeting', pinMode: 'FOLLOW_ACTIVE', pinnedVersion: null }],
  tokenTtlSeconds: 900,
  maxMessages: 20,
  maxMessageChars: 4000,
  maxTotalChars: 20000,
}

describe('publicApiAdminService', () => {
  let fetchMock: ReturnType<typeof vi.fn>

  beforeEach(() => {
    localStorage.clear()
    fetchMock = vi.fn()
    vi.stubGlobal('fetch', fetchMock)
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('lista paginado em /public-api/clients e nunca devolve secret nem hash', async () => {
    fetchMock.mockResolvedValue(
      json({
        content: [{ ...rawClient, client_secret: 'vazamento', secret_hash: 'hash', previous_secret_hash: 'hash2' }],
        page: 1,
        size: 20,
        totalElements: 21,
        totalPages: 2,
      })
    )

    const page = await publicApiAdminService.list(1, 20)

    expect(fetchMock.mock.calls[0][0]).toBe('/hive/v1/public-api/clients?page=1&size=20')
    expect(page.page).toBe(1)
    expect(page.totalElements).toBe(21)
    expect(page.content).toHaveLength(1)
    expect(page.content[0].clientId).toBe('cli-123')
    expect(page.content[0].grants).toEqual(['PUBLIC_CHAT_COMPLETE'])
    expect(page.content[0].prompts).toEqual([
      { promptCode: 'greeting', pinMode: 'PINNED', pinnedVersion: 3 },
      { promptCode: 'bad', pinMode: 'FOLLOW_ACTIVE', pinnedVersion: null },
    ])
    expect(JSON.stringify(page)).not.toContain('vazamento')
    expect(JSON.stringify(page)).not.toContain('hash')
    expect(Object.keys(page.content[0])).not.toContain('client_secret')
  })

  it('normaliza envelope {data} e page com content vazio', async () => {
    fetchMock.mockResolvedValue(json({ data: { content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 } }))
    const page = await publicApiAdminService.list()
    expect(page.content).toEqual([])
    expect(page.totalElements).toBe(0)
    expect(fetchMock.mock.calls[0][0]).toBe('/hive/v1/public-api/clients?page=0&size=20')
  })

  it('busca detalhe sem secret', async () => {
    fetchMock.mockResolvedValue(json({ ...rawClient, client_secret: 'vazamento' }))
    const client = await publicApiAdminService.get('cli-123')
    expect(fetchMock.mock.calls[0][0]).toBe('/hive/v1/public-api/clients/cli-123')
    expect(JSON.stringify(client)).not.toContain('vazamento')
  })

  it('cria com 201 e separa o plaintext one-shot do client seguro', async () => {
    fetchMock.mockResolvedValue(json({ ...rawClient, client_secret: 'sk-one-shot' }, 201))

    const result = await publicApiAdminService.create(payload)

    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit]
    expect(url).toBe('/hive/v1/public-api/clients')
    expect(init.method).toBe('POST')
    expect(JSON.parse(String(init.body))).toEqual(payload)
    expect(JSON.parse(String(init.body))).not.toHaveProperty('clientId')
    expect(JSON.parse(String(init.body))).not.toHaveProperty('tenantId')
    expect(JSON.parse(String(init.body))).not.toHaveProperty('rate_limit_per_minute')
    expect(result.secret).toBe('sk-one-shot')
    expect(JSON.stringify(result.client)).not.toContain('sk-one-shot')
  })

  it('rejeita resposta de criação sem plaintext', async () => {
    fetchMock.mockResolvedValue(json(rawClient, 201))
    await expect(publicApiAdminService.create(payload)).rejects.toMatchObject({ code: 'SECRET_MISSING' })
  })

  it('rotaciona devolvendo plaintext e janela do secret anterior', async () => {
    fetchMock.mockResolvedValue(
      json({
        clientId: 'cli-123',
        client_secret: 'sk-rotacionado',
        previous_secret_expires_at: '2026-11-22T00:10:00Z',
      })
    )

    const result = await publicApiAdminService.rotate('cli 123')

    expect(fetchMock.mock.calls[0][0]).toBe('/hive/v1/public-api/clients/cli%20123/rotate')
    expect((fetchMock.mock.calls[0][1] as RequestInit).method).toBe('POST')
    expect(result).toEqual({
      clientId: 'cli-123',
      secret: 'sk-rotacionado',
      previousSecretExpiresAt: '2026-11-22T00:10:00Z',
    })
  })

  it('revoga com POST e 204', async () => {
    fetchMock.mockResolvedValue(new Response(null, { status: 204 }))
    await expect(publicApiAdminService.revoke('cli-123')).resolves.toBeUndefined()
    expect(fetchMock.mock.calls[0][0]).toBe('/hive/v1/public-api/clients/cli-123/revoke')
    expect((fetchMock.mock.calls[0][1] as RequestInit).method).toBe('POST')
  })

  it('substitui grants e prompts com PUT', async () => {
    fetchMock.mockResolvedValue(json(rawClient)).mockResolvedValueOnce(json(rawClient))
    const client: PublicApiClient = await publicApiAdminService.updateGrants('cli-123', ['A'])
    expect(client.clientId).toBe('cli-123')
    expect(fetchMock.mock.calls[0][0]).toBe('/hive/v1/public-api/clients/cli-123/grants')
    expect((fetchMock.mock.calls[0][1] as RequestInit).method).toBe('PUT')

    await publicApiAdminService.updatePrompts('cli-123', payload.prompts)
    expect(fetchMock.mock.calls[1][0]).toBe('/hive/v1/public-api/clients/cli-123/prompts')
    expect(JSON.parse(String((fetchMock.mock.calls[1][1] as RequestInit).body))).toEqual({
      prompts: payload.prompts,
    })
  })

  it('propaga 403 e detail de problem+json sem exponha segredos', async () => {
    fetchMock.mockResolvedValue(
      new Response(JSON.stringify({ status: 403, detail: 'sem AGENT_MANAGE', code: 'FORBIDDEN' }), {
        status: 403,
        headers: { 'content-type': 'application/problem+json' },
      })
    )
    await expect(publicApiAdminService.list()).rejects.toMatchObject({
      status: 403,
      detail: 'sem AGENT_MANAGE',
      code: 'FORBIDDEN',
    })
    expect(ApiError).toBeDefined()
  })
})
