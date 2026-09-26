import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import PrimeVue from 'primevue/config'
import ProviderSelect from '../ProviderSelect.vue'

function mountSelect(props: Record<string, unknown> = {}) {
  return mount(ProviderSelect, {
    props: { modelValue: 'OPENAI', ...props },
    global: { plugins: [PrimeVue] },
  } as never)
}

describe('ProviderSelect', () => {
  it('renders logo, name, description and cost hint per vendor', () => {
    const w = mountSelect()
    const text = w.text()
    expect(text).toContain('OpenAI')
    expect(text).toContain('Google Gemini')
    expect(text).toContain('Anthropic Claude')
    expect(text).toContain('Ollama')
    expect(w.find('[data-testid="provider-option-OPENAI"]').exists()).toBe(true)
    expect(w.find('[data-testid="provider-option-GEMINI"]').exists()).toBe(true)
    expect(w.find('[data-testid="provider-option-ANTHROPIC"]').exists()).toBe(true)
    expect(w.find('[data-testid="provider-option-OLLAMA"]').exists()).toBe(true)
  })

  it('renders all 7 vendors in VENDOR_ORDER with Custom label visible', () => {
    const w = mountSelect()
    const text = w.text()
    expect(text).toContain('OpenAI')
    expect(text).toContain('Google Gemini')
    expect(text).toContain('Anthropic Claude')
    expect(text).toContain('Ollama')
    expect(text).toContain('OpenCode GO')
    expect(text).toContain('OpenCode Zen')
    expect(text).toContain('Custom (endpoint próprio)')
    expect(w.find('[data-testid="provider-option-OPENAI"]').exists()).toBe(true)
    expect(w.find('[data-testid="provider-option-GEMINI"]').exists()).toBe(true)
    expect(w.find('[data-testid="provider-option-ANTHROPIC"]').exists()).toBe(true)
    expect(w.find('[data-testid="provider-option-OLLAMA"]').exists()).toBe(true)
    expect(w.find('[data-testid="provider-option-OPENCODE_GO"]').exists()).toBe(true)
    expect(w.find('[data-testid="provider-option-OPENCODE_ZEN"]').exists()).toBe(true)
    expect(w.find('[data-testid="provider-option-OPENAI_COMPATIBLE"]').exists()).toBe(true)
  })

  it('orders vendors as OPENAI, GEMINI, ANTHROPIC, OLLAMA, GO, ZEN, COMPATIBLE', () => {
    const w = mountSelect()
    const cards = w.findAll('[data-testid^="provider-option-"]')
    expect(cards.map((c) => c.attributes('data-testid'))).toEqual([
      'provider-option-OPENAI',
      'provider-option-GEMINI',
      'provider-option-ANTHROPIC',
      'provider-option-OLLAMA',
      'provider-option-OPENCODE_GO',
      'provider-option-OPENCODE_ZEN',
      'provider-option-OPENAI_COMPATIBLE',
    ])
  })

  it('shows read-only baseUrl and costHint for GO/ZEN', () => {
    const w = mountSelect()
    expect(w.find('[data-testid="provider-baseurl-OPENCODE_GO"]').exists()).toBe(true)
    expect(w.find('[data-testid="provider-baseurl-OPENCODE_GO"]').text()).toContain('https://opencode.ai/zen/go/v1')
    expect(w.find('[data-testid="provider-baseurl-OPENCODE_ZEN"]').exists()).toBe(true)
    expect(w.find('[data-testid="provider-cost-OPENCODE_GO"]').text()).toContain('US$ 10/mês')
    expect(w.find('[data-testid="provider-cost-OPENCODE_ZEN"]').text()).toContain('Pay-as-you-go')
    expect(w.find('[data-testid="provider-baseurl-OPENAI"]').exists()).toBe(false)
  })

  it('emits update:modelValue on select with keyboard-accessible radios', async () => {
    const w = mountSelect()
    const radio = w.find('[data-testid="provider-option-GEMINI"] input[type="radio"]')
    expect(radio.exists()).toBe(true)
    expect(radio.attributes('aria-label')).toContain('Gemini')
    await radio.setValue()
    expect(w.emitted('update:modelValue')).toBeTruthy()
    expect(w.emitted('update:modelValue')![0]).toEqual(['GEMINI'])
  })

  it('marks current value as checked', () => {
    const w = mountSelect({ modelValue: 'OLLAMA' })
    const radio = w.find('[data-testid="provider-option-OLLAMA"] input[type="radio"]')
    expect((radio.element as HTMLInputElement).checked).toBe(true)
  })
})
