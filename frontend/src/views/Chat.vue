<template>
  <div class="flex flex-column h-full chat-view">
    <a href="#chat-input" class="skip-link">Pular para o campo de entrada</a>
    <ChatSidebar
      v-model="sidebarVisible"
      :selectedId="sessionId"
      @select="selectSession"
      @newChat="newChat"
      @deleted="onSessionDeleted"
      @logout="handleLogout"
      ref="sidebarRef"
    />

    <div class="flex align-items-center justify-content-between mb-4 gap-3 flex-wrap">
      <div class="flex align-items-center gap-3">
        <Button
          icon="pi pi-history"
          text
          rounded
          v-tooltip.top="'Histórico'"
          @click="sidebarVisible = true"
          class="text-surface-700"
          data-testid="open-sidebar"
        />
        <h1 class="text-2xl font-bold m-0 text-900">Hive Assistant</h1>
      </div>
      <div class="flex align-items-center gap-2">
        <PromptSelector v-model="selectedPromptTemplateId" :disabled="!!sessionId" class="w-18rem" />
      </div>
    </div>

    <div ref="chatContainer" class="flex-grow-1 overflow-y-auto px-2 py-4 relative" role="log" aria-live="polite" aria-relevant="additions">
      <div class="chat-container-centralized flex flex-column gap-6 pb-4">
        <div v-if="loadingHistory" class="flex flex-column gap-3" aria-busy="true" aria-label="Carregando histórico">
          <Skeleton v-for="i in 3" :key="i" height="5rem" borderRadius="16px" />
        </div>

        <div v-else-if="messages.length === 0" class="flex flex-column align-items-center justify-content-center text-center mt-8 fade-in-up">
          <div class="w-6rem h-6rem bg-primary rounded-2xl flex align-items-center justify-content-center mb-4 shadow-lg transform rotate-12">
            <i class="pi pi-bolt text-amber-400 text-5xl"></i>
          </div>
          <h2 class="text-900 text-3xl font-bold mb-2 tracking-tight">Bem-vindo ao <span class="text-primary">Hive AI</span></h2>
          <p class="text-surface-500 max-w-20rem m-auto line-height-3">Escolha um prompt acima e escreva sua pergunta. A resposta segue exatamente aquele prompt.</p>
          <p v-if="!selectedPromptTemplateId" class="text-sm text-amber-600 mt-3">Escolha um prompt acima para começar.</p>
          <div class="starter-prompts flex flex-wrap gap-2 justify-content-center mt-4" role="group" aria-label="Sugestões de início">
            <button
              v-for="sp in starterPrompts"
              :key="sp"
              type="button"
              class="starter-chip"
              @click="useStarter(sp)"
              :disabled="sending || !selectedPromptTemplateId"
            >{{ sp }}</button>
          </div>
        </div>

        <template v-for="(msg, idx) in messages" :key="msg.id">
          <ChatMessage
            :role="roleOf(msg.role)"
            :content="msg.content"
            :timestamp="msg.createAt"
          />
          <div v-if="roleOf(msg.role) === 'SYSTEM' && idx === messages.length - 1" class="flex justify-content-start">
            <button type="button" class="retry-btn" @click="retryLastMessage" :disabled="sending">
              Tentar novamente
            </button>
          </div>
        </template>

        <div v-if="sending" class="flex justify-content-start fade-in-up" aria-busy="true">
          <div class="assistant-msg-bubble p-3 rounded-2xl shadow-sm flex flex-column gap-2 bg-surface-0 border-1 border-surface-200 backdrop-blur-md progress-bubble">
            <span class="text-xs text-surface-500 font-medium italic">Gerando resposta...</span>
            <div class="progress-bar" role="progressbar" aria-label="Gerando resposta" aria-valuemin="0" aria-valuemax="100">
              <div class="progress-bar-indeterminate"></div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <div class="chat-footer pt-4">
      <div class="chat-container-centralized">
        <div id="chat-input" tabindex="-1">
          <ChatInput v-model="userInput" :disabled="sending || loadingHistory" @send="sendMessage" />
        </div>
        <p class="text-center text-xs text-surface-400 mt-3 px-4">
          Hive AI pode cometer erros. Considere verificar informações importantes.
        </p>
      </div>
    </div>
    <Toast />
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth.store'
import { sessionService } from '../services/session.service'
import { chatService } from '../services/chat.service'
import type { ChatMessageDTO } from '../types/session'
import Button from 'primevue/button'
import Skeleton from 'primevue/skeleton'
import Toast from 'primevue/toast'
import Tooltip from 'primevue/tooltip'
import { useToast } from 'primevue/usetoast'
import ChatSidebar from '../components/ChatSidebar.vue'
import ChatMessage from '../components/ChatMessage.vue'
import ChatInput from '../components/ChatInput.vue'
import PromptSelector from '../components/PromptSelector.vue'
import {
  LLM_CONFIG_NOT_FOUND_TITLE,
  MODEL_RETURNED_EMPTY_MESSAGE,
  MODEL_RETURNED_EMPTY_TITLE,
} from '../types/chat-messages'

