<template>
  <AuthLayout
    title="Hive AI"
    subtitle="Junte-se à orquestração inteligente de IA"
    icon="pi pi-user-plus"
  >
    <Toast position="top-right" />
    <form @submit.prevent="handleRegister" class="flex flex-column gap-4" novalidate>
      <div class="formgrid grid">
        <!-- Nome Completo -->
        <div class="field col-12 md:col-6 flex flex-column gap-2">
          <label for="name" class="text-900 font-semibold ml-1">Nome Completo *</label>
          <InputText
            id="name"
            v-model="form.name"
            placeholder="Seu nome completo"
            autocomplete="name"
            aria-required="true"
            :aria-invalid="!!errors.name"
            :class="{ 'p-invalid': !!errors.name }"
            class="w-full p-inputtext-lg"
            @blur="validateField('name')"
          />
          <small v-if="errors.name" role="alert" class="text-red-500">{{ errors.name }}</small>
        </div>

        <!-- Username -->
        <div class="field col-12 md:col-6 flex flex-column gap-2">
          <label for="username" class="text-900 font-semibold ml-1">Username *</label>
          <InputText
            id="username"
            v-model="form.username"
            placeholder="seu.username"
            autocomplete="username"
            aria-required="true"
            :aria-invalid="!!errors.username"
            :class="{ 'p-invalid': !!errors.username }"
            class="w-full p-inputtext-lg"
            @blur="validateField('username')"
          />
          <small v-if="errors.username" role="alert" class="text-red-500">{{ errors.username }}</small>
          <small v-else class="text-surface-500">Será usado para login</small>
        </div>

        <!-- Email -->
        <div class="field col-12 md:col-6 flex flex-column gap-2">
          <label for="email" class="text-900 font-semibold ml-1">Email *</label>
          <InputText
            id="email"
            v-model="form.email"
            type="email"
            placeholder="seu@email.com"
            autocomplete="email"
            aria-required="true"
            :aria-invalid="!!errors.email"
            :class="{ 'p-invalid': !!errors.email }"
            class="w-full p-inputtext-lg"
            @blur="validateField('email')"
          />
          <small v-if="errors.email" role="alert" class="text-red-500">{{ errors.email }}</small>
        </div>

        <!-- Documento -->
        <div class="field col-12 md:col-6 flex flex-column gap-2">
          <label for="document" class="text-900 font-semibold ml-1">Documento (CPF/CNPJ) *</label>
          <InputText
            id="document"
            v-model="form.document"
            placeholder="000.000.000-00"
            autocomplete="off"
            aria-required="true"
            :aria-invalid="!!errors.document"
            :class="{ 'p-invalid': !!errors.document }"
            class="w-full p-inputtext-lg"
            maxlength="18"
            @blur="validateField('document')"
            @input="onDocumentInput"
          />
          <small v-if="errors.document" role="alert" class="text-red-500">{{ errors.document }}</small>
        </div>

        <!-- Senha -->
        <div class="field col-12 md:col-6 flex flex-column gap-2">
          <label for="password" class="text-900 font-semibold ml-1">Senha *</label>
          <Password
            id="password"
            v-model="form.password"
            placeholder="Mínimo 8 caracteres"
            autocomplete="new-password"
            aria-required="true"
            :aria-invalid="!!errors.password"
            :class="{ 'p-invalid': !!errors.password }"
            class="w-full"
            input-class="w-full p-inputtext-lg"
            :feedback="true"
            toggle-mask
            prompt-label="Digite a senha"
            weak-label="Fraco"
            medium-label="Médio"
            strong-label="Forte"
            @blur="validateField('password')"
          />
          <small v-if="errors.password" role="alert" class="text-red-500">{{ errors.password }}</small>
          <small v-else-if="form.password" :class="passwordStrengthClass">{{ passwordStrengthLabel }}</small>
        </div>

        <!-- Confirmar Senha -->
        <div class="field col-12 md:col-6 flex flex-column gap-2">
          <label for="confirmPassword" class="text-900 font-semibold ml-1">Confirmar Senha *</label>
          <Password
            id="confirmPassword"
            v-model="form.confirmPassword"
            placeholder="Digite novamente"
            autocomplete="new-password"
            aria-required="true"
            :aria-invalid="!!errors.confirmPassword"
            :class="{ 'p-invalid': !!errors.confirmPassword }"
            class="w-full"
            input-class="w-full p-inputtext-lg"
            :feedback="false"
            toggle-mask
            @blur="validateField('confirmPassword')"
          />
          <small v-if="errors.confirmPassword" role="alert" class="text-red-500">{{ errors.confirmPassword }}</small>
          <small v-else-if="form.confirmPassword" :class="confirmPasswordMatch ? 'text-green-500' : 'text-red-500'">
            {{ confirmPasswordMatch ? '✓ coincide' : '✗ não coincide' }}
          </small>
        </div>
      </div>

      <div class="flex align-items-center justify-content-between mt-1">
        <router-link to="/login" class="text-primary hover:text-amber-500 font-bold no-underline transition-colors transition-duration-200 ml-1">
          Já possui uma conta? Entrar
        </router-link>
      </div>

      <Button
        label="Registrar Agora"
        type="submit"
        class="w-full mt-3 p-button-lg shadow-2"
        :loading="loading"
        :disabled="loading"
      />
    </form>
  </AuthLayout>
