import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '@/services/api'
import type { PublicApiClient } from '@/services/admin.types'
import { PublicClientsContainer } from './PublicClientsContainer'

const mocks = vi.hoisted(() => ({
  list: vi.fn(),
  get: vi.fn(),
  create: vi.fn(),
  rotate: vi.fn(),
  revoke: vi.fn(),
  updateGrants: vi.fn(),
  updatePrompts: vi.fn(),
}))

vi.mock('@/services/public-api-admin.service', () => ({
  publicApiAdminService: {
    list: mocks.list,
    get: mocks.get,
    create: mocks.create,
    rotate: mocks.rotate,
    revoke: mocks.revoke,
    updateGrants: mocks.updateGrants,
    updatePrompts: mocks.updatePrompts,
  },
}))

const client: PublicApiClient = {
  clientId: 'cli-123',
  label: 'Chat do site',
  tenantId: 'tenant-abc',
  grants: ['PUBLIC_CHAT_COMPLETE'],
  prompts: [],
  token_ttl_seconds: 900,
  max_messages: 20,
  max_message_chars: 4000,
  max_total_chars: 20000,
  revoked_at: null,
  expires_at: null,
  previous_secret_expires_at: null,
}

const page = (content: PublicApiClient[] = [client]) => ({
  content,
  page: 0,
  size: 20,
  totalElements: content.length,
  totalPages: 1,
})

