import { act, renderHook, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '@/services/api'
import { buildChatPayload } from '@/services/chat.service'
import { useChatController } from './use-chat-controller'

const P = '11111111-1111-1111-1111-111111111111'
const S = '22222222-2222-2222-2222-222222222222'

const mocks = vi.hoisted(() => ({
  send: vi.fn(),
  promptList: vi.fn(),
  listSessions: vi.fn(),
  getMessages: vi.fn(),
  getSession: vi.fn(),
  deleteSession: vi.fn(),
  updateSession: vi.fn(),
}))

vi.mock(import('@/services/chat.service'), async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/services/chat.service')>()
  return { ...actual, chatService: { sendMessage: mocks.send } }
})
vi.mock('@/services/prompt.service', () => ({
  promptService: { list: mocks.promptList },
}))
vi.mock('@/services/session.service', () => ({
  sessionService: {
    listSessions: mocks.listSessions,
    getMessages: mocks.getMessages,
    getSession: mocks.getSession,
    createSession: vi.fn(),
    deleteSession: mocks.deleteSession,
    updateSession: mocks.updateSession,
  },
}))

const prompt = { id: P, name: 'Saudação', code: 'greeting', template: 'Olá', status: 'ACTIVE' }
const session = { id: S, userId: 'u', promptTemplateId: P, title: 'Conversa', createAt: '', updateAt: '' }
const sessionPage = {
  content: [session],
  totalElements: 42,
  totalPages: 3,
  number: 0,
  size: 20,
}
const messagePage = (page: number) => ({
  content: [
    {
      id: `m-${page}`,
      sessionId: S,
      role: 'USER',
      content: `mensagem ${page}`,
      createAt: '2026-11-19T12:00:00Z',
    },
  ],
  totalElements: 80,
  totalPages: 2,
  number: page,
  size: 50,
})
const answer = { sessionId: S, role: 'ASSISTANT', message: 'Resposta', timestamp: '2026-11-19T12:01:00Z' }

