<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useToast } from 'primevue/usetoast'
import Button from 'primevue/button'
import Card from 'primevue/card'
import InputText from 'primevue/inputtext'
import Textarea from 'primevue/textarea'
import Select from 'primevue/select'
import Tag from 'primevue/tag'
import Slider from 'primevue/slider'
import FileUpload, { type FileUploadUploaderEvent } from 'primevue/fileupload'
import ProgressBar from 'primevue/progressbar'
import MultiSelect from 'primevue/multiselect'
import Message from 'primevue/message'
import Tabs from 'primevue/tabs'
import TabList from 'primevue/tablist'
import Tab from 'primevue/tab'
import TabPanels from 'primevue/tabpanels'
import TabPanel from 'primevue/tabpanel'
import Dialog from 'primevue/dialog'
import Toast from 'primevue/toast'
import { knowledgeService } from '../../services/knowledge.service'
import { agentAdminService } from '../../services/agent-admin.service'
import { setBreadcrumbLabel } from '../../utils/breadcrumbOverride'
import { scoreSeverity as scoreSeverityFor } from '../../utils/embeddingConvention'
import {
  KB_ALLOWED_EXTENSIONS,
  KB_MAX_FILE_SIZE,
  KB_MAX_LINKS,
  type AgentLinkOption,
  type KbChunkDTO,
  type KbDocumentDTO,
  type KnowledgeBaseDTO,
} from '../../types/knowledge'
import type { AgentDTO } from '../../types/agent-admin'

const route = useRoute()
const router = useRouter()
const toast = useToast()

const isNew = computed(() => route.path === '/knowledge/new')
const kbId = computed(() => route.params.id as string | undefined)

const kb = ref<KnowledgeBaseDTO | null>(null)
const name = ref('')
const description = ref('')
const embeddingModel = ref<string | null>(null)
const loading = ref(false)
const saving = ref(false)

const embeddingOptions = [
  { label: 'OpenAI (1536d) — padrão', value: 'text-embedding-3-small' },
  { label: 'Ollama (768d) — local', value: 'nomic-embed-text' },
]

const docs = ref<KbDocumentDTO[]>([])
const docsLoading = ref(false)
const urlInput = ref('')
const urlError = ref<string | null>(null)
const uploadProgress = ref<Record<string, number>>({})
const docToRemove = ref<KbDocumentDTO | null>(null)
const showRemoveDoc = ref(false)

const agentOptions = ref<AgentLinkOption[]>([])
const linkedAgentIds = ref<string[]>([])
const agentsLoading = ref(false)

const playQuery = ref('')
const playTopK = ref(5)
const playLoading = ref(false)
const playError = ref<string | null>(null)
const playSearched = ref(false)
const chunks = ref<KbChunkDTO[]>([])

function isValidHttpUrl(s: string): boolean {
  return /^https?:\/\/.+/.test(s.trim())
}

function docIcon(doc: KbDocumentDTO): string {
  if (doc.sourceType === 'URL') return 'pi pi-link'
  const n = (doc.fileName ?? doc.sourceUri ?? '').toLowerCase()
  if (n.endsWith('.pdf')) return 'pi pi-file-pdf'
  return 'pi pi-file-lines'
}

function severityFor(status: string) {
  if (status === 'PENDING') return 'warn'
  if (status === 'INDEXING') return 'info'
  if (status === 'READY') return 'success'
  if (status === 'FAILED') return 'danger'
  return 'secondary'
}

/**
 * Severity of a retrieved score, read against the cut-off this base's own model was searched with.
 *
 * Was a fixed 0.9/0.7 ladder, which is the same defect as the hardcoded backend constant one layer
 * down: on a `nomic-embed-text` base — whose scores sit measurably lower — a real hit of 0.6219 came
 * out grey, indistinguishable from noise. See `utils/embeddingConvention` for the mirrored table and
 * why it is only a labelling aid.
 */
function scoreSeverity(score: number): 'success' | 'warn' | 'secondary' {
  return scoreSeverityFor(score, embeddingModel.value)
}

