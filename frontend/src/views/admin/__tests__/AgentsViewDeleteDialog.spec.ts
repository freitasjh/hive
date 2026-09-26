import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createRouter, createWebHistory } from 'vue-router'
import { createPinia, setActivePinia } from 'pinia'
import PrimeVue from 'primevue/config'
import ToastService from 'primevue/toastservice'

const mockList = vi.fn()
const mockRemove = vi.fn()
const mockSetDefault = vi.fn()

vi.mock('../../../services/agent-admin.service', () => ({
  agentAdminService: {
    list: (...args: unknown[]) => mockList(...args),
    remove: (...args: unknown[]) => mockRemove(...args),
    setDefault: (...args: unknown[]) => mockSetDefault(...args),
    getById: vi.fn(),
    create: vi.fn(),
    update: vi.fn(),
    getDefault: vi.fn(),
    listCredentials: vi.fn().mockResolvedValue([]),
    listProviderModels: vi.fn().mockResolvedValue({ models: [], filteredOut: [], cachedAt: new Date().toISOString() }),
    validate: vi.fn(),
  }
}))

vi.mock('../../../services/api', () => ({
  default: { get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn(), interceptors: { request: { use: vi.fn() }, response: { use: vi.fn() } } }
}))

import AgentsView from '../AgentsView.vue'

/** Agente não-padrão: o botão de lixeira fica desabilitado no único agente padrão do tenant. */
const AGENT = { id: 'a2', name: 'Bot Assistente', provider: 'OPENAI', model: 'gpt-4o-mini', isDefault: false, active: true, createAt: '', updateAt: '' }

function mountView() {
  setActivePinia(createPinia())
  const router = createRouter({
    history: createWebHistory(),
    routes: [
      { path: '/', component: { template: '<div />' } },
      { path: '/agents', component: AgentsView },
      { path: '/agents/new', component: { template: '<div />' } },
      { path: '/agents/:id/edit', component: { template: '<div />' } },
    ]
  })
  router.push('/agents')
  const wrapper = mount(AgentsView, {
    global: {
      plugins: [router, PrimeVue, ToastService],
      stubs: {
        DataTable: { template: '<div><slot /></div>' },
        Column: { template: '<div><slot /></div>' },
        Toast: { template: '<div />' },
        Dialog: { template: '<div v-if="$attrs.visible !== false"><slot /><slot name="footer" /></div>' },
      }
    }
  } as never)
  return wrapper as unknown as {
    text: () => string
    find: (s: string) => { exists: () => boolean; text: () => string; attributes: (k: string) => string | undefined; trigger: (e: string) => Promise<unknown> }
    vm: any
  }
}

/** Deixa o diálogo de exclusão aberto com um alvo — o mesmo estado que `confirmDelete` produz. */
async function openDeleteDialog(wrapper: ReturnType<typeof mountView>) {
  wrapper.vm.deleteTarget = AGENT
  wrapper.vm.showDelete = true
  await flushPromises()
}

/**
 * Excluir agente apaga em cascata as conversas, as mensagens e os vínculos com bases de conhecimento
 * (ON DELETE CASCADE em `chat_session.agent_id` e `agent_knowledge_base.agent_id`, migrations V14).
 * Não há lixeira nem desfazer. Se o aviso some da tela, o usuário apaga um histórico de conversas sem
 * saber — e o primeiro sintoma é o histórico vazio, sem nenhuma explicação.
 */
describe('AgentsView — diálogo de exclusão destrutiva', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockList.mockResolvedValue({ content: [AGENT], totalElements: 1, totalPages: 1, number: 0, size: 20 })
    mockRemove.mockResolvedValue(undefined)
  })

  it('só abre o diálogo depois de pedir a exclusão, sem chamar a API', async () => {
    const w = mountView()
    await flushPromises()
    expect(w.vm.showDelete).toBe(false)
    expect(mockRemove).not.toHaveBeenCalled()

    w.vm.confirmDelete(AGENT)
    await flushPromises()

    expect(w.vm.showDelete).toBe(true)
    expect(w.vm.deleteTarget).toEqual(AGENT)
    // Abrir o diálogo não exclui nada: a remoção só sai no clique de confirmação.
    expect(mockRemove).not.toHaveBeenCalled()
  })

  it('avisa que a exclusão é permanente e não pode ser desfeita', async () => {
    const w = mountView()
    await openDeleteDialog(w)
    expect(w.text()).toContain('permanente')
    expect(w.text()).toMatch(/n[aã]o pode ser desfeita/i)
  })

  it('avisa explicitamente que as conversas e mensagens serão removidas', async () => {
    // A frase precisa existir — é ela que separa "estou apagando um cadastro" de "estou apagando
    // conversas inteiras". Um "tem certeza?" genérico não cobre isso.
    const w = mountView()
    await openDeleteDialog(w)
    const text = w.text()
    expect(text).toContain('conversas')
    expect(text).toContain('mensagens')
    expect(text).toMatch(/ser[aã]o removidas/i)
  })

  it('avisa que os vínculos com bases de conhecimento também são desfeitos', async () => {
    const w = mountView()
    await openDeleteDialog(w)
    expect(w.text()).toContain('bases de conhecimento')
  })

  it('nomeia o agente que está sendo excluído', async () => {
    const w = mountView()
    await openDeleteDialog(w)
    expect(w.text()).toContain(AGENT.name)
  })

  it('o botão de confirmação diz que a exclusão é definitiva', async () => {
    const w = mountView()
    await openDeleteDialog(w)
    const btn = w.find('[data-testid="confirm-delete"]')
    expect(btn.exists()).toBe(true)
    expect(btn.text()).toContain('definitivamente')
  })

  it('o aviso fica marcado como role=alert para leitores de tela', async () => {
    // A exclusão é irreversível e some da tela rápido: quem navega por teclado precisa ouvi-la,
    // não apenas enxergá-la.
    const w = mountView()
    await openDeleteDialog(w)
    const warn = w.find('[data-testid="delete-warning"]')
    expect(warn.exists()).toBe(true)
    expect(warn.attributes('role')).toBe('alert')
  })

  it('confirmar chama remove com o id do agente em foco', async () => {
    const w = mountView()
    await openDeleteDialog(w)
    await w.vm.doDelete()
    expect(mockRemove).toHaveBeenCalledWith(AGENT.id)
  })
})
