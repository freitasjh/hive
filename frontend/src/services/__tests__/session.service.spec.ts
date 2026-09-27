import { describe, it, expect, vi, beforeEach } from 'vitest'

vi.mock('../api', () => ({
  default: {
    get: vi.fn(),
    post: vi.fn(),
    delete: vi.fn(),
    patch: vi.fn()
  }
}))

import api from '../api'
import { sessionService } from '../session.service'

describe('session.service', () => {
  beforeEach(() => vi.clearAllMocks())

  it('listSessions calls GET /chat/sessions with page&size and unwraps data', async () => {
    const page = { content: [], totalElements: 0, totalPages: 0, number: 0, size: 20 }
    vi.mocked(api.get).mockResolvedValue({ data: page } as never)
    const result = await sessionService.listSessions(0, 20)
    expect(api.get).toHaveBeenCalledWith('/chat/sessions', { params: { page: 0, size: 20 } })
    expect(result).toEqual(page)
  })

  it('getMessages calls GET /chat/sessions/:id/messages', async () => {
    const page = { content: [{ id: 'm1' }], totalElements: 1, totalPages: 1, number: 0, size: 50 }
    vi.mocked(api.get).mockResolvedValue({ data: page } as never)
    const result = await sessionService.getMessages('s1', 0, 50)
    expect(api.get).toHaveBeenCalledWith('/chat/sessions/s1/messages', { params: { page: 0, size: 50 } })
    expect(result).toEqual(page)
  })

  it('deleteSession calls DELETE', async () => {
    vi.mocked(api.delete).mockResolvedValue({} as never)
    await sessionService.deleteSession('s1')
    expect(api.delete).toHaveBeenCalledWith('/chat/sessions/s1')
  })

  it('createSession calls POST with the prompt template, not an agent', async () => {
    // O prompt é escolhido na criação da conversa e é NOT NULL lá atrás: sem ele não há sessão.
    const dto = { id: 's1', title: 'hi' }
    vi.mocked(api.post).mockResolvedValue({ data: dto } as never)
    const result = await sessionService.createSession('p1', 'hi')
    expect(api.post).toHaveBeenCalledWith('/chat/sessions', { promptTemplateId: 'p1', title: 'hi' })
    expect(result).toEqual(dto)
  })

  it('updateSession calls PATCH', async () => {
    const dto = { id: 's1', title: 'new' }
    vi.mocked(api.patch).mockResolvedValue({ data: dto } as never)
    const result = await sessionService.updateSession('s1', 'new')
    expect(api.patch).toHaveBeenCalledWith('/chat/sessions/s1', { title: 'new' })
    expect(result).toEqual(dto)
  })
})
