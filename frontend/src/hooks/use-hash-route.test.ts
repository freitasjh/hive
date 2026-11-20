import { act, renderHook, waitFor } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { DashboardViewId } from '@/components/shadcn/navigation'
import { buildHash, isTabbedView, parseHash, parseRoute, useHashRoute } from './use-hash-route'

/** replaceState muda o fragmento sem disparar hashchange (MDN, Location.hash). */
function setHash(hash: string) {
  window.history.replaceState(null, '', `/${hash}`)
}

/** Únicos ids sobreviventes: o Hive não tem telas para os ids de template removidos. */
const CANONICAL_IDS: DashboardViewId[] = [
  'overview',
  'login',
  'mfa',
  'providers',
  'chat',
  'prompts',
]

const UUID = '11111111-1111-1111-1111-111111111111'

describe('parseHash (compat: retorna só a view)', () => {
  it.each(CANONICAL_IDS)('aceita #%s como id canônico', (id) => {
    expect(parseHash(`#${id}`)).toBe(id)
  })

  it('alias legado #ai cai em overview (quebra intencional de deep-link)', () => {
    expect(parseHash('#ai')).toBe('overview')
    expect(parseHash('#ai/models')).toBe('overview')
  })

  it('alias legado #settings cai em overview (quebra intencional de deep-link)', () => {
    expect(parseHash('#settings')).toBe('overview')
    expect(parseHash('#settings/editor/new')).toBe('overview')
  })

  it('lê a view mesmo quando a URL traz sub-rota de aba', () => {
    expect(parseHash('#providers/models')).toBe('providers')
    expect(parseHash('#prompts/editor/new')).toBe('prompts')
  })

  it('deep-link legado #agents cai em overview (quebra intencional de link)', () => {
    expect(parseHash('#agents')).toBe('overview')
    expect(parseHash('#agents/models')).toBe('overview')
  })

  it.each(['', '#', '#/shadcn', '#desconhecido', '#foo/bar'])(
    'cai em overview para hash %j',
    (hash) => {
      expect(parseHash(hash)).toBe('overview')
    }
  )

  it('aceita #login e a forma legada #/login como rota de autenticação', () => {
    expect(parseHash('#login')).toBe('login')
    expect(parseHash('#/login')).toBe('login')
  })
})

describe('parseRoute', () => {
  it('login é view simples, sem aba, serializada como #login', () => {
    expect(parseRoute('#login')).toEqual({ view: 'login' })
    expect(buildHash('login')).toBe('#login')
    expect(isTabbedView('login')).toBe(false)
  })

  it('mfa é view simples fora do shell, serializada como #mfa', () => {
    expect(parseRoute('#mfa')).toEqual({ view: 'mfa' })
    expect(buildHash('mfa')).toBe('#mfa')
    expect(isTabbedView('mfa')).toBe(false)
  })

  it('deep-link de aba nas views admin', () => {
    expect(parseRoute('#providers/models')).toEqual({
      view: 'providers',
      tab: 'models',
    })
    expect(parseRoute('#providers/credentials')).toEqual({
      view: 'providers',
      tab: 'credentials',
    })
    expect(parseRoute('#prompts/templates')).toEqual({
      view: 'prompts',
      tab: 'templates',
    })
  })

  it('abre o editor com id new ou uuid', () => {
    expect(parseRoute('#prompts/editor/new')).toEqual({
      view: 'prompts',
      tab: 'editor',
      id: 'new',
    })
    expect(parseRoute(`#prompts/editor/${UUID}`)).toEqual({
      view: 'prompts',
      tab: 'editor',
      id: UUID,
    })
  })

  it('aliases legados não são reconhecidos e caem em overview', () => {
    expect(parseRoute('#ai/models')).toEqual({ view: 'overview' })
    expect(parseRoute('#settings/editor/new')).toEqual({ view: 'overview' })
  })

  it('view agents removida cai em overview, inclusive com sub-rota', () => {
    expect(parseRoute('#agents')).toEqual({ view: 'overview' })
    expect(parseRoute('#agents/models')).toEqual({ view: 'overview' })
    expect(parseRoute('#agents/credentials')).toEqual({ view: 'overview' })
    expect(isTabbedView('providers')).toBe(true)
  })

  it('tab desconhecida cai na tab default da view', () => {
    expect(parseRoute('#prompts/foo')).toEqual({ view: 'prompts', tab: 'templates' })
    expect(parseRoute('#providers/foo')).toEqual({
      view: 'providers',
      tab: 'credentials',
    })
  })

  it('view desconhecida cai em overview', () => {
    expect(parseRoute('#foo/bar')).toEqual({ view: 'overview' })
    expect(parseRoute('#foo/bar/baz')).toEqual({ view: 'overview' })
  })

  it('editor sem id não abre editor vazio', () => {
    expect(parseRoute('#prompts/editor')).toEqual({
      view: 'prompts',
      tab: 'templates',
    })
  })

  it('ignora segmentos além do id', () => {
    expect(parseRoute(`#prompts/editor/${UUID}/extra`)).toEqual({
      view: 'prompts',
      tab: 'editor',
      id: UUID,
    })
  })
})

