import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { LoginError, mfaService } from '@/services/auth.service'
import { TENANT_STORAGE_KEY, TOKEN_STORAGE_KEY } from '@/services/auth.service'
import { MfaVerifyPage } from './MfaVerifyPage'

const CHALLENGE_TENANT = 'bbbbbbbb-cccc-4ddd-8eee-ffffffffffff'
const CHALLENGE = {
  challengeId: 'e8a2658f-8830-4bcd-aa4c-a1f260ae59fd',
  expiresIn: 120,
  tenantId: CHALLENGE_TENANT,
}
const TOKEN = 'jwt-opaco'

describe('MfaVerifyPage — desafio fora do shell', () => {
  const onAuthenticated = vi.fn()
  const onCancel = vi.fn()

  beforeEach(() => {
    vi.restoreAllMocks()
    localStorage.clear()
    onAuthenticated.mockClear()
    onCancel.mockClear()
  })

  afterEach(() => {
    vi.restoreAllMocks()
    localStorage.clear()
  })

  function renderPage(challenge = CHALLENGE) {
    return render(
      <MfaVerifyPage
        challenge={challenge}
        onAuthenticated={onAuthenticated}
        onCancel={onCancel}
      />
    )
  }

  it('campo de 6 dígitos com atributos corretos, contador e expiração', () => {
    renderPage()

    const input = screen.getByRole('textbox', { name: /código de 6 dígitos/i }) as HTMLInputElement
    expect(input.getAttribute('inputmode')).toBe('numeric')
    expect(input.getAttribute('autocomplete')).toBe('one-time-code')
    expect(input.maxLength).toBe(6)
    expect(screen.getByText(/tentativas restantes/i)).toBeTruthy()
    expect(screen.getByText(/expira em/i)).toBeTruthy()
    expect(screen.queryByRole('banner')).toBeNull()
    expect(
      screen.queryByRole('navigation', { name: 'Navegação móvel inferior' })
    ).toBeNull()
  })

  it('código correto chama verify e onAuthenticated', async () => {
    const verify = vi.spyOn(mfaService, 'verify').mockResolvedValue({
      userId: 'u', accessToken: TOKEN, type: 'Bearer',
    })

    renderPage()
    fireEvent.change(screen.getByRole('textbox', { name: /código de 6 dígitos/i }), {
      target: { value: '123456' },
    })
    fireEvent.click(screen.getByRole('button', { name: /verificar/i }))

    await waitFor(() =>
      expect(verify).toHaveBeenCalledWith(CHALLENGE.challengeId, '123456', CHALLENGE_TENANT)
    )
    expect(onAuthenticated).toHaveBeenCalledTimes(1)
  })

  it('recovery chama recovery com o código informado', async () => {
    const recovery = vi.spyOn(mfaService, 'recovery').mockResolvedValue({
      userId: 'u', accessToken: TOKEN, type: 'Bearer',
    })

    renderPage()
    // Radix Tabs ativa no mousedown (padrão já usado nos testes de App).
    fireEvent.mouseDown(screen.getByRole('tab', { name: /recuperação/i }))
    fireEvent.change(screen.getByRole('textbox', { name: /código de recuperação/i }), {
      target: { value: 'a1b2-c3d4' },
    })
    fireEvent.click(screen.getByRole('button', { name: /verificar/i }))

    await waitFor(() =>
      expect(recovery).toHaveBeenCalledWith(CHALLENGE.challengeId, 'a1b2-c3d4', CHALLENGE_TENANT)
    )
    expect(onAuthenticated).toHaveBeenCalledTimes(1)
  })

  it('browser fresco: tenantId do desafio é repassado sem aparecer em UI, URL ou storage', async () => {
    const verify = vi.spyOn(mfaService, 'verify').mockResolvedValue({
      userId: 'u', accessToken: TOKEN, type: 'Bearer',
    })

    renderPage()
    expect(localStorage.getItem(TENANT_STORAGE_KEY)).toBeNull()
    expect(document.body.textContent).not.toContain(CHALLENGE_TENANT)
    expect(window.location.hash).not.toContain(CHALLENGE_TENANT)

    fireEvent.change(screen.getByRole('textbox', { name: /código de 6 dígitos/i }), {
      target: { value: '123456' },
    })
    fireEvent.click(screen.getByRole('button', { name: /verificar/i }))

    await waitFor(() =>
      expect(verify).toHaveBeenCalledWith(CHALLENGE.challengeId, '123456', CHALLENGE_TENANT)
    )
    expect(document.body.textContent).not.toContain(CHALLENGE_TENANT)
    expect(window.location.hash).not.toContain(CHALLENGE_TENANT)
  })

  it('código inválido (401) mostra erro orientado e decrementa tentativas em aria-live', async () => {
    vi.spyOn(mfaService, 'verify').mockRejectedValue(new LoginError('MFA_INVALID'))

    renderPage()
    fireEvent.change(screen.getByRole('textbox', { name: /código de 6 dígitos/i }), {
      target: { value: '000000' },
    })
    fireEvent.click(screen.getByRole('button', { name: /verificar/i }))

    const alert = await screen.findByRole('alert')
    expect(alert.textContent).toMatch(/código inválido/i)
    expect(alert.closest('[aria-live]')?.getAttribute('aria-live')).toBe('assertive')
    expect(screen.getByText(/tentativas restantes/i).textContent).toContain('4')
    expect(onAuthenticated).not.toHaveBeenCalled()
  })

  it('429 mostra bloqueio orientado, desabilita submit e permite voltar ao login', async () => {
    vi.spyOn(mfaService, 'verify').mockRejectedValue(new LoginError('MFA_TOO_MANY'))

    renderPage()
    fireEvent.change(screen.getByRole('textbox', { name: /código de 6 dígitos/i }), {
      target: { value: '123456' },
    })
    fireEvent.click(screen.getByRole('button', { name: /verificar/i }))

    expect(await screen.findByText(/muitas tentativas/i)).toBeTruthy()
    expect((screen.getByRole('button', { name: /verificar/i }) as HTMLButtonElement).disabled).toBe(true)
    fireEvent.click(screen.getByRole('button', { name: /voltar ao login/i }))
    expect(onCancel).toHaveBeenCalledTimes(1)
  })

  it('desafio expirado mostra mensagem e bloqueia o submit; nada é persistido', () => {
    renderPage({ ...CHALLENGE, expiresIn: 0 })

    expect(screen.getByText(/prazo deste desafio terminou/i)).toBeTruthy()
    expect((screen.getByRole('button', { name: /verificar/i }) as HTMLButtonElement).disabled).toBe(true)
    expect(localStorage.getItem(TOKEN_STORAGE_KEY)).toBeNull()
    expect(localStorage.getItem(TENANT_STORAGE_KEY)).toBeNull()
    expect(window.location.hash).not.toContain(CHALLENGE.challengeId)
  })

  it('MFA sem tenant salvo mostra erro orientado sem vazar payload', async () => {
    vi.spyOn(mfaService, 'verify').mockRejectedValue(new LoginError('MFA_TENANT_MISSING'))

    renderPage()
    fireEvent.change(screen.getByRole('textbox', { name: /código de 6 dígitos/i }), {
      target: { value: '123456' },
    })
    fireEvent.click(screen.getByRole('button', { name: /verificar/i }))

    expect(await screen.findByText(/não foi possível identificar a sessão/i)).toBeTruthy()
    expect(screen.queryByText(/\bMFA_[A-Z_]+\b/)).toBeNull()
  })
})