async function loadKb() {
  if (isNew.value || !kbId.value) return
  loading.value = true
  try {
    kb.value = await knowledgeService.getById(kbId.value)
    name.value = kb.value.name
    description.value = kb.value.description ?? ''
    embeddingModel.value = kb.value.embeddingModel
    setBreadcrumbLabel(kb.value.name)
  } catch {
    toast.add({ severity: 'error', summary: 'Erro', detail: 'Falha ao carregar base de conhecimento', life: 3000 })
  } finally {
    loading.value = false
  }
}

async function loadDocs() {
  if (isNew.value || !kbId.value) return
  docsLoading.value = true
  try {
    docs.value = await knowledgeService.listDocuments(kbId.value)
  } catch {
    toast.add({ severity: 'error', summary: 'Erro', detail: 'Falha ao carregar documentos', life: 3000 })
  } finally {
    docsLoading.value = false
  }
}

async function loadAgents() {
  if (isNew.value || !kbId.value) return
  agentsLoading.value = true
  try {
    const page = await agentAdminService.list({ page: 0, size: 50 })
    const agents: AgentDTO[] = page.content
    agentOptions.value = agents.map((a) => ({ id: a.id, name: a.name }))
    const links = await Promise.all(
      agents.map(async (a) => {
        try {
          const ids = await knowledgeService.listAgentLinks(a.id)
          return { agentId: a.id, ids }
        } catch {
          return { agentId: a.id, ids: [] as string[] }
        }
      }),
    )
    linkedAgentIds.value = links.filter((l) => l.ids.includes(kbId.value as string)).map((l) => l.agentId)
  } catch {
    toast.add({ severity: 'error', summary: 'Erro', detail: 'Falha ao carregar agentes vinculados', life: 3000 })
  } finally {
    agentsLoading.value = false
  }
}

onMounted(async () => {
  await loadKb()
  await Promise.all([loadDocs(), loadAgents()])
})

onUnmounted(() => setBreadcrumbLabel(null))

async function saveKb() {
  if (!name.value.trim()) {
    toast.add({ severity: 'warn', summary: 'Validação', detail: 'Nome obrigatório', life: 3000 })
    return
  }
  saving.value = true
  try {
    if (isNew.value) {
      const created = await knowledgeService.create({
        name: name.value.trim(),
        description: description.value.trim() || null,
        embeddingModel: embeddingModel.value,
      })
      toast.add({ severity: 'success', summary: 'Criada', detail: 'Base de conhecimento criada', life: 2000 })
      router.push(`/knowledge/${created.id}/edit`)
    } else if (kbId.value) {
      await knowledgeService.update(kbId.value, {
        name: name.value.trim(),
        description: description.value.trim() || null,
      })
      toast.add({ severity: 'success', summary: 'Atualizada', detail: 'Base de conhecimento atualizada', life: 2000 })
      loadKb()
    }
  } catch (err: unknown) {
    const e = err as { response?: { status?: number; data?: { detail?: string } } }
    toast.add({ severity: 'error', summary: 'Erro', detail: e.response?.data?.detail ?? 'Falha ao salvar', life: 4000 })
  } finally {
    saving.value = false
  }
}

function validateClientFile(file: File): string | null {
  const ext = file.name.split('.').pop()?.toLowerCase() ?? ''
  if (!(KB_ALLOWED_EXTENSIONS as readonly string[]).includes(ext)) {
    return 'Tipo de arquivo não suportado. Envie PDF, TXT ou MD.'
  }
  if (file.size > KB_MAX_FILE_SIZE) {
    return 'Arquivo maior que 10MB. Comprima ou divida o documento.'
  }
  return null
}

