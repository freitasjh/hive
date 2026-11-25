import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { PublicApiClient } from '@/services/admin.types'
import { emptyPublicClientForm } from './form'
import { PublicClientsView } from './PublicClientsView'
import type { OneShotSecret, PublicClientsActions, PublicClientsState } from '../state'

const client: PublicApiClient = {
  clientId: 'cli-123',
  label: 'Chat do site',
  tenantId: 'tenant-abc',
  grants: ['PUBLIC_CHAT_COMPLETE'],
  prompts: [{ promptCode: 'greeting', pinMode: 'PINNED', pinnedVersion: 3 }],
  token_ttl_seconds: 900,
  max_messages: 20,
  max_message_chars: 4000,
  max_total_chars: 20000,
  revoked_at: null,
  expires_at: null,
  previous_secret_expires_at: '2999-01-01T00:00:00Z',
}

function makeState(overrides: Partial<PublicClientsState> = {}): PublicClientsState {
  return {
    items: [client],
    total: 1,
    page: 0,
    size: 20,
    q: '',
    loading: false,
    error: null,
    actionError: null,
    notice: null,
    createOpen: false,
    form: emptyPublicClientForm,
    validationError: null,
    creating: false,
    revokeTarget: null,
    revoking: false,
    rotatingClientId: null,
    ...overrides,
  }
}

function makeActions(overrides: Partial<PublicClientsActions> = {}): PublicClientsActions {
  return {
    onQueryChange: vi.fn(),
    onPageChange: vi.fn(),
    onRetry: vi.fn(),
    onOpenCreate: vi.fn(),
    onCloseCreate: vi.fn(),
    onFormChange: vi.fn(),
    onToggleGrant: vi.fn(),
    onPromptChange: vi.fn(),
    onAddPrompt: vi.fn(),
    onRemovePrompt: vi.fn(),
    onCreate: vi.fn(),
    onRotate: vi.fn(),
    onAskRevoke: vi.fn(),
    onCancelRevoke: vi.fn(),
    onConfirmRevoke: vi.fn(),
    onDismissSecret: vi.fn(),
    ...overrides,
  }
}

