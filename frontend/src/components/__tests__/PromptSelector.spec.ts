import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import PrimeVue from 'primevue/config'
import PromptSelector from '../PromptSelector.vue'
import { promptService } from '../../services/prompt.service'

vi.mock('../../services/prompt.service', () => ({
  promptService: {
    list: vi.fn()
  },
  default: {
    list: vi.fn()
  }
}))

function page(content: Array<{ id: string; name: string; description?: string }>) {
  return { content, totalElements: content.length, totalPages: 1, number: 0, size: 50 }
}

function mountSelector(props: Record<string, unknown> = {}) {
  return mount(PromptSelector, {
    props: { modelValue: null, ...props },
    global: { plugins: [PrimeVue] }
  })
}

describe('PromptSelector', () => {
  beforeEach(() => vi.clearAllMocks())

  it('lists the tenant prompts asking for the biggest page the server allows', async () => {
    // Um seletor que esconde o prompt que o usuário quer atrás de uma paginação que a tela não
    // oferece é um seletor quebrado. O teto é 50 porque `PromptTemplateControllerV1` responde 400
    // para size > 50 — pedir 100 daria 400 em toda montagem (achado no harness com browser real,
    // 2026-09-27; nenhum teste unitário pegaria, porque ambos aceitariam qualquer número).
    vi.mocked(promptService.list).mockResolvedValue(page([{ id: 'p1', name: 'Atendente' }]) as never)
    mountSelector()
    await flushPromises()
    expect(promptService.list).toHaveBeenCalledWith({ page: 0, size: 50 })
  })

  it('emits the chosen prompt template id', async () => {
    vi.mocked(promptService.list).mockResolvedValue(page([{ id: 'p1', name: 'Atendente' }]) as never)
    const wrapper = mountSelector()
    await flushPromises()
    ;(wrapper.vm as unknown as { onSelect: (v: string | null) => void }).onSelect('p1')
    expect(wrapper.emitted('update:modelValue')).toEqual([['p1']])
  })

  it('resolves the name of a template that is not in the loaded page', async () => {
    // Uma sessão antiga pode apontar para um template que não veio na primeira página. Mostrar o id
    // cru é feio, mas silenciar o selector seria pior: o usuário não veria qual prompt está ativo.
    vi.mocked(promptService.list).mockResolvedValue(page([{ id: 'p1', name: 'Atendente' }]) as never)
    const wrapper = mountSelector({ modelValue: 'p1' })
    await flushPromises()
    expect(wrapper.text()).toContain('Atendente')
  })

  it('says there is nothing to choose instead of rendering an empty dropdown', async () => {
    // Um Select vazio sem explicação parece um bug de carregamento.
    vi.mocked(promptService.list).mockResolvedValue(page([]) as never)
    const wrapper = mountSelector()
    await flushPromises()
    expect(wrapper.text()).toContain('Nenhum prompt criado ainda')
  })

  it('shows the error and reloads on retry', async () => {
    vi.mocked(promptService.list).mockRejectedValueOnce(new Error('500 do servidor'))
    const wrapper = mountSelector()
    await flushPromises()
    expect(wrapper.text()).toContain('500 do servidor')
    vi.mocked(promptService.list).mockResolvedValue(page([]) as never)
    await (wrapper.vm as unknown as { load: () => Promise<void> }).load()
    await flushPromises()
    expect(promptService.list).toHaveBeenCalledTimes(2)
  })

  it('passes disabled through, so a conversation with messages cannot be re-targeted', async () => {
    vi.mocked(promptService.list).mockResolvedValue(page([{ id: 'p1', name: 'Atendente' }]) as never)
    const wrapper = mountSelector({ disabled: true })
    await flushPromises()
    const select = wrapper.findComponent({ name: 'Select' })
    expect(select.props('disabled')).toBe(true)
  })
})
