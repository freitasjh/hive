<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useToast } from 'primevue/usetoast'
import Button from 'primevue/button'
import Card from 'primevue/card'
import InputText from 'primevue/inputtext'
import Tag from 'primevue/tag'
import Skeleton from 'primevue/skeleton'
import Dialog from 'primevue/dialog'
import Toast from 'primevue/toast'
import AdminEmptyState from '../../components/admin/AdminEmptyState.vue'
import { useDebouncedSearch } from '../../composables/useDebouncedSearch'
import { knowledgeService } from '../../services/knowledge.service'
import type { KnowledgeBaseDTO } from '../../types/knowledge'

const router = useRouter()
const toast = useToast()

const loading = ref(false)
const items = ref<KnowledgeBaseDTO[]>([])
const total = ref(0)
const page = ref(0)
const size = ref(20)
const { query: q, debounced, flush } = useDebouncedSearch(400)
const deleteTarget = ref<KnowledgeBaseDTO | null>(null)
const showDelete = ref(false)
const inUseAgents = ref<string[]>([])

async function load() {
  loading.value = true
  try {
    const res = await knowledgeService.list({ page: page.value, size: size.value, q: debounced.value || undefined })
    items.value = res.content
    total.value = res.totalElements
  } catch {
    toast.add({ severity: 'error', summary: 'Erro', detail: 'Falha ao carregar bases de conhecimento', life: 3000 })
  } finally {
    loading.value = false
  }
}

onMounted(load)
watch(debounced, () => {
  page.value = 0
  load()
})

function onSearch() {
  flush()
  page.value = 0
  load()
}

function severityFor(status: string): 'warn' | 'info' | 'success' | 'danger' | 'secondary' {
  if (status === 'PENDING') return 'warn'
  if (status === 'INDEXING') return 'info'
  if (status === 'READY') return 'success'
  if (status === 'FAILED') return 'danger'
  if (status === 'ACTIVE') return 'success'
  if (status === 'ARCHIVED') return 'secondary'
  return 'secondary'
}

async function doReindex(kb: KnowledgeBaseDTO) {
  try {
    await knowledgeService.reindex(kb.id)
    toast.add({ severity: 'success', summary: 'Reindexação iniciada', detail: `Base "${kb.name}" será reindexada`, life: 2500 })
  } catch {
    toast.add({ severity: 'error', summary: 'Erro', detail: 'Falha ao iniciar reindexação', life: 3000 })
  }
}

function confirmDelete(kb: KnowledgeBaseDTO) {
  deleteTarget.value = kb
  inUseAgents.value = []
  showDelete.value = true
}

async function doDelete(force = false) {
  if (!deleteTarget.value) return
  try {
    await knowledgeService.remove(deleteTarget.value.id, force)
    toast.add({ severity: 'success', summary: 'Removida', detail: 'Base de conhecimento removida', life: 2000 })
    showDelete.value = false
    deleteTarget.value = null
    load()
  } catch (err: unknown) {
    const e = err as { response?: { status?: number; data?: { code?: string; detail?: string; agents?: string[] } } }
    if (e.response?.status === 409) {
      const agents = (e.response.data as { agents?: string[] })?.agents ?? []
      inUseAgents.value = agents
      const detail = agents.length
        ? `Base em uso pelos agentes: ${agents.join(', ')}. Use "Excluir mesmo assim" para forçar.`
        : (e.response.data?.detail ?? 'Base em uso por agentes')
      toast.add({ severity: 'warn', summary: 'Base em uso (KB_IN_USE)', detail, life: 6000 })
    } else {
      toast.add({ severity: 'error', summary: 'Erro', detail: 'Falha ao remover base', life: 3000 })
    }
  }
}

function loadMore() {
  if (items.value.length >= total.value) return
  page.value += 1
  loadMorePage()
}

