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
})