const vTooltip = Tooltip

const router = useRouter()
const authStore = useAuthStore()
const toast = useToast()

const messages = ref<ChatMessageDTO[]>([])
const userInput = ref('')
const sending = ref(false)
const loadingHistory = ref(false)
const sidebarVisible = ref(false)
const sessionId = ref<string | null>(localStorage.getItem('hive_sessionId'))
const selectedPromptTemplateId = ref<string | null>(null)
const chatContainer = ref<HTMLElement | null>(null)
const sidebarRef = ref<InstanceType<typeof ChatSidebar> | null>(null)
const loadSeq = ref(0)
const lastUserMessage = ref('')

const STORAGE_KEY = 'hive_sessionId'

const starterPrompts = [
  'Explique RAG',
  'Resuma este texto',
  'Gere um checklist',
  'Traduza para inglês',
]

function roleOf(role: string): 'USER' | 'ASSISTANT' | 'SYSTEM' {
  const upper = role.toUpperCase()
  if (upper === 'USER') return 'USER'
  if (upper === 'ASSISTANT') return 'ASSISTANT'
  return 'SYSTEM'
}

async function scrollToBottom() {
  await nextTick()
  if (chatContainer.value && typeof chatContainer.value.scrollTo === 'function') {
    chatContainer.value.scrollTo({ top: chatContainer.value.scrollHeight, behavior: 'smooth' })
  }
}

function handleLogout() {
  authStore.logout()
  router.push('/login')
}

async function selectSession(id: string) {
  const seq = ++loadSeq.value
  sessionId.value = id
  localStorage.setItem(STORAGE_KEY, id)
  loadingHistory.value = true
  try {
    const page = await sessionService.getMessages(id, 0, 50)
    if (seq !== loadSeq.value) return
    messages.value = page.content
    const session = await sessionService.getSession(id).catch(() => null)
    if (seq !== loadSeq.value) return
    if (session?.promptTemplateId) selectedPromptTemplateId.value = session.promptTemplateId
    await scrollToBottom()
  } catch {
    if (seq !== loadSeq.value) return
    toast.add({ severity: 'error', summary: 'Erro', detail: 'Falha ao carregar histórico', life: 3000 })
  } finally {
    if (seq === loadSeq.value) loadingHistory.value = false
  }
}

function newChat() {
  sessionId.value = null
  messages.value = []
  selectedPromptTemplateId.value = null
  lastUserMessage.value = ''
  localStorage.removeItem(STORAGE_KEY)
}

function onSessionDeleted(id: string) {
  if (id === sessionId.value) newChat()
}

async function useStarter(prompt: string) {
  userInput.value = prompt
  await sendMessage()
}

async function sendMessage() {
  const text = userInput.value.trim()
  if (!text || sending.value) return
  if (text.length > 4000) {
    toast.add({ severity: 'warn', summary: 'Mensagem muito longa', detail: 'Máximo 4000 caracteres', life: 3000 })
    return
  }
  if (!selectedPromptTemplateId.value) {
    toast.add({ severity: 'warn', summary: 'Escolha um prompt', detail: 'Selecione um prompt antes de enviar', life: 3000 })
    return
  }

  const promptTemplateId = selectedPromptTemplateId.value
  const currentSession = sessionId.value
  const userMsgText = text
  lastUserMessage.value = userMsgText
  userInput.value = ''
  sending.value = true

  const tempUserMsg: ChatMessageDTO = {
    id: `temp-${Date.now()}`,
    sessionId: currentSession ?? 'pending',
    role: 'user',
    content: userMsgText,
    createAt: new Date().toISOString()
  }
  messages.value.push(tempUserMsg)
  await scrollToBottom()

  try {
    const resp = await chatService.sendMessage(promptTemplateId, currentSession, userMsgText)
    if (!currentSession && resp.sessionId) {
      sessionId.value = resp.sessionId
      localStorage.setItem(STORAGE_KEY, resp.sessionId)
    }
    messages.value.push({
      id: `assistant-${Date.now()}`,
      sessionId: resp.sessionId,
      role: resp.role,
      content: resp.message,
      createAt: resp.timestamp
    })
    await scrollToBottom()
    sidebarRef.value?.refresh()
  } catch (err: unknown) {
    const problem = readApiProblem(err)
    if (problem.code === 'MODEL_RETURNED_EMPTY') {
      toast.add({
        severity: 'warn',
        summary: MODEL_RETURNED_EMPTY_TITLE,
        detail: MODEL_RETURNED_EMPTY_MESSAGE,
        life: 6000,
      })
    } else if (problem.code === 'TENANT_LLM_CONFIG_NOT_FOUND') {
      toast.add({ severity: 'warn', summary: LLM_CONFIG_NOT_FOUND_TITLE, detail: problem.detail, life: 8000 })
    } else {
      toast.add({ severity: 'error', summary: 'Erro', detail: problem.detail, life: 4000 })
    }
    messages.value.push({
      id: `system-${Date.now()}`,
      sessionId: currentSession ?? '',
      role: 'system',
      content: problem.detail,
      createAt: new Date().toISOString()
    })
  } finally {
    sending.value = false
    await scrollToBottom()
  }
}

