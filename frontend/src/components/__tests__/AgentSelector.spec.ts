import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import AgentSelector from '../AgentSelector.vue'
import PrimeVue from 'primevue/config'

vi.mock('../../services/agent.service', () => ({
  agentService: {
    listAgents: vi.fn()
  },
  default: {
    listAgents: vi.fn()
  }
}))

import { agentService } from '../../services/agent.service'

const mockAgents = [
  { id: 'a1', name: 'Agent One', description: 'desc1' },
  { id: 'a2', name: 'Agent Two', description: 'desc2' },
  { id: 'a3', name: 'Agent Three', description: 'desc3' }
]

function mountComponent(props: Record<string, unknown> = {}) {
  return mount(AgentSelector, {
    props: {
      modelValue: null,
      ...props
    },
    global: {
      plugins: [PrimeVue],
      stubs: {
        Select: {
          template: '<select data-testid="select" :disabled="disabled"><option v-for="o in options" :key="o.id" :value="o.id">{{o.name}}</option></select>',
          props: ['modelValue', 'options', 'disabled', 'placeholder']
        },
        Skeleton: { template: '<div data-testid="skeleton" />' },
        Message: { template: '<div data-testid="message"><slot /></div>' },
        Button: { template: '<button @click="$emit(\'click\')"><slot /></button>' }
      }
    }
  })
}

describe('AgentSelector.vue', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('calls listAgents onMounted and renders options', async () => {
    vi.mocked(agentService.listAgents).mockResolvedValue({ content: mockAgents, totalElements: 3, totalPages: 1, number: 0, size: 50 } as never)
    const wrapper = mountComponent()
    await flushPromises()
    expect(agentService.listAgents).toHaveBeenCalledWith(0, 50)
    expect(wrapper.vm.agents.length).toBe(3)
  })

  it('shows 3 agents after load', async () => {
    vi.mocked(agentService.listAgents).mockResolvedValue({ content: mockAgents, totalElements: 3, totalPages: 1, number: 0, size: 50 } as never)
    const wrapper = mountComponent()
    await flushPromises()
    const select = wrapper.find('[data-testid="select"]')
    expect(select.exists()).toBe(true)
    expect(wrapper.vm.agents.map((a: { name: string }) => a.name)).toEqual(['Agent One', 'Agent Two', 'Agent Three'])
  })

  it('emits update:modelValue when onSelect called', async () => {
    vi.mocked(agentService.listAgents).mockResolvedValue({ content: mockAgents, totalElements: 3, totalPages: 1, number: 0, size: 50 } as never)
    const wrapper = mountComponent()
    await flushPromises()
    // call exposed method via vm
    ;(wrapper.vm as unknown as { onSelect: (v: string) => void }).onSelect('a2')
    expect(wrapper.emitted('update:modelValue')).toBeTruthy()
    expect(wrapper.emitted('update:modelValue')![0]).toEqual(['a2'])
  })

  it('disabled when prop disabled true', async () => {
    vi.mocked(agentService.listAgents).mockResolvedValue({ content: mockAgents, totalElements: 3, totalPages: 1, number: 0, size: 50 } as never)
    const wrapper = mountComponent({ disabled: true })
    await flushPromises()
    const select = wrapper.find('[data-testid="select"]')
    expect(select.attributes('disabled')).toBeDefined()
  })

  it('shows error Message when load fails and retry works', async () => {
    vi.mocked(agentService.listAgents).mockRejectedValueOnce(new Error('network fail'))
    const wrapper = mountComponent()
    await flushPromises()
    expect(wrapper.find('[data-testid="message"]').exists()).toBe(true)
    vi.mocked(agentService.listAgents).mockResolvedValue({ content: mockAgents, totalElements: 3, totalPages: 1, number: 0, size: 50 } as never)
    await (wrapper.vm as unknown as { loadAgents: () => Promise<void> }).loadAgents()
    await flushPromises()
    expect(wrapper.find('[data-testid="select"]').exists()).toBe(true)
  })
})