describe('PublicClientsView', () => {
  beforeEach(() => {
    Object.defineProperty(navigator, 'clipboard', {
      configurable: true,
      value: { writeText: vi.fn().mockResolvedValue(undefined) },
    })
  })

  it('exibe link seguro para a documentação pública da API', () => {
    render(<PublicClientsView state={makeState()} actions={makeActions()} oneShot={null} />)
    const link = screen.getByTestId('public-docs-link')
    expect(link.tagName).toBe('A')
    expect(link.getAttribute('href')).toBe('/hive/scalar#public')
    expect(link.getAttribute('target')).toBe('_blank')
    expect(link.getAttribute('rel')).toBe('noopener noreferrer')
    expect(link.textContent).toContain('Ver documentação')
  })

  it('mostra loading com aria-busy e sem tabela', () => {
    render(<PublicClientsView state={makeState({ loading: true })} actions={makeActions()} oneShot={null} />)
    expect(screen.getByTestId('public-clients-loading').getAttribute('aria-busy')).toBe('true')
    expect(screen.queryByTestId('public-clients-table')).toBeNull()
  })

  it('mostra erro de carregamento com retry', () => {
    const actions = makeActions()
    render(
      <PublicClientsView
        state={makeState({ error: 'Você não tem permissão para gerenciar a API pública.' })}
        actions={actions}
        oneShot={null}
      />
    )
    expect(screen.getByTestId('public-clients-error').textContent).toContain('permissão')
    fireEvent.click(screen.getByRole('button', { name: /tentar novamente/i }))
    expect(actions.onRetry).toHaveBeenCalledTimes(1)
  })

  it('mostra empty state com CTA', () => {
    const actions = makeActions()
    render(
      <PublicClientsView
        state={makeState({ items: [], total: 0 })}
        actions={actions}
        oneShot={null}
      />
    )
    fireEvent.click(screen.getByTestId('empty-cta'))
    expect(actions.onOpenCreate).toHaveBeenCalledTimes(1)
  })

  it('lista sem secret mesmo se o item vier com campo vazado', () => {
    const leaked = { ...client, client_secret: 'sk-vazamento', secret_hash: 'hash' } as unknown as PublicApiClient
    render(<PublicClientsView state={makeState({ items: [leaked] })} actions={makeActions()} oneShot={null} />)

    const row = screen.getByTestId('row-cli-123')
    expect(row.textContent).toContain('Chat do site')
    expect(row.textContent).toContain('PUBLIC_CHAT_COMPLETE')
    expect(row.textContent).toContain('greeting')
    expect(row.textContent).toContain('PINNED')
    expect(row.textContent).not.toContain('sk-vazamento')
    expect(row.textContent).not.toContain('hash')
    expect(document.body.textContent).not.toContain('sk-vazamento')
  })

  it('mostra badge da janela do secret anterior apenas enquanto vigente', () => {
    const { unmount } = render(
      <PublicClientsView state={makeState()} actions={makeActions()} oneShot={null} />
    )
    expect(screen.getByText(/Secret anterior válido até/)).toBeTruthy()
    unmount()

    render(
      <PublicClientsView
        state={makeState({ items: [{ ...client, previous_secret_expires_at: '2000-01-01T00:00:00Z' }] })}
        actions={makeActions()}
        oneShot={null}
      />
    )
    expect(screen.queryByText(/Secret anterior válido até/)).toBeNull()
  })

  it('desabilita rotação e revogação de client revogado', () => {
    render(
      <PublicClientsView
        state={makeState({ items: [{ ...client, revoked_at: '2026-11-22T00:00:00Z' }] })}
        actions={makeActions()}
        oneShot={null}
      />
    )
    expect(screen.getByTestId('row-cli-123').textContent).toContain('Revogado')
    expect((screen.getByTestId('rotate-cli-123') as HTMLButtonElement).disabled).toBe(true)
    expect((screen.getByTestId('revoke-cli-123') as HTMLButtonElement).disabled).toBe(true)
  })

  it('router actions de rotação e paginação', () => {
    const actions = makeActions()
    render(<PublicClientsView state={makeState({ page: 1, total: 60 })} actions={actions} oneShot={null} />)
    fireEvent.click(screen.getByTestId('rotate-cli-123'))
    fireEvent.click(screen.getByTestId('next-page'))
    fireEvent.change(screen.getByTestId('search-q'), { target: { value: 'site' } })

    expect(actions.onRotate).toHaveBeenCalledWith(client)
    expect(actions.onPageChange).toHaveBeenCalledWith(2, 20)
    expect(actions.onQueryChange).toHaveBeenCalledWith('site')
  })

  it('exibe secret one-shot com aviso, cópia explícita e dispensa', async () => {
    const actions = makeActions()
    const oneShot: OneShotSecret = {
      secret: 'sk-one-shot-xyz',
      scope: 'create',
      clientId: 'cli-123',
      label: 'Chat do site',
      previousSecretExpiresAt: null,
    }
    render(<PublicClientsView state={makeState()} actions={actions} oneShot={oneShot} />)

    expect(screen.getByTestId('secret-value').textContent).toBe('sk-one-shot-xyz')
    expect(screen.getByTestId('secret-dialog').textContent).toContain('uma única vez')
    fireEvent.click(screen.getByTestId('secret-copy'))
    await waitFor(() => expect(navigator.clipboard.writeText).toHaveBeenCalledWith('sk-one-shot-xyz'))
    fireEvent.click(screen.getByTestId('secret-dismiss'))
    expect(actions.onDismissSecret).toHaveBeenCalledTimes(1)
  })

  it('abre formulário com campos rotulados, vínculos e validação', () => {
    const actions = makeActions()
    render(
      <PublicClientsView
        state={makeState({
          createOpen: true,
          validationError: 'Informe ao menos um grant.',
          form: {
            ...emptyPublicClientForm,
            prompts: [{ promptCode: 'greeting', pinMode: 'PINNED', pinnedVersion: 2 }],
          },
        })}
        actions={actions}
        oneShot={null}
      />
    )

    expect(document.querySelector('label[for="pc-label"]')).toBeTruthy()
    expect(screen.getByTestId('validation-error').textContent).toContain('grant')
    expect(screen.getByTestId('prompt-version-0')).toBeTruthy()
    fireEvent.click(screen.getByTestId('add-prompt'))
    fireEvent.click(screen.getByTestId('remove-prompt-0'))
    fireEvent.click(screen.getByTestId('create-submit'))
    expect(actions.onAddPrompt).toHaveBeenCalledTimes(1)
    expect(actions.onRemovePrompt).toHaveBeenCalledWith(0)
    expect(actions.onCreate).toHaveBeenCalledTimes(1)
  })

  it('seleciona grants por checkbox do catálogo, sem campo livre', () => {
    const actions = makeActions()
    const { unmount } = render(
      <PublicClientsView
        state={makeState({ createOpen: true, form: emptyPublicClientForm })}
        actions={actions}
        oneShot={null}
      />
    )

    expect(screen.queryByTestId('input-grants')).toBeNull()
    expect(document.querySelector('label[for="pc-grant-PUBLIC_CHAT_COMPLETE"]')).toBeTruthy()
    const checkbox = screen.getByRole('checkbox', { name: /Concluir chat público/i }) as HTMLButtonElement
    expect(checkbox.getAttribute('aria-checked')).toBe('false')
    fireEvent.click(checkbox)
    expect(actions.onToggleGrant).toHaveBeenCalledWith('PUBLIC_CHAT_COMPLETE', true)
    unmount()

    render(
      <PublicClientsView
        state={makeState({
          createOpen: true,
          form: { ...emptyPublicClientForm, grants: ['PUBLIC_CHAT_COMPLETE'] },
        })}
        actions={actions}
        oneShot={null}
      />
    )
    const checked = screen.getByRole('checkbox', { name: /Concluir chat público/i }) as HTMLButtonElement
    expect(checked.getAttribute('aria-checked')).toBe('true')
    fireEvent.click(checked)
    expect(actions.onToggleGrant).toHaveBeenCalledWith('PUBLIC_CHAT_COMPLETE', false)
  })

  it('pede confirmação antes de revogar', () => {
    const actions = makeActions()
    render(
      <PublicClientsView
        state={makeState({ revokeTarget: client, actionError: 'Falha ao revogar o client.' })}
        actions={actions}
        oneShot={null}
      />
    )
    expect(screen.getByTestId('revoke-confirm').textContent).toContain('Chat do site')
    fireEvent.click(screen.getByTestId('confirm-revoke'))
    expect(actions.onConfirmRevoke).toHaveBeenCalledTimes(1)
    expect(screen.getByTestId('public-clients-action-error').textContent).toContain('Falha ao revogar')
  })
})
