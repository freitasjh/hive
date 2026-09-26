import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createRouter, createWebHistory } from 'vue-router'
import { createPinia, setActivePinia } from 'pinia'
import PrimeVue from 'primevue/config'
import ToastService from 'primevue/toastservice'

const mockList = vi.fn()
const mockUpsert = vi.fn()
const mockDelete = vi.fn()
const { toastAdd } = vi.hoisted(() => ({ toastAdd: vi.fn() }))

vi.mock('primevue/usetoast', () => ({
  useToast: () => ({
    add: (...args: unknown[]) => toastAdd(...args),
    remove: vi.fn(),
    removeGroup: vi.fn(),
    removeAllGroups: vi.fn(),
  }),
}))

vi.mock('../../../services/agent-admin.service', () => ({
  agentAdminService: {
    listCredentials: (...args: unknown[]) => mockList(...args),
    upsertCredential: (...args: unknown[]) => mockUpsert(...args),
    deleteCredential: (...args: unknown[]) => mockDelete(...args),
  },
}))
vi.mock('../../../services/api', () => ({
  default: { get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn(), interceptors: { request: { use: vi.fn() }, response: { use: vi.fn() } } },
}))

import CredentialsView from '../CredentialsView.vue'

interface CredentialsVm {
  $nextTick: () => Promise<void>
  apiKey: string
  baseUrl: string
  showDialog: boolean
  editing: string | null
  saveCredential: () => Promise<void>
  prefixError: string | null
}

function mountView() {
  setActivePinia(createPinia())
  const router = createRouter({
    history: createWebHistory(),
    routes: [{ path: '/', component: { template: '<div />' } }],
  })
  const w = mount(CredentialsView as unknown as object, {
    global: {
      plugins: [router, PrimeVue, ToastService],
      stubs: {
        Toast: { template: '<div />' },
        Dialog: { template: '<div><slot /><slot name="footer" /></div>' },
      },
    },
  })
  return w as unknown as {
    text: () => string
    find: (s: string) => { exists: () => boolean; trigger: (e: string) => Promise<void>; text: () => string }
    vm: CredentialsVm
  }
}

const seedStatuses = [
  { provider: 'OPENAI', configured: true, last4: 'abcd', updatedAt: '2026-01-01T00:00:00' },
  { provider: 'GEMINI', configured: false, last4: null, updatedAt: null },
  { provider: 'ANTHROPIC', configured: false, last4: null, updatedAt: null },
  { provider: 'OLLAMA', configured: false, last4: null, baseUrl: 'http://localhost:11434', updatedAt: null },
  { provider: 'OPENAI_COMPATIBLE', configured: false, last4: null, updatedAt: null },
]

