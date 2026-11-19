import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { CredentialsContainer } from './credentials/CredentialsContainer'
import { LlmConfigContainer } from './llm/LlmConfigContainer'
import { PromptsContainer } from './prompts/PromptsContainer'
import { PromptEditorContainer } from './prompts/PromptEditorContainer'

const mocks = vi.hoisted(() => ({
  listCredentials: vi.fn(),
  upsertCredential: vi.fn(),
  deleteCredential: vi.fn(),
  getLlmConfig: vi.fn(),
  saveLlmConfig: vi.fn(),
  validate: vi.fn(),
  listProviderModels: vi.fn(),
  promptList: vi.fn(),
  promptGetById: vi.fn(),
  promptCreate: vi.fn(),
  promptUpdate: vi.fn(),
}))

vi.mock('@/services/agent-admin.service', () => ({
  agentAdminService: {
    listCredentials: mocks.listCredentials,
    upsertCredential: mocks.upsertCredential,
    deleteCredential: mocks.deleteCredential,
    getLlmConfig: mocks.getLlmConfig,
    saveLlmConfig: mocks.saveLlmConfig,
    validate: mocks.validate,
    listProviderModels: mocks.listProviderModels,
  },
}))

vi.mock('@/services/prompt.service', () => ({
  promptService: {
    list: mocks.promptList,
    getById: mocks.promptGetById,
    create: mocks.promptCreate,
    update: mocks.promptUpdate,
  },
}))

const promptFixture = {
  id: '11111111-1111-1111-1111-111111111111',
  name: 'Saudação',
  code: 'greeting-v1',
  description: null,
  tags: ['chat'],
  template: 'Olá {{nome}}',
  params: null,
  inferenceParams: { temperature: 0.7, topP: 0.9, maxTokens: 2048 },
  status: 'ACTIVE' as const,
  version: 1,
  createAt: '2026-01-01T00:00:00Z',
  updateAt: '2026-01-01T00:00:00Z',
}

