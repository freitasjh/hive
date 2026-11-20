import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import App from './App'
import { ApiError } from '@/services/api'
import { setToken, TENANT_STORAGE_KEY, TOKEN_STORAGE_KEY } from '@/services/auth.service'

const AUTH_TOKEN = 'jwt-opaco-de-teste'
const AUTH_TENANT = 'aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee'

/** Shell exige sessão; quase todo teste renderiza rotas protegidas. */
function authenticate() {
  setToken(AUTH_TOKEN, AUTH_TENANT)
}

/** Testes de login/guard precisam do estado anônimo real. */
function anonymize() {
  localStorage.removeItem(TOKEN_STORAGE_KEY)
  localStorage.removeItem(TENANT_STORAGE_KEY)
}

const mocks = vi.hoisted(() => ({
  listCredentials: vi.fn(),
  getLlmConfig: vi.fn(),
  promptList: vi.fn(),
  getPromptById: vi.fn(),
  sessionList: vi.fn(),
}))

vi.mock('@/services/agent-admin.service', () => ({
  agentAdminService: {
    listCredentials: mocks.listCredentials,
    getLlmConfig: mocks.getLlmConfig,
    validate: vi.fn(),
    upsertCredential: vi.fn(),
    deleteCredential: vi.fn(),
    saveLlmConfig: vi.fn(),
    listProviderModels: vi.fn(),
  },
}))

vi.mock('@/services/prompt.service', () => ({
  promptService: {
    list: mocks.promptList,
    getById: mocks.getPromptById,
    create: vi.fn(),
    update: vi.fn(),
  },
}))

vi.mock('@/services/session.service', () => ({
  sessionService: {
    listSessions: mocks.sessionList,
    getMessages: vi.fn(),
    getSession: vi.fn(),
    createSession: vi.fn(),
    deleteSession: vi.fn(),
    updateSession: vi.fn(),
  },
}))

vi.mock('@/services/chat.service', () => ({
  chatService: { sendMessage: vi.fn() },
}))

const MODULES = [
  'tenant',
  'commons',
  'bootstrap',
  'identity',
  'security',
  'agent',
  'provider',
  'publicapi',
  'integration-test',
]

const STACK = [
  'Java 25',
  'Spring Boot 4.1.0',
  'PostgreSQL 16 + pgvector',
  'Redis',
  'RabbitMQ',
]

const EMPTY_PAGE = {
  content: [],
  totalElements: 0,
  totalPages: 1,
  number: 0,
  size: 20,
}

/** replaceState muda o fragmento sem disparar hashchange (MDN, Location.hash). */
function setHash(hash: string) {
  window.history.replaceState(null, '', `/${hash}`)
}

function overviewHeading() {
  return screen.getByRole('heading', { level: 1, name: 'Visão geral do Hive' })
}

function breadcrumbPage(name: string) {
  return screen.getByRole('link', { name, current: 'page' })
}

function tabSelected(name: RegExp) {
  return screen.getByRole('tab', { name }).getAttribute('aria-selected') === 'true'
}

/** Sidebar e barra móvel repetem os rótulos; o primeiro é o item do menu lateral. */
function menuButton(name: RegExp) {
  return screen.getAllByRole('button', { name })[0]
}

