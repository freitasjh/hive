<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import MultiSelect from 'primevue/multiselect'
import Message from 'primevue/message'
import { knowledgeService } from '../../services/knowledge.service'
import {
  KB_MAX_LINKS,
  KB_MAX_LINKS_MESSAGE,
  KB_MIXED_DIMENSION_MESSAGE,
  dimensionsMixed,
  kbDisplayName,
  type KnowledgeBaseDTO,
} from '../../types/knowledge'

const props = withDefaults(
  defineProps<{
    modelValue: string[]
    disabled?: boolean
    id?: string
  }>(),
  { disabled: false, id: 'kb-picker' },
)

const emit = defineEmits<{ (e: 'update:modelValue', value: string[]): void }>()

const options = ref<KnowledgeBaseDTO[]>([])
const loading = ref(false)
const loadError = ref<string | null>(null)

async function load() {
  loading.value = true
  loadError.value = null
  try {
    const page = await knowledgeService.list({ page: 0, size: 50 })
    options.value = page.content.filter((kb) => kb.status === 'ACTIVE')
  } catch {
    loadError.value = 'Falha ao carregar bases de conhecimento.'
  } finally {
    loading.value = false
  }
}

onMounted(load)

const selected = computed<string[]>({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v.slice(0, KB_MAX_LINKS)),
})

const selectedKbs = computed(() => options.value.filter((kb) => selected.value.includes(kb.id)))

const selectedDimensions = computed(() => selectedKbs.value.map((kb) => kb.dimension))

const mixedDimensions = computed(
  () => selectedKbs.value.length > 1 && dimensionsMixed(selectedDimensions.value),
)

const atLimit = computed(() => selected.value.length >= KB_MAX_LINKS)

watch(selectedKbs, () => {}, { deep: true })

function optionLabel(kb: KnowledgeBaseDTO): string {
  return kbDisplayName(kb)
}

defineExpose({ reload: load, mixedDimensions })
</script>

<template>
  <div class="kb-picker flex flex-column gap-2">
    <MultiSelect
      v-model="selected"
      :options="options"
      optionLabel="name"
      optionValue="id"
      :placeholder="loading ? 'Carregando bases...' : 'Selecionar bases de conhecimento'"
      :loading="loading"
      :disabled="disabled"
      :maxSelectedLabels="KB_MAX_LINKS"
      :selectionLimit="KB_MAX_LINKS"
      display="chip"
      class="w-full"
      :inputId="id"
      data-testid="kb-picker"
      :aria-label="`Selecionar bases de conhecimento (${selected.length} de ${KB_MAX_LINKS})`"
    >
      <template #option="{ option }">
        <div class="flex flex-column">
          <span class="font-medium">{{ option.name }}</span>
          <span class="text-xs text-surface-500">{{ optionLabel(option) }}</span>
        </div>
      </template>
    </MultiSelect>
    <div class="flex align-items-center justify-content-between">
      <small class="text-surface-500" data-testid="kb-picker-count">
        {{ selected.length }} de {{ KB_MAX_LINKS }} bases selecionadas
      </small>
    </div>
    <Message v-if="atLimit" severity="info" :closable="false" data-testid="kb-picker-limit">
      {{ KB_MAX_LINKS_MESSAGE }} Remova uma base para trocar a seleção.
    </Message>
    <Message v-if="mixedDimensions" severity="warn" :closable="false" data-testid="kb-picker-dimension-error">
      {{ KB_MIXED_DIMENSION_MESSAGE }}
    </Message>
    <Message v-if="loadError" severity="error" :closable="false" data-testid="kb-picker-load-error">
      {{ loadError }}
    </Message>
  </div>
</template>