describe('useHashRoute', () => {
  beforeEach(() => {
    setHash('')
  })

  afterEach(() => {
    vi.restoreAllMocks()
    setHash('')
  })

  it('deep-link: lê a rota completa no mount', () => {
    setHash('#providers/models')
    const { result } = renderHook(() => useHashRoute())
    expect(result.current[0]).toEqual({ view: 'providers', tab: 'models' })
  })

  it('reage a hashchange — volta/avanco troca a tela', () => {
    const { result } = renderHook(() => useHashRoute())
    expect(result.current[0]).toEqual({ view: 'overview' })

    setHash('#prompts')
    act(() => {
      window.dispatchEvent(new Event('hashchange'))
    })
    expect(result.current[0]).toEqual({ view: 'prompts', tab: 'templates' })

    setHash('#providers/models')
    act(() => {
      window.dispatchEvent(new Event('hashchange'))
    })
    expect(result.current[0]).toEqual({ view: 'providers', tab: 'models' })
  })

  it('navega escrevendo o hash e atualiza a rota somente no hashchange (fonte única)', async () => {
    const { result } = renderHook(() => useHashRoute())

    act(() => {
      result.current[1]('chat')
    })
    expect(window.location.hash).toBe('#chat')
    // navigate não chama setState direto: a rota só muda quando o evento chega.
    expect(result.current[0]).toEqual({ view: 'overview' })

    await waitFor(() => expect(result.current[0]).toEqual({ view: 'chat' }))
  })

  it('navigate escreve view/aba/id', async () => {
    const { result } = renderHook(() => useHashRoute())
    act(() => {
      result.current[1]('prompts', 'editor', UUID)
    })
    expect(window.location.hash).toBe(`#prompts/editor/${UUID}`)
    await waitFor(() =>
      expect(result.current[0]).toEqual({
        view: 'prompts',
        tab: 'editor',
        id: UUID,
      })
    )
  })

  it('replaceRoute usa replaceState e atualiza o estado local sem hashchange', () => {
    const handler = vi.fn()
    const replaceSpy = vi.spyOn(window.history, 'replaceState')
    window.addEventListener('hashchange', handler)
    try {
      const { result } = renderHook(() => useHashRoute())
      replaceSpy.mockClear()

      act(() => {
        result.current[2]('prompts', 'templates')
      })

      expect(window.location.hash).toBe('#prompts/templates')
      expect(result.current[0]).toEqual({ view: 'prompts', tab: 'templates' })
      expect(replaceSpy).toHaveBeenCalled()
      expect(handler).not.toHaveBeenCalled()
    } finally {
      window.removeEventListener('hashchange', handler)
    }
  })

  it('não canonicaliza alias no mount: hash legado permanece e a view cai em overview', () => {
    setHash('#ai/models')
    const replaceSpy = vi.spyOn(window.history, 'replaceState')
    const { result } = renderHook(() => useHashRoute())

    expect(window.location.hash).toBe('#ai/models')
    expect(result.current[0]).toEqual({ view: 'overview' })
    expect(replaceSpy).not.toHaveBeenCalled()
  })

  it('não canonicaliza alias em hashchange: cai em overview', () => {
    const { result } = renderHook(() => useHashRoute())
    setHash('#settings/editor/new')
    act(() => {
      window.dispatchEvent(new Event('hashchange'))
    })

    expect(window.location.hash).toBe('#settings/editor/new')
    expect(result.current[0]).toEqual({ view: 'overview' })
  })

  it('não gera loop de hashchange (spy no handler)', async () => {
    const handler = vi.fn()
    window.addEventListener('hashchange', handler)
    try {
      const { result } = renderHook(() => useHashRoute())

      act(() => {
        result.current[1]('providers')
      })
      await waitFor(() => expect(result.current[0]).toEqual({ view: 'providers', tab: 'credentials' }))
      await new Promise((resolve) => setTimeout(resolve, 25))

      const hashchangeCalls = handler.mock.calls.filter(
        ([event]) => (event as Event).type === 'hashchange'
      )
      expect(hashchangeCalls).toHaveLength(1)
    } finally {
      window.removeEventListener('hashchange', handler)
    }
  })

  it('registra o listener e o remove no unmount', () => {
    const addSpy = vi.spyOn(window, 'addEventListener')
    const removeSpy = vi.spyOn(window, 'removeEventListener')

    const { unmount } = renderHook(() => useHashRoute())
    const registered = addSpy.mock.calls.find(([type]) => type === 'hashchange')
    expect(registered).toBeTruthy()

    unmount()
    expect(
      removeSpy.mock.calls.some(
        ([type, callback]) => type === 'hashchange' && callback === registered?.[1]
      )
    ).toBe(true)
  })
})
