import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { SidebarProvider } from '@/components/shadcn/ui/sidebar'
import { mfaService } from '@/services/auth.service'
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

describe('NavUser — gerenciamento MFA', () => {
  const CODES = ['a1b2-c3d4', 'e5f6-g7h8', 'i9j0-k1l2', 'm3n4-o5p6', 'q7r8-s9t0', 'u1v2-w3x4', 'y5z6-a7b8', 'c9d0-e1f2']

  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('menu expõe Configurar MFA e mostra QR, secret e 8 códigos', async () => {
    vi.spyOn(mfaService, 'setup').mockResolvedValue({
      secret: 'JBSWY3DPEHPK3PXP',
      qrDataUri: 'data:image/png;base64,iVBORw0KGgo=',
      recoveryCodes: CODES,
    })
    openMenu(vi.fn().mockResolvedValue(undefined))

    fireEvent.click(await screen.findByRole('menuitem', { name: 'Configurar MFA' }))

    expect(await screen.findByRole('dialog')).toBeTruthy()
    expect(await screen.findByAltText(/QR Code/i)).toBeTruthy()
    expect(screen.getByText('JBSWY3DPEHPK3PXP')).toBeTruthy()
    for (const code of CODES) expect(screen.getByText(code)).toBeTruthy()
    expect(mfaService.setup).toHaveBeenCalledTimes(1)
  })

  it('confirma setup com código de 6 dígitos via Bearer', async () => {
    vi.spyOn(mfaService, 'setup').mockResolvedValue({
      secret: 'JBSWY3DPEHPK3PXP',
      qrDataUri: 'data:image/png;base64,iVBORw0KGgo=',
      recoveryCodes: CODES,
    })
    const enable = vi.spyOn(mfaService, 'enable').mockResolvedValue(undefined)
    openMenu(vi.fn().mockResolvedValue(undefined))

    fireEvent.click(await screen.findByRole('menuitem', { name: 'Configurar MFA' }))
    const codeInput = await screen.findByTestId('mfa-setup-code')
    fireEvent.change(codeInput, { target: { value: '123456' } })
    fireEvent.click(screen.getByRole('button', { name: /confirmar e ativar/i }))

    await waitFor(() => expect(enable).toHaveBeenCalledWith('123456'))
    expect(await screen.findByText(/ativada/i)).toBeTruthy()
  })

  it('Desativar MFA envia a senha e usa Bearer', async () => {
    const disable = vi.spyOn(mfaService, 'disable').mockResolvedValue(undefined)
    openMenu(vi.fn().mockResolvedValue(undefined))

    fireEvent.click(await screen.findByRole('menuitem', { name: 'Desativar MFA' }))
    const password = await screen.findByTestId('mfa-disable-password')
    fireEvent.change(password, { target: { value: 'senha-atual' } })
    fireEvent.click(screen.getByRole('button', { name: /desativar mfa/i }))

    await waitFor(() => expect(disable).toHaveBeenCalledWith('senha-atual'))
  })
})
