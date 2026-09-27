<script setup lang="ts">
import { ref, onMounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useToast } from 'primevue/usetoast'
import DataTable from 'primevue/datatable'
import Column from 'primevue/column'
import Button from 'primevue/button'
import InputText from 'primevue/inputtext'
import Select from 'primevue/select'
import Tag from 'primevue/tag'
import Skeleton from 'primevue/skeleton'
import Dialog from 'primevue/dialog'
import Toast from 'primevue/toast'
import AdminEmptyState from '../../components/admin/AdminEmptyState.vue'
import { promptService } from '../../services/prompt.service'
import type { PromptTemplateDTO } from '../../types/prompt'

const router = useRouter()
const toast = useToast()

const loading = ref(false)
const items = ref<PromptTemplateDTO[]>([])
const total = ref(0)
const page = ref(0)
const size = ref(20)
const q = ref('')
const status = ref<string | null>(null)
const deleteTarget = ref<PromptTemplateDTO | null>(null)
const showDelete = ref(false)

const statusOptions = [
  { label: 'Todos', value: null },
  { label: 'DRAFT', value: 'DRAFT' },
  { label: 'ACTIVE', value: 'ACTIVE' },
  { label: 'ARCHIVED', value: 'ARCHIVED' },
]

async function load() {
  loading.value = true
  try {
    const res = await promptService.list({ page: page.value, size: size.value, q: q.value || undefined, status: status.value || undefined })
    items.value = res.content
    total.value = res.totalElements
  } catch {
    toast.add({ severity: 'error', summary: 'Erro', detail: 'Falha ao carregar prompts', life: 3000 })
  } finally {
    loading.value = false
  }
}

onMounted(load)
watch([status], () => { page.value = 0; load() })

function onPage(e: { page: number; rows: number }) {
  page.value = e.page
  size.value = e.rows
  load()
}

function onSearch() {
  page.value = 0
  load()
}

function confirmDelete(row: PromptTemplateDTO) {
  deleteTarget.value = row
  showDelete.value = true
}

async function doDelete() {
  if (!deleteTarget.value) return
  try {
    await promptService.remove(deleteTarget.value.id)
    toast.add({ severity: 'success', summary: 'Removido', detail: 'Prompt removido', life: 2000 })
    showDelete.value = false
    deleteTarget.value = null
    load()
  } catch (err: unknown) {
    const e = err as { response?: { status?: number; data?: { code?: string; detail?: string } } }
    if (e.response?.status === 409) {
      toast.add({ severity: 'warn', summary: 'Em uso', detail: e.response.data?.detail ?? 'Este prompt está em uso por uma conversa.', life: 4000 })
    } else {
      toast.add({ severity: 'error', summary: 'Erro', detail: 'Falha ao remover', life: 3000 })
    }
  }
}

function severityFor(s: string) {
  if (s === 'ACTIVE') return 'success'
  if (s === 'DRAFT') return 'warn'
  return 'secondary'
}
</script>

<template>
  <div class="prompts-view">
    <div class="flex align-items-center justify-content-between mb-4 gap-3 flex-wrap">
      <h1 class="text-2xl font-bold m-0 text-900">Prompts</h1>
      <Button label="Novo Prompt" icon="pi pi-plus" @click="router.push('/prompts/new')" data-testid="new-prompt-btn" />
    </div>

    <div class="flex gap-3 mb-4 flex-wrap">
      <div class="flex align-items-center gap-2">
        <InputText v-model="q" placeholder="Buscar por nome, code..." class="w-20rem" @keyup.enter="onSearch" data-testid="search-q" />
        <Button icon="pi pi-search" outlined aria-label="Buscar" @click="onSearch" data-testid="search-btn" />
      </div>
      <Select v-model="status" :options="statusOptions" optionLabel="label" optionValue="value" placeholder="Status" class="w-12rem" data-testid="filter-status" />
    </div>

    <div v-if="loading" class="flex flex-column gap-3">
      <Skeleton height="3rem" v-for="i in 5" :key="i" />
    </div>

    <AdminEmptyState
      v-else-if="items.length === 0"
      icon="pi pi-file"
      title="Nenhum prompt encontrado"
      description="Crie seu primeiro prompt para começar a conversar."
      ctaLabel="Criar primeiro prompt"
      @cta="router.push('/prompts/new')"
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
      :globalFilterFields="['name','code','description']"
      class="p-datatable-sm"
      data-testid="prompts-table"
    >
      <Column field="name" header="Nome" sortable />
      <Column field="code" header="Code" sortable />
      <Column field="status" header="Status">
        <template #body="{ data }">
          <Tag :value="data.status" :severity="severityFor(data.status)" />
        </template>
      </Column>
      <Column header="Tags">
        <template #body="{ data }">
          <span class="text-sm text-surface-600">{{ (data.tags ?? []).join(', ') || '—' }}</span>
        </template>
      </Column>
      <Column field="createAt" header="Criado" sortable>
        <template #body="{ data }">{{ new Date(data.createAt).toLocaleDateString() }}</template>
      </Column>
      <Column header="Ações" style="width: 10rem">
        <template #body="{ data }">
          <div class="flex gap-1">
            <Button icon="pi pi-pencil" text rounded severity="secondary" aria-label="Editar prompt" @click="router.push(`/prompts/${data.id}/edit`)" :data-testid="`edit-${data.id}`" />
            <Button icon="pi pi-trash" text rounded severity="danger" aria-label="Excluir prompt" @click="confirmDelete(data)" :data-testid="`delete-${data.id}`" />
          </div>
        </template>
      </Column>
    </DataTable>

    <Dialog v-model:visible="showDelete" modal header="Confirmar exclusão" :style="{ width: '25rem' }">
      <p>Excluir prompt <strong>{{ deleteTarget?.name }}</strong>?</p>
      <template #footer>
        <Button label="Cancelar" text @click="showDelete=false" />
        <Button label="Excluir" severity="danger" @click="doDelete" data-testid="confirm-delete" />
      </template>
    </Dialog>

    <Toast />
  </div>
</template>