describe('CredentialsView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockList.mockResolvedValue(seedStatuses)
    mockUpsert.mockResolvedValue({ provider: 'OPENAI', configured: true, last4: 'wxyz' })
    mockDelete.mockResolvedValue(undefined)
  })

  it('lists providers with status and masked key, never the secret value', async () => {
    const w = mountView()
    await flushPromises()
    expect(mockList).toHaveBeenCalled()
    const text = w.text()
    expect(text).toContain('OpenAI')
    expect(text).toContain('••••abcd')
    expect(text).not.toContain('sk-')
    expect(w.find('[data-testid="cred-row-OPENAI"]').exists()).toBe(true)
    expect(w.find('[data-testid="cred-row-GEMINI"]').exists()).toBe(true)
  })

  it('opens upsert dialog with password show/hide and inline prefix validation', async () => {
    const w = mountView()
    await flushPromises()
    await w.find('[data-testid="cred-edit-OPENAI"]').trigger('click')
    await w.vm.$nextTick()
    const dialog = w.find('[data-testid="cred-dialog"]')
    expect(dialog.exists()).toBe(true)
    expect(w.find('[data-testid="cred-key-input"]').exists()).toBe(true)
    const vm = w.vm as unknown as { apiKey: string; prefixError: string | null }
    vm.apiKey = 'bad-key'
    await w.vm.$nextTick()
    await flushPromises()
    expect(w.text()).toContain('sk-')
  })

  it('saving with blank key keeps existing (omits apiKey from PUT payload)', async () => {
    const w = mountView()
    await flushPromises()
    await w.find('[data-testid="cred-edit-OPENAI"]').trigger('click')
    await w.vm.$nextTick()
    const vm = w.vm as unknown as { apiKey: string; saveCredential: () => Promise<void> }
    vm.apiKey = ''
    await vm.saveCredential()
    await flushPromises()
    expect(mockUpsert).toHaveBeenCalled()
    const payload = mockUpsert.mock.calls[0][1] as Record<string, unknown>
    expect(payload).not.toHaveProperty('apiKey')
  })

  it('lists GO/ZEN rows with read-only baseUrl, costHint and LGPD banner in dialog', async () => {
    const w = mountView()
    await flushPromises()
    expect(w.find('[data-testid="cred-row-OPENCODE_GO"]').exists()).toBe(true)
    expect(w.find('[data-testid="cred-row-OPENCODE_ZEN"]').exists()).toBe(true)
    expect(w.find('[data-testid="cred-baseurl-OPENCODE_GO"]').text()).toContain('https://opencode.ai/zen/go/v1')
    expect(w.find('[data-testid="cred-baseurl-OPENCODE_ZEN"]').text()).toContain('https://opencode.ai/zen/v1')
    expect(w.find('[data-testid="cred-cost-OPENCODE_GO"]').text()).toContain('US$ 10/mês')
    await w.find('[data-testid="cred-edit-OPENCODE_GO"]').trigger('click')
    await w.vm.$nextTick()
    expect(w.find('[data-testid="cred-lgpd-banner"]').exists()).toBe(true)
    expect(w.find('[data-testid="cred-baseurl-readonly"]').exists()).toBe(true)
    expect(w.text()).toContain('https://opencode.ai/privacy')
  })

  it('delete asks confirmation before calling DELETE', async () => {
    const w = mountView()
    await flushPromises()
    await w.find('[data-testid="cred-delete-OPENAI"]').trigger('click')
    await w.vm.$nextTick()
    expect(w.find('[data-testid="cred-delete-confirm"]').exists()).toBe(true)
    expect(mockDelete).not.toHaveBeenCalled()
    await w.find('[data-testid="cred-delete-confirm-btn"]').trigger('click')
    await flushPromises()
    expect(mockDelete).toHaveBeenCalledWith('OPENAI')
  })

  // Regressão do bug P0: a URL fixa do vendor é readonly na tela mas alimentava o payload,
  // fazendo todo PUT de GO/Zen voltar 400 ("baseUrl é fixa do vendor").
  it.each([
    { provider: 'OPENCODE_GO', apiKey: 'go-fake-key' },
    { provider: 'OPENCODE_ZEN', apiKey: 'zen-fake-key' },
  ])('saving $provider via the Salvar button omits baseUrl from the payload', async ({ provider, apiKey }) => {
    const w = mountView()
    await flushPromises()
    await w.find(`[data-testid="cred-edit-${provider}"]`).trigger('click')
    await w.vm.$nextTick()
    w.vm.apiKey = apiKey
    await w.vm.$nextTick()

    await w.find('[data-testid="cred-save-btn"]').trigger('click')
    await flushPromises()

    expect(mockUpsert).toHaveBeenCalledWith(provider, { apiKey })
    expect(w.vm.showDialog).toBe(false)
    expect(w.vm.editing).toBeNull()
    expect(toastAdd).toHaveBeenCalledWith(expect.objectContaining({ severity: 'success' }))
    expect(mockList).toHaveBeenCalledTimes(2)
  })

  it('saving OPENCODE_GO omits baseUrl even when the read-only vendor url is sitting in form state', async () => {
    const w = mountView()
    await flushPromises()
    await w.find('[data-testid="cred-edit-OPENCODE_GO"]').trigger('click')
    await w.vm.$nextTick()
    w.vm.apiKey = 'go-fake-key'
    // eco do valor readonly exibido na tela — nunca pode virar payload
    w.vm.baseUrl = 'https://opencode.ai/zen/go/v1'
    await w.vm.$nextTick()

    await w.find('[data-testid="cred-save-btn"]').trigger('click')
    await flushPromises()

    expect(mockUpsert).toHaveBeenCalledWith('OPENCODE_GO', { apiKey: 'go-fake-key' })
  })

  it.each([
    { provider: 'OLLAMA', baseUrl: 'http://localhost:11434', apiKey: undefined },
    { provider: 'OPENAI_COMPATIBLE', baseUrl: 'https://203.0.113.10/v1', apiKey: 'custom-fake-key' },
  ])('saving $provider keeps baseUrl in the payload', async ({ provider, baseUrl, apiKey }) => {
    const w = mountView()
    await flushPromises()
    await w.find(`[data-testid="cred-edit-${provider}"]`).trigger('click')
    await w.vm.$nextTick()
    expect(w.find('[data-testid="cred-baseurl-input"]').exists()).toBe(true)
    w.vm.baseUrl = baseUrl
    if (apiKey) w.vm.apiKey = apiKey
    await w.vm.$nextTick()

    await w.find('[data-testid="cred-save-btn"]').trigger('click')
    await flushPromises()

    expect(mockUpsert).toHaveBeenCalledWith(provider, apiKey ? { apiKey, baseUrl } : { baseUrl })
    expect(toastAdd).toHaveBeenCalledWith(expect.objectContaining({ severity: 'success' }))
  })
})
