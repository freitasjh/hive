import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { agentAdminService } from './agent-admin.service'

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'content-type': 'application/json' },
  })
}

describe('agentAdminService', () => {
  let fetchMock: ReturnType<typeof vi.fn>

  beforeEach(() => {
    localStorage.clear()
    fetchMock = vi.fn()
    vi.stubGlobal('fetch', fetchMock)
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('lista credenciais mascaradas', async () => {
    const body = [{ provider: 'OPENAI', configured: true, last4: '1234', baseUrl: null }]
    fetchMock.mockResolvedValue(json(body))

    const result = await agentAdminService.listCredentials()

    expect(result).toEqual(body)
    expect(fetchMock.mock.calls[0][0]).toBe('/hive/v1/provider-credentials')
  })

  it('aceita envelope {data} e corpo nu', async () => {
    fetchMock.mockResolvedValue(json({ data: [{ provider: 'GEMINI', configured: false, last4: null }] }))
    const result = await agentAdminService.listCredentials()
    expect(result[0].provider).toBe('GEMINI')
  })

  it('faz upsert com PUT e body sem segredo na URL', async () => {
    fetchMock.mockResolvedValue(json({ provider: 'OPENAI', configured: true, last4: '4242' }))

    const result = await agentAdminService.upsertCredential('OPENAI', {
      apiKey: 'sk-chave-secreta',
      baseUrl: '',
    })

    expect(result.last4).toBe('4242')
    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit]
    expect(url).toBe('/hive/v1/provider-credentials/OPENAI')
    expect(url).not.toContain('sk-chave-secreta')
    expect(init.method).toBe('PUT')
    expect(JSON.parse(init.body as string).apiKey).toBe('sk-chave-secreta')
  })

  it('remove credencial com DELETE', async () => {
    fetchMock.mockResolvedValue(new Response(null, { status: 204 }))
    await agentAdminService.deleteCredential('OLLAMA')
    expect(fetchMock.mock.calls[0][0]).toBe('/hive/v1/provider-credentials/OLLAMA')
    expect((fetchMock.mock.calls[0][1] as RequestInit).method).toBe('DELETE')
  })

  it('valida conexão e aborta por timeout de 20s', async () => {
    vi.useFakeTimers()
    fetchMock.mockImplementation((_url: string, init: RequestInit) =>
      new Promise((_resolve, reject) => {
        init.signal?.addEventListener('abort', () => {
          const error = new Error('aborted')
          error.name = 'AbortError'
          reject(error)
        })
      })
    )

    let settled = false
    try {
      const promise = agentAdminService
        .validate({ provider: 'OPENAI', model: 'gpt-4o-mini' })
        .then((value) => { settled = true; return value })
        .catch((error: unknown) => { settled = true; throw error })
      void promise.catch(() => {})

      await vi.advanceTimersByTimeAsync(19_999)
      expect(settled).toBe(false)

      await vi.advanceTimersByTimeAsync(1)
      await expect(promise).rejects.toMatchObject({ code: 'TIMEOUT' })
      expect(settled).toBe(true)

      const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit]
      expect(url).toBe('/hive/v1/providers/validate')
      expect(init.method).toBe('POST')
    } finally {
      vi.useRealTimers()
    }
  })

  it('mapeia providers remotos para slug go/zen e repassa refresh', async () => {
    fetchMock.mockResolvedValue(json({ data: { models: ['mimo-v2.5'], filteredOut: [], cachedAt: '2026-01-01T00:00:00Z' } }))

    const result = await agentAdminService.listProviderModels('OPENCODE_GO', true)

    expect(result.models).toEqual(['mimo-v2.5'])
    expect(fetchMock.mock.calls[0][0]).toBe('/hive/v1/providers/go/models?refresh=true')
  })

  it('rejeita discovery remoto para provider local sem chamada de rede', async () => {
    await expect(agentAdminService.listProviderModels('OPENAI')).rejects.toMatchObject({
      code: 'INVALID_PROVIDER',
    })
    expect(fetchMock).not.toHaveBeenCalled()
  })

  it('getLlmConfig devolve null quando tenant ainda não configurou', async () => {
    fetchMock.mockResolvedValue(json({ data: null }))
    await expect(agentAdminService.getLlmConfig()).resolves.toBeNull()
  })

  it('salva config LLM do tenant com PUT', async () => {
    fetchMock.mockResolvedValue(
      json({ data: { id: '1', tenantId: 't', provider: 'OLLAMA', model: 'llama3.2', createAt: '', updateAt: '' } })
    )

    const result = await agentAdminService.saveLlmConfig({ provider: 'OLLAMA', model: 'llama3.2' })

    expect(result.provider).toBe('OLLAMA')
    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit]
    expect(url).toBe('/hive/v1/tenant/llm-config')
    expect(init.method).toBe('PUT')
  })
})