async function loadMorePage() {
  loading.value = true
  try {
    const res = await knowledgeService.list({ page: page.value, size: size.value, q: debounced.value || undefined })
    items.value = [...items.value, ...res.content]
    total.value = res.totalElements
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="kb-list">
    <div class="flex align-items-center justify-content-between mb-4 gap-3 flex-wrap">
      <h1 class="text-2xl font-bold m-0 text-900">Bases de Conhecimento</h1>
      <Button
        label="Nova Base"
        icon="pi pi-plus"
        class="sticky-create"
        @click="router.push('/knowledge/new')"
        data-testid="new-kb-btn"
      />
    </div>

    <div class="flex gap-3 mb-4">
      <InputText
        v-model="q"
        placeholder="Buscar por nome..."
        class="w-20rem"
        aria-label="Buscar bases de conhecimento"
        @keyup.enter="onSearch"
        data-testid="search-q"
      />
      <Button icon="pi pi-search" outlined aria-label="Buscar" @click="onSearch" data-testid="search-btn" />
    </div>

    <div v-if="loading && items.length === 0" class="kb-grid" data-testid="kb-skeleton">
      <Skeleton height="10rem" v-for="i in 4" :key="i" borderRadius="1rem" />
    </div>

    <AdminEmptyState
      v-else-if="items.length === 0"
      icon="pi pi-book"
      title="Nenhuma base de conhecimento encontrada"
      description="Crie sua primeira base para indexar documentos e responder com citações."
      ctaLabel="Criar primeira base"
      @cta="router.push('/knowledge/new')"
    />

    <template v-else>
      <div class="kb-grid" data-testid="kb-cards">
        <Card v-for="kb in items" :key="kb.id" class="kb-card" :data-testid="`kb-card-${kb.id}`">
          <template #title>
            <div class="flex align-items-center gap-2">
              <span class="font-semibold text-900 white-space-nowrap overflow-hidden text-overflow-ellipsis">{{ kb.name }}</span>
              <Tag :value="kb.status" :severity="severityFor(kb.status)" :data-testid="`kb-status-${kb.id}`" />
            </div>
          </template>
          <template #content>
            <p class="text-sm text-surface-600 m-0 mb-2 line-clamp-2">{{ kb.description || 'Sem descrição' }}</p>
            <small class="text-surface-500">{{ kb.embeddingModel }} · {{ kb.dimension }}d</small>
            <div class="flex gap-1 mt-3">
              <Button
                icon="pi pi-pencil"
                text
                rounded
                severity="secondary"
                aria-label="Editar base"
                @click="router.push(`/knowledge/${kb.id}/edit`)"
                :data-testid="`edit-${kb.id}`"
              />
              <Button
                icon="pi pi-refresh"
                text
                rounded
                severity="secondary"
                aria-label="Reindexar base"
                v-tooltip.top="'Reindexar'"
                @click="doReindex(kb)"
                :data-testid="`reindex-${kb.id}`"
              />
              <Button
                icon="pi pi-trash"
                text
                rounded
                severity="danger"
                aria-label="Excluir base"
                @click="confirmDelete(kb)"
                :data-testid="`delete-${kb.id}`"
              />
            </div>
          </template>
        </Card>
      </div>
      <div v-if="items.length < total" class="flex justify-content-center mt-4">
        <Button
          label="Carregar mais"
          outlined
          :loading="loading"
          @click="loadMore"
          data-testid="load-more-btn"
        />
      </div>
    </template>

    <Dialog v-model:visible="showDelete" modal header="Confirmar exclusão" :style="{ width: '28rem' }">
      <p>Excluir base <strong>{{ deleteTarget?.name }}</strong>?</p>
      <p v-if="inUseAgents.length" class="text-sm text-orange-600" data-testid="kb-in-use">
        Em uso pelos agentes: {{ inUseAgents.join(', ') }}
      </p>
      <template #footer>
        <Button label="Cancelar" text @click="showDelete = false" />
        <Button
          v-if="inUseAgents.length"
          label="Excluir mesmo assim"
          severity="warn"
          @click="doDelete(true)"
          data-testid="confirm-delete-force"
        />
        <Button label="Excluir" severity="danger" @click="doDelete(false)" data-testid="confirm-delete" />
      </template>
    </Dialog>

    <Toast />
  </div>
</template>

<style scoped>
.kb-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(18rem, 1fr));
  gap: 1rem;
}
.kb-card {
  border-radius: 1rem;
}
.line-clamp-2 {
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
@media (max-width: 767px) {
  .kb-grid {
    grid-template-columns: 1fr;
  }
  .sticky-create {
    position: sticky;
    top: 0;
    z-index: 10;
  }
}
</style>
