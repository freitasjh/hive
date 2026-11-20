import { fireEvent, render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import type { ChatMessageDTO, ChatSessionDTO } from '@/services/chat.types'
import type { PromptTemplateDTO } from '@/services/admin.types'
import { ChatComposer } from './ChatComposer'
import { ChatThread } from './ChatThread'
import { PromptSelector } from './PromptSelector'
import { SessionSidebar } from './SessionSidebar'
import { ChatView } from './ChatView'
import type { ChatControllerState } from '../hooks/use-chat-controller'
import { STARTER_PROMPTS } from '../data/chat.data'

const P = '11111111-1111-1111-1111-111111111111'
const S = '22222222-2222-2222-2222-222222222222'

const prompt: PromptTemplateDTO = {
  id: P,
  name: 'Saudação',
  code: 'greeting',
  template: 'Olá {{nome}}',
  status: 'ACTIVE',
  version: 1,
  createAt: '',
  updateAt: '',
}

const session: ChatSessionDTO = {
  id: S,
  userId: 'u',
  promptTemplateId: P,
  title: 'Conversa',
  createAt: '',
  updateAt: '',
}

const message = (id: string, role: string, content: string): ChatMessageDTO => ({
  id,
  sessionId: S,
  role,
  content,
  createAt: '2026-11-19T12:00:00Z',
})

describe('PromptSelector', () => {
  it('lista prompts e propaga a escolha', () => {
    const onChange = vi.fn()
    render(<PromptSelector prompts={[prompt]} value={null} onChange={onChange} />)
    expect(screen.getByLabelText(/prompt/i)).toBeTruthy()
    fireEvent.change(screen.getByTestId('prompt-select'), { target: { value: P } })
    expect(onChange).toHaveBeenCalledWith(P)
  })

  it('trava o seletor quando há sessão (prompt da sessão é autoritativo)', () => {
    render(<PromptSelector prompts={[prompt]} value={P} locked onChange={vi.fn()} />)
    const select = screen.getByTestId('prompt-select') as HTMLSelectElement
    expect(select.disabled).toBe(true)
    expect(screen.getByText(/travado nesta conversa/i)).toBeTruthy()
  })

  it('mostra loading, erro e disabled explicitamente', () => {
    const { rerender } = render(<PromptSelector prompts={[]} value={null} loading onChange={vi.fn()} />)
    expect(screen.getByTestId('prompt-loading')).toBeTruthy()

    rerender(<PromptSelector prompts={[]} value={null} error="Falha" onChange={vi.fn()} />)
    expect(screen.getByRole('alert').textContent).toContain('Falha')

    rerender(<PromptSelector prompts={[prompt]} value={P} disabled onChange={vi.fn()} />)
    expect((screen.getByTestId('prompt-select') as HTMLSelectElement).disabled).toBe(true)
  })
})

describe('ChatComposer', () => {
  it('envia no submit e ao pressionar Enter (sem Shift)', () => {
    const onSend = vi.fn()
    render(<ChatComposer value="oi" onChange={vi.fn()} onSend={onSend} />)

    fireEvent.submit(screen.getByTestId('chat-form'))
    expect(onSend).toHaveBeenCalledTimes(1)

    const input = screen.getByLabelText(/mensagem para o assistente/i)
    fireEvent.keyDown(input, { key: 'Enter', shiftKey: false })
    expect(onSend).toHaveBeenCalledTimes(2)
    fireEvent.keyDown(input, { key: 'Enter', shiftKey: true })
    expect(onSend).toHaveBeenCalledTimes(2)
  })

  it('bloqueia envio durante carregamento e mostra aviso sem prompt', () => {
    const onSend = vi.fn()
    const { rerender } = render(
      <ChatComposer value="oi" onChange={vi.fn()} onSend={onSend} disabled sending />
    )
    const button = screen.getByRole('button', { name: /enviando/i }) as HTMLButtonElement
    expect(button.disabled).toBe(true)
    fireEvent.submit(screen.getByTestId('chat-form'))
    expect(onSend).not.toHaveBeenCalled()

    rerender(<ChatComposer value="oi" onChange={vi.fn()} onSend={onSend} warning="Escolha um prompt antes de enviar." />)
    expect(screen.getByRole('alert').textContent).toMatch(/escolha um prompt/i)
  })

  it('textarea é controlada e não envia com texto vazio', () => {
    const onSend = vi.fn()
    const onChange = vi.fn()
    render(<ChatComposer value="" onChange={onChange} onSend={onSend} />)
    fireEvent.change(screen.getByLabelText(/mensagem para o assistente/i), { target: { value: 'novo' } })
    expect(onChange).toHaveBeenCalledWith('novo')
    fireEvent.submit(screen.getByTestId('chat-form'))
    expect(onSend).not.toHaveBeenCalled()
  })
})

describe('ChatThread', () => {
  it('empty com sugestões desabilitadas sem prompt', () => {
    const onStarter = vi.fn()
    render(
      <ChatThread
        messages={[]}
        starterPrompts={STARTER_PROMPTS}
        canUseStarter={false}
        onStarter={onStarter}
        onLoadOlder={vi.fn()}
        onRetry={vi.fn()}
      />
    )
    expect(screen.getByText(/escolha um prompt e escreva sua pergunta/i)).toBeTruthy()
    const first = screen.getAllByRole('button', { name: STARTER_PROMPTS[0] })[0] as HTMLButtonElement
    expect(first.disabled).toBe(true)
  })

  it('sugestão dispara o texto e o histórico renderiza mensagens', () => {
    const onStarter = vi.fn()
    const { rerender } = render(
      <ChatThread
        messages={[]}
        starterPrompts={STARTER_PROMPTS}
        canUseStarter
        onStarter={onStarter}
        onLoadOlder={vi.fn()}
        onRetry={vi.fn()}
      />
    )
    fireEvent.click(screen.getAllByRole('button', { name: STARTER_PROMPTS[0] })[0])
    expect(onStarter).toHaveBeenCalledWith(STARTER_PROMPTS[0])

    rerender(
      <ChatThread
        messages={[message('a', 'USER', 'pergunta'), message('b', 'ASSISTANT', 'resposta')]}
        starterPrompts={STARTER_PROMPTS}
        onStarter={onStarter}
        onLoadOlder={vi.fn()}
        onRetry={vi.fn()}
      />
    )
    expect(screen.getByText('pergunta')).toBeTruthy()
    expect(screen.getByText('resposta')).toBeTruthy()
  })

  it('erro SYSTEM tem rótulo próprio — nunca máscara de assistente', () => {
    render(
      <ChatThread
        messages={[message('s', 'SYSTEM', 'Não foi possível enviar a mensagem.')]}
        onStarter={vi.fn()}
        onLoadOlder={vi.fn()}
        onRetry={vi.fn()}
      />
    )
    expect(screen.getByText('Sistema')).toBeTruthy()
    expect(screen.queryByText('Assistente')).toBeNull()
    const article = screen.getByLabelText('Sistema')
    expect(article.textContent).toContain('Não foi possível enviar a mensagem.')
  })

  it('loading, erro, gerando e paginação antiga são estados explícitos', () => {
    const onLoadOlder = vi.fn()
    const onRetry = vi.fn()
    const { rerender } = render(
      <ChatThread messages={[]} loading onStarter={vi.fn()} onLoadOlder={onLoadOlder} onRetry={onRetry} />
    )
    expect(screen.getByTestId('thread-loading')).toBeTruthy()

    rerender(
      <ChatThread messages={[]} error="Falha no histórico" onStarter={vi.fn()} onLoadOlder={onLoadOlder} onRetry={onRetry} />
    )
    expect(screen.getByRole('alert').textContent).toContain('Falha no histórico')

    rerender(
      <ChatThread
        messages={[message('a', 'USER', 'oi')]}
        generating
        canLoadOlder
        retryable
        onStarter={vi.fn()}
        onLoadOlder={onLoadOlder}
        onRetry={onRetry}
      />
    )
    expect(screen.getByRole('progressbar')).toBeTruthy()
    fireEvent.click(screen.getByRole('button', { name: /mensagens antigas/i }))

    rerender(
      <ChatThread
        messages={[message('a', 'USER', 'oi')]}
        retryable
        onStarter={vi.fn()}
        onLoadOlder={onLoadOlder}
        onRetry={onRetry}
      />
    )
    fireEvent.click(screen.getByRole('button', { name: /tentar novamente/i }))
    expect(onLoadOlder).toHaveBeenCalledTimes(1)
    expect(onRetry).toHaveBeenCalledTimes(1)
  })
})

describe('SessionSidebar', () => {
  it('renderiza loading, vazio e erro', () => {
    const base = { onSelect: vi.fn(), onNew: vi.fn(), onDelete: vi.fn(), onPageChange: vi.fn() }
    const { rerender } = render(<SessionSidebar sessions={[]} selectedId={null} page={0} totalPages={1} loading {...base} />)
    expect(screen.getByTestId('sessions-loading')).toBeTruthy()

    rerender(<SessionSidebar sessions={[]} selectedId={null} page={0} totalPages={1} error="Falha" {...base} />)
    expect(screen.getByRole('alert').textContent).toContain('Falha')

    rerender(<SessionSidebar sessions={[]} selectedId={null} page={0} totalPages={1} {...base} />)
    expect(screen.getByText(/nenhuma conversa ainda/i)).toBeTruthy()
  })

  it('paginador só aparece com totalPages > 1 e troca de página', () => {
    const onPageChange = vi.fn()
    const onSelect = vi.fn()
    render(
      <SessionSidebar
        sessions={[session]}
        selectedId={S}
        page={0}
        totalPages={3}
        onSelect={onSelect}
        onNew={vi.fn()}
        onDelete={vi.fn()}
        onPageChange={onPageChange}
      />
    )
    fireEvent.click(screen.getByText('Conversa'))
    expect(onSelect).toHaveBeenCalledWith(S)
    expect(screen.getByText(/página 1 de 3/i)).toBeTruthy()
    fireEvent.click(screen.getByRole('button', { name: /próximas conversas/i }))
    expect(onPageChange).toHaveBeenCalledWith(1)
  })

  it('totalPages=1 esconde paginador (regressão da paginação silenciosa)', () => {
    render(
      <SessionSidebar
        sessions={[session]}
        selectedId={null}
        page={0}
        totalPages={1}
        onSelect={vi.fn()}
        onNew={vi.fn()}
        onDelete={vi.fn()}
        onPageChange={vi.fn()}
      />
    )
    expect(screen.queryByRole('button', { name: /próximas conversas/i })).toBeNull()
  })
})

describe('ChatView', () => {
  const baseState: ChatControllerState = {
    prompts: [prompt],
    promptsLoading: false,
    promptsError: null,
    selectedPromptTemplateId: P,
    sessions: [session],
    sessionsPage: 0,
    sessionsTotalPages: 2,
    sessionsLoading: false,
    sessionsError: null,
    sessionId: null,
    messages: [],
    messagesPage: 0,
    messagesTotalPages: 1,
    historyLoading: false,
    historyError: null,
    input: '',
    sending: false,
    sendError: null,
    notice: null,
    lastUserMessage: null,
  }

  const baseActions = {
    selectPrompt: vi.fn(),
    setInput: vi.fn(),
    send: vi.fn(),
    useStarter: vi.fn(),
    selectSession: vi.fn(),
    newChat: vi.fn(),
    removeSession: vi.fn(),
    goToSessionsPage: vi.fn(),
    loadOlderMessages: vi.fn(),
    retryLast: vi.fn(),
    dismissNotice: vi.fn(),
  }

  it('compõe seletor, thread e composer com aviso de prompt', () => {
    render(<ChatView state={{ ...baseState, notice: 'Escolha um prompt antes de enviar.' }} actions={baseActions} />)
    expect(screen.getByRole('heading', { level: 1, name: /assistant/i })).toBeTruthy()
    expect(screen.getByTestId('prompt-select')).toBeTruthy()
    expect(screen.getByTestId('chat-form')).toBeTruthy()
    expect(screen.getByRole('alert').textContent).toMatch(/escolha um prompt/i)
  })

  it('trava o seletor depois do primeiro turno', () => {
    render(<ChatView state={{ ...baseState, sessionId: S }} actions={baseActions} />)
    expect((screen.getByTestId('prompt-select') as HTMLSelectElement).disabled).toBe(true)
  })
})
