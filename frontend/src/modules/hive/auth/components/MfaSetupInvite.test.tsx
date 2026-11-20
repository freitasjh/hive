import { fireEvent, render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { MfaSetupInvite } from './MfaSetupInvite'

describe('MfaSetupInvite — convite opcional de MFA', () => {
  const originalLang = document.documentElement.lang

  afterEach(() => {
    document.documentElement.lang = originalLang
    localStorage.clear()
    vi.restoreAllMocks()
  })

  it('oferece configurar e pular sem dialog, overlay ou bloqueio de ponteiro', () => {
    render(<MfaSetupInvite onConfigure={vi.fn()} onSkip={vi.fn()} />)

    expect(screen.getByTestId('mfa-setup-invite')).toBeTruthy()
    expect(screen.getByRole('region', { name: /convite de configuração de mfa/i })).toBeTruthy()
    expect(screen.getByRole('button', { name: 'Configurar agora' })).toBeTruthy()
    expect(screen.getByRole('button', { name: 'Pular' })).toBeTruthy()
    expect(screen.queryByRole('dialog')).toBeNull()
    expect(document.body.style.pointerEvents).not.toBe('none')
    expect(screen.getByTestId('mfa-setup-invite').className).not.toContain('inset-0')
  })

  it('Configurar agora dispara apenas onConfigure', () => {
    const onConfigure = vi.fn()
    const onSkip = vi.fn()
    render(<MfaSetupInvite onConfigure={onConfigure} onSkip={onSkip} />)

    fireEvent.click(screen.getByRole('button', { name: 'Configurar agora' }))

    expect(onConfigure).toHaveBeenCalledTimes(1)
    expect(onSkip).not.toHaveBeenCalled()
  })

  it('Pular dispensa sem bloquear e sem persistir estado', () => {
    const onSkip = vi.fn()
    render(<MfaSetupInvite onConfigure={vi.fn()} onSkip={onSkip} />)

    fireEvent.click(screen.getByRole('button', { name: 'Pular' }))

    expect(onSkip).toHaveBeenCalledTimes(1)
    expect(localStorage.length).toBe(0)
    expect(sessionStorage.length).toBe(0)
  })

  it('mantém rótulos en-US quando o idioma é inglês', () => {
    document.documentElement.lang = 'en'
    render(<MfaSetupInvite onConfigure={vi.fn()} onSkip={vi.fn()} />)

    expect(screen.getByRole('button', { name: 'Set up now' })).toBeTruthy()
    expect(screen.getByRole('button', { name: 'Skip' })).toBeTruthy()
  })
})
