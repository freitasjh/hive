import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { promptService } from './prompt.service'

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'content-type': 'application/json' },
  })
}

const prompt = {
  id: '11111111-1111-1111-1111-111111111111',
  name: 'Saudação',
  code: 'greeting-v1',
  description: null,
  tags: ['chat'],
  template: 'Olá {{nome}}',
  params: ['nome'],
  inferenceParams: { temperature: 0.7, topP: 0.9, maxTokens: 2048 },
  status: 'ACTIVE',
  version: 1,
  createAt: '2026-01-01T00:00:00Z',
  updateAt: '2026-01-01T00:00:00Z',
}

describe('promptService', () => {
  let fetchMock: ReturnType<typeof vi.fn>

  beforeEach(() => {
    localStorage.clear()
    fetchMock = vi.fn()
    vi.stubGlobal('fetch', fetchMock)
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('desembrulha Page do Spring Boot 3 com meta aninhada em page', async () => {
    fetchMock.mockResolvedValue(
      json({ content: [prompt], page: { size: 20, number: 0, totalElements: 56, totalPages: 3 } })
    )

    const result = await promptService.list({ page: 0, size: 20, q: 'saud', status: 'ACTIVE' })

    expect(result.content).toHaveLength(1)
    expect(result.totalElements).toBe(56)
    expect(result.totalPages).toBe(3)
    expect(fetchMock.mock.calls[0][0]).toBe(
      '/hive/v1/prompt-templates?page=0&size=20&q=saud&status=ACTIVE'
    )
  })

  it('aceita envelope {data} e forma achatada legada', async () => {
    fetchMock.mockResolvedValueOnce(json({ data: { content: [prompt], totalElements: 1 } }))
    const wrapped = await promptService.list({ size: 10 })
    expect(wrapped.totalElements).toBe(1)

    fetchMock.mockResolvedValueOnce(json([prompt]))
    const plain = await promptService.list({ size: 10 })
    expect(plain.content).toHaveLength(1)
  })

  it('repassa timeoutMs para o seletor não ficar carregando para sempre', async () => {
    fetchMock.mockResolvedValue(json({ content: [], totalElements: 0 }))
    await promptService.list({ timeoutMs: 10000 })
    expect(fetchMock).toHaveBeenCalledTimes(1)
  })

  it('aplica timeout padrão de 10s quando timeoutMs não é informado', async () => {
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

    try {
      const promise = promptService.list()
      void promise.catch(() => {})
      await vi.advanceTimersByTimeAsync(9_999)
      await vi.advanceTimersByTimeAsync(1)
      await expect(promise).rejects.toMatchObject({ code: 'TIMEOUT' })
    } finally {
      vi.useRealTimers()
    }
  })

  it('busca por id, cria, atualiza e remove', async () => {
    fetchMock
      .mockResolvedValueOnce(json({ data: prompt }))
      .mockResolvedValueOnce(json(prompt, 201))
      .mockResolvedValueOnce(json(prompt))
      .mockResolvedValueOnce(new Response(null, { status: 204 }))

    await expect(promptService.getById(prompt.id)).resolves.toMatchObject({ code: 'greeting-v1' })
    await expect(
      promptService.create({
        name: prompt.name,
        code: prompt.code,
        template: prompt.template,
        status: 'DRAFT',
      })
    ).resolves.toMatchObject({ code: 'greeting-v1' })
    await expect(
      promptService.update(prompt.id, { name: prompt.name, template: prompt.template, status: 'ACTIVE' })
    ).resolves.toMatchObject({ status: 'ACTIVE' })
    await expect(promptService.remove(prompt.id)).resolves.toBeUndefined()

    expect(fetchMock.mock.calls.map((call) => (call[1] as RequestInit).method)).toEqual([
      'GET',
      'POST',
      'PUT',
      'DELETE',
    ])
    expect(fetchMock.mock.calls[2][0]).toBe(`/hive/v1/prompt-templates/${prompt.id}`)
  })

  it('preserva status/code do erro 409 para a tela avisar que prompt está em uso', async () => {
    fetchMock.mockResolvedValue(
      json({ status: 409, code: 'PROMPT_IN_USE', detail: 'Prompt em uso por uma sessão de chat.' }, 409)
    )

    const error = await promptService.remove(prompt.id).catch((e: unknown) => e)

    expect(error).toMatchObject({ status: 409, code: 'PROMPT_IN_USE' })
  })
})
