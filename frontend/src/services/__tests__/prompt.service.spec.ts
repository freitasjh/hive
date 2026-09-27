import { describe, it, expect, vi, beforeEach } from 'vitest'

vi.mock('../api', () => ({
  default: {
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
    delete: vi.fn(),
  }
}))

import api from '../api'
import { promptService } from '../prompt.service'

describe('prompt.service', () => {
  beforeEach(() => vi.clearAllMocks())

  it('list calls GET /prompt-templates and unwraps page', async () => {
    const page = { content: [{ id: '1', name: 'P1' }], totalElements: 1, totalPages: 1, number: 0, size: 20 }
    vi.mocked(api.get).mockResolvedValue({ data: page } as never)
    const result = await promptService.list({ page: 0, size: 20 })
    expect(api.get).toHaveBeenCalledWith('/prompt-templates', { params: { page: 0, size: 20, q: undefined, status: undefined } })
    expect(result).toEqual(page)
  })

  it('list forwards q and status params', async () => {
    const page = { content: [], totalElements: 0, totalPages: 0, number: 0, size: 20 }
    vi.mocked(api.get).mockResolvedValue({ data: page } as never)
    await promptService.list({ page: 1, size: 10, q: 'hello', status: 'ACTIVE' })
    expect(api.get).toHaveBeenCalledWith('/prompt-templates', { params: { page: 1, size: 10, q: 'hello', status: 'ACTIVE' } })
  })

  it('getById calls GET /prompt-templates/:id and unwraps data wrapper', async () => {
    const dto = { id: 'p1', name: 'Test', code: 'test', template: 'hi', status: 'DRAFT', version: 1 }
    vi.mocked(api.get).mockResolvedValue({ data: { data: dto } } as never)
    const result = await promptService.getById('p1')
    expect(api.get).toHaveBeenCalledWith('/prompt-templates/p1')
    expect(result).toEqual(dto)
  })

  it('create calls POST /prompt-templates', async () => {
    const payload = { name: 'N', code: 'code', template: 't {{v}}', status: 'DRAFT' as const }
    const created = { id: 'new', ...payload }
    vi.mocked(api.post).mockResolvedValue({ data: created } as never)
    const result = await promptService.create(payload as never)
    expect(api.post).toHaveBeenCalledWith('/prompt-templates', payload)
    expect(result).toEqual(created)
  })

  it('update calls PUT /prompt-templates/:id', async () => {
    const payload = { name: 'N2', template: 't2', status: 'ACTIVE' as const }
    vi.mocked(api.put).mockResolvedValue({ data: { id: 'p1', ...payload } } as never)
    const result = await promptService.update('p1', payload as never)
    expect(api.put).toHaveBeenCalledWith('/prompt-templates/p1', payload)
    expect(result.id).toBe('p1')
  })

  it('remove calls DELETE', async () => {
    vi.mocked(api.delete).mockResolvedValue({} as never)
    await promptService.remove('p1')
    expect(api.delete).toHaveBeenCalledWith('/prompt-templates/p1')
  })

  it('unwrapPage handles Spring success wrapper { success:true, data: Page }', async () => {
    const page = { content: [{ id: '1' }], totalElements: 1, totalPages: 1, number: 0, size: 20 }
    vi.mocked(api.get).mockResolvedValue({ data: { success: true, data: page } } as never)
    const result = await promptService.list()
    expect(result.content).toHaveLength(1)
  })

  it('lê o totalElements do shape REAL do Spring 3+, onde ele vem aninhado em `page`', async () => {
    // Body real, medido no H3 com o backend no ar (2026-09-27):
    //   GET /hive/v1/prompt-templates?page=0&size=50  (56 prompts no tenant)
    //   → {"content":[...50 itens...],"page":{"size":50,"number":0,"totalElements":56,"totalPages":2}}
    //
    // A forma achatada (`totalElements` no topo) é a que os testes de componente usam, e é a que
    // escondeu o bug: com ela tudo passava e o rodapé "Ver todos os N prompts" nunca aparecia, porque
    // o `N` caía no comprimento da página. É a mesma classe de erro do filtro que fingia ser busca:
    // o teste afirmava o invariante do mock, não o do servidor.
    const body = {
      content: Array.from({ length: 50 }, (_, i) => ({ id: String(i), name: `P${i}`, code: `p${i}` })),
      page: { size: 50, number: 0, totalElements: 56, totalPages: 2 }
    }
    vi.mocked(api.get).mockResolvedValue({ data: body } as never)

    const result = await promptService.list({ page: 0, size: 50 })

    expect(result.content).toHaveLength(50)
    // O teto é 50 e existem 56: é exatamente a condição que faz o rodapé existir.
    expect(result.totalElements).toBe(56)
    expect(result.totalPages).toBe(2)
    expect(result.size).toBe(50)
  })

  it('aceita a forma achatada sem perder o total', async () => {
    const body = { content: [{ id: '1', name: 'P1', code: 'p1' }], totalElements: 137, totalPages: 14, number: 0, size: 10 }
    vi.mocked(api.get).mockResolvedValue({ data: body } as never)
    const result = await promptService.list({ page: 0, size: 10 })
    expect(result.totalElements).toBe(137)
  })

  it('cai no comprimento da página quando o servidor não manda metadados', async () => {
    vi.mocked(api.get).mockResolvedValue({ data: { content: [{ id: '1', name: 'P1', code: 'p1' }] } } as never)
    const result = await promptService.list()
    expect(result.totalElements).toBe(1)
    expect(result.size).toBe(20)
  })
})
