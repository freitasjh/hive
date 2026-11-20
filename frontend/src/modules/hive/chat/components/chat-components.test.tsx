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

  it('rótulo é associado ao select (clique no rótulo ativa o controle)', () => {
    const { container } = render(<PromptSelector prompts={[prompt]} value={null} onChange={vi.fn()} />)
    const label = container.querySelector('label[for="chat-prompt"]') as HTMLLabelElement
    const select = screen.getByTestId('prompt-select') as HTMLSelectElement
    expect(label).toBeTruthy()
    expect(label.textContent).toContain('Prompt')
    expect(label.htmlFor).toBe('chat-prompt')
    expect(label.control).toBe(select)
    const onClick = vi.fn()
    select.addEventListener('click', onClick)
    label.click()
    expect(onClick).toHaveBeenCalled()
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

  it('disclaimer de risco não é truncado em uma linha e expõe title', () => {
    render(<ChatComposer value="" onChange={vi.fn()} onSend={vi.fn()} />)
    const disclaimer = screen.getByText(/o assistente pode cometer erros/i)
    expect(disclaimer.className).toContain('line-clamp-2')
    expect(disclaimer.getAttribute('title')).toMatch(/o assistente pode cometer erros/i)
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

  it('usa avatar e bolha por papel e mantém o container de rolagem interno', () => {
    const { container } = render(
      <ChatThread
        messages={[message('a', 'USER', 'pergunta'), message('b', 'ASSISTANT', 'resposta')]}
        onStarter={vi.fn()}
        onLoadOlder={vi.fn()}
        onRetry={vi.fn()}
      />
    )
    expect(container.querySelector('[data-slot="scroll-area-viewport"]')).toBeTruthy()
    expect(container.querySelectorAll('[data-slot="avatar-fallback"]')).toHaveLength(2)
    const userRow = screen.getByLabelText('Você')
    const assistantRow = screen.getByLabelText('Assistente')
    expect(userRow.querySelector('[data-chat-bubble="USER"]')?.textContent).toContain('pergunta')
    expect(assistantRow.querySelector('[data-chat-bubble="ASSISTANT"]')?.textContent).toContain('resposta')
  })

  it('rola para o fim já na montagem quando o histórico vem preenchido', () => {
    const descriptor = Object.getOwnPropertyDescriptor(Element.prototype, 'scrollHeight')
    Object.defineProperty(Element.prototype, 'scrollHeight', { configurable: true, get: () => 200 })
    try {
      const { container } = render(
        <ChatThread
          messages={[message('a', 'USER', 'oi'), message('b', 'ASSISTANT', 'resposta')]}
          onStarter={vi.fn()}
          onLoadOlder={vi.fn()}
          onRetry={vi.fn()}
        />
      )
      const viewport = container.querySelector('[data-slot="scroll-area-viewport"]') as HTMLElement
      expect(viewport.scrollTop).toBe(200)
    } finally {
      if (descriptor) Object.defineProperty(Element.prototype, 'scrollHeight', descriptor)
      else Reflect.deleteProperty(Element.prototype, 'scrollHeight')
    }
  })

  it('rola para o fim só quando o usuário já estava no fim', () => {
    const first = message('a', 'USER', 'oi')
    const second = message('b', 'ASSISTANT', 'resposta')
    const third = message('c', 'USER', 'mais uma')
    const { container, rerender } = render(
      <ChatThread messages={[first]} onStarter={vi.fn()} onLoadOlder={vi.fn()} onRetry={vi.fn()} />
    )
    const viewport = container.querySelector('[data-slot="scroll-area-viewport"]') as HTMLElement
    expect(viewport).toBeTruthy()

    const metrics = (scrollHeight: number, clientHeight: number) => {
      Object.defineProperty(viewport, 'scrollHeight', { configurable: true, value: scrollHeight })
      Object.defineProperty(viewport, 'clientHeight', { configurable: true, value: clientHeight })
    }
    metrics(1000, 200)

    viewport.scrollTop = 600
    fireEvent.scroll(viewport) // usuário subiu: não gruda mais
    rerender(
      <ChatThread messages={[first, second]} onStarter={vi.fn()} onLoadOlder={vi.fn()} onRetry={vi.fn()} />
    )
    expect(viewport.scrollTop).toBe(600)

    viewport.scrollTop = 800
    fireEvent.scroll(viewport) // voltou ao fim: volta a grudar
    metrics(1200, 200)
    rerender(
      <ChatThread messages={[first, second, third]} onStarter={vi.fn()} onLoadOlder={vi.fn()} onRetry={vi.fn()} />
    )
    expect(viewport.scrollTop).toBe(1200)
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

  it('ordena conversa antes da sidebar em mobile e preserva grid 18rem|1fr no desktop', () => {
    const { container } = render(<ChatView state={baseState} actions={baseActions} />)
    const conversation = container.querySelector('[data-chat-pane="conversation"]') as HTMLElement
    const sidebar = container.querySelector('[data-chat-pane="sidebar"]') as HTMLElement
    const grid = conversation.parentElement as HTMLElement

    expect(conversation.className).toContain('order-1')
    expect(conversation.className).toContain('lg:col-start-2')
    expect(sidebar.className).toContain('order-2')
    expect(sidebar.className).toContain('lg:col-start-1')
    expect(grid.className).toContain('grid-rows-[minmax(0,1fr)_auto]')
    expect(grid.className).toContain('lg:grid-cols-[18rem_minmax(0,1fr)]')

    expect(screen.getByTestId('chat-thread').className).toContain('min-h-0')
    expect(container.querySelector('[data-slot="scroll-area-viewport"]')).toBeTruthy()
    expect(screen.getByTestId('chat-form')).toBeTruthy()
  })

  it('cap mobile da sidebar acomoda lista e paginador quando totalPages > 1', () => {
    const { container } = render(
      <ChatView
        state={{ ...baseState, sessions: [session, { ...session, id: S + '-2', title: 'Conversa 2' }], sessionsTotalPages: 3 }}
        actions={baseActions}
      />
    )
    const sidebar = container.querySelector('[data-chat-pane="sidebar"]') as HTMLElement
    expect(sidebar.className).toContain('max-h-40')
    expect(sidebar.querySelector('[data-slot="scroll-area-viewport"]')).toBeTruthy()
    expect(screen.getByText('Conversa')).toBeTruthy()
    expect(screen.getByRole('button', { name: /próximas conversas/i })).toBeTruthy()
  })
})
