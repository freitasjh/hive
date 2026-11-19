import { fireEvent, render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { PromptsView } from './PromptsView'
import type { PromptsActions, PromptsState } from '../state'

const prompt = {
  id: '11111111-1111-1111-1111-111111111111',
  name: 'Saudação',
  code: 'greeting-v1',
  description: null,
  tags: ['chat', 'boas-vindas'],
  template: 'Olá {{nome}}',
  params: null,
  inferenceParams: null,
  status: 'ACTIVE' as const,
  version: 1,
  createAt: '2026-01-01T00:00:00Z',
  updateAt: '2026-01-01T00:00:00Z',
}

function makeState(overrides: Partial<PromptsState> = {}): PromptsState {
  return {
    items: [prompt],
    total: 1,
    page: 0,
    size: 20,
    q: '',
    status: null,
    loading: false,
    error: null,
    actionError: null,
    deleteTarget: null,
    deleting: false,
    ...overrides,
  }
}

function makeActions(overrides: Partial<PromptsActions> = {}): PromptsActions {
  return {
    onSearch: vi.fn(),
    onQueryChange: vi.fn(),
    onStatusChange: vi.fn(),
    onPageChange: vi.fn(),
    onNew: vi.fn(),
    onEdit: vi.fn(),
    onAskDelete: vi.fn(),
    onCancelDelete: vi.fn(),
    onConfirmDelete: vi.fn(),
    onRetry: vi.fn(),
    ...overrides,
  }
}

describe('PromptsView', () => {
  it('mostra esqueletos no loading', () => {
    render(<PromptsView state={makeState({ loading: true })} actions={makeActions()} />)
    expect(screen.getByTestId('prompts-loading')).toBeTruthy()
    expect(screen.queryByTestId('prompts-table')).toBeNull()
  })

  it('mostra estado vazio com CTA quando não há prompts', () => {
    const actions = makeActions()
    render(<PromptsView state={makeState({ items: [], total: 0 })} actions={actions} />)

    expect(screen.getByTestId('prompts-empty')).toBeTruthy()
    fireEvent.click(screen.getByTestId('empty-cta'))
    expect(actions.onNew).toHaveBeenCalledTimes(1)
  })

  it('mostra erro de carregamento com retry', () => {
    const actions = makeActions()
    render(<PromptsView state={makeState({ error: 'Falha ao carregar prompts' })} actions={actions} />)

    expect(screen.getByText('Falha ao carregar prompts')).toBeTruthy()
    fireEvent.click(screen.getByRole('button', { name: /tentar novamente/i }))
    expect(actions.onRetry).toHaveBeenCalledTimes(1)
  })

  it('lista prompts com status e tags e dispara edição', () => {
    const actions = makeActions()
    render(<PromptsView state={makeState()} actions={actions} />)

    const table = screen.getByTestId('prompts-table')
    expect(table.textContent).toContain('Saudação')
    expect(table.textContent).toContain('greeting-v1')
    expect(table.textContent).toContain('ACTIVE')
    fireEvent.click(screen.getByTestId(`edit-${prompt.id}`))
    expect(actions.onEdit).toHaveBeenCalledWith(prompt.id)
  })

  it('busca ao pressionar Enter e troca de página', () => {
    const actions = makeActions()
    render(<PromptsView state={makeState({ total: 45, size: 20 })} actions={actions} />)

    const search = screen.getByTestId('search-q') as HTMLInputElement
    fireEvent.change(search, { target: { value: 'saud' } })
    fireEvent.keyDown(search, { key: 'Enter' })
    expect(actions.onQueryChange).toHaveBeenCalledWith('saud')
    expect(actions.onSearch).toHaveBeenCalledTimes(1)

    fireEvent.click(screen.getByTestId('next-page'))
    expect(actions.onPageChange).toHaveBeenCalledWith(1, 20)
  })

  it('confirma exclusão e exibe aviso de prompt em uso (409)', () => {
    const actions = makeActions()
    render(
      <PromptsView
        state={makeState({
          deleteTarget: prompt,
          actionError: 'Prompt em uso por uma sessão de chat, não pode ser removido.',
        })}
        actions={actions}
      />
    )

    fireEvent.click(screen.getByTestId('confirm-delete'))
    expect(actions.onConfirmDelete).toHaveBeenCalledTimes(1)
    expect(screen.getByText(/Prompt em uso/)).toBeTruthy()
  })
})
