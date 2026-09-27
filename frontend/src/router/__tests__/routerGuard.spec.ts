import { describe, it, expect, beforeEach, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useAuthStore } from '../../stores/auth.store'

/**
 * A guarda de rota do `router/index.ts`, exercida no router REAL.
 *
 * <p>Copiar a guarda para dentro do teste provaria que o teste sabe escrever a guarda. A versão
 * anterior deste arquivo fazia exatamente isso. O que se quer é prender o COMPORTAMENTO do
 * `beforeEach` que está no arquivo de verdade — então o router é importado e navegado.
 *
 * <p>O que este teste impede é o sinal falso: uma rota declarando `meta.permission` que ninguém lê.
 * Com a guarda no lugar, uma tela cuja permissão falta cai no /chat, e o próximo dev que copiar o
 * padrão está copiando algo que funciona.
 */

// O router importa MainLayout e as telas; só as telas de rota são lazy, então isto puxa o layout
// e o topo. As telas lazy NÃO são carregadas porque a navegação é abortada antes de resolvê-las.
vi.mock('../../components/MainLayout.vue', () => ({
  default: { template: '<div><slot /></div>' },
}))

function tokenWith(permissions: string[]): string {
  const b64 = btoa(JSON.stringify({ sub: 'u', permissions }))
    .replace(/\+/g, '-')
    .replace(/\//g, '_')
    .replace(/=+$/, '')
  return `eyJhbGciOiJIUzI1NiJ9.${b64}.sig`
}

/**
 * Navega e devolve o caminho em que o app ficou.
 *
 * <p>O reset para `/login` antes do alvo não é decoração: o router é um singleton de módulo, e
 * `push` para o MESMO path que já é o atual é um no-op do vue-router — a guarda nem roda. Sem o
 * reset, os testes depois do primeiro passariam sem exercitar nada.
 */
async function goto(path: string): Promise<string> {
  const { default: router } = await import('../index')
  await router.push('/login').catch(() => undefined)
  await router.push(path).catch(() => undefined)
  return router.currentRoute.value.path
}

describe('router — guarda de permissão', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    localStorage.clear()
  })

  it('nega /settings/llm quando o token NÃO carrega AGENT_MANAGE, e cai no chat', async () => {
    useAuthStore().setToken(tokenWith(['CHAT_SEND', 'CHAT_SESSION_MANAGE', 'PROMPT_MANAGE']))
    expect(await goto('/settings/llm')).toBe('/chat')
  })

  it('libera /settings/llm quando o token carrega a permissão', async () => {
    useAuthStore().setToken(tokenWith(['CHAT_SEND', 'AGENT_MANAGE']))
    expect(await goto('/settings/llm')).toBe('/settings/llm')
  })

  it('nega quando o token não tem claim de permissões nenhum', async () => {
    // Token sem claim é o estado em que o interceptor de 401 deixa a sessão. O guard não pode abrir
    // a tela "só por enquanto" e deixar a decisão para o primeiro clique.
    useAuthStore().setToken(tokenWith([]))
    expect(await goto('/settings/llm')).toBe('/chat')
  })

  it('manda para o login uma rota que exige sessão quando não há token', async () => {
    expect(await goto('/settings/llm')).toBe('/login')
  })

  it('não consulta permissão em rota que não declara uma', async () => {
    useAuthStore().setToken(tokenWith([]))
    expect(await goto('/prompts')).toBe('/prompts')
  })
})