describe('containers admin', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.listCredentials.mockResolvedValue([
      { provider: 'OPENAI', configured: true, last4: '1234', baseUrl: null },
    ])
    mocks.getLlmConfig.mockResolvedValue(null)
    mocks.listProviderModels.mockResolvedValue({ models: [], filteredOut: [], cachedAt: '' })
    mocks.promptList.mockResolvedValue({ content: [], totalElements: 0, totalPages: 1, number: 0, size: 20 })
    mocks.promptGetById.mockResolvedValue(promptFixture)
    mocks.promptCreate.mockResolvedValue(promptFixture)
    mocks.promptUpdate.mockResolvedValue(promptFixture)
  })

  it('CredentialsContainer carrega status e faz upsert sem expor chave', async () => {
    mocks.upsertCredential.mockResolvedValue({ provider: 'OPENAI', configured: true, last4: '5678' })
    render(<CredentialsContainer />)

    await waitFor(() => expect(screen.getByText('••••1234')).toBeTruthy())
    fireEvent.click(screen.getByTestId('cred-edit-OPENAI'))
    const input = screen.getByTestId('cred-key-input') as HTMLInputElement
    fireEvent.change(input, { target: { value: 'sk-nova-chave-super-secreta' } })

    expect(input.type).toBe('password')
    expect(input.value).toBe('sk-nova-chave-super-secreta')

    fireEvent.click(screen.getByTestId('cred-save-btn'))

    await waitFor(() => expect(mocks.upsertCredential).toHaveBeenCalledTimes(1))
    expect(mocks.upsertCredential.mock.calls[0][1].apiKey).toBe('sk-nova-chave-super-secreta')
    await waitFor(() => expect(screen.queryByTestId('cred-key-input')).toBeNull())
    expect(document.body.textContent).not.toContain('sk-nova-chave-super-secreta')
  })

  it('CredentialsContainer trata falha de carregamento com retry', async () => {
    mocks.listCredentials.mockRejectedValueOnce(new Error('rede indisponível'))
    render(<CredentialsContainer />)

    await waitFor(() => expect(screen.getByTestId('credentials-error')).toBeTruthy())
    fireEvent.click(screen.getByRole('button', { name: /tentar novamente/i }))
    await waitFor(() => expect(mocks.listCredentials).toHaveBeenCalledTimes(2))
  })

  it('LlmConfigContainer carrega config e salva provider/modelo', async () => {
    mocks.getLlmConfig.mockResolvedValue({
      id: '1',
      tenantId: 't',
      provider: 'OPENAI',
      model: 'gpt-4o-mini',
      createAt: '',
      updateAt: '',
    })
    mocks.saveLlmConfig.mockResolvedValue({
      id: '1',
      tenantId: 't',
      provider: 'OPENAI',
      model: 'gpt-4o-mini',
      createAt: '',
      updateAt: '',
    })
    render(<LlmConfigContainer />)

    await waitFor(() => expect((screen.getByTestId('llm-save') as HTMLButtonElement).disabled).toBe(false))
    fireEvent.click(screen.getByTestId('llm-save'))
    await waitFor(() => expect(mocks.saveLlmConfig).toHaveBeenCalledWith({ provider: 'OPENAI', model: 'gpt-4o-mini' }))
  })

  it('LlmConfigContainer testa conexão com provider/modelo atuais', async () => {
    mocks.getLlmConfig.mockResolvedValue({
      id: '1',
      tenantId: 't',
      provider: 'OLLAMA',
      model: 'llama3.2',
      createAt: '',
      updateAt: '',
    })
    mocks.validate.mockResolvedValue({ ok: true, provider: 'OLLAMA', model: 'llama3.2', latencyMs: 42, reason: null })
    render(<LlmConfigContainer />)

    await waitFor(() => expect((screen.getByTestId('llm-test') as HTMLButtonElement).disabled).toBe(false))
    fireEvent.click(screen.getByTestId('llm-test'))
    await waitFor(() => expect(mocks.validate).toHaveBeenCalledWith({ provider: 'OLLAMA', model: 'llama3.2' }))
    await waitFor(() => expect(screen.getByTestId('llm-test-result').textContent).toContain('42'))
  })

  it('LlmConfigContainer descarta resposta remota obsoleta quando provider muda', async () => {
    Element.prototype.scrollIntoView = vi.fn()
    mocks.getLlmConfig.mockResolvedValue({
      id: '1', tenantId: 't', provider: 'OPENCODE_GO', model: 'mimo-v2.5', createAt: '', updateAt: '',
    })
    let rejectGo!: (reason: unknown) => void
    mocks.listProviderModels
      .mockImplementationOnce(
        () => new Promise((_resolve, reject) => { rejectGo = reject })
      )
      .mockImplementationOnce(() =>
        Promise.resolve({ models: ['zen-model'], filteredOut: [], cachedAt: '2026-01-01T00:00:00Z' })
      )

    render(<LlmConfigContainer />)
    await waitFor(() => expect(mocks.listProviderModels).toHaveBeenCalledTimes(1))

    fireEvent.keyDown(screen.getByTestId('llm-provider'), { key: 'ArrowDown' })
    fireEvent.click(await screen.findByRole('option', { name: /OpenCode Zen/i }))
    await waitFor(() => expect(mocks.listProviderModels).toHaveBeenCalledTimes(2))
    await waitFor(() =>
      expect(screen.getByTestId('llm-model-remote').textContent).toContain('zen-model')
    )

    rejectGo(new Error('discovery GO obsoleto'))
    await new Promise((resolve) => setTimeout(resolve, 0))

    expect(screen.queryByTestId('llm-model-error')).toBeNull()
    fireEvent.click(screen.getByTestId('llm-save'))
    await waitFor(() =>
      expect(mocks.saveLlmConfig).toHaveBeenCalledWith({ provider: 'OPENCODE_ZEN', model: 'zen-model' })
    )
  })

  it('PromptsContainer lista, filtra e remove', async () => {
    mocks.promptList.mockResolvedValue({
      content: [promptFixture],
      totalElements: 1,
      totalPages: 1,
      number: 0,
      size: 20,
    })
    render(<PromptsContainer />)

    await waitFor(() => expect(screen.getByTestId('prompts-table').textContent).toContain('Saudação'))
    fireEvent.change(screen.getByTestId('search-q'), { target: { value: 'saud' } })
    fireEvent.keyDown(screen.getByTestId('search-q'), { key: 'Enter' })
    await waitFor(() => expect(mocks.promptList).toHaveBeenCalledWith(expect.objectContaining({ q: 'saud' })))

    fireEvent.click(screen.getByTestId(`delete-${promptFixture.id}`))
    fireEvent.click(screen.getByTestId('confirm-delete'))
    await waitFor(() => expect(mocks.promptList.mock.calls.length).toBeGreaterThan(1))
  })

  it('PromptEditorContainer carrega prompt para edição e salva sem code', async () => {
    render(<PromptEditorContainer promptId={promptFixture.id} onSaved={vi.fn()} onBack={vi.fn()} />)

    await waitFor(() =>
      expect((screen.getByTestId('input-name') as HTMLInputElement).value).toBe('Saudação')
    )
    expect((screen.getByTestId('input-code') as HTMLInputElement).disabled).toBe(true)
    fireEvent.click(screen.getByTestId('save-btn'))
    await waitFor(() => expect(mocks.promptUpdate).toHaveBeenCalledTimes(1))
    const payload = mocks.promptUpdate.mock.calls[0][1] as Record<string, unknown>
    expect(payload).not.toHaveProperty('code')
  })

  it('PromptEditorContainer cria novo prompt', async () => {
    const onSaved = vi.fn()
    render(<PromptEditorContainer promptId={null} onSaved={onSaved} onBack={vi.fn()} />)

    fireEvent.change(screen.getByTestId('input-name'), { target: { value: 'Saudação' } })
    fireEvent.change(screen.getByTestId('input-code'), { target: { value: 'greeting-v1' } })
    fireEvent.change(screen.getByTestId('input-template'), { target: { value: 'Olá {{nome}}' } })
    fireEvent.click(screen.getByTestId('save-btn'))

    await waitFor(() => expect(mocks.promptCreate).toHaveBeenCalledTimes(1))
    await waitFor(() => expect(onSaved).toHaveBeenCalled())
  })
})