async function retryLastMessage() {
  if (!lastUserMessage.value) return
  // Remove a msg de sistema de erro (se houver) e a temp user msg do envio
  // falho, evitando bolha duplicada ao reenviar.
  const last = messages.value[messages.value.length - 1]
  if (last && roleOf(last.role) === 'SYSTEM') {
    messages.value = messages.value.slice(0, -1)
  }
  const prev = messages.value[messages.value.length - 1]
  if (prev && roleOf(prev.role) === 'USER' && String(prev.id).startsWith('temp-')) {
    messages.value = messages.value.slice(0, -1)
  }
  userInput.value = lastUserMessage.value
  await sendMessage()
}

function readApiProblem(err: unknown): { code?: string; detail: string } {
  const e = err as { response?: { data?: { code?: string; detail?: string } } }
  const data = e?.response?.data
  const detail = data?.detail?.trim()
  return {
    code: data?.code,
    detail: detail && detail.length > 0 ? detail : 'Não foi possível enviar a mensagem.',
  }
}

onMounted(async () => {
  const stored = localStorage.getItem(STORAGE_KEY)
  if (stored) {
    const seq = ++loadSeq.value
    sessionId.value = stored
    loadingHistory.value = true
    try {
      const page = await sessionService.getMessages(stored, 0, 50)
      if (seq !== loadSeq.value) return
      messages.value = page.content
      const session = await sessionService.getSession(stored).catch(() => null)
      if (seq !== loadSeq.value) return
      if (session?.promptTemplateId) selectedPromptTemplateId.value = session.promptTemplateId
      await scrollToBottom()
    } catch {
      if (seq !== loadSeq.value) return
      localStorage.removeItem(STORAGE_KEY)
      sessionId.value = null
    } finally {
      if (seq === loadSeq.value) loadingHistory.value = false
    }
  }
})
</script>

<style scoped>
.chat-view {
  max-height: calc(100vh - var(--topbar-height) - var(--footer-height) - 2rem);
}
.chat-container-centralized {
  width: 100%;
  max-width: 850px;
  margin: 0 auto;
}
.assistant-msg-bubble {
  border-bottom-left-radius: 4px !important;
}
.skip-link {
  position: absolute;
  left: -9999px;
  top: 0;
  z-index: 1000;
}
.skip-link:focus {
  left: 1rem;
  top: 1rem;
  background: var(--p-surface-0);
  padding: 0.5rem 1rem;
  border-radius: 8px;
}
.progress-bubble {
  min-width: 220px;
}
.progress-bar {
  width: 100%;
  height: 4px;
  background: var(--p-surface-200);
  border-radius: 999px;
  overflow: hidden;
}
.progress-bar-indeterminate {
  width: 40%;
  height: 100%;
  background: var(--p-primary-600);
  border-radius: 999px;
  animation: indeterminate 1.4s ease-in-out infinite;
}
@keyframes indeterminate {
  0% { transform: translateX(-100%); }
  100% { transform: translateX(350%); }
}
.starter-chip {
  padding: 0.5rem 1rem;
  border-radius: 999px;
  border: 1px solid var(--p-surface-200);
  background: var(--p-surface-0);
  color: var(--p-text-color);
  cursor: pointer;
  font-size: 0.875rem;
}
.starter-chip:hover {
  background: var(--p-primary-50);
  border-color: var(--p-primary-300);
}
.retry-btn {
  margin-top: 0.5rem;
  padding: 0.4rem 0.9rem;
  border-radius: 8px;
  border: 1px solid var(--p-amber-500);
  background: transparent;
  color: var(--p-amber-700);
  cursor: pointer;
  font-size: 0.8rem;
}
.retry-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
@keyframes fadeInUp { from { opacity:0; transform: translateY(20px);} to { opacity:1; transform: translateY(0);} }
.fade-in-up { animation: fadeInUp 0.6s cubic-bezier(0.22,1,0.36,1) forwards; }
.overflow-y-auto::-webkit-scrollbar { width: 6px; }
.overflow-y-auto::-webkit-scrollbar-track { background: transparent; }
.overflow-y-auto::-webkit-scrollbar-thumb { background: var(--p-surface-200); border-radius: 10px; }
.overflow-y-auto::-webkit-scrollbar-thumb:hover { background: var(--p-surface-300); }
.w-18rem { width: 18rem; }

/* mobile sticky input */
.chat-footer {
  position: sticky;
  bottom: 0;
  padding-bottom: env(safe-area-inset-bottom, 0);
}
.chat-view {
  max-height: 100dvh;
}
@media (prefers-reduced-motion: reduce) {
  .fade-in-up, .progress-bar-indeterminate { animation: none !important; }
}
</style>
