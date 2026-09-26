import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia } from 'pinia'
import PrimeVue from 'primevue/config'
import ToastService from 'primevue/toastservice'
import Chat from '../Chat.vue'

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: vi.fn() }),
  useRoute: () => ({ params: {} })
}))

// mock localStorage for jsdom if missing
if (typeof globalThis.localStorage === 'undefined') {
  const store: Record<string, string> = {}
  // @ts-ignore
  globalThis.localStorage = {
    getItem: (k: string) => store[k] ?? null,
    setItem: (k: string, v: string) => { store[k] = v },
    removeItem: (k: string) => { delete store[k] },
    clear: () => { Object.keys(store).forEach(k => delete store[k]) }
  } as never
}

vi.mock('../../services/session.service', () => ({
  sessionService: {
    listSessions: vi.fn(),
    getSession: vi.fn(),
    getMessages: vi.fn(),
    deleteSession: vi.fn()
  },
  default: {
    listSessions: vi.fn(),
    getSession: vi.fn(),
    getMessages: vi.fn(),
    deleteSession: vi.fn()
  }
}))

vi.mock('../../services/chat.service', () => ({
  chatService: {
    sendMessage: vi.fn()
  },
  default: {
    sendMessage: vi.fn()
  }
}))

vi.mock('../../services/agent.service', () => ({
  agentService: {
    listAgents: vi.fn().mockResolvedValue({ content: [], totalElements: 0, totalPages: 0, number: 0, size: 50 })
  },
  default: {
    listAgents: vi.fn().mockResolvedValue({ content: [], totalElements: 0, totalPages: 0, number: 0, size: 50 })
  }
}))

import { sessionService } from '../../services/session.service'
import { chatService } from '../../services/chat.service'

const mockSessionId = '11111111-1111-1111-1111-111111111111'
const mockAgentId = '22222222-2222-2222-2222-222222222222'

const mockSession = { id: mockSessionId, userId: 'u1', agentId: mockAgentId, title: 'Explique RAG', summary: null, createAt: '2026-08-26T12:00:00Z', updateAt: '2026-08-26T12:00:00Z' }

const mockMessages = [
  { id: 'm1', sessionId: mockSessionId, role: 'user', content: 'Explique RAG', createAt: '2026-08-26T12:00:00Z' },
  { id: 'm2', sessionId: mockSessionId, role: 'assistant', content: 'RAG combina...', createAt: '2026-08-26T12:00:01Z' }
]

function mountChat() {
  const pinia = createPinia()
  return mount(Chat, {
    global: {
      plugins: [pinia, PrimeVue, ToastService],
      stubs: {
        ChatSidebar: {
          template: '<div data-testid="sidebar"><button data-testid="sidebar-select" @click="$emit(\'select\', \'' + mockSessionId + '\')">select</button><button data-testid="sidebar-new" @click="$emit(\'newChat\')">new</button></div>',
          props: ['modelValue', 'selectedId']
        },
        AgentSelector: {
          template: '<div data-testid="agent-selector" />',
          props: ['modelValue', 'disabled']
        },
        ChatMessage: { template: '<div data-testid="msg">{{ content }}</div>', props: ['role', 'content', 'timestamp'] },
        ChatInput: {
          template: '<div><input data-testid="chat-input" :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)" /><button data-testid="send-btn" @click="$emit(\'send\')">send</button></div>',
          props: ['modelValue', 'disabled']
        },
        Button: { template: '<button><slot /></button>' },
        Skeleton: { template: '<div data-testid="skeleton" />' },
        Toast: { template: '<div />' },
        Drawer: { template: '<div><slot /></div>' }
      },
      directives: { tooltip: {} },
      mocks: {
        $toast: { add: vi.fn() }
      }
    }
  })
}

