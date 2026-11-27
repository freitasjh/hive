import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { API_BASE, parseSseFrames, apiStreamFrames, type SseFrame } from './api'

/** Reader mínimo: evita depender de ReadableStream global no ambiente jsdom. */
function sseResponse(chunks: string[]): Response {
  let index = 0
  return {
    ok: true,
    status: 200,
    headers: { get: () => 'text/event-stream' },
    body: {
      getReader: () => ({
        read: async () => {
          if (index < chunks.length) return { done: false, value: new TextEncoder().encode(chunks[index++]) }
          return { done: true, value: undefined }
        },
        releaseLock: () => {},
      }),
    },
  } as unknown as Response
}

async function collect(frames: AsyncGenerator<SseFrame>): Promise<SseFrame[]> {
  const out: SseFrame[] = []
  for await (const frame of frames) out.push(frame)
  return out
}

describe('parseSseFrames', () => {
  it('monta eventos nomeados mesmo quando o frame vem dividido em chunks', () => {
    const first = parseSseFrames('event:thinking\ndata:{"type":"thinking","text":"ana')
    expect(first.frames).toEqual([])
    expect(first.rest).toBe('event:thinking\ndata:{"type":"thinking","text":"ana')

    const second = parseSseFrames(`${first.rest}lisa"}\n\nevent:text\ndata:{"type":"text","text":"Ol"}`)
    expect(second.frames).toEqual([
      { event: 'thinking', data: '{"type":"thinking","text":"analisa"}' },
    ])

    const third = parseSseFrames(`${second.rest}\n\n`, true)
    expect(third.frames).toEqual([
      { event: 'text', data: '{"type":"text","text":"Ol"}' },
    ])
  })

  it('ignora comentários/heartbeat, CRLF e campos id/retry', () => {
    const { frames } = parseSseFrames(
      ': heartbeat\r\nid: 7\r\nretry: 3000\r\nevent:text\r\ndata:{"type":"text","text":"hi"}\r\n\r\n'
    )
    expect(frames).toEqual([{ event: 'text', data: '{"type":"text","text":"hi"}' }])
  })

  it('reconhece o sentinel [DONE] sem evento nomeado', () => {
    const { frames } = parseSseFrames('data: [DONE]\n\n')
    expect(frames).toEqual([{ event: 'message', data: '[DONE]' }])
  })

  it('junta linhas data: consecutivas com \\n', () => {
    const { frames } = parseSseFrames('event:text\ndata:{"type":"text",\ndata:"text":"oi"}\n\n')
    expect(frames).toEqual([{ event: 'text', data: '{"type":"text",\n"text":"oi"}' }])
  })
})

