import { act, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import App from './App'

const mocks = vi.hoisted(() => ({
  listCredentials: vi.fn(),
  getLlmConfig: vi.fn(),
  promptList: vi.fn(),
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
    getById: vi.fn(),
    create: vi.fn(),
    update: vi.fn(),
  },
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

describe('App', () => {
  beforeEach(() => {
    setHash('')
  })

  afterEach(() => {
    setHash('')
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

  it('navega para uma view rotulada, atualiza a URL e retorna à visão geral', async () => {
    render(<App />)

    fireEvent.click(screen.getByRole('button', { name: /Quadro Kanban/ }))

    await waitFor(() => {
      expect(
        screen.getByRole('heading', { level: 1, name: 'Quadro Kanban' })
      ).toBeTruthy()
    })
    expect(window.location.hash).toBe('#kanban')
    expect(
      screen.queryByRole('heading', { level: 1, name: 'Visão geral do Hive' })
    ).toBeNull()
    expect(screen.getByText('Em preparação')).toBeTruthy()

    fireEvent.click(screen.getByRole('button', { name: 'Voltar à visão geral' }))

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

  it('abre Agentes (LLM) (id agents) na aba Provedor e modelo', async () => {
    mocks.getLlmConfig.mockResolvedValue(null)
    render(<App />)

    fireEvent.click(screen.getByRole('button', { name: /Agentes \(LLM\)/ }))

    await waitFor(() => expect(breadcrumbPage('Agentes (LLM)')).toBeTruthy())
    expect(window.location.hash).toBe('#agents')
    expect(tabSelected(/Provedor e modelo/)).toBe(true)
    expect(tabSelected(/Credenciais/)).toBe(false)
    expect(screen.queryByTestId('credentials-loading')).toBeNull()
  })

  it('usa o titulo en-US para Agentes (LLM) quando o idioma e en', async () => {
    mocks.getLlmConfig.mockResolvedValue(null)
    const originalLang = document.documentElement.lang
    document.documentElement.lang = 'en'
    try {
      render(<App />)

      fireEvent.click(screen.getByRole('button', { name: /Agentes \(LLM\)/ }))

      await waitFor(() => expect(breadcrumbPage('Agents (LLM)')).toBeTruthy())
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

    fireEvent.click(screen.getByRole('button', { name: /Agentes \(LLM\)/ }))

    await waitFor(() => expect(breadcrumbPage('Agentes (LLM)')).toBeTruthy())
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

  it('nao exibe rotulos antigos enganosos no menu', () => {
    render(<App />)

    expect(screen.queryByRole('button', { name: /Atlas AI Copilot/ })).toBeNull()
    expect(screen.queryByRole('link', { name: /Configurações/ })).toBeNull()
  })

  it('navega pela barra inferior móvel e mantém o shell acessível', async () => {
    render(<App />)

    const mobileNav = screen.getByRole('navigation', {
      name: 'Navegação móvel inferior',
    })
    const transactions = screen.getAllByRole('button', { name: /Transações/i })

    fireEvent.click(transactions[transactions.length - 1])

    await waitFor(() => {
      expect(
        screen.getByRole('heading', { level: 1, name: 'Transações' })
      ).toBeTruthy()
    })
    expect(window.location.hash).toBe('#transactions')
    expect(mobileNav).toBeTruthy()
  })

  it('deep-link: /#agents abre Agentes na aba Provedor e modelo', () => {
    mocks.getLlmConfig.mockResolvedValue(null)
    setHash('#agents')
    render(<App />)

    expect(breadcrumbPage('Agentes (LLM)')).toBeTruthy()
    expect(tabSelected(/Provedor e modelo/)).toBe(true)
    expect(window.location.hash).toBe('#agents')
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
    expect(breadcrumbPage('Agentes (LLM)')).toBeTruthy()

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
    await waitFor(() => expect(breadcrumbPage('Agentes (LLM)')).toBeTruthy())
  })

  it('alias #ai cai em Provedores e #settings cai em Prompts', async () => {
    mocks.listCredentials.mockResolvedValue([])
    mocks.getLlmConfig.mockResolvedValue(null)
    mocks.promptList.mockResolvedValue(EMPTY_PAGE)

    setHash('#ai')
    const first = render(<App />)
    expect(breadcrumbPage('Provedores de IA')).toBeTruthy()
    expect(tabSelected(/Credenciais/)).toBe(true)
    first.unmount()

    setHash('#settings')
    render(<App />)
    expect(breadcrumbPage('Prompts')).toBeTruthy()
    expect(tabSelected(/Templates/)).toBe(true)
  })

  it('hash vazio ou desconhecido cai em overview', () => {
    setHash('')
    const first = render(<App />)
    expect(overviewHeading()).toBeTruthy()
    first.unmount()

    setHash('#/login')
    render(<App />)
    expect(overviewHeading()).toBeTruthy()
  })
})
