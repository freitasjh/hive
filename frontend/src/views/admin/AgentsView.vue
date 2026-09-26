<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useToast } from 'primevue/usetoast'
import DataTable from 'primevue/datatable'
import Column from 'primevue/column'
import Button from 'primevue/button'
import InputText from 'primevue/inputtext'
import Tag from 'primevue/tag'
import Avatar from 'primevue/avatar'
import Skeleton from 'primevue/skeleton'
import Dialog from 'primevue/dialog'
import Message from 'primevue/message'
import Toast from 'primevue/toast'
import Tooltip from 'primevue/tooltip'
import AdminEmptyState from '../../components/admin/AdminEmptyState.vue'
import { agentAdminService } from '../../services/agent-admin.service'
import type { AgentDTO } from '../../types/agent-admin'

const vTooltip = Tooltip

const router = useRouter()
const toast = useToast()

const loading = ref(false)
const items = ref<AgentDTO[]>([])
const total = ref(0)
const page = ref(0)
const size = ref(20)
const q = ref('')
const deleteTarget = ref<AgentDTO | null>(null)
const showDelete = ref(false)
const toggleTarget = ref<AgentDTO | null>(null)
const showToggle = ref(false)

async function load() {
  loading.value = true
  try {
    const res = await agentAdminService.list({ page: page.value, size: size.value, q: q.value || undefined })
    items.value = res.content
    total.value = res.totalElements
  } catch {
    toast.add({ severity: 'error', summary: 'Erro', detail: 'Falha ao carregar agentes', life: 3000 })
  } finally {
    loading.value = false
  }
}

onMounted(load)

function onPage(e: { page: number; rows: number }) {
  page.value = e.page
  size.value = e.rows
  load()
}

function onSearch() {
  page.value = 0
  load()
}

function confirmDelete(row: AgentDTO) {
  deleteTarget.value = row
  showDelete.value = true
}

async function doDelete() {
  if (!deleteTarget.value) return
  try {
    await agentAdminService.remove(deleteTarget.value.id)
    toast.add({ severity: 'success', summary: 'Removido', detail: 'Agente removido', life: 2000 })
    showDelete.value = false
    deleteTarget.value = null
    load()
  } catch (err: unknown) {
    const e = err as { response?: { status?: number; data?: { detail?: string } } }
    toast.add({ severity: 'error', summary: 'Erro', detail: e.response?.data?.detail ?? 'Falha ao remover', life: 4000 })
  }
}

function confirmToggle(row: AgentDTO) {
  if (row.isDefault) {
    toast.add({ severity: 'info', summary: 'Já é padrão', detail: 'Este agente já é o padrão', life: 2000 })
    return
  }
  toggleTarget.value = row
  showToggle.value = true
}

async function doToggle() {
  if (!toggleTarget.value) return
  try {
    await agentAdminService.setDefault(toggleTarget.value.id)
    toast.add({ severity: 'success', summary: 'Padrão alterado', detail: `${toggleTarget.value.name} agora é padrão`, life: 2000 })
    showToggle.value = false
    toggleTarget.value = null
    load()
  } catch (err: unknown) {
    const e = err as { response?: { status?: number; data?: { detail?: string } } }
    toast.add({ severity: 'error', summary: 'Erro', detail: e.response?.data?.detail ?? 'Falha ao definir padrão', life: 4000 })
  }
}

function isSoleDefault(row: AgentDTO) {
  return row.isDefault && items.value.filter(a => a.isDefault).length === 1
}

function initials(name: string) {
  return name.split(' ').map(s => s[0]).join('').slice(0,2).toUpperCase()
}
</script>