describe('Chat.vue', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    localStorage.clear()
    vi.mocked(sessionService.listSessions).mockResolvedValue({ content: [], totalElements: 0, totalPages: 0, number: 0, size: 20 } as never)
    vi.mocked(sessionService.getMessages).mockResolvedValue({ content: [], totalElements: 0, totalPages: 0, number: 0, size: 50 } as never)
    vi.mocked(sessionService.getSession).mockResolvedValue(mockSession as never)
    vi.mocked(chatService.sendMessage).mockResolvedValue({ sessionId: mockSessionId, role: 'assistant', message: 'Resposta', timestamp: new Date().toISOString() } as never)
  })

  it('sendMessage with null sessionId creates session and stores hive_sessionId', async () => {
    const wrapper = mountChat()
    await flushPromises()
    ;(wrapper.vm as unknown as { selectedAgentId: string | null }).selectedAgentId = mockAgentId
    ;(wrapper.vm as unknown as { userInput: string }).userInput = 'Olá'
    await (wrapper.vm as unknown as { sendMessage: () => Promise<void> }).sendMessage()
    await flushPromises()
    expect(chatService.sendMessage).toHaveBeenCalledWith(mockAgentId, null, 'Olá')
    expect(localStorage.getItem('hive_sessionId')).toBe(mockSessionId)
    expect((wrapper.vm as unknown as { sessionId: string | null }).sessionId).toBe(mockSessionId)
    expect((wrapper.vm as unknown as { messages: unknown[] }).messages.length).toBeGreaterThanOrEqual(2)
  })

  it('selectSession loads messages ASC', async () => {
    vi.mocked(sessionService.getMessages).mockResolvedValue({ content: mockMessages, totalElements: 2, totalPages: 1, number: 0, size: 50 } as never)
    const wrapper = mountChat()
    await flushPromises()
    await (wrapper.vm as unknown as { selectSession: (id: string) => Promise<void> }).selectSession(mockSessionId)
    await flushPromises()
    expect(sessionService.getMessages).toHaveBeenCalledWith(mockSessionId, 0, 50)
    expect((wrapper.vm as unknown as { messages: typeof mockMessages }).messages.length).toBe(2)
    expect((wrapper.vm as unknown as { messages: typeof mockMessages }).messages[0].content).toBe('Explique RAG')
  })

  it('newChat clears sessionId and messages and localStorage', async () => {
    localStorage.setItem('hive_sessionId', mockSessionId)
    const wrapper = mountChat()
    ;(wrapper.vm as unknown as { sessionId: string | null }).sessionId = mockSessionId
    ;(wrapper.vm as unknown as { messages: unknown[] }).messages = [...mockMessages] as never
    await (wrapper.vm as unknown as { newChat: () => void }).newChat()
    expect((wrapper.vm as unknown as { sessionId: string | null }).sessionId).toBeNull()
    expect((wrapper.vm as unknown as { messages: unknown[] }).messages.length).toBe(0)
    expect(localStorage.getItem('hive_sessionId')).toBeNull()
  })

  it('reload via localStorage loads history onMounted', async () => {
    localStorage.setItem('hive_sessionId', mockSessionId)
    vi.mocked(sessionService.getMessages).mockResolvedValue({ content: mockMessages, totalElements: 2, totalPages: 1, number: 0, size: 50 } as never)
    const wrapper = mountChat()
    await flushPromises()
    expect(sessionService.getMessages).toHaveBeenCalled()
    expect((wrapper.vm as unknown as { messages: typeof mockMessages }).messages.length).toBe(2)
  })

  it('deleteSession handling via onSessionDeleted clears active session', async () => {
    const wrapper = mountChat()
    ;(wrapper.vm as unknown as { sessionId: string | null }).sessionId = mockSessionId
    ;(wrapper.vm as unknown as { messages: unknown[] }).messages = [...mockMessages] as never
    localStorage.setItem('hive_sessionId', mockSessionId)
    ;(wrapper.vm as unknown as { onSessionDeleted: (id: string) => void }).onSessionDeleted(mockSessionId)
    expect((wrapper.vm as unknown as { sessionId: string | null }).sessionId).toBeNull()
    expect((wrapper.vm as unknown as { messages: unknown[] }).messages.length).toBe(0)
  })
})
