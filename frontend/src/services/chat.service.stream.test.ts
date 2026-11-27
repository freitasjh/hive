import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { apiStreamFrames, type SseFrame } from './api'
import { chatService, type ChatStreamHandlers } from './chat.service'

const mocks = vi.hoisted(() => ({ frames: [] as SseFrame[] }))

vi.mock(import('./api'), async (importOriginal) => {
  const actual = await importOriginal<typeof import('./api')>()
  return {
    ...actual,
    apiStreamFrames: vi.fn(async function* () {
      for (const frame of mocks.frames) yield frame
    }),
  }
})

describe('chatService.streamMessage', () => {
  let handlers: ChatStreamHandlers

  beforeEach(() => {
    mocks.frames = []
    handlers = {
      onThinking: vi.fn(),
      onText: vi.fn(),
      onDone: vi.fn(),
      onError: vi.fn(),
    }
  })

  afterEach(() => {
    vi.clearAllMocks()
  })

  it('despacha thinking/text/done com meta do frame', async () => {
    mocks.frames = [
      { event: 'thinking', data: '{"type":"thinking","text":"Penso"}' },
      { event: 'text', data: '{"type":"text","text":"Olá"}' },
      {
        event: 'done',
        data: '{"type":"done","finish_reason":"stop","usage":{"prompt_tokens":2,"completion_tokens":3,"total_tokens":5}}',
      },
    ]

    await chatService.streamMessage({ message: 'oi' }, null, handlers)

    expect(handlers.onThinking).toHaveBeenCalledWith('Penso')
    expect(handlers.onText).toHaveBeenCalledWith('Olá')
    expect(handlers.onDone).toHaveBeenCalledWith({
      type: 'done',
      finishReason: 'stop',
      usage: { promptTokens: 2, completionTokens: 3, totalTokens: 5 },
    })
    expect(apiStreamFrames).toHaveBeenCalledWith(
      '/chat/stream',
      expect.objectContaining({ method: 'POST', body: { message: 'oi' } })
    )
  })

  it('para no done e não consome frames posteriores', async () => {
    mocks.frames = [
      { event: 'done', data: '{"type":"done","finish_reason":"stop"}' },
      { event: 'text', data: '{"type":"text","text":"não deve chegar"}' },
    ]

    await chatService.streamMessage({ message: 'oi' }, null, handlers)

    expect(handlers.onDone).toHaveBeenCalledTimes(1)
    expect(handlers.onText).not.toHaveBeenCalled()
  })

  it('trata [DONE] do wire público como terminal', async () => {
    mocks.frames = [{ event: 'message', data: '[DONE]' }]
    await chatService.streamMessage({ message: 'oi' }, null, handlers)
    expect(handlers.onDone).toHaveBeenCalledWith({})
  })

  it('erro expõe somente código de conjunto fechado — nunca message/Throwable', async () => {
    mocks.frames = [
      {
        event: 'error',
        data: '{"type":"error","error":{"code":"rate_limited","message":"SEGREDO sk-ant-abc123 Throwable"}}',
      },
      { event: 'text', data: '{"type":"text","text":"não deve chegar"}' },
    ]

    await chatService.streamMessage({ message: 'oi' }, null, handlers)

    expect(handlers.onError).toHaveBeenCalledWith('rate_limited')
    expect(handlers.onDone).not.toHaveBeenCalled()
    expect(JSON.stringify(handlers)).not.toContain('SEGREDO')
    expect(JSON.stringify(handlers)).not.toContain('Throwable')
  })

  it('código fora do conjunto cai em stream_failed', async () => {
    mocks.frames = [
      { event: 'error', data: '{"type":"error","error":{"code":"<script>alert(1)</script>"}}' },
    ]
    await chatService.streamMessage({ message: 'oi' }, null, handlers)
    expect(handlers.onError).toHaveBeenCalledWith('stream_failed')
  })

  it('frames com JSON inválido ou sem texto são ignorados', async () => {
    mocks.frames = [
      { event: 'text', data: 'nao-e-json' },
      { event: 'thinking', data: '{"type":"thinking","text":""}' },
      { event: 'text', data: '{"type":"text","text":"ok"}' },
    ]

    await chatService.streamMessage({ message: 'oi' }, null, handlers)

    expect(handlers.onThinking).not.toHaveBeenCalled()
    expect(handlers.onText).toHaveBeenCalledTimes(1)
  })
})
