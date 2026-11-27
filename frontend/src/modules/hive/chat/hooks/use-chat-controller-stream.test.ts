import { act, renderHook, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '@/services/api'
import { useChatController } from './use-chat-controller'

const S = '22222222-2222-2222-2222-222222222222'

const mocks = vi.hoisted(() => ({
  send: vi.fn(),
  stream: vi.fn(),
  promptList: vi.fn(),
  listSessions: vi.fn(),
  getMessages: vi.fn(),
  getSession: vi.fn(),
}))

vi.mock(import('@/services/chat.service'), async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/services/chat.service')>()
  return { ...actual, chatService: { sendMessage: mocks.send, streamMessage: mocks.stream } }
})
vi.mock('@/services/prompt.service', () => ({ promptService: { list: mocks.promptList } }))
vi.mock('@/services/session.service', () => ({
  sessionService: {
    listSessions: mocks.listSessions,
    getMessages: mocks.getMessages,
    getSession: mocks.getSession,
    deleteSession: vi.fn(),
    updateSession: vi.fn(),
    createSession: vi.fn(),
  },
}))
vi.mock('@/services/agent-admin.service', () => ({
  agentAdminService: { getProviderCatalog: vi.fn().mockResolvedValue([]), getLlmConfig: vi.fn().mockResolvedValue(null) },
}))

