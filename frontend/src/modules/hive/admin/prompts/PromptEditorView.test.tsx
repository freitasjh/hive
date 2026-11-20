import { fireEvent, render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { PromptEditorView } from './PromptEditorView'
import type { PromptEditorActions, PromptEditorState } from '../state'

function makeState(overrides: Partial<PromptEditorState> = {}): PromptEditorState {
  return {
    isEdit: false,
    promptId: null,
    name: '',
    code: '',
    description: '',
    tags: [],
    tagsInput: '',
    template: '',
    status: 'DRAFT',
    temperature: 0.7,
    topP: 0.9,
    maxTokens: 2048,
    loading: false,
    saving: false,
    error: null,
    validationError: null,
    ...overrides,
  }
}

function makeActions(overrides: Partial<PromptEditorActions> = {}): PromptEditorActions {
  return {
    onNameChange: vi.fn(),
    onCodeChange: vi.fn(),
    onDescriptionChange: vi.fn(),
    onTagsInputChange: vi.fn(),
    onAddTag: vi.fn(),
    onRemoveTag: vi.fn(),
    onTemplateChange: vi.fn(),
    onStatusChange: vi.fn(),
    onTemperatureChange: vi.fn(),
    onTopPChange: vi.fn(),
    onMaxTokensChange: vi.fn(),
    onSave: vi.fn(),
    onBack: vi.fn(),
    onRetry: vi.fn(),
    ...overrides,
  }
}

describe('PromptEditorView', () => {
  it('mostra loading e erro de carregamento com retry', () => {
    const actions = makeActions()
    render(<PromptEditorView state={makeState({ loading: true })} actions={actions} />)
    expect(screen.getByTestId('editor-loading')).toBeTruthy()

    render(<PromptEditorView state={makeState({ error: 'Falha ao carregar prompt' })} actions={actions} />)
    expect(screen.getByText('Falha ao carregar prompt')).toBeTruthy()
  })

  it('valida campos obrigatórios antes de salvar', () => {
    const actions = makeActions()
    render(<PromptEditorView state={makeState({ validationError: 'Preencha nome, code e template' })} actions={actions} />)

    expect(screen.getByTestId('editor-validation')).toBeTruthy()
    fireEvent.click(screen.getByTestId('save-btn'))
    expect(actions.onSave).toHaveBeenCalledTimes(1)
  })

  it('mostra a validação no topo do editor, visível sem rolagem e sem duplicata no rodapé', () => {
    const message = 'Preencha nome, code e template'
    render(<PromptEditorView state={makeState({ validationError: message })} actions={makeActions()} />)

    const banner = screen.getByTestId('editor-validation')
    expect(banner.getAttribute('role')).toBe('alert')
    expect(banner.textContent).toContain(message)
    // banner vem antes dos campos obrigatórios no DOM (topo, sem rolagem)
    expect(banner.compareDocumentPosition(screen.getByTestId('input-name')) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy()
    // mensagem não se repete no rodapé
    expect(screen.queryByTestId('editor-validation-error')).toBeNull()
    expect(screen.getAllByRole('alert')).toHaveLength(1)
  })

  it('não oferece retry para erro de validação', () => {
    render(
      <PromptEditorView
        state={makeState({ validationError: 'Preencha nome, code e template' })}
        actions={makeActions()}
      />
    )
    expect(screen.queryByRole('button', { name: /tentar novamente/i })).toBeNull()
  })

  it('dá prioridade ao erro de API no banner do topo e mantém retry', () => {
    const actions = makeActions()
    render(
      <PromptEditorView
        state={makeState({ error: 'Conflito ao salvar (409)', validationError: 'Preencha nome, code e template' })}
        actions={actions}
      />
    )
    const banner = screen.getByTestId('editor-error')
    expect(banner.textContent).toContain('Conflito ao salvar (409)')
    expect(banner.textContent).not.toContain('Preencha nome, code e template')
    fireEvent.click(screen.getByRole('button', { name: /tentar novamente/i }))
    expect(actions.onRetry).toHaveBeenCalledTimes(1)
  })

  it('desabilita code quando editando e mostra tags com remoção', () => {
    const actions = makeActions()
    render(
      <PromptEditorView
        state={makeState({
          isEdit: true,
          promptId: '11111111-1111-1111-1111-111111111111',
          name: 'Saudação',
          code: 'greeting-v1',
          template: 'Olá {{nome}}',
          tags: ['chat'],
        })}
        actions={actions}
      />
    )

    expect((screen.getByTestId('input-code') as HTMLInputElement).disabled).toBe(true)
    expect(screen.getByText('chat')).toBeTruthy()
    fireEvent.click(screen.getByTestId('remove-tag-0'))
    expect(actions.onRemoveTag).toHaveBeenCalledWith(0)
  })

  it('adiciona tag por Enter e dispara mudanças de campo', () => {
    const actions = makeActions()
    render(<PromptEditorView state={makeState({ tagsInput: 'boas-vindas' })} actions={actions} />)

    fireEvent.keyDown(screen.getByTestId('input-tag'), { key: 'Enter' })
    expect(actions.onAddTag).toHaveBeenCalledTimes(1)
    fireEvent.change(screen.getByTestId('input-name'), { target: { value: 'Novo nome' } })
    expect(actions.onNameChange).toHaveBeenCalledWith('Novo nome')
    fireEvent.change(screen.getByTestId('select-status'), { target: { value: 'ACTIVE' } })
    expect(actions.onStatusChange).toHaveBeenCalledWith('ACTIVE')
  })

  it('faz preview local de variáveis sem chamar LLM', () => {
    render(<PromptEditorView state={makeState({ template: 'Olá {{nome}}, bem-vindo ao {{produto}}' })} actions={makeActions()} />)

    expect(screen.getByTestId('playground-preview').textContent).toContain('{{nome}}')
    expect(screen.getByTestId('var-input-nome')).toBeTruthy()
    expect(screen.getByTestId('var-input-produto')).toBeTruthy()
  })

  it('associa id, name e label a todos os campos editáveis', () => {
    render(<PromptEditorView state={makeState({ template: 'Olá {{nome}}' })} actions={makeActions()} />)

    const fields: Array<[string, string]> = [
      ['prompt-name', 'name'],
      ['prompt-code', 'code'],
      ['prompt-desc', 'description'],
      ['prompt-tag', 'tags'],
      ['prompt-status', 'status'],
      ['input-template', 'template'],
      ['param-temperature', 'temperature'],
      ['param-topP', 'topP'],
      ['param-maxTokens', 'maxTokens'],
      ['var-nome', 'var-nome'],
    ]

    for (const [id, name] of fields) {
      const field = document.getElementById(id)
      expect(field, `campo #${id} ausente`).toBeTruthy()
      expect(field!.getAttribute('name')).toBe(name)
      expect(document.querySelector(`label[for="${id}"]`), `label de #${id} ausente`).toBeTruthy()
    }
    expect(document.querySelector('[name="template"]')).toBe(document.getElementById('input-template'))
  })

  it('volta para a listagem', () => {
    const actions = makeActions()
    render(<PromptEditorView state={makeState()} actions={actions} />)

    fireEvent.click(screen.getByTestId('back-btn'))
    expect(actions.onBack).toHaveBeenCalledTimes(1)
  })
})
