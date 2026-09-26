<template>
  <AuthLayout 
    title="Hive AI" 
    subtitle="Faça login para continuar sua jornada inteligente" 
    icon="pi pi-bolt"
  >
    <form @submit.prevent="handleLogin" class="flex flex-column gap-4">
      <div class="flex flex-column gap-2">
        <label for="username" class="text-900 font-semibold ml-1">Usuário</label>
        <InputText id="username" v-model="username" type="text" placeholder="Seu nome de usuário" class="w-full p-inputtext-lg" required />
      </div>

      <div class="flex flex-column gap-2">
        <label for="password" class="text-900 font-semibold ml-1">Senha</label>
        <InputText id="password" v-model="password" type="password" placeholder="Sua senha secreta" class="w-full p-inputtext-lg" required />
      </div>

      <div class="flex align-items-center justify-content-between mt-1">
        <router-link to="/register" class="text-primary hover:text-amber-500 font-bold no-underline transition-colors transition-duration-200 ml-1">
          Criar conta gratuita
        </router-link>
      </div>

      <Button label="Entrar Agora" type="submit" class="w-full mt-3 p-button-lg shadow-2" :loading="loading"></Button>
    </form>
  </AuthLayout>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth.store'
import api from '../services/api'
import InputText from 'primevue/inputtext'
import Button from 'primevue/button'
import AuthLayout from '../components/AuthLayout.vue'

const username = ref('')
const password = ref('')
const loading = ref(false)
const router = useRouter()
const authStore = useAuthStore()

const handleLogin = async () => {
  loading.value = true
  try {
    const { data } = await api.post('/auth/login', { 
      username: username.value, 
      password: password.value
    })
    authStore.setToken(data.accessToken)
    router.push('/chat')
  } catch (error) {
    console.error('Login error:', error)
  } finally {
    loading.value = false
  }
}
</script>
