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
import { unwrapPage } from '../page'

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

  // ─────────────────────────────────────────────────────────────────────────────
  // O shape que o servidor REAL manda.
  //
  // Body colado de uma resposta real (`GET /hive/v1/chat/sessions?page=0&size=20`, 25 conversas
  // no tenant, medido no H3 em 2026-09-27):
  //
  //   { "content": [ ...20... ], "page": { "size": 20, "number": 0, "totalElements": 25, "totalPages": 2 } }
  //
  // O `totalElements` vem **aninhado em `page`**. Um `res.data as Page<T>` — que é o que este
  // serviço fazia — entrega `totalElements` e `totalPages` como `undefined`, e `undefined > 1` é
  // simplesmente falso: o `ChatSidebar` **nunca renderiza o paginador**, sem erro e sem aviso. Num
  // tenant com 58 conversas o usuário via 20 e não tinha como chegar às outras 38. Achar conversa
  // antiga é função principal de um produto de chat, não conveniência.
  // ─────────────────────────────────────────────────────────────────────────────

  it('lê o shape REAL do Spring 3+ em listSessions, onde o total vem aninhado em `page`', async () => {
    vi.mocked(api.get).mockResolvedValue({
      data: { content: [{ id: 's1' }, { id: 's2' }], page: { size: 20, number: 0, totalElements: 25, totalPages: 2 } }
    } as never)

    const page = await sessionService.listSessions(0, 20)

    expect(page.content).toHaveLength(2)
    expect(page.totalElements).toBe(25)
    expect(page.totalPages).toBe(2)
    // A condição que faz o paginador existir. Com `undefined` ela é falsa e o paginador não renderiza.
    expect(page.totalPages > 1).toBe(true)
  })

  it('lê o shape REAL em getMessages', async () => {
    vi.mocked(api.get).mockResolvedValue({
      data: { content: [{ id: 'm1' }], page: { size: 50, number: 0, totalElements: 137, totalPages: 3 } }
    } as never)

    const page = await sessionService.getMessages('s1', 0, 50)
    expect(page.totalElements).toBe(137)
    expect(page.totalPages).toBe(3)
    expect(page.size).toBe(50)
  })

  it('aceita a forma achatada sem perder o total', async () => {
    vi.mocked(api.get).mockResolvedValue({
      data: { content: [{ id: 's1' }], totalElements: 7, totalPages: 1, number: 0, size: 20 }
    } as never)
    const page = await sessionService.listSessions(0, 20)
    expect(page.totalElements).toBe(7)
  })

  it('cai no comprimento quando o servidor não manda metadados', async () => {
    vi.mocked(api.get).mockResolvedValue({ data: { content: [{ id: 's1' }] } } as never)
    const page = await sessionService.listSessions(0, 20)
    expect(page.totalElements).toBe(1)
    expect(page.size).toBe(20)
  })

  it('desambulha um data que é array puro, que o normalizador antigo tratava e o novo não devia perder', async () => {
    // Ramo que existia antes e que a minha primeira versão do normalizador removeu em silêncio. O
    // backend hoje não embrulha, então o caminho é inalcançável — mas "inalcançável" é exatamente o
    // que se perde quando ninguém registra. Fica tratado, com teste.
    vi.mocked(api.get).mockResolvedValue({ data: { data: [{ id: 's1' }, { id: 's2' }] } } as never)
    const page = await sessionService.listSessions(0, 20)
    expect(page.content).toHaveLength(2)
    expect(page.totalElements).toBe(2)
  })

  it('reutiliza o mesmo normalizador do prompt.service em vez de duplicar a lógica', async () => {
    // A regra é "nenhum serviço faz cast de payload paginado". Este teste é sobre a duplicação, não
    // sobre comportamento: os dois serviços precisam passar por `services/page.ts`.
    const shape = { content: [{ id: '1' }], page: { size: 20, number: 0, totalElements: 25, totalPages: 2 } }
    vi.mocked(api.get).mockResolvedValue({ data: shape } as never)

    const page = await sessionService.listSessions(0, 20)
    expect(page).toEqual(unwrapPage(shape, 20))
  })
})
