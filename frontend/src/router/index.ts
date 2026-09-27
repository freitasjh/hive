import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '../stores/auth.store'
import { hasPermission } from '../utils/jwtPermissions'
import MainLayout from '../components/MainLayout.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { 
      path: '/login', 
      name: 'login', 
      component: () => import('../views/Login.vue') 
    },
    { 
      path: '/register', 
      name: 'register', 
      component: () => import('../views/Register.vue') 
    },
    {
      path: '/',
      component: MainLayout,
      meta: { requiresAuth: true },
      children: [
        { 
          path: '', 
          redirect: '/chat' 
        },
        { 
          path: 'chat', 
          name: 'chat', 
          component: () => import('../views/Chat.vue') 
        },
        { 
          path: 'prompts', 
          name: 'prompts', 
          component: () => import('../views/admin/PromptsView.vue'),
          meta: { requiresAuth: true }
        },
        { 
          path: 'prompts/new', 
          name: 'prompt-new', 
          component: () => import('../views/admin/PromptEditorView.vue'),
          meta: { requiresAuth: true }
        },
        { 
          path: 'prompts/:id/edit', 
          name: 'prompt-edit', 
          component: () => import('../views/admin/PromptEditorView.vue'),
          meta: { requiresAuth: true }
        },
        {
          path: 'admin/prompts',
          redirect: '/prompts'
        },
        {
          path: 'settings/providers',
          name: 'settings-providers',
          component: () => import('../views/admin/CredentialsView.vue'),
          meta: { requiresAuth: true, permission: 'AGENT_MANAGE' }
        },
        {
          // Fica ao lado das credenciais de propósito: as duas telas dizem "com o que e com qual
          // chave este tenant conversa", e separá-las é como um tenant fica com chave e sem modelo.
          path: 'settings/llm',
          name: 'settings-llm',
          component: () => import('../views/admin/LlmConfigView.vue'),
          meta: { requiresAuth: true, permission: 'AGENT_MANAGE' }
        }
      ]
    }
  ]
})

/**
 * Guarda de rota em duas camadas.
 *
 * <p>1. `requiresAuth` sem token → login. Isso é a porta de verdade da sessão.
 *
 * <p>2. `meta.permission` declarado e ausente no token → /chat. Esta camada EXISTE e é consultada:
 * o backend tem {@code @HasPermission} em cada endpoint e é ele que impede a operação, mas um meta
 * que declara uma permissão e não é lido por ninguém é pior que um meta ausente — o próximo dev
 * copia o padrão, acredita que está protegido, e escreve uma tela atrás dele.
 *
 * <p>Por que /chat e não uma tela de 403: não há tela de acesso negado no produto, e o chat é onde
 * qualquer usuário autenticado tem pelo menos o que fazer. A negação de verdade aparece como 403 no
 * primeiro clique que o gate deixou passar.
 */
router.beforeEach((to) => {
  const authStore = useAuthStore()
  if (to.meta.requiresAuth && !authStore.token) {
    return { name: 'login' }
  }
  const permission = to.meta.permission as string | undefined
  if (permission && !hasPermission(authStore.token, permission)) {
    return { name: 'chat' }
  }
  return true
})

export default router
