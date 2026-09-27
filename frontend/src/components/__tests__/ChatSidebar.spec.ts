import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import ChatSidebar from '../ChatSidebar.vue'
import PrimeVue from 'primevue/config'

vi.mock('../../services/session.service', () => ({
  sessionService: {
    listSessions: vi.fn(),
    deleteSession: vi.fn()
  },
  default: {
    listSessions: vi.fn(),
    deleteSession: vi.fn()
  }
}))

import { sessionService } from '../../services/session.service'

const sessions = [
  { id: 's1', userId: 'u1', promptTemplateId: 'p1', title: 'Conversa 1', summary: null, createAt: '2026-08-26T10:00:00Z', updateAt: '2026-08-26T12:00:00Z' },
  { id: 's2', userId: 'u1', promptTemplateId: 'p2', title: 'Conversa 2', summary: null, createAt: '2026-08-26T09:00:00Z', updateAt: '2026-08-26T11:00:00Z' }
]

function mountComponent(props: Record<string, unknown> = {}) {
  return mount(ChatSidebar, {
    props: {
      modelValue: true,
      selectedId: null,
      ...props
    },
    global: {
      plugins: [PrimeVue],
      stubs: {
        Drawer: { template: '<div><slot name="header" /><slot /></div>', props: ['visible'] },
        Dialog: { template: '<div data-testid="confirm-dialog"><slot /><slot name="footer" /></div>', props: ['visible'] },
        ScrollPanel: { template: '<div><slot /></div>' },
        Paginator: { template: '<div data-testid="paginator" />' },
        Skeleton: { template: '<div data-testid="skeleton" />' },
        Message: { template: '<div data-testid="message"><slot /></div>' },
        Button: {
          template: '<button :data-testid="$attrs[\'data-testid\'] || \'button\'" @click="$emit(\'click\', { stopPropagation: () => {} })"><slot />{{ $attrs.label }}</button>',
          props: ['label', 'icon']
        }
      },
      directives: {
        tooltip: {}
      }
    }
  })
}

describe('ChatSidebar.vue', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(sessionService.listSessions).mockResolvedValue({ content: sessions, totalElements: 2, totalPages: 1, number: 0, size: 20 } as never)
    vi.mocked(sessionService.deleteSession).mockResolvedValue(undefined as never)
  })

  it('renders list title+data DESC after fetch', async () => {
    const wrapper = mountComponent()
    await flushPromises()
    expect(sessionService.listSessions).toHaveBeenCalledWith(0, 20)
    expect(wrapper.text()).toContain('Conversa 1')
    expect(wrapper.text()).toContain('Conversa 2')
  })

  it('click emits select', async () => {
    const wrapper = mountComponent()
    await flushPromises()
    const item = wrapper.find('[data-testid="session-item-s1"]')
    expect(item.exists()).toBe(true)
    await item.trigger('click')
    expect(wrapper.emitted('select')).toBeTruthy()
    expect(wrapper.emitted('select')![0]).toEqual(['s1'])
  })

  it('Nova Conversa emits newChat', async () => {
    const wrapper = mountComponent()
    await flushPromises()
    const btn = wrapper.find('[data-testid="new-chat-btn"]')
    expect(btn.exists()).toBe(true)
    await btn.trigger('click')
    expect(wrapper.emitted('newChat')).toBeTruthy()
  })

  it('delete removes item without reload', async () => {
    const wrapper = mountComponent()
    await flushPromises()
    expect(wrapper.findAll('[data-testid^="session-item-"]').length).toBe(2)
    const delBtn = wrapper.find('[data-testid="delete-s1"]')
    await delBtn.trigger('click')
    await flushPromises()
    const confirmBtn = wrapper.find('[data-testid="confirm-delete"]')
    expect(confirmBtn.exists()).toBe(true)
    await confirmBtn.trigger('click')
    await flushPromises()
    expect(sessionService.deleteSession).toHaveBeenCalledWith('s1')
    expect(wrapper.find('[data-testid="session-item-s1"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="session-item-s2"]').exists()).toBe(true)
    expect(wrapper.emitted('deleted')).toBeTruthy()
  })

  it('empty state renders Nenhuma conversa', async () => {
    vi.mocked(sessionService.listSessions).mockResolvedValue({ content: [], totalElements: 0, totalPages: 0, number: 0, size: 20 } as never)
    const wrapper = mountComponent()
    await flushPromises()
    expect(wrapper.find('[data-testid="empty-state"]').exists()).toBe(true)
    expect(wrapper.text()).toContain('Nenhuma conversa')
  })

  it('mostra o paginador quando o tenant tem mais conversas que a página', async () => {
    // Este é o efeito do `unwrapPage`. O `ChatSidebar` só renderiza o paginador com
    // `totalPages > 1`, e o Spring 3+ aninha o `totalElements`/`totalPages` dentro de `page`. Com o
    // `res.data as Page<T>` que existia antes, os dois chegavam `undefined`, `undefined > 1` era
    // falso, e o paginador **nunca aparecia** — sem erro, sem aviso. Num tenant com 58 conversas o
    // usuário via 20 e não tinha como chegar às outras 38: achar conversa antiga é função principal
    // de um produto de chat.
    const many = Array.from({ length: 20 }, (_, i) => ({
      id: `s${i}`, userId: 'u1', promptTemplateId: 'p1', title: `Conversa ${i}`,
      summary: null, createAt: '2026-08-26T10:00:00Z', updateAt: '2026-08-26T12:00:00Z'
    }))
    vi.mocked(sessionService.listSessions).mockResolvedValue({
      content: many, totalElements: 58, totalPages: 3, number: 0, size: 20
    } as never)

    const wrapper = mountComponent()
    await flushPromises()

    expect(wrapper.find('[data-testid="paginator"]').exists()).toBe(true)
  })

  it('esconde o paginador quando tudo cabe em uma página', async () => {
    vi.mocked(sessionService.listSessions).mockResolvedValue({
      content: sessions, totalElements: 2, totalPages: 1, number: 0, size: 20
    } as never)
    const wrapper = mountComponent()
    await flushPromises()
    expect(wrapper.find('[data-testid="paginator"]').exists()).toBe(false)
  })

})
