import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from './api'
import { buildChatPayload, chatService } from './chat.service'

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'content-type': 'application/json' },
  })
}

const FIRST = '11111111-1111-1111-1111-111111111111'
const SESSION = '22222222-2222-2222-2222-222222222222'

const response = {
  sessionId: SESSION,
  role: 'ASSISTANT',
  message: 'Olá!',
  timestamp: '2026-11-19T12:00:00Z',
}

describe('buildChatPayload — regra do primeiro turno', () => {
  it('primeiro turno envia promptTemplateId e NÃO envia sessionId', () => {
    expect(buildChatPayload({ promptTemplateId: FIRST, message: 'oi' })).toEqual({
      promptTemplateId: FIRST,
      message: 'oi',
    })
  })

  it('turnos seguintes enviam sessionId e NÃO reenviam promptTemplateId', () => {
    expect(
      buildChatPayload({ promptTemplateId: FIRST, sessionId: SESSION, message: 'oi de novo' })
    ).toEqual({ sessionId: SESSION, message: 'oi de novo' })
  })

  it('mensagem vazia não gera payload (bloqueio antes da rede)', () => {
    expect(buildChatPayload({ promptTemplateId: FIRST, message: '   ' })).toBeNull()
  })

  it('sem prompt no primeiro turno gera payload só com message (backend aplica fallback)', () => {
    const payload = buildChatPayload({ message: 'oi' })
    expect(payload).toEqual({ message: 'oi' })
    expect(payload).not.toHaveProperty('promptTemplateId')
    expect(payload).not.toHaveProperty('sessionId')
  })
})

describe('chatService.sendMessage', () => {
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

  it('POST /chat com prompt no primeiro turno e guarda sessionId da resposta', async () => {
    fetchMock.mockResolvedValue(json(response))

    const result = await chatService.sendMessage({ promptTemplateId: FIRST, message: 'oi' })

    expect(result).toEqual(response)
    expect(fetchMock).toHaveBeenCalledTimes(1)
    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit]
    expect(url).toBe('/hive/v1/chat')
    expect(init.method).toBe('POST')
    expect((init.headers as Record<string, string>)['Content-Type']).toBe('application/json')
    expect(JSON.parse(init.body as string)).toEqual({ promptTemplateId: FIRST, message: 'oi' })
    expect(JSON.stringify(init.body)).not.toContain('sessionId')
  })

  it('POST /chat sem prompt no primeiro turno envia somente message (fallback no backend)', async () => {
    fetchMock.mockResolvedValue(json(response))

    const result = await chatService.sendMessage({ message: 'oi' })

    expect(result).toEqual(response)
    expect(fetchMock).toHaveBeenCalledTimes(1)
    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit]
    expect(url).toBe('/hive/v1/chat')
    expect(JSON.parse(init.body as string)).toEqual({ message: 'oi' })
    expect(JSON.stringify(init.body)).not.toContain('promptTemplateId')
  })

  it('turno seguinte envia somente sessionId + message', async () => {
    fetchMock.mockResolvedValue(json(response))

    await chatService.sendMessage({ sessionId: SESSION, message: 'segundo' })

    const [, init] = fetchMock.mock.calls[0] as [string, RequestInit]
    expect(JSON.parse(init.body as string)).toEqual({ sessionId: SESSION, message: 'segundo' })
    expect(JSON.stringify(init.body)).not.toContain('promptTemplateId')
  })

  it('aceita envelope {data} legado sem quebrar o chamador', async () => {
    fetchMock.mockResolvedValue(json({ data: response }))
    await expect(chatService.sendMessage({ sessionId: SESSION, message: 'oi' })).resolves.toEqual(
      response
    )
  })

  it('rejeita sem sair do cliente quando payload é inválido', async () => {
    await expect(chatService.sendMessage({ message: '  ' })).rejects.toThrow(/prompt|mensagem/i)
    expect(fetchMock).not.toHaveBeenCalled()
  })

  it('mensagem vazia orienta sobre a mensagem, sem exigir prompt', async () => {
    const error = await chatService.sendMessage({ message: '  ' }).catch((e: unknown) => e)
    expect(error).toBeInstanceOf(Error)
    expect((error as Error).message).toBe('Informe uma mensagem antes de enviar.')
  })

  it('preserva status 404 para a tela orientar sessão inexistente', async () => {
    fetchMock.mockResolvedValue(
      json({ status: 404, code: 'SESSION_NOT_FOUND', detail: 'Sessão não encontrada.' }, 404)
    )
    const error = await chatService
      .sendMessage({ sessionId: SESSION, message: 'oi' })
      .catch((e: unknown) => e)
    expect(error).toBeInstanceOf(ApiError)
    expect(error).toMatchObject({ status: 404, code: 'SESSION_NOT_FOUND' })
  })

  it('em 401 expõe status para a UI encaminhar ao login', async () => {
    localStorage.setItem('hive_token', 'expirado')
    vi.stubGlobal('window', { ...globalThis.window, location: { assign: vi.fn() } })
    fetchMock.mockResolvedValue(json({ status: 401, detail: 'Não autenticado.' }, 401))

    const error = await chatService
      .sendMessage({ promptTemplateId: FIRST, message: 'oi' })
      .catch((e: unknown) => e)
    expect(error).toMatchObject({ status: 401 })
    expect(localStorage.getItem('hive_token')).toBeNull()
  })

  it('não imprime o token no console em falha de rede', async () => {
    const errorSpy = vi.spyOn(console, 'error').mockImplementation(() => {})
    localStorage.setItem('hive_token', 'token-secreto')
    fetchMock.mockRejectedValue(new TypeError('token-secreto falhou'))

    const error = await chatService
      .sendMessage({ promptTemplateId: FIRST, message: 'oi' })
      .catch((e: unknown) => e)
    expect(error).toMatchObject({ code: 'NETWORK_ERROR' })
    expect(JSON.stringify(error)).not.toContain('token-secreto')
    expect(errorSpy).not.toHaveBeenCalled()
  })
})
