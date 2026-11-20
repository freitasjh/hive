import { act, renderHook, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { loginService } from '@/services/auth.service'
import { useLoginController } from './use-login-controller'

const CHALLENGE = 'e8a2658f-8830-4bcd-aa4c-a1f260ae59fd'
const CHALLENGE_TENANT = 'bbbbbbbb-cccc-4ddd-8eee-ffffffffffff'

describe('useLoginController — desafio MFA', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
  })

  it('desafio chama onMfaRequired com challengeId/expiresIn/tenantId e NÃO chama onAuthenticated', async () => {
    vi.spyOn(loginService, 'login').mockResolvedValue({
      kind: 'mfa-required',
      challengeId: CHALLENGE,
      expiresIn: 120,
      tenantId: CHALLENGE_TENANT,
    })
    const onAuthenticated = vi.fn()
    const onMfaRequired = vi.fn()

    const { result } = renderHook(() =>
      useLoginController(onAuthenticated, onMfaRequired)
    )

    await act(async () => {
      await result.current.submit({ username: 'teste.login.mfa', password: 'it-secret-pw' })
    })

    expect(onMfaRequired).toHaveBeenCalledTimes(1)
    expect(onMfaRequired).toHaveBeenCalledWith({
      challengeId: CHALLENGE,
      expiresIn: 120,
      tenantId: CHALLENGE_TENANT,
    })
    expect(onAuthenticated).not.toHaveBeenCalled()
    expect(result.current.status).toBe('success')
    expect(result.current.errorKey).toBeNull()
  })

  it('sucesso sem MFA continua chamando apenas onAuthenticated, com flag false', async () => {
    vi.spyOn(loginService, 'login').mockResolvedValue({
      kind: 'authenticated',
      userId: 'u',
      accessToken: 'jwt',
      type: 'Bearer',
      mfaSetupAvailable: false,
    })
    const onAuthenticated = vi.fn()
    const onMfaRequired = vi.fn()

    const { result } = renderHook(() =>
      useLoginController(onAuthenticated, onMfaRequired)
    )

    await act(async () => {
      await result.current.submit({ username: 'u', password: 'p' })
    })

    expect(onAuthenticated).toHaveBeenCalledTimes(1)
    expect(onAuthenticated).toHaveBeenCalledWith(false)
    expect(onMfaRequired).not.toHaveBeenCalled()
  })

  it('repassa mfaSetupAvailable=true ao onAuthenticated (onboarding opcional)', async () => {
    vi.spyOn(loginService, 'login').mockResolvedValue({
      kind: 'authenticated',
      userId: 'u',
      accessToken: 'jwt',
      type: 'Bearer',
      mfaSetupAvailable: true,
    })
    const onAuthenticated = vi.fn()
    const onMfaRequired = vi.fn()

    const { result } = renderHook(() =>
      useLoginController(onAuthenticated, onMfaRequired)
    )

    await act(async () => {
      await result.current.submit({ username: 'u', password: 'p' })
    })

    expect(onAuthenticated).toHaveBeenCalledTimes(1)
    expect(onAuthenticated).toHaveBeenCalledWith(true)
    expect(onMfaRequired).not.toHaveBeenCalled()
  })

  it('não dispara callback de MFA quando o login falha', async () => {
    vi.spyOn(loginService, 'login').mockRejectedValue(new Error('boom'))
    const onAuthenticated = vi.fn()
    const onMfaRequired = vi.fn()

    const { result } = renderHook(() =>
      useLoginController(onAuthenticated, onMfaRequired)
    )

    await act(async () => {
      await result.current.submit({ username: 'u', password: 'p' })
    })

    await waitFor(() => expect(result.current.status).toBe('error'))
    expect(onMfaRequired).not.toHaveBeenCalled()
    expect(onAuthenticated).not.toHaveBeenCalled()
  })
})