describe('PublicClientsContainer', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    localStorage.clear()
    mocks.list.mockResolvedValue(page())
    mocks.create.mockResolvedValue({ client, secret: 'sk-one-shot' })
    mocks.rotate.mockResolvedValue({
      clientId: 'cli-123',
      secret: 'sk-rotacionado',
      previousSecretExpiresAt: '2999-01-01T00:00:00Z',
    })
    mocks.revoke.mockResolvedValue(undefined)
  })

  it('carrega lista paginada', async () => {
    render(<PublicClientsContainer />)
    await waitFor(() => expect(screen.getByTestId('row-cli-123')).toBeTruthy())
    expect(mocks.list).toHaveBeenCalledWith(0, 20)
  })

  it('403 e 401 têm mensagens próprias; 500 usa detail redacionado', async () => {
    mocks.list.mockRejectedValueOnce(new ApiError('sem AGENT_MANAGE', 403))
    const { unmount } = render(<PublicClientsContainer />)
    await waitFor(() => expect(screen.getByTestId('public-clients-error').textContent).toContain('AGENT_MANAGE'))
    unmount()

    mocks.list.mockRejectedValueOnce(new ApiError('expirado', 401))
    const second = render(<PublicClientsContainer />)
    await waitFor(() => expect(screen.getByTestId('public-clients-error').textContent).toContain('sessão expirou'))
    second.unmount()

    mocks.list.mockRejectedValueOnce(new ApiError('erro interno', 500, undefined, 'erro interno'))
    render(<PublicClientsContainer />)
    await waitFor(() => expect(screen.getByTestId('public-clients-error').textContent).toContain('erro interno'))
  })

  it('retry recarrega a lista', async () => {
    mocks.list.mockRejectedValueOnce(new Error('rede'))
    render(<PublicClientsContainer />)
    await waitFor(() => expect(screen.getByTestId('public-clients-error')).toBeTruthy())
    fireEvent.click(screen.getByRole('button', { name: /tentar novamente/i }))
    await waitFor(() => expect(mocks.list).toHaveBeenCalledTimes(2))
  })

  it('cria sem campos do servidor e mantém o secret apenas no diálogo one-shot', async () => {
    mocks.list.mockResolvedValueOnce(page([]))
    render(<PublicClientsContainer />)
    await waitFor(() => expect(screen.getByTestId('public-clients-empty')).toBeTruthy())

    fireEvent.click(screen.getByTestId('empty-cta'))
    fireEvent.change(screen.getByTestId('input-label'), { target: { value: 'Chat do site' } })
    fireEvent.click(screen.getByTestId('checkbox-grant-PUBLIC_CHAT_COMPLETE'))
    fireEvent.click(screen.getByTestId('create-submit'))

    await waitFor(() => expect(mocks.create).toHaveBeenCalledTimes(1))
    expect(mocks.create.mock.calls[0][0]).toEqual({
      label: 'Chat do site',
      grants: ['PUBLIC_CHAT_COMPLETE'],
      prompts: [],
    })
    expect(mocks.create.mock.calls[0][0]).not.toHaveProperty('clientId')
    expect(mocks.create.mock.calls[0][0]).not.toHaveProperty('tenantId')
    expect(mocks.create.mock.calls[0][0]).not.toHaveProperty('rate_limit_per_minute')

    await waitFor(() => expect(screen.getByTestId('secret-value').textContent).toBe('sk-one-shot'))
    expect(screen.getByTestId('public-clients-notice').textContent).toContain('Client criado')
    expect(screen.getByTestId('public-clients-table').textContent).not.toContain('sk-one-shot')

    fireEvent.click(screen.getByTestId('secret-dismiss'))
    await waitFor(() => expect(screen.queryByTestId('secret-dialog')).toBeNull())
    expect(document.body.textContent).not.toContain('sk-one-shot')
  })

  it('valida formulário antes de chamar a API', async () => {
    render(<PublicClientsContainer />)
    await waitFor(() => expect(screen.getByTestId('row-cli-123')).toBeTruthy())
    fireEvent.click(screen.getByTestId('new-client-btn'))
    fireEvent.click(screen.getByTestId('create-submit'))
    await waitFor(() => expect(screen.getByTestId('validation-error')).toBeTruthy())
    expect(mocks.create).not.toHaveBeenCalled()
  })

  it('desseleciona grant e volta a exigir ao menos um', async () => {
    mocks.list.mockResolvedValueOnce(page([]))
    render(<PublicClientsContainer />)
    await waitFor(() => expect(screen.getByTestId('public-clients-empty')).toBeTruthy())

    fireEvent.click(screen.getByTestId('empty-cta'))
    fireEvent.change(screen.getByTestId('input-label'), { target: { value: 'Chat do site' } })
    const checkbox = screen.getByTestId('checkbox-grant-PUBLIC_CHAT_COMPLETE')
    fireEvent.click(checkbox)
    expect(checkbox.getAttribute('aria-checked')).toBe('true')
    fireEvent.click(checkbox)
    expect(checkbox.getAttribute('aria-checked')).toBe('false')
    fireEvent.click(screen.getByTestId('create-submit'))
    await waitFor(() => expect(screen.getByTestId('validation-error').textContent).toContain('grant'))
    expect(mocks.create).not.toHaveBeenCalled()
  })

  it('rotaciona e mostra a janela do secret anterior', async () => {
    render(<PublicClientsContainer />)
    await waitFor(() => expect(screen.getByTestId('rotate-cli-123')).toBeTruthy())
    fireEvent.click(screen.getByTestId('rotate-cli-123'))
    await waitFor(() => expect(mocks.rotate).toHaveBeenCalledWith('cli-123'))
    expect(screen.getByTestId('secret-value').textContent).toBe('sk-rotacionado')
    expect(screen.getByTestId('secret-dialog').textContent).toContain('Secret anterior')
  })

  it('revoga somente após confirmação', async () => {
    render(<PublicClientsContainer />)
    await waitFor(() => expect(screen.getByTestId('revoke-cli-123')).toBeTruthy())

    fireEvent.click(screen.getByTestId('revoke-cli-123'))
    fireEvent.click(screen.getByRole('button', { name: 'Cancelar' }))
    expect(mocks.revoke).not.toHaveBeenCalled()

    fireEvent.click(screen.getByTestId('revoke-cli-123'))
    fireEvent.click(screen.getByTestId('confirm-revoke'))
    await waitFor(() => expect(mocks.revoke).toHaveBeenCalledWith('cli-123'))
    await waitFor(() => expect(screen.getByTestId('public-clients-notice').textContent).toContain('revogado'))
  })

  it('pagina via service com page/size', async () => {
    mocks.list.mockResolvedValue({ ...page(), totalElements: 60, totalPages: 3 })
    render(<PublicClientsContainer />)
    await waitFor(() => expect(screen.getByTestId('row-cli-123')).toBeTruthy())
    fireEvent.click(screen.getByTestId('next-page'))
    await waitFor(() => expect(mocks.list).toHaveBeenCalledWith(1, 20))
  })
})
