import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia } from 'pinia'
import PrimeVue from 'primevue/config'
import ToastService from 'primevue/toastservice'

const mockToastAdd = vi.fn()

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: vi.fn() }),
  useRoute: () => ({ params: {} }),
}))

vi.mock('primevue/usetoast', () => ({
  useToast: () => ({ add: (...args: unknown[]) => mockToastAdd(...args) }),
}))

if (typeof globalThis.localStorage === 'undefined') {
  const store: Record<string, string> = {}
  // @ts-ignore
  globalThis.localStorage = {
    getItem: (k: string) => store[k] ?? null,
    setItem: (k: string, v: string) => { store[k] = v },
    removeItem: (k: string) => { delete store[k] },
    clear: () => { Object.keys(store).forEach(k => delete store[k]) },
  } as never
}

vi.mock('../../services/session.service', () => ({
  sessionService: { listSessions: vi.fn(), getSession: vi.fn(), getMessages: vi.fn(), deleteSession: vi.fn() },
  default: { listSessions: vi.fn(), getSession: vi.fn(), getMessages: vi.fn(), deleteSession: vi.fn() },
}))

vi.mock('../../services/chat.service', () => ({
  chatService: { sendMessage: vi.fn() },
  default: { sendMessage: vi.fn() },
}))

vi.mock('../../services/agent.service', () => ({
  agentService: { listAgents: vi.fn().mockResolvedValue({ content: [] }) },
  default: { listAgents: vi.fn().mockResolvedValue({ content: [] }) },
}))

import Chat from '../Chat.vue'
import { sessionService } from '../../services/session.service'
import { chatService } from '../../services/chat.service'
import { RAG_REASON_MESSAGES } from '../../types/chat-messages'

const mockSessionId = '11111111-1111-1111-1111-111111111111'
const mockAgentId = '22222222-2222-2222-2222-222222222222'

type ChatVm = {
  selectedAgentId: string | null
  userInput: string
  sendMessage: () => Promise<void>
}

function mountChat() {
  const pinia = createPinia()
  return mount(Chat, {
    global: {
      plugins: [pinia, PrimeVue, ToastService],
      stubs: {
        ChatSidebar: {
          template: '<div data-testid="sidebar" />',
          props: ['modelValue', 'selectedId'],
          // O componente real expõe refresh(); sem ele a chamada pós-resposta estoura um TypeError
          // e cai no catch, mascarando o estado que este spec verifica.
          methods: { refresh: () => undefined },
        },
        AgentSelector: { template: '<div data-testid="agent-selector" />', props: ['modelValue', 'disabled'] },
        ChatMessage: { template: '<div data-testid="msg">{{ content }}</div>', props: ['role', 'content', 'timestamp'] },
        ChatInput: { template: '<div />', props: ['modelValue', 'disabled'] },
        Button: { template: '<button><slot /></button>' },
        Skeleton: { template: '<div data-testid="skeleton" />' },
        Toast: { template: '<div />' },
        Drawer: { template: '<div><slot /></div>' },
      },
      directives: { tooltip: {} },
    },
  })
}

async function sendOnce() {
  const wrapper = mountChat()
  await flushPromises()
  const vm = wrapper.vm as unknown as ChatVm
  vm.selectedAgentId = mockAgentId
  vm.userInput = 'Explique o_runbook de deploy'
  await vm.sendMessage()
  await flushPromises()
  return wrapper
}

function respondWith(extra: Record<string, unknown>) {
  vi.mocked(chatService.sendMessage).mockResolvedValue({
    sessionId: mockSessionId,
    role: 'assistant',
    message: 'Resposta',
    timestamp: new Date().toISOString(),
    ...extra,
  } as never)
}