</template>

<script setup lang="ts">
import { reactive, ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { useToast } from 'primevue/usetoast'
import api from '../services/api'
import InputText from 'primevue/inputtext'
import Password from 'primevue/password'
import Button from 'primevue/button'
import Toast from 'primevue/toast'
import AuthLayout from '../components/AuthLayout.vue'

const router = useRouter()
const toast = useToast()

const loading = ref(false)

const form = reactive({
  name: '',
  username: '',
  email: '',
  document: '',
  password: '',
  confirmPassword: ''
})

const errors = reactive({
  name: '',
  username: '',
  email: '',
  document: '',
  password: '',
  confirmPassword: ''
})

const emailRegex = /^[^@\s]+@[^@\s]+\.[^@\s]+$/
const usernameRegex = /^[a-zA-Z0-9._]+$/

function formatDocument(value: string): string {
  const digits = value.replace(/\D/g, '')
  if (digits.length <= 11) {
    // CPF: 000.000.000-00
    return digits
      .replace(/(\d{3})(\d)/, '$1.$2')
      .replace(/(\d{3})(\d)/, '$1.$2')
      .replace(/(\d{3})(\d{1,2})$/, '$1-$2')
  }
  // CNPJ: 00.000.000/0000-00
  return digits
    .slice(0, 14)
    .replace(/^(\d{2})(\d)/, '$1.$2')
    .replace(/^(\d{2})\.(\d{3})(\d)/, '$1.$2.$3')
    .replace(/\.(\d{3})(\d)/, '.$1/$2')
    .replace(/(\d{4})(\d)/, '$1-$2')
}

function onDocumentInput(event: Event) {
  const target = event.target as HTMLInputElement
  form.document = formatDocument(target.value)
}

const passwordStrength = computed(() => {
  const pwd = form.password
  if (!pwd) return 0
  let score = 0
  if (pwd.length >= 8) score++
  if (/[A-Z]/.test(pwd)) score++
  if (/[0-9]/.test(pwd)) score++
  if (/[^A-Za-z0-9]/.test(pwd)) score++
  return score
})

const passwordStrengthLabel = computed(() => {
  const s = passwordStrength.value
  if (s <= 1) return 'Senha fraca'
  if (s === 2) return 'Senha média'
  return 'Senha forte'
})

const passwordStrengthClass = computed(() => {
  const s = passwordStrength.value
  if (s <= 1) return 'text-red-500'
  if (s === 2) return 'text-yellow-500'
  return 'text-green-500'
})

const confirmPasswordMatch = computed(() => {
  return form.password !== '' && form.confirmPassword !== '' && form.password === form.confirmPassword
})

function validateField(field: keyof typeof form) {
  switch (field) {
    case 'name':
      if (!form.name.trim()) errors.name = 'Nome é obrigatório'
      else if (form.name.trim().length < 2) errors.name = 'Nome deve ter ao menos 2 caracteres'
      else if (form.name.length > 80) errors.name = 'Nome deve ter no máximo 80 caracteres'
      else errors.name = ''
      break
    case 'username':
      if (!form.username.trim()) errors.username = 'Username é obrigatório'
      else if (form.username.length < 3) errors.username = 'Username deve ter ao menos 3 caracteres'
      else if (form.username.length > 40) errors.username = 'Username deve ter no máximo 40 caracteres'
      else if (!usernameRegex.test(form.username)) errors.username = 'Username deve conter apenas letras, números, ponto e underscore'
      else errors.username = ''
      break
    case 'email':
      if (!form.email.trim()) errors.email = 'Email é obrigatório'
      else if (!emailRegex.test(form.email)) errors.email = 'Email deve ser válido'
      else if (form.email.length > 255) errors.email = 'Email deve ter no máximo 255 caracteres'
      else errors.email = ''
      break
    case 'document':
      if (!form.document.trim()) errors.document = 'Documento é obrigatório'
      else {
        const digits = form.document.replace(/\D/g, '')
        if (digits.length < 11) errors.document = 'Documento deve ter ao menos 11 caracteres'
        else if (digits.length > 14 || form.document.length > 18) errors.document = 'Documento deve ter entre 11 e 18 caracteres'
        else errors.document = ''
      }
      break
    case 'password':
      if (!form.password) errors.password = 'Senha é obrigatória'
      else if (form.password.length < 8) errors.password = 'Senha deve ter ao menos 8 caracteres'
      else if (form.password.length > 64) errors.password = 'Senha deve ter no máximo 64 caracteres'
      else errors.password = ''
      if (form.confirmPassword) validateField('confirmPassword')
      break
    case 'confirmPassword':
      if (!form.confirmPassword) errors.confirmPassword = 'Confirmação de senha é obrigatória'
      else if (form.password !== form.confirmPassword) errors.confirmPassword = 'As senhas não coincidem'
      else errors.confirmPassword = ''
      break
  }
}

function validateAll(): boolean {
  (Object.keys(form) as (keyof typeof form)[]).forEach((k) => validateField(k))
  return !Object.values(errors).some((e) => e !== '')
}

function focusFirstInvalid() {
  if (errors.name) {
    const el = document.getElementById('name') as HTMLInputElement | null
    el?.focus()
  } else if (errors.username) {
    const el = document.getElementById('username') as HTMLInputElement | null
    el?.focus()
  } else if (errors.email) {
    const el = document.getElementById('email') as HTMLInputElement | null
    el?.focus()
  } else if (errors.document) {
    const el = document.getElementById('document') as HTMLInputElement | null
    el?.focus()
  } else if (errors.password) {
    const el = document.getElementById('password') as HTMLInputElement | null
    el?.focus()
  } else if (errors.confirmPassword) {
    const el = document.getElementById('confirmPassword') as HTMLInputElement | null
    el?.focus()
  }
}

const handleRegister = async () => {
  if (loading.value) return
  const isValid = validateAll()
  if (!isValid) {
    focusFirstInvalid()
    return
  }

  loading.value = true
  try {
    await api.post('/user-accounts/create', {
      name: form.name,
      username: form.username,
      email: form.email,
      password: form.password,
      document: form.document
    })
    toast.add({ severity: 'success', summary: 'Sucesso', detail: 'Conta criada! Redirecionando...', life: 3000 })
    setTimeout(() => router.push('/login'), 2000)
  } catch (error: unknown) {
    let detail = 'Erro inesperado. Tente novamente.'
    let severity: 'error' | 'warn' = 'error'
    const axiosError = error as { response?: { status?: number; data?: { message?: string; detailMessage?: string; detail?: string; errors?: { fieldName?: string; message?: string }[] } }; message?: string }
    const status = axiosError?.response?.status
    const data = axiosError?.response?.data
    if (status === 400) {
      if (data?.errors && Array.isArray(data.errors) && data.errors.length > 0) {
        detail = data.errors.map((e) => e.message || `${e.fieldName} inválido`).join(', ')
      } else if (data?.message) {
        detail = data.message
      } else if (data && (data as { detailMessage?: string }).detailMessage) {
        detail = (data as { detailMessage: string }).detailMessage
      } else {
        detail = 'Dados inválidos. Verifique os campos.'
      }
    } else if (status === 409) {
      if (data?.message) detail = data.message
      else if (data && (data as { detail?: string }).detail) detail = (data as { detail: string }).detail
      else detail = 'Email, username ou documento já cadastrado.'
      severity = 'warn'
    } else if (!status) {
      detail = 'Erro de rede. Verifique sua conexão.'
    }

    toast.add({ severity, summary: status === 409 ? 'Conflito' : 'Erro', detail, life: 4000 })
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.field {
  margin-bottom: 0;
}
</style>
