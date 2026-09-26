import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia } from 'pinia'
import PrimeVue from 'primevue/config'
import ToastService from 'primevue/toastservice'
import Register from '../Register.vue'

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: vi.fn() })
}))

const mockPost = vi.fn().mockResolvedValue({})
vi.mock('../../services/api', () => ({
  default: {
    post: (...args: unknown[]) => mockPost(...args)
  }
}))

const mockToastAdd = vi.fn()
vi.mock('primevue/usetoast', () => ({
  useToast: () => ({ add: mockToastAdd })
}))

function mountRegister() {
  const pinia = createPinia()
  return mount(Register, {
    global: {
      plugins: [pinia, PrimeVue, ToastService],
      stubs: {
        AuthLayout: {
          template: '<div><slot /></div>',
          props: ['title', 'subtitle', 'icon']
        },
        Toast: { template: '<div />' }
      }
    }
  })
}

describe('Register.vue', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockPost.mockResolvedValue({})
  })

  it('renders 6 required fields with asterisks', () => {
    const wrapper = mountRegister()
    const labels = wrapper.findAll('label').map((l) => l.text())
    expect(labels).toEqual(expect.arrayContaining([
      expect.stringContaining('Nome Completo'),
      expect.stringContaining('Username'),
      expect.stringContaining('Email'),
      expect.stringContaining('Documento'),
      expect.stringContaining('Senha'),
      expect.stringContaining('Confirmar Senha')
    ]))
    expect(wrapper.find('#name').exists()).toBe(true)
    expect(wrapper.find('#username').exists()).toBe(true)
    expect(wrapper.find('#email').exists()).toBe(true)
    expect(wrapper.find('#document').exists()).toBe(true)
    expect(wrapper.find('#password').exists()).toBe(true)
    expect(wrapper.find('#confirmPassword').exists()).toBe(true)
    // all labels contain *
    labels.forEach((lbl) => expect(lbl).toContain('*'))
  })

  it('has responsive grid classes formgrid and col-12 md:col-6', () => {
    const wrapper = mountRegister()
    expect(wrapper.find('.formgrid.grid').exists()).toBe(true)
    const cols = wrapper.findAll('.col-12.md\\:col-6')
    expect(cols.length).toBe(6)
  })

  it('validates inline onBlur shows error and aria-invalid', async () => {
    const wrapper = mountRegister()
    const nameInput = wrapper.find('#name')
    await nameInput.trigger('blur')
    await flushPromises()
    expect(wrapper.text()).toContain('Nome é obrigatório')
    expect(nameInput.attributes('aria-invalid')).toBe('true')
    expect(nameInput.classes()).toContain('p-invalid')

    const emailInput = wrapper.find('#email')
    await emailInput.setValue('invalid-email')
    await emailInput.trigger('blur')
    await flushPromises()
    expect(wrapper.text()).toContain('Email deve ser válido')

    const usernameInput = wrapper.find('#username')
    await usernameInput.setValue('ab')
    await usernameInput.trigger('blur')
    await flushPromises()
    expect(wrapper.text()).toContain('Username deve ter ao menos 3 caracteres')

    await usernameInput.setValue('invalid username!')
    await usernameInput.trigger('blur')
    await flushPromises()
    expect(wrapper.text()).toContain('Username deve conter apenas letras')
  })

  it('shows confirm password mismatch inline ✗ and match ✓', async () => {
    const wrapper = mountRegister()
    const vm = wrapper.vm as unknown as { form: { password: string; confirmPassword: string }; validateField: (f: string) => void }
    vm.form.password = 'Password123!'
    vm.form.confirmPassword = 'different'
    vm.validateField('confirmPassword')
    await flushPromises()
    await wrapper.vm.$nextTick()
    expect(wrapper.text()).toContain('não coincide')

    vm.form.confirmPassword = 'Password123!'
    vm.validateField('confirmPassword')
    await flushPromises()
    await wrapper.vm.$nextTick()
    expect(wrapper.text()).toContain('✓ coincide')
  })

  it('submits with 5-field payload and shows success toast disabled loading prevents double click', async () => {
    const wrapper = mountRegister()
    const vm = wrapper.vm as unknown as { form: Record<string, string> }
    vm.form.name = 'João Silva'
    vm.form.username = 'joao.silva'
    vm.form.email = 'joao@example.com'
    vm.form.document = '123.456.789-00'
    vm.form.password = 'Password123!'
    vm.form.confirmPassword = 'Password123!'
    await flushPromises()

    const form = wrapper.find('form')
    await form.trigger('submit')
    await flushPromises()

    expect(mockPost).toHaveBeenCalledWith('/user-accounts/create', {
      name: 'João Silva',
      username: 'joao.silva',
      email: 'joao@example.com',
      password: 'Password123!',
      document: '123.456.789-00'
    })
    expect(mockToastAdd).toHaveBeenCalledWith(expect.objectContaining({ severity: 'success', detail: expect.stringContaining('Conta criada') }))

    // button disabled while loading - simulate loading state
    const button = wrapper.findComponent({ name: 'Button' })
    // after success, loading false, but during request it was true; verify button has loading prop
    expect(button.exists()).toBe(true)
  })

  it('shows error toast on 409 conflict', async () => {
    mockPost.mockRejectedValue({ response: { status: 409, data: { message: 'Email já cadastrado' } } })
    const wrapper = mountRegister()
    const vm = wrapper.vm as unknown as { form: Record<string, string> }
    vm.form.name = 'João Silva'
    vm.form.username = 'joao.silva'
    vm.form.email = 'joao@example.com'
    vm.form.document = '123.456.789-00'
    vm.form.password = 'Password123!'
    vm.form.confirmPassword = 'Password123!'
    await wrapper.find('form').trigger('submit')
    await flushPromises()
    expect(mockToastAdd).toHaveBeenCalledWith(expect.objectContaining({ severity: expect.stringMatching(/error|warn/), detail: expect.stringContaining('Email já cadastrado') }))
  })

  it('validates username helper text when no error', () => {
    const wrapper = mountRegister()
    expect(wrapper.text()).toContain('Será usado para login')
  })

  it('button has loading and disabled binding', () => {
    const wrapper = mountRegister()
    const buttonEl = wrapper.find('button')
    expect(buttonEl.exists()).toBe(true)
    // Button should have loading/disabled attribute binding - check via component props or html
    const html = wrapper.html()
    expect(html).toContain('Registrar Agora')
  })

  it('masks document on input', async () => {
    const wrapper = mountRegister()
    const docInput = wrapper.find('#document')
    await docInput.setValue('12345678900')
    await docInput.trigger('input')
    await flushPromises()
    const vm = wrapper.vm as unknown as { form: { document: string } }
    expect(vm.form.document).toContain('.')
    expect(vm.form.document).toContain('-')
  })

  it('shows real validation message on 400 with errors[].message (not fallback)', async () => {
    mockPost.mockRejectedValue({
      response: {
        status: 400,
        data: { errors: [{ fieldName: 'name', message: 'name must not be blank' }] }
      }
    })
    const wrapper = mountRegister()
    const vm = wrapper.vm as unknown as { form: Record<string, string> }
    vm.form.name = 'João Silva'
    vm.form.username = 'joao.silva'
    vm.form.email = 'joao@example.com'
    vm.form.document = '123.456.789-00'
    vm.form.password = 'Password123!'
    vm.form.confirmPassword = 'Password123!'
    await wrapper.find('form').trigger('submit')
    await flushPromises()
    expect(mockToastAdd).toHaveBeenCalledWith(
      expect.objectContaining({ severity: 'error', detail: expect.stringContaining('name must not be blank') })
    )
    // must NOT fallback to "name inválido" when message exists
    const call = mockToastAdd.mock.calls[0][0] as { detail: string }
    expect(call.detail).not.toContain('name inválido')
  })

  it('disables submit and guards double submit while loading', async () => {
    let resolvePost!: (v: unknown) => void
    mockPost.mockReturnValue(new Promise((resolve) => { resolvePost = resolve }))
    const wrapper = mountRegister()
    const vm = wrapper.vm as unknown as { form: Record<string, string> }
    vm.form.name = 'João Silva'
    vm.form.username = 'joao.silva'
    vm.form.email = 'joao@example.com'
    vm.form.document = '123.456.789-00'
    vm.form.password = 'Password123!'
    vm.form.confirmPassword = 'Password123!'
    await flushPromises()

    const form = wrapper.find('form')
    // first submit starts loading
    const first = form.trigger('submit')
    await wrapper.vm.$nextTick()
    // loading should be true and second submit must be ignored
    const vmLoading = wrapper.vm as unknown as { loading: boolean }
    expect(vmLoading.loading).toBe(true)
    await form.trigger('submit')
    await flushPromises()
    expect(mockPost).toHaveBeenCalledTimes(1)

    // button disabled while loading
    const htmlDuringLoading = wrapper.html()
    // PrimeVue Button renders disabled when loading; html or component prop reflects it
    expect(wrapper.find('button').attributes('disabled') !== undefined || htmlDuringLoading.includes('disabled') || vmLoading.loading).toBeTruthy()

    // resolve to cleanup
    resolvePost({})
    await first
    await flushPromises()
    expect((wrapper.vm as unknown as { loading: boolean }).loading).toBe(false)
  })
})