describe('Chat.vue — motivo do RAG ausente visível na tela', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    localStorage.clear()
    vi.mocked(sessionService.listSessions).mockResolvedValue({ content: [] } as never)
    vi.mocked(sessionService.getMessages).mockResolvedValue({ content: [] } as never)
    respondWith({})
  })

  it('não mostra aviso quando o RAG funcionou', async () => {
    respondWith({ ragUsed: true, ragReason: 'ok', citations: [{ docId: 'd', docName: 'runbook', chunkId: 'c', score: 0.9 }] })
    const w = await sendOnce()
    expect(w.find('[data-testid="rag-notice"]').exists()).toBe(false)
  })

  it('não mostra aviso quando o módulo de knowledge está desabilitado', async () => {
    // Avisar a cada mensagem que a instalação não tem knowledge module só gera ruído.
    respondWith({ ragUsed: false, ragReason: 'rag_disabled' })
    const w = await sendOnce()
    expect(w.find('[data-testid="rag-notice"]').exists()).toBe(false)
  })

  it.each([
    ['no_link', RAG_REASON_MESSAGES.no_link],
    ['kb_not_found_for_tenant', RAG_REASON_MESSAGES.kb_not_found_for_tenant],
    ['no_chunk_above_threshold', RAG_REASON_MESSAGES.no_chunk_above_threshold],
    ['timeout', RAG_REASON_MESSAGES.timeout],
    ['error', RAG_REASON_MESSAGES.error],
    ['dimension_mismatch', RAG_REASON_MESSAGES.dimension_mismatch],
  ])('mostra o texto pt-BR de %s para o usuário', async (reason, expected) => {
    respondWith({ ragUsed: false, ragReason: reason })
    const w = await sendOnce()
    const notice = w.find('[data-testid="rag-notice"]')
    expect(notice.exists()).toBe(true)
    expect(notice.text()).toContain(expected)
  })

  it('anuncia o motivo com role=status em vez de interromper com alert', async () => {
    respondWith({ ragUsed: false, ragReason: 'no_link' })
    const w = await sendOnce()
    const root = w.find('[data-testid="rag-notice"]')
    expect(root.attributes('role')).toBe('status')
    expect(root.attributes('aria-live')).toBe('polite')
  })

  it('troca o aviso quando um turno sem contexto vem depois de um com contexto', async () => {
    respondWith({ ragUsed: true, ragReason: 'ok' })
    const w = await sendOnce()
    expect(w.find('[data-testid="rag-notice"]').exists()).toBe(false)
    respondWith({ ragUsed: false, ragReason: 'timeout' })
    const vm = w.vm as unknown as ChatVm
    vm.userInput = 'E agora?'
    await vm.sendMessage()
    await flushPromises()
    expect(w.find('[data-testid="rag-notice"]').text()).toContain(RAG_REASON_MESSAGES.timeout)
  })
})

describe('Chat.vue — resposta vazia do modelo (422 MODEL_RETURNED_EMPTY)', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    localStorage.clear()
    vi.mocked(sessionService.listSessions).mockResolvedValue({ content: [] } as never)
    vi.mocked(sessionService.getMessages).mockResolvedValue({ content: [] } as never)
  })

  it('mostra a orientação do token e não a mensagem genérica de falha', async () => {
    vi.mocked(chatService.sendMessage).mockRejectedValue({
      response: {
        status: 422,
        data: {
          code: 'MODEL_RETURNED_EMPTY',
          detail: 'O modelo mimo-v2.5 não devolveu texto (limite de tokens atingido sem conteúdo).',
        },
      },
    })

    await sendOnce()

    const toast = mockToastAdd.mock.calls.at(-1)?.[0] as { severity: string; summary: string; detail: string }
    expect(toast.severity).toBe('warn')
    expect(toast.summary).toBe('Resposta vazia')
    expect(toast.detail).toContain('limite de tokens')
    expect(toast.detail).toContain('Aumente o limite de tokens')
  })

  it('não mostra o aviso de RAG junto com o erro de resposta vazia', async () => {
    vi.mocked(chatService.sendMessage).mockRejectedValue({
      response: { status: 422, data: { code: 'MODEL_RETURNED_EMPTY', detail: 'sem texto' } },
    })

    const w = await sendOnce()

    expect(w.find('[data-testid="rag-notice"]').exists()).toBe(false)
  })

  it('usa o detail do servidor para os demais erros, em vez da string da axios', async () => {
    vi.mocked(chatService.sendMessage).mockRejectedValue({
      response: { status: 500, data: { code: 'INTERNAL_ERROR', detail: 'Ocorreu um erro interno no servidor.' } },
      message: 'Request failed with status code 500',
    })

    await sendOnce()

    const toast = mockToastAdd.mock.calls.at(-1)?.[0] as { detail: string }
    expect(toast.detail).toBe('Ocorreu um erro interno no servidor.')
  })
})
