import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { LoginError } from '@/services/auth.service'
import { LoginPage } from './LoginPage'

const mocks = vi.hoisted(() => ({
  login: vi.fn(),
  getToken: vi.fn<() => string | null>(() => null),
}))

vi.mock('@/services/auth.service', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/services/auth.service')>()
  return {
    ...actual,
    getToken: mocks.getToken,
    loginService: { ...actual.loginService, login: mocks.login },
  }
})

describe('LoginPage — fluxo de autenticação fora do shell', () => {
  beforeEach(() => {
    mocks.login.mockReset()
    mocks.getToken.mockReset()
    mocks.getToken.mockReturnValue(null)
    window.history.replaceState(null, '', '/#login')
  })

  afterEach(() => {
    window.history.replaceState(null, '', '/')
  })

  it('mostra formulário de usuário/senha sem sidebar ou header do Atlas', () => {
    render(<LoginPage />)

    expect(screen.getByRole('heading', { level: 1, name: 'Entrar na sua conta' })).toBeTruthy()
    expect(screen.queryByRole('banner')).toBeNull()
    expect(
      screen.queryByRole('navigation', { name: 'Navegação móvel inferior' })
    ).toBeNull()

    const username = screen.getByLabelText('Usuário') as HTMLInputElement
    const password = screen.getByLabelText('Senha') as HTMLInputElement
    expect(username.id).toBe('username')
    expect(username.name).toBe('username')
    expect(username.autocomplete).toBe('username')
    expect(password.id).toBe('password')
    expect(password.name).toBe('password')
    expect(password.type).toBe('password')
    expect(password.autocomplete).toBe('current-password')
  })

  it('submete username/password, persiste via serviço e navega para overview', async () => {
    mocks.login.mockResolvedValue({
      userId: '11111111-1111-1111-1111-111111111111',
      accessToken: 'jwt-opaco',
      type: 'Bearer',
    })
    const onAuthenticated = vi.fn()
    render(<LoginPage onAuthenticated={onAuthenticated} />)

    fireEvent.change(screen.getByLabelText('Usuário'), {
      target: { value: 'freitasjh' },
    })
    fireEvent.change(screen.getByLabelText('Senha'), {
      target: { value: 'senha-nunca-persistida' },
    })
    fireEvent.click(screen.getByRole('button', { name: 'Entrar' }))

    await waitFor(() => expect(onAuthenticated).toHaveBeenCalledTimes(1))
    expect(mocks.login).toHaveBeenCalledWith({
      username: 'freitasjh',
      password: 'senha-nunca-persistida',
    })
    expect(window.location.href).not.toContain('senha-nunca-persistida')
    expect(document.body.textContent).not.toContain('senha-nunca-persistida')
  })

  it('credencial errada mostra erro semântico sem expor a senha', async () => {
    mocks.login.mockRejectedValue(new LoginError('INVALID_CREDENTIALS'))
    render(<LoginPage />)

    fireEvent.change(screen.getByLabelText('Usuário'), { target: { value: 'freitasjh' } })
    fireEvent.change(screen.getByLabelText('Senha'), {
      target: { value: 'senha-nunca-persistida' },
    })
    fireEvent.click(screen.getByRole('button', { name: 'Entrar' }))

    const alert = await screen.findByRole('alert')
    expect(alert.textContent).toMatch(/usuário ou senha incorretos/i)
    expect(alert.textContent).not.toContain('senha-nunca-persistida')
    expect(alert.getAttribute('aria-live')).toBe('assertive')
    expect(screen.getByLabelText('Senha').getAttribute('aria-invalid')).toBe('true')
    expect(document.body.textContent).not.toContain('senha-nunca-persistida')
    expect(window.location.href).not.toContain('senha-nunca-persistida')
  })

  it('erro genérico também não carrega payload de credencial', async () => {
    mocks.login.mockRejectedValue(new Error('senha-nunca-persistida'))
    render(<LoginPage />)

    fireEvent.change(screen.getByLabelText('Usuário'), { target: { value: 'freitasjh' } })
    fireEvent.change(screen.getByLabelText('Senha'), { target: { value: 'senha-nunca-persistida' } })
    fireEvent.click(screen.getByRole('button', { name: 'Entrar' }))

    const alert = await screen.findByRole('alert')
    expect(alert.textContent).not.toContain('senha-nunca-persistida')
  })

  it('desabilita campos e botão e sinaliza aria-busy durante o envio', async () => {
    mocks.login.mockReturnValue(new Promise(() => {}))
    render(<LoginPage />)

    fireEvent.change(screen.getByLabelText('Usuário'), { target: { value: 'freitasjh' } })
    fireEvent.change(screen.getByLabelText('Senha'), { target: { value: 'opaca' } })
    fireEvent.click(screen.getByRole('button', { name: 'Entrar' }))

    await waitFor(() =>
      expect(screen.getByRole('button', { name: 'Entrando…' }).hasAttribute('disabled')).toBe(true)
    )
    expect((screen.getByLabelText('Usuário') as HTMLInputElement).disabled).toBe(true)
    expect((screen.getByLabelText('Senha') as HTMLInputElement).disabled).toBe(true)
    expect(document.querySelector('form')?.getAttribute('aria-busy')).toBe('true')
  })

  it('com token existente redireciona para overview sem exigir novo login', () => {
    mocks.getToken.mockReturnValue('jwt-valido')
    const onAuthenticated = vi.fn()
    render(<LoginPage onAuthenticated={onAuthenticated} />)

    expect(onAuthenticated).toHaveBeenCalledTimes(1)
    expect(mocks.login).not.toHaveBeenCalled()
  })
})