describe('apiStreamFrames', () => {
  beforeEach(() => {
    localStorage.clear()
    window.history.replaceState(null, '', '/')
  })

  afterEach(() => {
    vi.unstubAllGlobals()
    vi.restoreAllMocks()
    window.history.replaceState(null, '', '/')
  })

  it('envia Bearer/X-Tenant-ID/Accept SSE e nunca coloca token na URL', async () => {
    localStorage.setItem('hive_token', 'token-super-secreto')
    localStorage.setItem('hive_tenant_id', 'tenant-7')
    const fetchMock = vi.fn().mockResolvedValue(sseResponse([
      'event:thinking\ndata:{"type":"thinking","text":"penso"}\n\n',
      'event:done\ndata:{"type":"done","finish_reason":"stop"}\n\n',
    ]))
    vi.stubGlobal('fetch', fetchMock)

    const frames = await collect(apiStreamFrames('/chat/stream', {
      method: 'POST',
      body: { message: 'oi' },
      signal: new AbortController().signal,
    }))

    expect(frames.map((frame) => frame.event)).toEqual(['thinking', 'done'])
    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit]
    expect(url).toBe(`${API_BASE}/chat/stream`)
    expect(url).not.toContain('token-super-secreto')
    const headers = init.headers as Record<string, string>
    expect(headers.Authorization).toBe('Bearer token-super-secreto')
    expect(headers.Accept).toBe('text/event-stream')
    expect(headers['X-Tenant-ID']).toBe('tenant-7')
    expect((init.signal as AbortSignal).aborted).toBe(true) // cancel ao terminar o consumo
  })

  it('reusa refresh single-flight no 401 e repete o stream uma vez', async () => {
    localStorage.setItem('hive_token', 'token-expirado')
    localStorage.setItem('hive_tenant_id', 'tenant-7')
    const fetchMock = vi.fn().mockImplementation((url: string) => {
      if (url.endsWith('/auth/refresh-token')) {
        return Promise.resolve(new Response(JSON.stringify({ accessToken: 'token-renovado' }), {
          status: 200,
          headers: { 'content-type': 'application/json' },
        }))
      }
      if (fetchMock.mock.calls.filter(([u]) => String(u).endsWith('/chat/stream')).length === 1) {
        return Promise.resolve(new Response(null, { status: 401 }))
      }
      return Promise.resolve(sseResponse(['event:text\ndata:{"type":"text","text":"ok"}\n\n']))
    })
    vi.stubGlobal('fetch', fetchMock)

    const frames = await collect(apiStreamFrames('/chat/stream', { method: 'POST', body: { message: 'oi' } }))

    expect(frames).toEqual([{ event: 'text', data: '{"type":"text","text":"ok"}' }])
    const streamCalls = fetchMock.mock.calls.filter(([u]) => String(u).endsWith('/chat/stream'))
    expect(streamCalls).toHaveLength(2)
    expect((streamCalls[1][1] as RequestInit).headers).toMatchObject({ Authorization: 'Bearer token-renovado' })
  })

  it('rotação multi-aba no 401 repete o stream com o token vigente, sem refresh', async () => {
    localStorage.setItem('hive_token', 'token-antigo')
    localStorage.setItem('hive_tenant_id', 'tenant-7')
    const fetchMock = vi.fn().mockImplementation((url: string) => {
      if (url.endsWith('/auth/refresh-token')) throw new Error('refresh não deveria ser chamado')
      const streamCalls = fetchMock.mock.calls.filter(([u]) => String(u).endsWith('/chat/stream'))
      if (streamCalls.length === 1) {
        // outra aba rotacionou enquanto o 401 voltava
        localStorage.setItem('hive_token', 'token-rotacionado')
        return Promise.resolve(new Response(null, { status: 401 }))
      }
      return Promise.resolve(sseResponse(['event:text\ndata:{"type":"text","text":"ok"}\n\n']))
    })
    vi.stubGlobal('fetch', fetchMock)

    const frames = await collect(apiStreamFrames('/chat/stream', { method: 'POST', body: { message: 'oi' } }))

    expect(frames).toEqual([{ event: 'text', data: '{"type":"text","text":"ok"}' }])
    const streamCalls = fetchMock.mock.calls.filter(([u]) => String(u).endsWith('/chat/stream'))
    expect(streamCalls).toHaveLength(2)
    expect((streamCalls[0][1] as RequestInit).headers).toMatchObject({ Authorization: 'Bearer token-antigo' })
    expect((streamCalls[1][1] as RequestInit).headers).toMatchObject({ Authorization: 'Bearer token-rotacionado' })
    expect(fetchMock.mock.calls.some(([u]) => String(u).endsWith('/auth/refresh-token'))).toBe(false)
    expect(localStorage.getItem('hive_token')).toBe('token-rotacionado')
  })

  it('abort externo encerra o generator sem erro nem log', async () => {
    const controller = new AbortController()
    const errorSpy = vi.spyOn(console, 'error').mockImplementation(() => {})
    let markRead!: () => void
    const readStarted = new Promise<void>((resolve) => { markRead = resolve })
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      body: {
        getReader: () => ({
          read: () => new Promise((_resolve, reject) => {
            markRead()
            const internal = (fetchMock.mock.calls[0][1] as RequestInit).signal as AbortSignal
            internal.addEventListener('abort', () => {
              const error = new Error('aborted')
              error.name = 'AbortError'
              reject(error)
            }, { once: true })
          }),
          releaseLock: () => {},
        }),
      },
    } as unknown as Response)
    vi.stubGlobal('fetch', fetchMock)

    const collected: SseFrame[] = []
    const consuming = (async () => {
      for await (const frame of apiStreamFrames('/chat/stream', {
        method: 'POST',
        body: { message: 'oi' },
        signal: controller.signal,
      })) collected.push(frame)
    })()

    await readStarted
    controller.abort()
    await consuming

    expect(collected).toEqual([])
    expect(errorSpy).not.toHaveBeenCalled()
  })
})
