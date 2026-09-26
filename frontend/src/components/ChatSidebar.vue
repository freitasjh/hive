<template>
  <Drawer v-model:visible="visible" class="chat-sidebar" :dismissable="true" style="width: 320px">
    <template #header>
      <div class="flex align-items-center gap-3 py-2 px-1">
        <div class="w-2.5rem h-2.5rem bg-primary border-circle flex align-items-center justify-content-center shadow-2">
          <i class="pi pi-bolt text-amber-400 text-lg"></i>
        </div>
        <span class="text-xl font-bold tracking-tight text-900">Hive AI</span>
      </div>
    </template>

    <div class="flex flex-column h-full">
      <Button
        label="Nova Conversa"
        icon="pi pi-plus"
        class="w-full rounded-2xl mb-3"
        severity="primary"
        @click="handleNewChat"
        data-testid="new-chat-btn"
      />

      <div class="text-xs font-bold text-surface-400 uppercase tracking-wider px-2 mb-2">Histórico Recente</div>

      <div v-if="loading" class="flex flex-column gap-3 px-2">
        <Skeleton v-for="i in 3" :key="i" height="3.5rem" borderRadius="12px" />
      </div>

      <Message v-else-if="loadError" severity="error" :closable="false" class="mx-2">
        Erro ao carregar conversas
        <Button label="Tentar novamente" text size="small" class="ml-2" @click="() => loadSessions(0)" />
      </Message>

      <div v-else-if="sessions.length === 0" class="text-center py-6 px-2" data-testid="empty-state">
        <i class="pi pi-comments text-3xl text-surface-300 mb-2 block"></i>
        <p class="text-sm text-surface-500 m-0">Nenhuma conversa</p>
        <p class="text-xs text-surface-400 mt-1">Inicie uma nova conversa para ver o histórico aqui.</p>
      </div>

      <ScrollPanel v-else style="width: 100%; height: 100%" class="flex-grow-1">
        <div class="flex flex-column gap-2 px-2 pb-2">
          <div
            v-for="session in sessions"
            :key="session.id"
            :data-testid="`session-item-${session.id}`"
            :class="['flex align-items-center gap-3 p-3 rounded-2xl cursor-pointer transition-colors transition-duration-200 border-1', selectedId === session.id ? 'bg-primary-50 border-primary-200' : 'hover:bg-surface-50 border-transparent']"
            @click="handleSelect(session.id)"
          >
            <i class="pi pi-comment text-surface-400 flex-shrink-0"></i>
            <div class="flex flex-column gap-1 overflow-hidden flex-grow-1" @click.stop="handleSelect(session.id)">
              <span class="text-sm font-semibold text-900 truncate" :title="session.title">{{ session.title }}</span>
              <span class="text-xs text-surface-500">{{ formatDate(session.updateAt) }}</span>
            </div>
            <Button
              icon="pi pi-trash"
              text
              rounded
              severity="danger"
              size="small"
              :data-testid="`delete-${session.id}`"
              v-tooltip.top="'Excluir conversa'"
              @click.stop="handleDelete(session.id)"
              class="flex-shrink-0"
            />
          </div>
        </div>
      </ScrollPanel>

      <div v-if="totalPages > 1 && !loading" class="px-2 mt-2">
        <Paginator
          :rows="pageSize"
          :totalRecords="totalElements"
          :first="currentPage * pageSize"
          template="PrevPageLink CurrentPageReport NextPageLink"
          @page="onPageChange"
        />
      </div>

      <div class="mt-auto p-2 border-top-1 border-surface-100">
        <div class="flex flex-column gap-1">
          <Button icon="pi pi-cog" label="Configurações" text class="w-full text-left justify-content-start text-surface-700 font-medium" />
          <Button icon="pi pi-sign-out" label="Sair da Conta" text severity="danger" class="w-full text-left justify-content-start font-bold" @click="emit('logout')" />
        </div>
      </div>
    </div>
    <Dialog v-model:visible="showDeleteConfirm" modal header="Excluir conversa" :style="{ width: '25rem' }" data-testid="confirm-dialog">
      <p class="m-0">Tem certeza que deseja excluir esta conversa? Esta ação não pode ser desfeita.</p>
      <template #footer>
        <Button label="Cancelar" text @click="showDeleteConfirm = false" data-testid="confirm-cancel" />
        <Button label="Excluir" severity="danger" @click="confirmDelete" data-testid="confirm-delete" />
      </template>
    </Dialog>
  </Drawer>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import Drawer from 'primevue/drawer'
import Dialog from 'primevue/dialog'
import Button from 'primevue/button'
import Skeleton from 'primevue/skeleton'
import Message from 'primevue/message'
import ScrollPanel from 'primevue/scrollpanel'
import Paginator from 'primevue/paginator'
import Tooltip from 'primevue/tooltip'
import { sessionService } from '../services/session.service'
import type { ChatSessionDTO } from '../types/session'

const vTooltip = Tooltip

const props = defineProps<{
  modelValue: boolean
  selectedId?: string | null
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  select: [id: string]
  newChat: []
  logout: []
  deleted: [id: string]
}>()

const visible = computed({
  get: () => props.modelValue,
  set: (val: boolean) => emit('update:modelValue', val)
})

const selectedId = computed(() => props.selectedId ?? null)

const sessions = ref<ChatSessionDTO[]>([])
const loading = ref(false)
const loadError = ref(false)
const currentPage = ref(0)
const pageSize = ref(20)
const totalElements = ref(0)
const totalPages = ref(0)
const showDeleteConfirm = ref(false)
const pendingDeleteId = ref<string | null>(null)

function formatDate(iso: string): string {
  try {
    return new Date(iso).toLocaleDateString('pt-BR', { day: '2-digit', month: 'short', hour: '2-digit', minute: '2-digit' })
  } catch {
    return iso
  }
}

async function loadSessions(page = 0) {
  loading.value = true
  loadError.value = false
  try {
    const result = await sessionService.listSessions(page, pageSize.value)
    if (page === 0) sessions.value = result.content
    else sessions.value = result.content
    totalElements.value = result.totalElements
    totalPages.value = result.totalPages
    currentPage.value = result.number ?? page
  } catch {
    loadError.value = true
  } finally {
    loading.value = false
  }
}

function handleSelect(id: string) {
  emit('select', id)
  visible.value = false
}

function handleNewChat() {
  emit('newChat')
  visible.value = false
}

async function handleDelete(id: string) {
  pendingDeleteId.value = id
  showDeleteConfirm.value = true
}

async function confirmDelete() {
  const id = pendingDeleteId.value
  if (!id) return
  showDeleteConfirm.value = false
  try {
    await sessionService.deleteSession(id)
    sessions.value = sessions.value.filter(s => s.id !== id)
    totalElements.value = Math.max(0, totalElements.value - 1)
    emit('deleted', id)
  } catch {
    // silent fail, could toast
  } finally {
    pendingDeleteId.value = null
  }
}

function onPageChange(event: { page: number }) {
  loadSessions(event.page)
}

function refresh() {
  return loadSessions(0)
}

onMounted(() => loadSessions(0))

watch(() => props.modelValue, (open) => {
  if (open) loadSessions(currentPage.value)
})

defineExpose({ sessions, loadSessions, refresh, handleDelete, confirmDelete })
</script>

<style scoped>
.chat-sidebar {
  border-right: 1px solid var(--p-surface-100);
  background: var(--p-surface-0);
}
.truncate {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
</style>