async function onUploader(e: FileUploadUploaderEvent) {
  if (!kbId.value) return
  const files = e.files as File[]
  for (const file of files) {
    const err = validateClientFile(file)
    if (err) {
      toast.add({ severity: 'warn', summary: 'Arquivo rejeitado', detail: `${file.name}: ${err}`, life: 5000 })
      continue
    }
    try {
      const doc = await knowledgeService.uploadDocument(kbId.value, file, (p) => {
        uploadProgress.value = { ...uploadProgress.value, [file.name]: p }
      })
      toast.add({ severity: 'success', summary: 'Enviado', detail: `${file.name} em indexação`, life: 2500 })
      docs.value = [doc, ...docs.value]
    } catch (uploadErr: unknown) {
      const ue = uploadErr as { response?: { status?: number; data?: { detail?: string } } }
      const detail = ue.response?.status === 413
        ? 'Arquivo maior que 10MB (limite do servidor).'
        : (ue.response?.data?.detail ?? 'Falha no upload')
      toast.add({ severity: 'error', summary: 'Erro no upload', detail, life: 4000 })
    } finally {
      const next = { ...uploadProgress.value }
      delete next[file.name]
      uploadProgress.value = next
    }
  }
}

async function addUrl() {
  urlError.value = null
  if (!isValidHttpUrl(urlInput.value)) {
    urlError.value = 'Informe uma URL válida começando com http(s)://'
    return
  }
  if (!kbId.value) return
  try {
    const doc = await knowledgeService.addUrlDocument(kbId.value, urlInput.value.trim())
    toast.add({ severity: 'success', summary: 'URL registrada', detail: 'Conteúdo será indexado como snapshot único', life: 3000 })
    urlInput.value = ''
    docs.value = [doc, ...docs.value]
  } catch (err: unknown) {
    const e = err as { response?: { status?: number; data?: { detail?: string } } }
    toast.add({ severity: 'error', summary: 'Erro', detail: e.response?.data?.detail ?? 'Falha ao registrar URL', life: 4000 })
  }
}

async function reindexDoc() {
  if (!kbId.value) return
  try {
    await knowledgeService.reindex(kbId.value)
    toast.add({ severity: 'success', summary: 'Reindexação iniciada', detail: 'Documento será reprocessado', life: 2500 })
    loadDocs()
  } catch {
    toast.add({ severity: 'error', summary: 'Erro', detail: 'Falha ao reindexar', life: 3000 })
  }
}

function confirmRemoveDoc(doc: KbDocumentDTO) {
  docToRemove.value = doc
  showRemoveDoc.value = true
}

async function doRemoveDoc() {
  if (!kbId.value || !docToRemove.value) return
  try {
    await knowledgeService.deleteDocument(kbId.value, docToRemove.value.id)
    toast.add({ severity: 'success', summary: 'Removido', detail: 'Documento removido', life: 2000 })
    docs.value = docs.value.filter((d) => d.id !== docToRemove.value?.id)
  } catch {
    toast.add({ severity: 'error', summary: 'Erro', detail: 'Falha ao remover documento', life: 3000 })
  } finally {
    showRemoveDoc.value = false
    docToRemove.value = null
  }
}

async function saveAgentLinks() {
  if (!kbId.value) return
  const target = kbId.value
  const previous = new Set<string>()
  try {
    const links = await Promise.all(
      agentOptions.value.map(async (a) => {
        const ids = await knowledgeService.listAgentLinks(a.id).catch(() => [] as string[])
        return { agentId: a.id, ids }
      }),
    )
    for (const l of links) if (l.ids.includes(target)) previous.add(l.agentId)
  } catch {
    toast.add({ severity: 'error', summary: 'Erro', detail: 'Falha ao ler vínculos atuais', life: 3000 })
    return
  }
  const next = new Set(linkedAgentIds.value)
  const toAdd = [...next].filter((id) => !previous.has(id))
  const toRemove = [...previous].filter((id) => !next.has(id))
  try {
    for (const agentId of toAdd) {
      const ids = await knowledgeService.listAgentLinks(agentId).catch(() => [] as string[])
      if (!ids.includes(target) && ids.length >= KB_MAX_LINKS) {
        toast.add({ severity: 'warn', summary: 'Limite atingido', detail: 'Máximo de 5 bases de conhecimento por agente.', life: 4000 })
        continue
      }
      await knowledgeService.patchAgentKnowledge(agentId, [...ids, target])
    }
    for (const agentId of toRemove) {
      const ids = await knowledgeService.listAgentLinks(agentId).catch(() => [] as string[])
      await knowledgeService.patchAgentKnowledge(agentId, ids.filter((id) => id !== target))
    }
    toast.add({ severity: 'success', summary: 'Vínculos atualizados', detail: 'Agentes vinculados com sucesso', life: 2000 })
  } catch (err: unknown) {
    const e = err as { response?: { data?: { detail?: string } } }
    toast.add({ severity: 'error', summary: 'Erro', detail: e.response?.data?.detail ?? 'Falha ao salvar vínculos', life: 4000 })
  }
}