<template>
  <div class="agents-view">
    <div class="flex align-items-center justify-content-between mb-4 gap-3 flex-wrap">
      <h1 class="text-2xl font-bold m-0 text-900">Agentes</h1>
      <Button label="Novo Agente" icon="pi pi-plus" @click="router.push('/agents/new')" data-testid="new-agent-btn" />
    </div>

    <div class="flex gap-3 mb-4">
      <InputText v-model="q" placeholder="Buscar por nome..." class="w-20rem" @keyup.enter="onSearch" data-testid="search-q" />
      <Button icon="pi pi-search" outlined aria-label="Buscar" @click="onSearch" data-testid="search-btn" />
    </div>

    <div v-if="loading" class="flex flex-column gap-3">
      <Skeleton height="3rem" v-for="i in 5" :key="i" />
    </div>

    <AdminEmptyState
      v-else-if="items.length === 0"
      icon="pi pi-users"
      title="Nenhum agente encontrado"
      description="Crie seu primeiro agente para começar."
      ctaLabel="Criar primeiro agente"
      @cta="router.push('/agents/new')"
    />

    <DataTable
      v-else
      :value="items"
      paginator
      :rows="size"
      :totalRecords="total"
      lazy
      :rowsPerPageOptions="[10,20,50]"
      @page="onPage"
      dataKey="id"
      data-testid="agents-table"
    >
      <Column header="Agente">
        <template #body="{ data }">
          <div class="flex align-items-center gap-3">
            <Avatar :image="data.avatarUrl || undefined" :label="!data.avatarUrl ? initials(data.name) : undefined" shape="circle" size="normal" />
            <span class="font-medium text-900">{{ data.name }}</span>
            <Tag v-if="data.isDefault" value="⭐ Padrão" severity="warn" data-testid="badge-default" />
          </div>
        </template>
      </Column>
      <Column field="description" header="Descrição">
        <template #body="{ data }"><span class="text-sm text-surface-600">{{ data.description || '—' }}</span></template>
      </Column>
      <Column field="model" header="Modelo" />
      <Column header="Ações" style="width: 14rem">
        <template #body="{ data }">
          <div class="flex gap-1">
            <Button icon="pi pi-pencil" text rounded severity="secondary" aria-label="Editar agente" @click="router.push(`/agents/${data.id}/edit`)" :data-testid="`edit-${data.id}`" />
            <Button
              icon="pi pi-star"
              text rounded
              :severity="data.isDefault ? 'warn' : 'secondary'"
              :aria-label="data.isDefault ? 'Já é padrão' : 'Definir como padrão'"
              @click="confirmToggle(data)"
              v-tooltip.top="data.isDefault ? 'Já é padrão' : 'Definir como padrão'"
              :data-testid="`toggle-${data.id}`"
            />
            <Button
              icon="pi pi-trash"
              text rounded severity="danger"
              aria-label="Excluir agente"
              @click="confirmDelete(data)"
              :disabled="isSoleDefault(data)"
              v-tooltip.top="isSoleDefault(data) ? 'Defina outro como padrão antes de excluir.' : 'Excluir'"
              :data-testid="`delete-${data.id}`"
            />
          </div>
        </template>
      </Column>
    </DataTable>

    <Dialog v-model:visible="showDelete" modal header="Confirmar exclusão" :style="{ width: '28rem' }">
      <p>Excluir agente <strong>{{ deleteTarget?.name }}</strong>?</p>
      <!-- O CASCADE é irreversível: excluir o agente leva as conversas e as mensagens dele, e os
           vínculos dele com bases de conhecimento. Sem lixeira, sem desfazer. O diálogo precisa
           dizer isso antes do clique, não depois do 404 na tela de histórico.
           `Message` e não uma div com classe de cor: o projeto não tem Tailwind, e o Message
           respeita o preset Aura e o dark mode. -->
      <Message
        severity="warn"
        :closable="false"
        class="mt-3"
        role="alert"
        data-testid="delete-warning"
      >
        <p class="m-0 mb-2">Esta ação é permanente e não pode ser desfeita.</p>
        <ul class="m-0 pl-4">
          <li>Todas as conversas e mensagens deste agente serão removidas.</li>
          <li>Os vínculos com bases de conhecimento também serão desfeitos.</li>
        </ul>
      </Message>
      <template #footer>
        <Button label="Cancelar" text @click="showDelete=false" />
        <Button label="Excluir definitivamente" severity="danger" @click="doDelete" data-testid="confirm-delete" />
      </template>
    </Dialog>

    <Dialog v-model:visible="showToggle" modal header="Definir como padrão" :style="{ width: '28rem' }">
      <p v-if="items.some(a => a.isDefault)">Já existe padrão. Trocar para <strong>{{ toggleTarget?.name }}</strong>?</p>
      <p v-else>Definir <strong>{{ toggleTarget?.name }}</strong> como padrão?</p>
      <template #footer>
        <Button label="Cancelar" text @click="showToggle=false" />
        <Button label="Confirmar" @click="doToggle" data-testid="confirm-toggle" />
      </template>
    </Dialog>

    <Toast />
  </div>
</template>