describe('useChatController — streaming SSE', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    localStorage.clear()
    mocks.promptList.mockResolvedValue({ content: [], totalElements: 0, totalPages: 1, number: 0 })
    mocks.listSessions.mockResolvedValue({ content: [], totalElements: 0, totalPages: 1, number: 0 })
    mocks.getMessages.mockResolvedValue({ content: [], totalElements: 0, totalPages: 1, number: 0 })
    mocks.getSession.mockResolvedValue(null)
    mocks.send.mockResolvedValue(undefined)
    mocks.stream.mockResolvedValue(undefined)
  })

  async function ready() {
    const hook = renderHook(() => useChatController())
    await waitFor(() => expect(hook.result.current.state.promptsLoading).toBe(false))
    return hook
  }

  it('thinking aparece antes do texto; chunks são anexados e done finaliza sem duplicar mensagem', async () => {
    let emitText!: () => void
    let finish!: () => void
    mocks.stream.mockImplementation((_request, _defaults, handlers) => new Promise<void>((resolve) => {
      handlers.onThinking('Analisando ')
      handlers.onThinking('o pedido')
      emitText = () => {
        handlers.onText('Olá ')
        handlers.onText('mundo')
      }
      finish = () => {
        handlers.onDone({
          sessionId: S,
          timestamp: '2026-11-27T12:00:00Z',
          provider: 'OPENAI',
          model: 'gpt-4o',
        })
        resolve()
      }
    }))

    const { result } = await ready()
    act(() => result.current.actions.setInput('oi'))
    let pending!: Promise<void>
    await act(async () => {
      pending = result.current.actions.send()
      await Promise.resolve()
    })

    const streaming = result.current.state.messages
    expect(streaming).toHaveLength(2)
    expect(streaming[1].role).toBe('ASSISTANT')
    expect(streaming[1].reasoning).toBe('Analisando o pedido')
    expect(streaming[1].content).toBe('')

    await act(async () => { emitText(); await Promise.resolve() })
    expect(result.current.state.messages[1].content).toBe('Olá mundo')
    expect(result.current.state.messages[1].reasoning).toBe('Analisando o pedido')

    await act(async () => { finish(); await pending })
    expect(result.current.state.messages).toHaveLength(2)
    expect(result.current.state.messages[1]).toMatchObject({
      sessionId: S,
      content: 'Olá mundo',
      createAt: '2026-11-27T12:00:00Z',
    })
    expect(result.current.state.effectiveModel).toEqual({ provider: 'OPENAI', model: 'gpt-4o' })
    expect(result.current.state.sendError).toBeNull()
    expect(result.current.state.sending).toBe(false)
    expect(mocks.send).not.toHaveBeenCalled()
  })

  it('troca de turno aborta o stream e não mostra erro', async () => {
    mocks.stream.mockImplementation((_request, _defaults, _handlers, signal?: AbortSignal) =>
      new Promise<void>((_resolve, reject) => {
        signal?.addEventListener('abort', () => {
          const error = new Error('aborted')
          error.name = 'AbortError'
          reject(error)
        })
      })
    )

    const { result } = await ready()
    act(() => result.current.actions.setInput('oi'))
    let pending!: Promise<void>
    await act(async () => {
      pending = result.current.actions.send()
      await Promise.resolve()
    })
    expect(result.current.state.sending).toBe(true)

    act(() => result.current.actions.newChat())
    await act(async () => { await pending })

    expect(result.current.state.messages).toEqual([])
    expect(result.current.state.sendError).toBeNull()
    expect(result.current.state.sending).toBe(false)
    const signal = mocks.stream.mock.calls[0][3] as AbortSignal
    expect(signal.aborted).toBe(true)
  })

  it('desmontar aborta o stream em voo', async () => {
    let captured: AbortSignal | undefined
    mocks.stream.mockImplementation((_request, _defaults, _handlers, signal?: AbortSignal) => {
      captured = signal
      return new Promise<void>(() => {})
    })

    const hook = await ready()
    act(() => hook.result.current.actions.setInput('oi'))
    await act(async () => {
      void hook.result.current.actions.send()
      await Promise.resolve()
    })
    expect(captured?.aborted).toBe(false)
    hook.unmount()
    expect(captured?.aborted).toBe(true)
  })

  it('stream sem terminal não reenvia o turno: placeholder removido e erro seguro', async () => {
    mocks.stream.mockResolvedValue(undefined)
    const { result } = await ready()
    act(() => result.current.actions.setInput('oi'))
    await act(async () => { await result.current.actions.send() })

    expect(mocks.send).not.toHaveBeenCalled()
    expect(result.current.state.messages.filter((m) => m.role === 'ASSISTANT')).toHaveLength(0)
    expect(result.current.state.sendError).toMatch(/não foi possível concluir/i)
  })

  it('TIMEOUT usa mensagem específica em vez da genérica', async () => {
    mocks.stream.mockRejectedValue(
      new ApiError('Tempo limite atingido. Tente novamente.', 0, 'TIMEOUT')
    )
    const { result } = await ready()
    act(() => result.current.actions.setInput('oi'))
    await act(async () => { await result.current.actions.send() })

    expect(result.current.state.sendError).toMatch(/demorou demais/i)
    expect(result.current.state.sendError).not.toMatch(/não foi possível enviar/i)
    expect(mocks.send).not.toHaveBeenCalled()
  })

  it('NETWORK_ERROR depois de um frame não reenvia o turno', async () => {
    mocks.stream.mockImplementation(async (_request, _defaults, handlers) => {
      handlers.onThinking('pensamento parcial')
      throw new ApiError('Falha de rede.', 0, 'NETWORK_ERROR')
    })
    const { result } = await ready()
    act(() => result.current.actions.setInput('oi'))
    await act(async () => { await result.current.actions.send() })

    expect(mocks.send).not.toHaveBeenCalled()
    expect(result.current.state.messages.filter((m) => m.role === 'ASSISTANT')).toHaveLength(0)
    expect(result.current.state.sendError).toMatch(/não foi possível concluir/i)
    expect(result.current.state.sendError).not.toContain('Falha de rede.')
  })

  it('erro do stream mostra mensagem segura derivada do código', async () => {
    mocks.stream.mockImplementation(async (_request, _defaults, handlers) => {
      handlers.onThinking('pensamento interno SEGREDO')
      handlers.onError('rate_limited')
    })

    const { result } = await ready()
    act(() => result.current.actions.setInput('oi'))
    await act(async () => { await result.current.actions.send() })

    expect(result.current.state.sendError).toMatch(/muitas|limite|tente novamente/i)
    expect(result.current.state.sendError).not.toContain('rate_limited')
    expect(result.current.state.sendError).not.toContain('SEGREDO')
    expect(result.current.state.messages.filter((m) => m.role === 'ASSISTANT')).toHaveLength(0)
    expect(mocks.send).not.toHaveBeenCalled()
  })
})

// Fallback para sendMessage já é coberto por use-chat-controller.test.ts e
// use-chat-controller-ai.test.ts: ambos mockam chatService sem streamMessage.
