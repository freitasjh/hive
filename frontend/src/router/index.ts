import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '../stores/auth.store'
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
          path: 'agents', 
          name: 'agents', 
          component: () => import('../views/admin/AgentsView.vue'),
          meta: { requiresAuth: true }
        },
        { 
          path: 'agents/new', 
          name: 'agent-new', 
          component: () => import('../views/admin/AgentEditorView.vue'),
          meta: { requiresAuth: true }
        },
        { 
          path: 'agents/:id/edit', 
          name: 'agent-edit', 
          component: () => import('../views/admin/AgentEditorView.vue'),
          meta: { requiresAuth: true }
        },
        { 
          path: 'knowledge', 
          name: 'knowledge', 
          component: () => import('../views/admin/KnowledgeBaseList.vue'),
          meta: { requiresAuth: true }
        },
        { 
          path: 'knowledge/new', 
          name: 'knowledge-new', 
          component: () => import('../views/admin/KnowledgeBaseDetail.vue'),
          meta: { requiresAuth: true }
        },
        { 
          path: 'knowledge/:id/edit', 
          name: 'knowledge-edit', 
          component: () => import('../views/admin/KnowledgeBaseDetail.vue'),
          meta: { requiresAuth: true }
        },
        {
          path: 'admin/prompts',
          redirect: '/prompts'
        },
        {
          path: 'admin/agents',
          redirect: '/agents'
        },
        {
          path: 'settings/providers',
          name: 'settings-providers',
          component: () => import('../views/admin/CredentialsView.vue'),
          meta: { requiresAuth: true, permission: 'AGENT_MANAGE' }
        }
      ]
    }
  ]
})

router.beforeEach((to) => {
  const authStore = useAuthStore()
  if (to.meta.requiresAuth && !authStore.token) {
    return { name: 'login' }
  }
  return true
})

export default router
