import { act, renderHook, waitFor } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { DashboardViewId } from '@/components/shadcn/navigation'
import { parseHash, parseRoute, useHashRoute } from './use-hash-route'

/** replaceState muda o fragmento sem disparar hashchange (MDN, Location.hash). */
function setHash(hash: string) {
  window.history.replaceState(null, '', `/${hash}`)
}

const CANONICAL_IDS: DashboardViewId[] = [
  'overview',
  'providers',
  'agents',
  'prompts',
  'kanban',
  'scheduler',
  'transactions',
  'cards',
  'checkout',
  'pdv',
  'accounts',
  'files',
  'backoffice',
  'internal-admin',
]

const UUID = '11111111-1111-1111-1111-111111111111'

describe('parseHash (compat: retorna só a view)', () => {
  it.each(CANONICAL_IDS)('aceita #%s como id canônico', (id) => {
    expect(parseHash(`#${id}`)).toBe(id)
  })

  it('normaliza alias #ai -> providers', () => {
    expect(parseHash('#ai')).toBe('providers')
  })

  it('normaliza alias #settings -> prompts', () => {
    expect(parseHash('#settings')).toBe('prompts')
  })

  it('lê a view mesmo quando a URL traz sub-rota de aba', () => {
    expect(parseHash('#agents/models')).toBe('agents')
    expect(parseHash('#prompts/editor/new')).toBe('prompts')
    expect(parseHash('#ai/models')).toBe('providers')
  })

  it.each(['', '#', '#/login', '#/shadcn', '#desconhecido', '#foo/bar'])(
    'cai em overview para hash %j',
    (hash) => {
      expect(parseHash(hash)).toBe('overview')
    }
  )
})

describe('parseRoute', () => {
  it('deep-link de aba nas views admin', () => {
    expect(parseRoute('#agents/models')).toEqual({ view: 'agents', tab: 'models' })
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

  it('canonicaliza aliases no primeiro segmento preservando aba e id', () => {
    expect(parseRoute('#ai/models')).toEqual({ view: 'providers', tab: 'models' })
    expect(parseRoute('#settings/editor/new')).toEqual({
      view: 'prompts',
      tab: 'editor',
      id: 'new',
    })
  })

  it('tab desconhecida cai na tab default da view', () => {
    expect(parseRoute('#prompts/foo')).toEqual({ view: 'prompts', tab: 'templates' })
    expect(parseRoute('#agents/foo')).toEqual({ view: 'agents', tab: 'models' })
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
    setHash('#agents/models')
    const { result } = renderHook(() => useHashRoute())
    expect(result.current[0]).toEqual({ view: 'agents', tab: 'models' })
  })

  it('reage a hashchange — volta/avanco troca a tela', () => {
    const { result } = renderHook(() => useHashRoute())
    expect(result.current[0]).toEqual({ view: 'overview' })

    setHash('#prompts')
    act(() => {
      window.dispatchEvent(new Event('hashchange'))
    })
    expect(result.current[0]).toEqual({ view: 'prompts', tab: 'templates' })

    setHash('#agents')
    act(() => {
      window.dispatchEvent(new Event('hashchange'))
    })
    expect(result.current[0]).toEqual({ view: 'agents', tab: 'models' })
  })

  it('navega escrevendo o hash e atualiza a rota somente no hashchange (fonte única)', async () => {
    const { result } = renderHook(() => useHashRoute())

    act(() => {
      result.current[1]('kanban')
    })
    expect(window.location.hash).toBe('#kanban')
    // navigate não chama setState direto: a rota só muda quando o evento chega.
    expect(result.current[0]).toEqual({ view: 'overview' })

    await waitFor(() => expect(result.current[0]).toEqual({ view: 'kanban' }))
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

  it('canonicaliza alias no mount via replaceState', () => {
    setHash('#ai/models')
    const replaceSpy = vi.spyOn(window.history, 'replaceState')
    const { result } = renderHook(() => useHashRoute())

    expect(window.location.hash).toBe('#providers/models')
    expect(result.current[0]).toEqual({ view: 'providers', tab: 'models' })
    expect(replaceSpy).toHaveBeenCalled()
  })

  it('canonicaliza alias também em hashchange', () => {
    const { result } = renderHook(() => useHashRoute())
    setHash('#settings/editor/new')
    act(() => {
      window.dispatchEvent(new Event('hashchange'))
    })

    expect(window.location.hash).toBe('#prompts/editor/new')
    expect(result.current[0]).toEqual({
      view: 'prompts',
      tab: 'editor',
      id: 'new',
    })
  })

  it('não gera loop de hashchange (spy no handler)', async () => {
    const handler = vi.fn()
    window.addEventListener('hashchange', handler)
    try {
      const { result } = renderHook(() => useHashRoute())

      act(() => {
        result.current[1]('agents')
      })
      await waitFor(() => expect(result.current[0]).toEqual({ view: 'agents', tab: 'models' }))
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
