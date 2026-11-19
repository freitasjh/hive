import { act, renderHook, waitFor } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { DashboardViewId } from '@/components/shadcn/navigation'
import { parseHash, useHashRoute } from './use-hash-route'

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

describe('parseHash', () => {
  it.each(CANONICAL_IDS)('aceita #%s como id canônico', (id) => {
    expect(parseHash(`#${id}`)).toBe(id)
  })

  it('normaliza alias #ai -> providers', () => {
    expect(parseHash('#ai')).toBe('providers')
  })

  it('normaliza alias #settings -> prompts', () => {
    expect(parseHash('#settings')).toBe('prompts')
  })

  it.each(['', '#', '#/login', '#/shadcn', '#desconhecido', '#prompts/editor'])(
    'cai em overview para hash %j',
    (hash) => {
      expect(parseHash(hash)).toBe('overview')
    }
  )
})

describe('useHashRoute', () => {
  beforeEach(() => {
    setHash('')
  })

  afterEach(() => {
    vi.restoreAllMocks()
    setHash('')
  })

  it('deep-link: lê o hash no mount', () => {
    setHash('#agents')
    const { result } = renderHook(() => useHashRoute())
    expect(result.current[0]).toBe('agents')
  })

  it('reage a hashchange — volta/avanco troca a tela', () => {
    const { result } = renderHook(() => useHashRoute())
    expect(result.current[0]).toBe('overview')

    setHash('#prompts')
    act(() => {
      window.dispatchEvent(new Event('hashchange'))
    })
    expect(result.current[0]).toBe('prompts')

    setHash('#agents')
    act(() => {
      window.dispatchEvent(new Event('hashchange'))
    })
    expect(result.current[0]).toBe('agents')
  })

  it('navega escrevendo o hash e atualiza o view somente no hashchange (fonte única)', async () => {
    const { result } = renderHook(() => useHashRoute())

    act(() => {
      result.current[1]('kanban')
    })
    expect(window.location.hash).toBe('#kanban')
    // navigate não chama setState direto: o view só muda quando o evento chega.
    expect(result.current[0]).toBe('overview')

    await waitFor(() => expect(result.current[0]).toBe('kanban'))
  })

  it('não gera loop de hashchange (spy no handler)', async () => {
    const handler = vi.fn()
    window.addEventListener('hashchange', handler)
    try {
      const { result } = renderHook(() => useHashRoute())

      act(() => {
        result.current[1]('agents')
      })
      await waitFor(() => expect(result.current[0]).toBe('agents'))
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
