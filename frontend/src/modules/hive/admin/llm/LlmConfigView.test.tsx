import { fireEvent, render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { LlmConfigView } from './LlmConfigView'
import type { LlmConfigActions, LlmConfigState } from '../state'

function makeState(overrides: Partial<LlmConfigState> = {}): LlmConfigState {
  return {
    provider: null,
    model: '',
    configured: false,
    loading: false,
    saving: false,
    testing: false,
    loadError: null,
    saveError: null,
    testResult: null,
    remoteModels: [],
    remoteLoading: false,
    remoteError: null,
    notice: null,
    ...overrides,
  }
}

function makeActions(overrides: Partial<LlmConfigActions> = {}): LlmConfigActions {
  return {
    onProviderChange: vi.fn(),
    onModelChange: vi.fn(),
    onSave: vi.fn(),
    onTest: vi.fn(),
    onRefreshModels: vi.fn(),
    onRetry: vi.fn(),
    ...overrides,
  }
}

describe('LlmConfigView', () => {
  it('mostra loading e erro com retry', () => {
    const actions = makeActions()
    render(<LlmConfigView state={makeState({ loading: true })} actions={actions} />)
    expect(screen.getByTestId('llm-loading')).toBeTruthy()

    render(<LlmConfigView state={makeState({ loadError: 'Falha ao carregar' })} actions={actions} />)
    expect(screen.getByText('Falha ao carregar')).toBeTruthy()
  })

  it('desabilita salvar e testar sem provider/modelo', () => {
    render(<LlmConfigView state={makeState()} actions={makeActions()} />)

    expect((screen.getByTestId('llm-save') as HTMLButtonElement).disabled).toBe(true)
    expect((screen.getByTestId('llm-test') as HTMLButtonElement).disabled).toBe(true)
  })

  it('carrega config existente e permite salvar/testar', () => {
    const actions = makeActions()
    render(
      <LlmConfigView
        state={makeState({ provider: 'OPENAI', model: 'gpt-4o-mini', configured: true })}
        actions={actions}
      />
    )

    const save = screen.getByTestId('llm-save') as HTMLButtonElement
    const test = screen.getByTestId('llm-test') as HTMLButtonElement
    expect(save.disabled).toBe(false)
    expect(test.disabled).toBe(false)
    fireEvent.click(save)
    fireEvent.click(test)
    expect(actions.onSave).toHaveBeenCalledTimes(1)
    expect(actions.onTest).toHaveBeenCalledTimes(1)
  })

  it('mostra modelos remotos quando discovery responde', () => {
    render(
      <LlmConfigView
        state={makeState({
          provider: 'OPENCODE_ZEN',
          model: 'claude-sonnet-4-5',
          remoteModels: ['claude-sonnet-4-5', 'mimo-v2.5'],
        })}
        actions={makeActions()}
      />
    )

    expect(screen.getByTestId('llm-model-remote')).toBeTruthy()
    expect(screen.queryByTestId('llm-model-manual')).toBeNull()
  })

  it('cai para entrada manual com aviso quando discovery falha', () => {
    render(
      <LlmConfigView
        state={makeState({
          provider: 'OPENCODE_GO',
          model: 'mimo-v2.5',
          remoteError: 'Discovery indisponível.',
          remoteModels: ['mimo-v2.5'],
        })}
        actions={makeActions()}
      />
    )

    expect(screen.getByTestId('llm-model-error')).toBeTruthy()
    expect(screen.getByTestId('llm-model-manual')).toBeTruthy()
  })

  it('exibe veredito do teste de conexão e erro de salvamento', () => {
    render(
      <LlmConfigView
        state={makeState({
          provider: 'OPENAI',
          model: 'gpt-4o-mini',
          configured: true,
          saveError: 'Não foi possível salvar',
          testResult: { ok: false, provider: 'OPENAI', model: 'gpt-4o-mini', latencyMs: 0, reason: 'chave inválida' },
        })}
        actions={makeActions()}
      />
    )

    expect(screen.getByText('Não foi possível salvar')).toBeTruthy()
    expect(screen.getByTestId('llm-test-result').textContent).toContain('chave inválida')
  })

  it('sinaliza remoteLoading sem travar o save em loop', () => {
    render(
      <LlmConfigView
        state={makeState({ provider: 'OPENCODE_GO', model: 'mimo-v2.5', remoteLoading: true })}
        actions={makeActions()}
      />
    )

    expect(screen.getByTestId('llm-remote-loading')).toBeTruthy()
    expect((screen.getByTestId('llm-save') as HTMLButtonElement).disabled).toBe(true)
  })
})
