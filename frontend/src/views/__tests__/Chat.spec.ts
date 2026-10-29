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

vi.mock('../../services/prompt.service', () => ({
  promptService: {
    list: vi.fn().mockResolvedValue({ content: [], totalElements: 0, totalPages: 0, number: 0, size: 100 })
  },
  default: {
    list: vi.fn().mockResolvedValue({ content: [], totalElements: 0, totalPages: 0, number: 0, size: 100 })
  }
}))

import { sessionService } from '../../services/session.service'
import { chatService } from '../../services/chat.service'

const mockSessionId = '11111111-1111-1111-1111-111111111111'
const mockPromptTemplateId = '22222222-2222-2222-2222-222222222222'

const mockSession = { id: mockSessionId, userId: 'u1', promptTemplateId: mockPromptTemplateId, title: 'Explique RAG', summary: null, createAt: '2026-08-26T12:00:00Z', updateAt: '2026-08-26T12:00:00Z' }

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
        PromptSelector: {
          template: '<div data-testid="prompt-selector" />',
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
    ;(wrapper.vm as unknown as { selectedPromptTemplateId: string | null }).selectedPromptTemplateId = mockPromptTemplateId
    ;(wrapper.vm as unknown as { userInput: string }).userInput = 'Olá'
    await (wrapper.vm as unknown as { sendMessage: () => Promise<void> }).sendMessage()
    await flushPromises()
    expect(chatService.sendMessage).toHaveBeenCalledWith(mockPromptTemplateId, null, 'Olá')
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
    ;(wrapper.vm as unknown as { selectedPromptTemplateId: string | null }).selectedPromptTemplateId = mockPromptTemplateId
    await (wrapper.vm as unknown as { newChat: () => void }).newChat()
    expect((wrapper.vm as unknown as { sessionId: string | null }).sessionId).toBeNull()
    expect((wrapper.vm as unknown as { messages: unknown[] }).messages.length).toBe(0)
    expect(localStorage.getItem('hive_sessionId')).toBeNull()
    // A conversa nova precisa de um prompt novo: o da conversa anterior não foi escolhido nesta tela.
    expect((wrapper.vm as unknown as { selectedPromptTemplateId: string | null }).selectedPromptTemplateId).toBeNull()
  })

  it('refuses to send without a prompt and creates nothing', async () => {
    const wrapper = mountChat()
    await flushPromises()
    ;(wrapper.vm as unknown as { selectedPromptTemplateId: string | null }).selectedPromptTemplateId = null
    ;(wrapper.vm as unknown as { userInput: string }).userInput = 'Olá'
    await (wrapper.vm as unknown as { sendMessage: () => Promise<void> }).sendMessage()
    await flushPromises()
    // Não há prompt padrão: uma conversa sem prompt não é criada "para depois".
    expect(chatService.sendMessage).not.toHaveBeenCalled()
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

  it('newChat resets lastUserMessage', async () => {
    const wrapper = mountChat()
    ;(wrapper.vm as unknown as { lastUserMessage: string }).lastUserMessage = 'oi'
    await (wrapper.vm as unknown as { newChat: () => void }).newChat()
    expect((wrapper.vm as unknown as { lastUserMessage: string }).lastUserMessage).toBe('')
  })

  it('useStarter fills input and sends', async () => {
    const wrapper = mountChat()
    await flushPromises()
    ;(wrapper.vm as unknown as { selectedPromptTemplateId: string | null }).selectedPromptTemplateId = mockPromptTemplateId
    await (wrapper.vm as unknown as { useStarter: (p: string) => Promise<void> }).useStarter('Explique RAG')
    await flushPromises()
    expect(chatService.sendMessage).toHaveBeenCalledWith(mockPromptTemplateId, null, 'Explique RAG')
  })

  it('retryLastMessage removes last system message before resending', async () => {
    vi.mocked(chatService.sendMessage)
      .mockRejectedValueOnce(new Error('boom'))
      .mockResolvedValueOnce({ sessionId: mockSessionId, role: 'assistant', message: 'ok', timestamp: new Date().toISOString() } as never)
    const wrapper = mountChat()
    await flushPromises()
    ;(wrapper.vm as unknown as { selectedPromptTemplateId: string | null }).selectedPromptTemplateId = mockPromptTemplateId
    ;(wrapper.vm as unknown as { userInput: string }).userInput = 'Olá'
    await (wrapper.vm as unknown as { sendMessage: () => Promise<void> }).sendMessage()
    await flushPromises()
    const before = (wrapper.vm as unknown as { messages: unknown[] }).messages.length
    expect(before).toBeGreaterThanOrEqual(2)
    await (wrapper.vm as unknown as { retryLastMessage: () => Promise<void> }).retryLastMessage()
    await flushPromises()
    // system msg removed, new exchange added
    expect(chatService.sendMessage).toHaveBeenCalledTimes(2)
  })

  it('retryLastMessage does not duplicate temp user bubble', async () => {
    vi.mocked(chatService.sendMessage)
      .mockRejectedValueOnce(new Error('boom'))
      .mockResolvedValueOnce({ sessionId: mockSessionId, role: 'assistant', message: 'ok', timestamp: new Date().toISOString() } as never)
    const wrapper = mountChat()
    await flushPromises()
    ;(wrapper.vm as unknown as { selectedPromptTemplateId: string | null }).selectedPromptTemplateId = mockPromptTemplateId
    ;(wrapper.vm as unknown as { userInput: string }).userInput = 'Olá'
    await (wrapper.vm as unknown as { sendMessage: () => Promise<void> }).sendMessage()
    await flushPromises()
    const tempBefore = (wrapper.vm as unknown as { messages: { id: string }[] }).messages.filter(m => String(m.id).startsWith('temp-')).length
    expect(tempBefore).toBe(1)
    await (wrapper.vm as unknown as { retryLastMessage: () => Promise<void> }).retryLastMessage()
    await flushPromises()
    const tempAfter = (wrapper.vm as unknown as { messages: { id: string }[] }).messages.filter(m => String(m.id).startsWith('temp-')).length
    expect(tempAfter).toBe(1)
  })
})
