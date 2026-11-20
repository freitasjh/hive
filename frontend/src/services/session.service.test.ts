import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from './api'
import { sessionService } from './session.service'

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'content-type': 'application/json' },
  })
}

const S = '22222222-2222-2222-2222-222222222222'
const P = '11111111-1111-1111-1111-111111111111'

const session = {
  id: S,
  userId: '33333333-3333-3333-3333-333333333333',
  promptTemplateId: P,
  title: 'Conversa',
  summary: null,
  createAt: '2026-11-19T10:00:00Z',
  updateAt: '2026-11-19T12:00:00Z',
}

const message = {
  id: '44444444-4444-4444-4444-444444444444',
  sessionId: S,
  role: 'USER',
  content: 'oi',
  createAt: '2026-11-19T12:00:00Z',
}

describe('sessionService', () => {
  let fetchMock: ReturnType<typeof vi.fn>

  beforeEach(() => {
    localStorage.clear()
    fetchMock = vi.fn()
    vi.stubGlobal('fetch', fetchMock)
  })

  afterEach(() => {
    vi.unstubAllGlobals()
    vi.restoreAllMocks()
  })

  it('listSessions desembrulha Page do Spring (meta em page) — sem isso o paginador some', async () => {
    fetchMock.mockResolvedValue(
      json({
        content: [session],
        page: { size: 20, number: 0, totalElements: 58, totalPages: 3 },
      })
    )

    const page = await sessionService.listSessions(0, 20)

    expect(page.content).toHaveLength(1)
    expect(page.totalElements).toBe(58)
    expect(page.totalPages).toBe(3)
    expect(page.number).toBe(0)
    expect(fetchMock.mock.calls[0][0]).toBe('/hive/v1/chat/sessions?page=0&size=20')
  })

  it('listSessions aceita envelope {data} e forma achatada legada', async () => {
    fetchMock.mockResolvedValueOnce(json({ data: { content: [session], totalElements: 1 } }))
    const wrapped = await sessionService.listSessions(0, 10)
    expect(wrapped.totalElements).toBe(1)
    expect(wrapped.totalPages).toBe(1)

    fetchMock.mockResolvedValueOnce(json([session]))
    const plain = await sessionService.listSessions(1, 10)
    expect(plain.content).toHaveLength(1)
    expect(plain.number).toBe(0)
  })

  it('getMessages desembrulha Page com totalPages > 1 (histórico paginado)', async () => {
    fetchMock.mockResolvedValue(
      json({
        content: [message],
        page: { size: 50, number: 1, totalElements: 120, totalPages: 3 },
      })
    )

    const page = await sessionService.getMessages(S, 1, 50)

    expect(page.content).toEqual([message])
    expect(page.totalPages).toBe(3)
    expect(page.number).toBe(1)
    expect(fetchMock.mock.calls[0][0]).toBe(`/hive/v1/chat/sessions/${S}/messages?page=1&size=50`)
  })

  it('cria sessão com promptTemplateId (prompt é parâmetro do turno, não entidade)', async () => {
    fetchMock.mockResolvedValue(json(session, 201))

    await expect(sessionService.createSession(P, 'Conversa')).resolves.toEqual(session)

    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit]
    expect(url).toBe('/hive/v1/chat/sessions')
    expect(init.method).toBe('POST')
    expect(JSON.parse(init.body as string)).toEqual({ promptTemplateId: P, title: 'Conversa' })
  })

  it('busca, renomeia e remove sessão pelos verbos do controller', async () => {
    fetchMock
      .mockResolvedValueOnce(json({ data: session }))
      .mockResolvedValueOnce(json({ ...session, title: 'Novo' }))
      .mockResolvedValueOnce(new Response(null, { status: 204 }))

    await expect(sessionService.getSession(S)).resolves.toEqual(session)
    await expect(sessionService.updateSession(S, 'Novo')).resolves.toMatchObject({ title: 'Novo' })
    await expect(sessionService.deleteSession(S)).resolves.toBeUndefined()

    expect(fetchMock.mock.calls.map((call) => (call[1] as RequestInit).method)).toEqual([
      'GET',
      'PATCH',
      'DELETE',
    ])
    expect(fetchMock.mock.calls[1][0]).toBe(`/hive/v1/chat/sessions/${S}`)
  })

  it('preserva 404 anti-IDOR para a tela dizer que a sessão não existe', async () => {
    fetchMock.mockResolvedValue(json({ status: 404, detail: 'Sessão não encontrada.' }, 404))
    const error = await sessionService.getSession(S).catch((e: unknown) => e)
    expect(error).toBeInstanceOf(ApiError)
    expect(error).toMatchObject({ status: 404 })
  })

  it('em 401 limpa token e redireciona (contrato de api.ts)', async () => {
    localStorage.setItem('hive_token', 'expirado')
    vi.stubGlobal('window', { ...globalThis.window, location: { assign: vi.fn() } })
    fetchMock.mockResolvedValue(json({ status: 401 }, 401))

    await expect(sessionService.listSessions()).rejects.toMatchObject({ status: 401 })
    expect(localStorage.getItem('hive_token')).toBeNull()
  })
})
