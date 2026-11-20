import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { SidebarProvider } from '@/components/shadcn/ui/sidebar'
import { NavUser } from './nav-user'

const USER = { name: 'Atlas Admin', email: 'admin@atlas.com.br', avatar: '' }

// jsdom não implementa Pointer Events / scroll usados pelo Radix DropdownMenu.
if (!Element.prototype.hasPointerCapture) {
  Element.prototype.hasPointerCapture = () => false
}
if (!Element.prototype.releasePointerCapture) {
  Element.prototype.releasePointerCapture = () => {}
}
if (!Element.prototype.scrollIntoView) {
  Element.prototype.scrollIntoView = () => {}
}

function openMenu(onLogout: () => Promise<void>) {
  render(
    <SidebarProvider>
      <NavUser user={USER} onLogout={onLogout} />
    </SidebarProvider>
  )
  fireEvent.pointerDown(screen.getByRole('button', { name: /Atlas Admin/ }), {
    button: 0,
    ctrlKey: false,
  })
}

describe('NavUser — ação Sair', () => {
  const originalLang = document.documentElement.lang

  afterEach(() => {
    document.documentElement.lang = originalLang
  })

  it('chama o logout no clique e desabilita a ação enquanto pendente', async () => {
    let finish!: () => void
    const pending = new Promise<void>((resolve) => {
      finish = resolve
    })
    const onLogout = vi.fn().mockReturnValue(pending)
    openMenu(onLogout)

    const item = await screen.findByRole('menuitem', { name: 'Sair' })
    fireEvent.click(item)

    expect(onLogout).toHaveBeenCalledTimes(1)
    await waitFor(() => expect(item.hasAttribute('data-disabled')).toBe(true))

    finish()
    await waitFor(() => expect(onLogout).toHaveBeenCalledTimes(1))
  })

  it('mantém o rótulo en-US via i18n', async () => {
    document.documentElement.lang = 'en'
    openMenu(vi.fn().mockResolvedValue(undefined))

    expect(await screen.findByRole('menuitem', { name: 'Log out' })).toBeTruthy()
  })
})