async function runPlayground() {
  playError.value = null
  if (!playQuery.value.trim()) {
    playError.value = 'Digite uma pergunta para testar a base.'
    return
  }
  if (!kbId.value) return
  playLoading.value = true
  try {
    chunks.value = await knowledgeService.search(kbId.value, { query: playQuery.value.trim(), topK: playTopK.value })
    playSearched.value = true
  } catch (err: unknown) {
    const e = err as { response?: { data?: { detail?: string } } }
    playError.value = e.response?.data?.detail ?? 'Falha na busca. Tente novamente.'
  } finally {
    playLoading.value = false
  }
}
</script>

<template>
  <div class="kb-detail">
    <div class="flex align-items-center gap-3 mb-4">
      <Button icon="pi pi-arrow-left" text rounded aria-label="Voltar" @click="router.push('/knowledge')" data-testid="back-btn" />
      <h1 class="text-2xl font-bold m-0 text-900">{{ isNew ? 'Nova Base de Conhecimento' : 'Editar Base de Conhecimento' }}</h1>
      <Tag v-if="kb" :value="kb.status" :severity="severityFor(kb.status)" />
    </div>

    <div v-if="loading" class="text-surface-500">Carregando...</div>

    <Card>
      <template #content>
        <div class="grid">
          <div class="col-12 md:col-6 flex flex-column gap-1">
            <label for="kb-name" class="text-sm font-medium">Nome *</label>
            <InputText
              id="kb-name"
              v-model="name"
              placeholder="Ex.: Central de Ajuda"
              data-testid="input-kb-name"
              @blur="name = name.trim()"
            />
          </div>
          <div v-if="isNew" class="col-12 md:col-6 flex flex-column gap-1">
            <label for="kb-model" class="text-sm font-medium">Modelo de embedding</label>
            <Select
              v-model="embeddingModel"
              :options="embeddingOptions"
              optionLabel="label"
              optionValue="value"
              placeholder="Padrão do sistema"
              class="w-full"
              inputId="kb-model"
              data-testid="select-kb-model"
            />
          </div>
          <div class="col-12 flex flex-column gap-1">
            <label for="kb-desc" class="text-sm font-medium">Descrição</label>
            <Textarea id="kb-desc" v-model="description" rows="2" placeholder="O que esta base contém?" data-testid="input-kb-desc" />
          </div>
        </div>
        <div class="flex justify-content-end mt-3">
          <Button :label="saving ? 'Salvando...' : 'Salvar'" :loading="saving" @click="saveKb" data-testid="save-kb-btn" />
        </div>
      </template>
    </Card>

    <Tabs v-if="!isNew" value="documentos" class="mt-4">
      <TabList>
        <Tab value="documentos" data-testid="tab-documentos">Documentos</Tab>
        <Tab value="agentes" data-testid="tab-agentes">Agentes</Tab>
        <Tab value="testar" data-testid="tab-testar">Testar</Tab>
      </TabList>
      <TabPanels>
        <TabPanel value="documentos">
          <div class="flex flex-column gap-4">
            <div>
              <h3 class="text-base font-semibold mb-2">Enviar arquivos</h3>
              <FileUpload
                customUpload
                multiple
                accept=".pdf,.txt,.md"
                :maxFileSize="KB_MAX_FILE_SIZE"
                @uploader="onUploader"
                data-testid="kb-upload"
                aria-label="Enviar documentos PDF, TXT ou MD de até 10MB"
              >
                <template #empty>
                  <div class="flex flex-column align-items-center gap-2 py-4 text-surface-500" data-testid="upload-dropzone">
                    <i class="pi pi-cloud-upload text-3xl" aria-hidden="true" />
                    <span>Arraste arquivos PDF, TXT ou MD aqui (máx. 10MB cada)</span>
                  </div>
                </template>
              </FileUpload>
              <small class="text-surface-500">PDF escaneado (imagem) não suportado — envie PDFs com texto selecionável.</small>
              <div v-for="(p, fname) in uploadProgress" :key="fname" class="mt-2">
                <small>{{ fname }} — {{ p }}%</small>
                <ProgressBar :value="p" :data-testid="`upload-progress-${fname}`" />
              </div>
            </div>

            <div class="flex flex-column gap-1">
              <h3 class="text-base font-semibold m-0">Adicionar por URL</h3>
              <small class="text-surface-500">URL é snapshot único (sem recrawl) — o conteúdo é capturado uma vez.</small>
              <div class="flex gap-2">
                <InputText
                  v-model="urlInput"
                  placeholder="https://exemplo.com/guia"
                  class="flex-grow-1"
                  aria-label="URL do documento"
                  data-testid="input-kb-url"
                />
                <Button label="Adicionar" icon="pi pi-link" outlined @click="addUrl" data-testid="add-url-btn" />
              </div>
              <small v-if="urlError" class="text-red-500" data-testid="url-error">{{ urlError }}</small>
            </div>

            <div>
              <h3 class="text-base font-semibold mb-2">Documentos ({{ docs.length }})</h3>
              <div v-if="docsLoading" class="text-surface-500">Carregando documentos...</div>
              <div v-else-if="docs.length === 0" class="text-surface-500" data-testid="docs-empty">
                Nenhum documento ainda. Envie um arquivo ou adicione uma URL.
              </div>
              <ul v-else class="list-none p-0 m-0 flex flex-column gap-2" data-testid="docs-list">
                <li
                  v-for="doc in docs"
                  :key="doc.id"
                  class="flex align-items-center gap-3 p-3 border-1 border-surface-200 rounded-lg"
                  :class="{ 'doc-failed': doc.status === 'FAILED' }"
                  :data-testid="`doc-${doc.id}`"
                >
                  <i :class="docIcon(doc)" class="text-xl text-surface-600" aria-hidden="true" />
                  <div class="flex flex-column flex-grow-1 min-w-0">
                    <span class="font-medium white-space-nowrap overflow-hidden text-overflow-ellipsis">
                      {{ doc.fileName || doc.sourceUri }}
                    </span>
                    <small v-if="doc.status === 'FAILED'" class="text-red-500" :data-testid="`doc-error-${doc.id}`">
                      {{ doc.error || 'Falha na indexação. Tente reindexar.' }}
                    </small>
                    <small v-else class="text-surface-500">{{ doc.sourceType === 'URL' ? 'URL · snapshot único' : doc.sourceType }}</small>
                  </div>
                  <Tag :value="doc.status" :severity="severityFor(doc.status)" />
                  <Button
                    icon="pi pi-refresh"
                    text
                    rounded
                    severity="secondary"
                    aria-label="Reindexar documento"
                    @click="reindexDoc()"
                    :data-testid="`doc-reindex-${doc.id}`"
                  />
                  <Button
                    icon="pi pi-trash"
                    text
                    rounded
                    severity="danger"
                    aria-label="Remover documento"
                    @click="confirmRemoveDoc(doc)"
                    :data-testid="`doc-remove-${doc.id}`"
                  />
                </li>
              </ul>
            </div>
          </div>
        </TabPanel>

        <TabPanel value="agentes">
          <div class="flex flex-column gap-3">
            <p class="text-sm text-surface-600 m-0">Selecione os agentes que podem consultar esta base (máx. {{ KB_MAX_LINKS }} bases por agente).</p>
            <MultiSelect
              v-model="linkedAgentIds"
              :options="agentOptions"
              optionLabel="name"
              optionValue="id"
              placeholder="Selecionar agentes"
              display="chip"
              class="w-full"
              :loading="agentsLoading"
              inputId="kb-agents"
              data-testid="kb-agents-select"
              aria-label="Agentes vinculados a esta base"
            />
            <div>
              <Button label="Salvar vínculos" :loading="agentsLoading" @click="saveAgentLinks" data-testid="save-links-btn" />
            </div>
          </div>
        </TabPanel>

        <TabPanel value="testar">
          <div class="flex flex-column gap-3" data-testid="kb-playground">
            <div class="flex flex-column gap-1">
              <label for="play-query" class="text-sm font-medium">Pergunta</label>
              <Textarea
                id="play-query"
                v-model="playQuery"
                rows="3"
                placeholder="Ex.: Como solicito reembolso?"
                data-testid="play-query"
              />
            </div>
            <div class="flex align-items-center gap-3">
              <label for="play-topk" class="text-sm font-medium white-space-nowrap">TopK: {{ playTopK }}</label>
              <Slider v-model="playTopK" :min="1" :max="10" :step="1" class="flex-grow-1" inputId="play-topk" data-testid="play-topk" aria-label="Número de trechos (1 a 10)" />
              <Button label="Buscar" icon="pi pi-search" :loading="playLoading" @click="runPlayground" data-testid="play-search-btn" />
            </div>
            <Message v-if="playError" severity="error" :closable="false" data-testid="play-error">{{ playError }}</Message>
            <div v-else-if="playLoading" class="text-surface-500">Buscando trechos...</div>
            <div v-else-if="playSearched && chunks.length === 0" class="text-surface-500" data-testid="play-empty">
              Nenhum trecho relevante encontrado. Tente outra pergunta.
            </div>
            <div v-else-if="!playSearched" class="text-surface-500" data-testid="play-hint">
              Digite uma pergunta e clique em Buscar para ver os trechos recuperados.
            </div>
            <div v-else class="flex flex-column gap-2" data-testid="play-results">
              <Card v-for="c in chunks" :key="c.chunkId" class="chunk-card" :data-testid="`chunk-${c.chunkId}`">
                <template #content>
                  <div class="flex align-items-center gap-2 mb-2">
                    <Tag :value="c.score.toFixed(2)" :severity="scoreSeverity(c.score)" :data-testid="`chunk-score-${c.chunkId}`" />
                    <small class="text-surface-500">Fonte: {{ c.docName }}</small>
                  </div>
                  <p class="text-sm m-0 line-height-3">{{ c.text }}</p>
                </template>
              </Card>
            </div>
          </div>
        </TabPanel>
      </TabPanels>
    </Tabs>

    <Dialog v-model:visible="showRemoveDoc" modal header="Remover documento" :style="{ width: '26rem' }">
      <p>Remover <strong>{{ docToRemove?.fileName || docToRemove?.sourceUri }}</strong> e seus trechos indexados?</p>
      <template #footer>
        <Button label="Cancelar" text @click="showRemoveDoc = false" />
        <Button label="Remover" severity="danger" @click="doRemoveDoc" data-testid="confirm-remove-doc" />
      </template>
    </Dialog>

    <Toast />
  </div>
</template>

<style scoped>
.doc-failed {
  border-color: var(--p-red-300) !important;
  background: var(--p-red-50);
}
:global(.p-dark) .doc-failed {
  background: rgba(239, 68, 68, 0.08);
}
.chunk-card {
  border-radius: 0.75rem;
}
@media (max-width: 767px) {
  .kb-detail :deep(.p-tablist) {
    overflow-x: auto;
  }
}
</style>
