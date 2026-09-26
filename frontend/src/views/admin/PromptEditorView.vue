<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useToast } from 'primevue/usetoast'
import InputText from 'primevue/inputtext'
import Textarea from 'primevue/textarea'
import Button from 'primevue/button'
import Select from 'primevue/select'
import Card from 'primevue/card'
import Toast from 'primevue/toast'
import Chip from 'primevue/chip'
import TemplateEditor from '../../components/admin/TemplateEditor.vue'
import InferenceParamsSliders from '../../components/admin/InferenceParamsSliders.vue'
import PlaygroundPanel from '../../components/admin/PlaygroundPanel.vue'
import { promptService } from '../../services/prompt.service'

const route = useRoute()
const router = useRouter()
const toast = useToast()

const isEdit = computed(() => !!route.params.id)
const id = computed(() => route.params.id as string | undefined)

const name = ref('')
const code = ref('')
const description = ref('')
const tagsInput = ref('')
const tags = ref<string[]>([])
const template = ref('')
const status = ref<'DRAFT'|'ACTIVE'|'ARCHIVED'>('DRAFT')
const temperature = ref<number | null>(0.7)
const topP = ref<number | null>(0.9)
const maxTokens = ref<number | null>(2048)
const saving = ref(false)
const loading = ref(false)

const statusOptions = [
  { label: 'DRAFT', value: 'DRAFT' },
  { label: 'ACTIVE', value: 'ACTIVE' },
  { label: 'ARCHIVED', value: 'ARCHIVED' },
]

function addTag() {
  const t = tagsInput.value.trim()
  if (!t) return
  if (tags.value.length >= 20) {
    toast.add({ severity: 'warn', summary: 'Limite', detail: 'Máximo 20 tags', life: 2000 })
    return
  }
  if (!tags.value.includes(t)) tags.value.push(t)
  tagsInput.value = ''
}

function removeTag(idx: number) {
  tags.value.splice(idx, 1)
}

onMounted(async () => {
  if (isEdit.value && id.value) {
    loading.value = true
    try {
      const dto = await promptService.getById(id.value)
      name.value = dto.name
      code.value = dto.code
      description.value = dto.description ?? ''
      tags.value = dto.tags ?? []
      template.value = dto.template
      status.value = dto.status
      temperature.value = dto.inferenceParams?.temperature ?? null
      topP.value = dto.inferenceParams?.topP ?? null
      maxTokens.value = dto.inferenceParams?.maxTokens ?? null
    } catch {
      toast.add({ severity: 'error', summary: 'Erro', detail: 'Falha ao carregar prompt', life: 3000 })
    } finally {
      loading.value = false
    }
  }
})

async function save() {
  if (!name.value.trim() || !code.value.trim() || !template.value.trim()) {
    toast.add({ severity: 'warn', summary: 'Validação', detail: 'Preencha nome, code e template', life: 3000 })
    return
  }
  saving.value = true
  const payload = {
    name: name.value.trim(),
    code: code.value.trim(),
    description: description.value.trim() || null,
    tags: tags.value.length ? tags.value : null,
    template: template.value,
    inferenceParams: {
      temperature: temperature.value,
      topP: topP.value,
      maxTokens: maxTokens.value,
    },
    status: status.value,
  }
  try {
    if (isEdit.value && id.value) {
      await promptService.update(id.value, {
        name: payload.name,
        description: payload.description,
        tags: payload.tags,
        template: payload.template,
        inferenceParams: payload.inferenceParams,
        status: payload.status,
      })
      toast.add({ severity: 'success', summary: 'Atualizado', detail: 'Prompt atualizado', life: 2000 })
    } else {
      await promptService.create(payload)
      toast.add({ severity: 'success', summary: 'Criado', detail: 'Prompt criado', life: 2000 })
    }
    router.push('/prompts')
  } catch (err: unknown) {
    const e = err as { response?: { status?: number; data?: { detail?: string; code?: string } } }
    const detail = e.response?.data?.detail ?? 'Falha ao salvar'
    toast.add({ severity: 'error', summary: 'Erro', detail, life: 4000 })
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <div class="prompt-editor-view">
    <div class="flex align-items-center gap-3 mb-4">
      <Button icon="pi pi-arrow-left" text rounded aria-label="Voltar" @click="router.push('/prompts')" data-testid="back-btn" />
      <h1 class="text-2xl font-bold m-0 text-900">{{ isEdit ? 'Editar Prompt' : 'Novo Prompt' }}</h1>
    </div>

    <div v-if="loading" class="text-surface-500">Carregando...</div>

    <div v-else class="grid">
      <!-- 60% editor -->
      <div class="col-12 lg:col-7">
        <Card>
          <template #content>
            <div class="flex flex-column gap-4">
              <div class="grid">
                <div class="col-12 md:col-6 flex flex-column gap-1">
                  <label for="prompt-name" class="text-sm font-medium">Nome *</label>
                  <InputText id="prompt-name" v-model="name" placeholder="Saudação" data-testid="input-name" />
                </div>
                <div class="col-12 md:col-6 flex flex-column gap-1">
                  <label for="prompt-code" class="text-sm font-medium">Code *</label>
                  <InputText id="prompt-code" v-model="code" :disabled="isEdit" placeholder="greeting-v1" data-testid="input-code" />
                  <small v-if="isEdit" class="text-surface-500">Code imutável após criação</small>
                </div>
                <div class="col-12 flex flex-column gap-1">
                  <label for="prompt-desc" class="text-sm font-medium">Descrição</label>
                  <Textarea id="prompt-desc" v-model="description" rows="2" placeholder="Descrição opcional" data-testid="input-desc" />
                </div>
                <div class="col-12 flex flex-column gap-2">
                  <label class="text-sm font-medium">Tags</label>
                  <div class="flex gap-2">
                    <InputText v-model="tagsInput" placeholder="Adicionar tag + Enter" @keydown.enter.prevent="addTag" class="flex-1" data-testid="input-tag" />
                    <Button label="Adicionar" outlined @click="addTag" data-testid="add-tag-btn" />
                  </div>
                  <div class="flex flex-wrap gap-2">
                    <Chip v-for="(t,idx) in tags" :key="t" :label="t" removable @remove="removeTag(idx)" />
                  </div>
                </div>
                <div class="col-12 md:col-6 flex flex-column gap-1">
                  <label class="text-sm font-medium">Status</label>
                  <Select v-model="status" :options="statusOptions" optionLabel="label" optionValue="value" class="w-full" data-testid="select-status" />
                </div>
              </div>

              <TemplateEditor v-model="template" />

              <InferenceParamsSliders v-model:temperature="temperature" v-model:topP="topP" v-model:maxTokens="maxTokens" />

              <div class="flex gap-3 justify-content-end mt-2">
                <Button label="Cancelar" text @click="router.push('/prompts')" />
                <Button :label="saving ? 'Salvando...' : 'Salvar'" :loading="saving" @click="save" data-testid="save-btn" />
              </div>
            </div>
          </template>
        </Card>
      </div>

      <!-- 40% playground -->
      <div class="col-12 lg:col-5">
        <PlaygroundPanel :template="template" :promptId="id ?? null" :collapsible="true" />
      </div>
    </div>

    <Toast />
  </div>
</template>