describe('useChatController', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    localStorage.clear()
    mocks.promptList.mockResolvedValue({ content: [prompt], totalElements: 1, totalPages: 1, number: 0, size: 20 })
    mocks.listSessions.mockImplementation((page = 0) =>
      Promise.resolve({ ...sessionPage, number: page })
    )
    mocks.getMessages.mockImplementation((_id: string, page = 0) =>
      Promise.resolve(messagePage(page))
    )
    mocks.getSession.mockResolvedValue(session)
    mocks.send.mockResolvedValue(answer)
    mocks.deleteSession.mockResolvedValue(undefined)
    mocks.updateSession.mockResolvedValue(undefined)
  })

  it('carrega prompts e sessões no mount e expõe totalPages do unwrapPage', async () => {
    const { result } = renderHook(() => useChatController())
    await waitFor(() => expect(result.current.state.promptsLoading).toBe(false))
    await waitFor(() => expect(result.current.state.sessionsLoading).toBe(false))

    expect(result.current.state.prompts).toEqual([prompt])
    expect(result.current.state.sessions).toEqual([session])
    expect(result.current.state.sessionsTotalPages).toBe(3)
    expect(mocks.promptList).toHaveBeenCalledWith(expect.objectContaining({ status: 'ACTIVE' }))
  })

  it('envia sem prompt selecionado: request sem promptTemplateId e sem aviso de bloqueio', async () => {
    const { result } = renderHook(() => useChatController())
    await waitFor(() => expect(result.current.state.promptsLoading).toBe(false))

    act(() => result.current.actions.setInput('oi'))
    await act(async () => {
      await result.current.actions.send()
    })

    expect(mocks.send).toHaveBeenCalledTimes(1)
    const request = mocks.send.mock.calls[0][0] as Record<string, unknown>
    expect(request).toMatchObject({ message: 'oi' })
    expect(request).not.toHaveProperty('promptTemplateId')
    expect(result.current.state.notice).toBeNull()
    expect(result.current.state.sendError).toBeNull()
  })

  it('primeiro turno: sessão nasce da resposta e o prompt fica travado', async () => {
    const { result } = renderHook(() => useChatController())
    await waitFor(() => expect(result.current.state.promptsLoading).toBe(false))

    act(() => result.current.actions.selectPrompt(P))
    act(() => result.current.actions.setInput('primeira pergunta'))
    await act(async () => {
      await result.current.actions.send()
    })

    expect(mocks.send).toHaveBeenCalledWith({
      promptTemplateId: P,
      message: 'primeira pergunta',
    }, null)
    expect(mocks.send.mock.calls[0][0]).toHaveProperty('promptTemplateId', P)
    expect(result.current.state.sessionId).toBe(S)
    expect(result.current.state.messages.map((m) => m.content)).toEqual([
      'primeira pergunta',
      'Resposta',
    ])
    expect(result.current.state.input).toBe('')
    // sessão recarregada para o sidebar refletir o novo turno
    expect(mocks.listSessions).toHaveBeenCalledTimes(2)
  })

  it('turno seguinte envia sessionId e o payload final NÃO contém promptTemplateId', async () => {
    const { result } = renderHook(() => useChatController())
    await waitFor(() => expect(result.current.state.promptsLoading).toBe(false))
    act(() => result.current.actions.selectPrompt(P))
    act(() => result.current.actions.setInput('um'))
    await act(async () => {
      await result.current.actions.send()
    })

    act(() => result.current.actions.setInput('dois'))
    await act(async () => {
      await result.current.actions.send()
    })

    expect(mocks.send).toHaveBeenLastCalledWith({
      promptTemplateId: P,
      sessionId: S,
      message: 'dois',
    }, null)
    const payload = buildChatPayload(mocks.send.mock.calls[1][0])
    expect(payload).toEqual({ sessionId: S, message: 'dois' })
    expect(payload).not.toHaveProperty('promptTemplateId')
  })

  it('resposta atrasada da LLM após newChat não reativa sessão nem injeta mensagem antiga', async () => {
    let resolveSend: (value: unknown) => void = () => {}
    mocks.send.mockImplementationOnce(() => new Promise((resolve) => { resolveSend = resolve }))
    const { result } = renderHook(() => useChatController())
    await waitFor(() => expect(result.current.state.promptsLoading).toBe(false))

    act(() => result.current.actions.selectPrompt(P))
    act(() => result.current.actions.setInput('mensagem em voo'))
    let pending!: Promise<void>
    await act(async () => {
      pending = result.current.actions.send()
      await Promise.resolve() // chega ao await da LLM pendente
    })
    expect(result.current.state.sending).toBe(true)

    act(() => result.current.actions.newChat())
    await act(async () => { resolveSend(answer); await pending })

    expect(result.current.state.sessionId).toBeNull()
    expect(result.current.state.messages).toEqual([])
    expect(result.current.state.sending).toBe(false)
  })

  it('resposta atrasada da LLM não recarrega a sidebar com sessão antiga', async () => {
    let resolveSend: (value: unknown) => void = () => {}
    mocks.send.mockImplementationOnce(() => new Promise((resolve) => { resolveSend = resolve }))
    const { result } = renderHook(() => useChatController())
    await waitFor(() => expect(result.current.state.promptsLoading).toBe(false))
    act(() => result.current.actions.selectPrompt(P))
    act(() => result.current.actions.setInput('primeira'))
    let pending!: Promise<void>
    await act(async () => {
      pending = result.current.actions.send()
      await Promise.resolve()
    })
    mocks.listSessions.mockClear()
    act(() => result.current.actions.newChat())
    await act(async () => { resolveSend(answer); await pending })
    await act(async () => { await Promise.resolve() })

    expect(mocks.listSessions).not.toHaveBeenCalled()
  })

  it('falha de getSession preserva o prompt selecionado e mantém o envio possível', async () => {
    mocks.getSession.mockRejectedValueOnce(new ApiError('Não encontrada', 404))
    const { result } = renderHook(() => useChatController())
    await waitFor(() => expect(result.current.state.sessionsLoading).toBe(false))
    act(() => result.current.actions.selectPrompt(P))

    await act(async () => { await result.current.actions.selectSession(S) })

    expect(result.current.state.historyError).toBeNull()
    expect(result.current.state.selectedPromptTemplateId).toBe(P)
    act(() => result.current.actions.setInput('ainda envia'))
    await act(async () => { await result.current.actions.send() })
    expect(mocks.send).toHaveBeenCalledWith({
      promptTemplateId: P,
      sessionId: S,
      message: 'ainda envia',
    }, null)
  })

  it('abrir sessão existente trava o seletor no prompt dela e carrega o histórico', async () => {
    const otherPrompt = '55555555-5555-5555-5555-555555555555'
    mocks.getSession.mockResolvedValue({ ...session, promptTemplateId: otherPrompt })
    const { result } = renderHook(() => useChatController())
    await waitFor(() => expect(result.current.state.sessionsLoading).toBe(false))

    await act(async () => {
      await result.current.actions.selectSession(S)
    })

    expect(mocks.getMessages).toHaveBeenCalledWith(S, 0, 50)
    expect(result.current.state.selectedPromptTemplateId).toBe(otherPrompt)
    expect(result.current.state.sessionId).toBe(S)
    expect(result.current.state.messages).toHaveLength(1)
  })

  it('resposta atrasada de getSession não troca o prompt da conversa atual', async () => {
    let resolveSession: (value: unknown) => void = () => {}
    mocks.getSession.mockImplementation(
      () => new Promise((resolve) => { resolveSession = resolve })
    )
    const { result } = renderHook(() => useChatController())
    await waitFor(() => expect(result.current.state.sessionsLoading).toBe(false))

    act(() => result.current.actions.selectPrompt(P))
    let first!: Promise<void>
    await act(async () => {
      first = result.current.actions.selectSession(S)
      await Promise.resolve() // deixa getMessages resolver e chegar ao getSession pendente
    })
    act(() => result.current.actions.newChat())
    await act(async () => { resolveSession({ ...session, promptTemplateId: P }); await first })

    expect(result.current.state.selectedPromptTemplateId).toBeNull()
    expect(result.current.state.sessionId).toBeNull()
  })

  it('paginador de sessões e histórico mais antigo são explícitos', async () => {
    const { result } = renderHook(() => useChatController())
    await waitFor(() => expect(result.current.state.sessionsLoading).toBe(false))

    await act(async () => { await result.current.actions.goToSessionsPage(1) })
    expect(mocks.listSessions).toHaveBeenCalledWith(1, 20)
    expect(result.current.state.sessionsPage).toBe(1)

    await act(async () => { await result.current.actions.selectSession(S) })
    await act(async () => { await result.current.actions.loadOlderMessages() })
    expect(mocks.getMessages).toHaveBeenLastCalledWith(S, 1, 50)
    expect(result.current.state.messages.map((m) => m.content)).toEqual([
      'mensagem 1',
      'mensagem 0',
    ])
  })

  it('nova conversa limpa sessão, mensagens e prompt', async () => {
    const { result } = renderHook(() => useChatController())
    await waitFor(() => expect(result.current.state.sessionsLoading).toBe(false))
    await act(async () => { await result.current.actions.selectSession(S) })

    act(() => result.current.actions.newChat())

    expect(result.current.state.sessionId).toBeNull()
    expect(result.current.state.messages).toEqual([])
    expect(result.current.state.selectedPromptTemplateId).toBeNull()
  })

  it('mapeia 404 ao abrir sessão e 401 ao enviar para mensagens orientadas', async () => {
    mocks.getMessages.mockRejectedValueOnce(new ApiError('Não encontrada', 404))
    const first = renderHook(() => useChatController())
    await waitFor(() => expect(first.result.current.state.sessionsLoading).toBe(false))
    await act(async () => { await first.result.current.actions.selectSession(S) })
    expect(first.result.current.state.historyError).toMatch(/não existe mais/i)
    first.unmount()

    mocks.send.mockRejectedValueOnce(new ApiError('Não autenticado', 401))
    const second = renderHook(() => useChatController())
    await waitFor(() => expect(second.result.current.state.promptsLoading).toBe(false))
    act(() => second.result.current.actions.selectPrompt(P))
    act(() => second.result.current.actions.setInput('oi'))
    await act(async () => { await second.result.current.actions.send() })
    expect(second.result.current.state.sendError).toMatch(/sessão expirou/i)
  })

  it('remover sessão atual volta para nova conversa e recarrega a lista', async () => {
    const { result } = renderHook(() => useChatController())
    await waitFor(() => expect(result.current.state.sessionsLoading).toBe(false))
    await act(async () => { await result.current.actions.selectSession(S) })

    await act(async () => { await result.current.actions.removeSession(S) })

    expect(mocks.deleteSession).toHaveBeenCalledWith(S)
    expect(result.current.state.sessionId).toBeNull()
    expect(mocks.listSessions).toHaveBeenCalledTimes(2)
  })

  it('busca local: query filtra sessões por título e mensagens carregadas por conteúdo', async () => {
    const { result } = renderHook(() => useChatController())
    await waitFor(() => expect(result.current.state.sessionsLoading).toBe(false))
    await act(async () => { await result.current.actions.selectSession(S) })

    act(() => result.current.actions.setQuery('MENSAGEM'))
    expect(result.current.state.query).toBe('MENSAGEM')
    expect(result.current.state.filteredSessions).toEqual([])
    expect(result.current.state.matchingMessages.map((m) => m.id)).toEqual(['m-0'])

    act(() => result.current.actions.setQuery('Conversa'))
    expect(result.current.state.filteredSessions.map((s) => s.id)).toEqual([S])
    expect(result.current.state.matchingMessages).toEqual([])
  })

  it('renomear sessão persiste o título e recarrega a lista', async () => {
    const { result } = renderHook(() => useChatController())
    await waitFor(() => expect(result.current.state.sessionsLoading).toBe(false))
    // servidor já devolve o título alterado na recarga (PATCH é síncrono)
    mocks.listSessions.mockImplementationOnce(() =>
      Promise.resolve({ ...sessionPage, content: [{ ...session, title: 'Novo título' }] })
    )

    await act(async () => { await result.current.actions.renameSession(S, 'Novo título') })

    expect(mocks.updateSession).toHaveBeenCalledWith(S, 'Novo título')
    expect(mocks.listSessions).toHaveBeenCalledTimes(2)
    expect(result.current.state.sessions[0].title).toBe('Novo título')
  })

  it('renomear com título vazio não chama a API', async () => {
    const { result } = renderHook(() => useChatController())
    await waitFor(() => expect(result.current.state.sessionsLoading).toBe(false))

    await act(async () => { await result.current.actions.renameSession(S, '   ') })

    expect(mocks.updateSession).not.toHaveBeenCalled()
    expect(mocks.listSessions).toHaveBeenCalledTimes(1)
  })

  it('jumpToMessage define o alvo e clearTargetMessage o limpa', async () => {
    const { result } = renderHook(() => useChatController())
    await waitFor(() => expect(result.current.state.sessionsLoading).toBe(false))

    await act(async () => { await result.current.actions.jumpToMessage('m-42') })
    expect(result.current.state.targetMessageId).toBe('m-42')

    act(() => result.current.actions.clearTargetMessage())
    expect(result.current.state.targetMessageId).toBeNull()
  })

  it('jumpToMessage entre sessões carrega o histórico antes de mirar a mensagem', async () => {
    const other = S + '-2'
    mocks.getMessages.mockImplementation((_id: string, page = 0) =>
      Promise.resolve({ ...messagePage(page), content: [{ ...messagePage(page).content[0], id: 'alvo' }] })
    )
    const { result } = renderHook(() => useChatController())
    await waitFor(() => expect(result.current.state.sessionsLoading).toBe(false))

    await act(async () => { await result.current.actions.jumpToMessage('alvo', other) })

    expect(mocks.getMessages).toHaveBeenCalledWith(other, 0, 50)
    expect(result.current.state.sessionId).toBe(other)
    expect(result.current.state.targetMessageId).toBe('alvo')
  })

  it('jump cancelado por newChat durante a troca de sessão não deixa alvo órfão', async () => {
    const other = S + '-2'
    let resolveMessages: (value: unknown) => void = () => {}
    mocks.getMessages.mockImplementationOnce(() => new Promise((resolve) => { resolveMessages = resolve }))
    const { result } = renderHook(() => useChatController())
    await waitFor(() => expect(result.current.state.sessionsLoading).toBe(false))

    let pending!: Promise<void>
    await act(async () => {
      pending = result.current.actions.jumpToMessage('alvo', other)
      await Promise.resolve() // chega ao await de selectSession
    })
    act(() => result.current.actions.newChat())
    await act(async () => { resolveMessages(messagePage(0)); await pending })

    expect(result.current.state.sessionId).toBeNull()
    expect(result.current.state.targetMessageId).toBeNull()
  })

  it('trocar de sessão ou nova conversa descarta alvo de jump pendente', async () => {
    const { result } = renderHook(() => useChatController())
    await waitFor(() => expect(result.current.state.sessionsLoading).toBe(false))
    await act(async () => { await result.current.actions.jumpToMessage('m-42') })

    await act(async () => { await result.current.actions.selectSession(S) })
    expect(result.current.state.targetMessageId).toBeNull()

    await act(async () => { await result.current.actions.jumpToMessage('m-42') })
    act(() => result.current.actions.newChat())
    expect(result.current.state.targetMessageId).toBeNull()
  })
})