describe('App', () => {
  beforeEach(() => {
    localStorage.clear()
    authenticate()
    setHash('')
    mocks.promptList.mockResolvedValue(EMPTY_PAGE)
    mocks.sessionList.mockResolvedValue(EMPTY_PAGE)
  })

  afterEach(() => {
    setHash('')
  })

  describe('guard de rota — sessão anônima', () => {
    it.each(['#overview', '#chat', '#providers'])(
      'rota protegida %s sem token redireciona para #login sem montar shell',
      async (hash) => {
        anonymize()
        setHash(hash)
        render(<App />)

        await waitFor(() => expect(window.location.hash).toBe('#login'))
        expect(screen.getByRole('heading', { level: 1, name: 'Entrar na sua conta' })).toBeTruthy()
        expect(screen.queryByRole('banner')).toBeNull()
        expect(
          screen.queryByRole('navigation', { name: 'Navegação móvel inferior' })
        ).toBeNull()
        expect(screen.queryByText('Visão geral do Hive')).toBeNull()
      }
    )

    it('com token, #login redireciona para overview', async () => {
      setHash('#login')
      render(<App />)

      await waitFor(() => expect(window.location.hash).toBe('#overview'))
      expect(overviewHeading()).toBeTruthy()
    })

    it('após remover o token, rota protegida deixa de ser acessível', async () => {
      setHash('#overview')
      const view = render(<App />)
      expect(overviewHeading()).toBeTruthy()

      anonymize()
      setHash('#chat')
      act(() => {
        window.dispatchEvent(new Event('hashchange'))
      })

      await waitFor(() => expect(window.location.hash).toBe('#login'))
      expect(screen.queryByRole('banner')).toBeNull()
      view.unmount()
    })
  })

  it('monta o shell admin com visão inicial overview', () => {
    render(<App />)

    expect(overviewHeading()).toBeTruthy()
    expect(screen.getByRole('banner')).toBeTruthy()
    expect(
      screen.getByRole('navigation', { name: 'Navegação móvel inferior' })
    ).toBeTruthy()
    expect(screen.getByRole('button', { name: /Visão geral/ })).toBeTruthy()
  })

  it('exibe informações estáticas do Hive derivadas do repo', () => {
    render(<App />)

    expect(screen.getByText('HIVE AI')).toBeTruthy()
    expect(screen.getByText('Monolito modular')).toBeTruthy()
    expect(screen.getByText('9', { selector: 'span' })).toBeTruthy()
    expect(screen.getByText('5', { selector: 'span' })).toBeTruthy()

    for (const moduleName of MODULES) {
      expect(screen.getByText(moduleName)).toBeTruthy()
    }
    for (const technology of STACK) {
      expect(screen.getByText(technology)).toBeTruthy()
    }
  })

  it('navega para Config LLM pelo menu, atualiza a URL e retorna à visão geral', async () => {
    mocks.getLlmConfig.mockResolvedValue(null)
    render(<App />)

    fireEvent.click(menuButton(/Config LLM/))

    await waitFor(() => expect(breadcrumbPage('Config LLM')).toBeTruthy())
    expect(window.location.hash).toBe('#agents')
    expect(
      screen.queryByRole('heading', { level: 1, name: 'Visão geral do Hive' })
    ).toBeNull()

    setHash('#overview')
    act(() => {
      window.dispatchEvent(new Event('hashchange'))
    })

    await waitFor(() => expect(overviewHeading()).toBeTruthy())
    expect(window.location.hash).toBe('#overview')
  })

  it('abre Provedores e credenciais (id providers) na aba Credenciais', async () => {
    // Promise pendente mantém o estado de loading estável para a asserção.
    mocks.listCredentials.mockReturnValue(new Promise(() => {}))
    mocks.getLlmConfig.mockResolvedValue(null)
    render(<App />)

    fireEvent.click(screen.getByRole('button', { name: /Provedores e credenciais/ }))

    await waitFor(() => expect(breadcrumbPage('Provedores de IA')).toBeTruthy())
    expect(window.location.hash).toBe('#providers')
    expect(screen.getByRole('tab', { name: /Credenciais/ })).toBeTruthy()
    expect(screen.getByRole('tab', { name: /Provedor e modelo/ })).toBeTruthy()
    expect(tabSelected(/Credenciais/)).toBe(true)
    expect(tabSelected(/Provedor e modelo/)).toBe(false)
    expect(await screen.findByTestId('credentials-loading')).toBeTruthy()
  })

  it('abre Config LLM (id agents) na aba Provedor e modelo', async () => {
    mocks.getLlmConfig.mockResolvedValue(null)
    render(<App />)

    fireEvent.click(menuButton(/Config LLM/))

    await waitFor(() => expect(breadcrumbPage('Config LLM')).toBeTruthy())
    expect(window.location.hash).toBe('#agents')
    expect(tabSelected(/Provedor e modelo/)).toBe(true)
    expect(tabSelected(/Credenciais/)).toBe(false)
    expect(screen.queryByTestId('credentials-loading')).toBeNull()
  })

  it('usa o titulo en-US da página Config LLM quando o idioma e en', async () => {
    mocks.getLlmConfig.mockResolvedValue(null)
    const originalLang = document.documentElement.lang
    document.documentElement.lang = 'en'
    try {
      render(<App />)

      fireEvent.click(menuButton(/Config LLM/))

      await waitFor(() => expect(breadcrumbPage('LLM Config')).toBeTruthy())
      expect(tabSelected(/Provider and model/)).toBe(true)
      expect(window.location.hash).toBe('#agents')
    } finally {
      document.documentElement.lang = originalLang
    }
  })

  it('troca a aba ativa ao navegar de providers para agents', async () => {
    mocks.listCredentials.mockReturnValue(new Promise(() => {}))
    mocks.getLlmConfig.mockResolvedValue(null)
    render(<App />)

    fireEvent.click(screen.getByRole('button', { name: /Provedores e credenciais/ }))
    expect(await screen.findByTestId('credentials-loading')).toBeTruthy()
    expect(window.location.hash).toBe('#providers')

    fireEvent.click(menuButton(/Config LLM/))

    await waitFor(() => expect(breadcrumbPage('Config LLM')).toBeTruthy())
    expect(window.location.hash).toBe('#agents')
    expect(tabSelected(/Provedor e modelo/)).toBe(true)
    expect(screen.queryByTestId('credentials-loading')).toBeNull()
  })

  it('abre Prompts (id prompts) com breadcrumb e aba Templates ativa', async () => {
    mocks.promptList.mockResolvedValue(EMPTY_PAGE)
    render(<App />)

    fireEvent.click(screen.getByRole('link', { name: 'Prompts' }))

    await waitFor(() => expect(breadcrumbPage('Prompts')).toBeTruthy())
    expect(window.location.hash).toBe('#prompts')
    expect(screen.getByRole('tab', { name: /Templates/ })).toBeTruthy()
    expect(screen.getByRole('tab', { name: /Editor/ })).toBeTruthy()
    expect(tabSelected(/Templates/)).toBe(true)
  })

  it('menu mostra apenas as telas existentes, sem ids de template', () => {
    render(<App />)

    const menuLabel = (label: string) => {
      const pattern = new RegExp(label.replace(/[.*+?^${}()|[\]\\]/g, '\\$&'))
      return (
        screen.queryAllByRole('button', { name: pattern })[0] ??
        screen.queryAllByRole('link', { name: pattern })[0]
      )
    }

    for (const removed of [
      'Atlas AI Copilot',
      'Configurações',
      'SaaS Admin',
      'Backoffice',
      'Arquivos',
      'Quadro Kanban',
      'Agendamento',
      'Transações',
      'Cartões & Faturas',
      'PDV Frente de Caixa',
      'Checkout',
      'Contas Bancárias',
      'Relatórios',
      'Equipe',
      'Ajuda',
      'Busca',
      'Documentos',
    ]) {
      expect(menuLabel(removed)).toBeUndefined()
    }

    for (const present of [
      'Visão geral',
      'Provedores e credenciais',
      'Config LLM',
      'Assistant',
      'Prompts',
    ]) {
      expect(menuLabel(present)).toBeTruthy()
    }
  })

  it('navega pela barra inferior móvel e mantém o shell acessível', async () => {
    render(<App />)

    const mobileNav = screen.getByRole('navigation', {
      name: 'Navegação móvel inferior',
    })

    fireEvent.click(within(mobileNav).getByRole('button', { name: /Assistant/ }))

    await waitFor(() => {
      expect(
        screen.getByRole('heading', { level: 1, name: 'Assistant' })
      ).toBeTruthy()
    })
    expect(window.location.hash).toBe('#chat')
    expect(mobileNav).toBeTruthy()
  })

  it('deep-link: /#agents abre Agentes na aba Provedor e modelo', () => {
    mocks.getLlmConfig.mockResolvedValue(null)
    setHash('#agents')
    render(<App />)

    expect(breadcrumbPage('Config LLM')).toBeTruthy()
    expect(tabSelected(/Provedor e modelo/)).toBe(true)
    expect(window.location.hash).toBe('#agents')
  })

  it('menu Assistant abre #chat com seletor de prompt e composer', async () => {
    render(<App />)

    fireEvent.click(menuButton(/Assistant/))

    await waitFor(() => {
      expect(screen.getByRole('heading', { level: 1, name: 'Assistant' })).toBeTruthy()
    })
    expect(window.location.hash).toBe('#chat')
    expect(screen.getByTestId('prompt-select')).toBeTruthy()
    expect(screen.getByTestId('chat-form')).toBeTruthy()
    expect(screen.getByRole('heading', { name: 'Conversas' })).toBeTruthy()
  })

  it('deep-link: /#chat abre Assistant sem abrir Config LLM', () => {
    setHash('#chat')
    render(<App />)

    expect(screen.getByRole('heading', { level: 1, name: 'Assistant' })).toBeTruthy()
    expect(screen.queryByRole('tab', { name: /Provedor e modelo/ })).toBeNull()
    expect(menuButton(/Config LLM/)).toBeTruthy()
    expect(window.location.hash).toBe('#chat')
  })

  it('deep-link: /#prompts abre Prompts', () => {
    mocks.promptList.mockResolvedValue(EMPTY_PAGE)
    setHash('#prompts')
    render(<App />)

    expect(breadcrumbPage('Prompts')).toBeTruthy()
    expect(tabSelected(/Templates/)).toBe(true)
    expect(window.location.hash).toBe('#prompts')
  })

  it('deep-link: /#providers abre Credenciais', () => {
    mocks.listCredentials.mockResolvedValue([])
    mocks.getLlmConfig.mockResolvedValue(null)
    setHash('#providers')
    render(<App />)

    expect(breadcrumbPage('Provedores de IA')).toBeTruthy()
    expect(tabSelected(/Credenciais/)).toBe(true)
    expect(window.location.hash).toBe('#providers')
  })

  it('volta/avança do browser troca a tela via hashchange', async () => {
    mocks.getLlmConfig.mockResolvedValue(null)
    mocks.promptList.mockResolvedValue(EMPTY_PAGE)
    setHash('#agents')
    render(<App />)
    expect(breadcrumbPage('Config LLM')).toBeTruthy()

    // avançar para Prompts (hash muda e o browser dispara hashchange)
    setHash('#prompts')
    act(() => {
      window.dispatchEvent(new Event('hashchange'))
    })
    await waitFor(() => expect(breadcrumbPage('Prompts')).toBeTruthy())

    // voltar para Agentes
    setHash('#agents')
    act(() => {
      window.dispatchEvent(new Event('hashchange'))
    })
    await waitFor(() => expect(breadcrumbPage('Config LLM')).toBeTruthy())
  })

  it('alias legado #ai e #settings caem em overview e não são canonicalizados', () => {
    setHash('#ai')
    const first = render(<App />)
    expect(overviewHeading()).toBeTruthy()
    expect(window.location.hash).toBe('#ai')
    expect(document.title).toBe('Visão geral')
    first.unmount()

    setHash('#settings')
    render(<App />)
    expect(overviewHeading()).toBeTruthy()
    expect(window.location.hash).toBe('#settings')
    expect(document.title).toBe('Visão geral')
  })

  it('hash vazio ou desconhecido cai em overview', () => {
    setHash('')
    const first = render(<App />)
    expect(overviewHeading()).toBeTruthy()
    first.unmount()

    setHash('#/rota-inexistente')
    render(<App />)
    expect(overviewHeading()).toBeTruthy()
  })

  it('rota #login renderiza o formulário antes do shell Atlas', () => {
    anonymize()
    setHash('#login')
    render(<App />)

    expect(
      screen.getByRole('heading', { level: 1, name: 'Entrar na sua conta' })
    ).toBeTruthy()
    expect(screen.getByLabelText('Usuário')).toBeTruthy()
    expect(screen.getByLabelText('Senha')).toBeTruthy()
    expect(screen.queryByRole('banner')).toBeNull()
    expect(
      screen.queryByRole('navigation', { name: 'Navegação móvel inferior' })
    ).toBeNull()
    expect(screen.queryByText('Visão geral do Hive')).toBeNull()
  })

  it('após autenticação bem-sucedida navega para overview', async () => {
    const { loginService } = await import('@/services/auth.service')
    const loginSpy = vi.spyOn(loginService, 'login').mockImplementation(async () => {
      // O serviço real persiste a sessão; o spy deve reproduzir esse efeito para o guard.
      authenticate()
      return {
        userId: '11111111-1111-1111-1111-111111111111',
        accessToken: AUTH_TOKEN,
        type: 'Bearer',
      }
    })
    try {
      anonymize()
      setHash('#login')
      render(<App />)

      fireEvent.change(screen.getByLabelText('Usuário'), {
        target: { value: 'freitasjh' },
      })
      fireEvent.change(screen.getByLabelText('Senha'), {
        target: { value: 'senha-nunca-persistida' },
      })
      fireEvent.click(screen.getByRole('button', { name: 'Entrar' }))

      await waitFor(() => {
        expect(window.location.hash).toBe('#overview')
        expect(overviewHeading()).toBeTruthy()
      })
      expect(loginSpy).toHaveBeenCalledTimes(1)
    } finally {
      loginSpy.mockRestore()
    }
  })

  it('deep-link: #agents/models abre models e mantém breadcrumb só da view', () => {
    mocks.getLlmConfig.mockResolvedValue(null)
    setHash('#agents/models')
    render(<App />)

    expect(breadcrumbPage('Config LLM')).toBeTruthy()
    expect(tabSelected(/Provedor e modelo/)).toBe(true)
    expect(document.title).toBe('Config LLM · Provedor e modelo')
    expect(screen.getAllByRole('link', { current: 'page' })).toHaveLength(1)
  })

  it('deep-link: #prompts/editor/new abre editor vazio com id new', () => {
    setHash('#prompts/editor/new')
    render(<App />)

    expect(tabSelected(/Editor/)).toBe(true)
    expect(screen.getByTestId('input-name')).toBeTruthy()
    expect(breadcrumbPage('Prompts')).toBeTruthy()
    expect(screen.getAllByRole('link', { current: 'page' })).toHaveLength(1)
    expect(document.title).toBe('Prompts · Editor')
  })

  it('deep-link: #prompts/editor/<uuid> abre editor com o prompt', async () => {
    mocks.getPromptById.mockResolvedValue({
      id: '11111111-1111-1111-1111-111111111111',
      name: 'Saudação',
      code: 'greeting-v1',
      description: '',
      tags: [],
      template: 'Olá {{nome}}',
      status: 'DRAFT',
    })
    setHash('#prompts/editor/11111111-1111-1111-1111-111111111111')
    render(<App />)

    expect(await screen.findByTestId('input-name')).toBeTruthy()
    expect((screen.getByTestId('input-code') as HTMLInputElement).disabled).toBe(true)
    expect((screen.getByTestId('input-name') as HTMLInputElement).value).toBe('Saudação')
  })

  it('deep-link: #ai/models cai em overview, sem canonicalizar (quebra intencional)', () => {
    setHash('#ai/models')
    render(<App />)

    expect(window.location.hash).toBe('#ai/models')
    expect(overviewHeading()).toBeTruthy()
    expect(document.title).toBe('Visão geral')
  })

  it('tab desconhecida cai em templates da view prompts, nunca overview', () => {
    mocks.promptList.mockResolvedValue(EMPTY_PAGE)
    setHash('#prompts/foo')
    render(<App />)

    expect(tabSelected(/Templates/)).toBe(true)
    expect(breadcrumbPage('Prompts')).toBeTruthy()
    expect(screen.queryByRole('heading', { level: 1, name: 'Visão geral do Hive' })).toBeNull()
  })

  it('view desconhecida cai em overview', () => {
    setHash('#foo/bar')
    render(<App />)

    expect(overviewHeading()).toBeTruthy()
    expect(document.title).toBe('Visão geral')
  })

  it('clique em aba substitui a URL sem empilhar historico', async () => {
    mocks.promptList.mockResolvedValue(EMPTY_PAGE)
    setHash('#prompts')
    const handler = vi.fn()
    window.addEventListener('hashchange', handler)
    try {
      render(<App />)
      handler.mockClear()

      // Radix Tabs ativa no mousedown; um clique real sempre o emite.
      fireEvent.mouseDown(screen.getByRole('tab', { name: /Editor/ }))

      await waitFor(() => expect(window.location.hash).toBe('#prompts/editor/new'))
      expect(tabSelected(/Editor/)).toBe(true)
      expect(handler).not.toHaveBeenCalled()
    } finally {
      window.removeEventListener('hashchange', handler)
    }
  })

  it('voltar do editor retorna a #prompts/templates de forma deterministica', async () => {
    setHash('#prompts/editor/new')
    render(<App />)

    fireEvent.click(screen.getByTestId('back-btn'))

    await waitFor(() => expect(window.location.hash).toBe('#prompts/templates'))
    expect(tabSelected(/Templates/)).toBe(true)
  })

  it('id de prompt invalido mostra erro orientado com CTA e aria-live', () => {
    setHash('#prompts/editor/invalid-id')
    render(<App />)

    const alert = screen.getByText(/não foi possível abrir este prompt/i)
    expect(alert).toBeTruthy()
    expect(alert.closest('[aria-live]')?.getAttribute('aria-live')).toBe('assertive')
    expect(screen.getByRole('button', { name: /voltar aos templates/i })).toBeTruthy()
    expect(screen.queryByTestId('input-name')).toBeNull()
  })

  it('id inexistente (uuid sem prompt) mostra erro orientado, nunca editor vazio', async () => {
    mocks.getPromptById.mockRejectedValue(new ApiError('Não encontrado', 404))
    setHash('#prompts/editor/22222222-2222-2222-2222-222222222222')
    render(<App />)

    expect(await screen.findByText(/não foi possível abrir este prompt/i)).toBeTruthy()
    expect(screen.getByRole('button', { name: /voltar aos templates/i })).toBeTruthy()
    expect(screen.queryByTestId('input-name')).toBeNull()
  })
})
