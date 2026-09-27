<template>
  <div class="prompt-selector">
    <div v-if="loading" class="flex flex-column gap-2">
      <Skeleton height="2.5rem" borderRadius="12px" />
    </div>
    <Message v-else-if="error" severity="error" :closable="false" class="m-0">
      {{ error }}
      <Button label="Tentar novamente" text size="small" class="ml-2" @click="() => load()" />
    </Message>
    <Message v-else-if="prompts.length === 0" severity="info" :closable="false" class="m-0">
      Nenhum prompt criado ainda. Crie um em <strong>Prompts</strong> para começar a conversar.
    </Message>
    <Select
      v-else
      :modelValue="modelValue"
      :options="prompts"
      optionLabel="name"
      optionValue="id"
      placeholder="Escolha um prompt"
      class="w-full"
      :disabled="disabled || loading"
      :loading="loading"
      filter
      data-testid="prompt-selector"
      @update:modelValue="onSelect"
    >
      <template #value="slotProps">
        <div v-if="slotProps.value" class="flex align-items-center gap-2">
          <i class="pi pi-file-edit text-primary"></i>
          <span>{{ nameOf(slotProps.value) }}</span>
        </div>
        <span v-else class="text-surface-500">Escolha um prompt</span>
      </template>
      <template #option="slotProps">
        <div class="flex flex-column">
          <span class="font-semibold text-sm">{{ slotProps.option.name }}</span>
          <span v-if="slotProps.option.description" class="text-xs text-surface-500">{{ slotProps.option.description }}</span>
        </div>
      </template>
    </Select>
  </div>
</template>

<script setup lang="ts">
/**
 * The prompt a new conversation is answered by.
 *
 * <p>It replaces the agent selector, and the difference is the point: an agent was a saved bundle of
 * prompt plus model plus limits, so choosing one in the chat also chose the model. The model is now
 * the tenant's, and the prompt is the only thing a conversation picks. Locked once a session exists,
 * because the session keeps the prompt it was opened with.
 */
import { ref, onMounted, watch } from 'vue'
import Select from 'primevue/select'
import Skeleton from 'primevue/skeleton'
import Message from 'primevue/message'
import Button from 'primevue/button'
import { promptService } from '../services/prompt.service'
import type { PromptTemplateDTO } from '../types/prompt'

const props = defineProps<{
  modelValue: string | null
  disabled?: boolean
}>()

const emit = defineEmits<{
  'update:modelValue': [value: string | null]
}>()

/** `PromptTemplateControllerV1` rejeita `size > 50` com 400. Este é o teto, não um palpite. */
const MAX_PROMPTS_IN_SELECTOR = 50

const prompts = ref<PromptTemplateDTO[]>([])
const loading = ref(false)
const error = ref<string | null>(null)

function nameOf(id: string): string {
  return prompts.value.find(p => p.id === id)?.name ?? id
}

function onSelect(value: string | null) {
  emit('update:modelValue', value)
}

async function load() {
  loading.value = true
  error.value = null
  try {
    // 50, o MÁXIMO que o contrato do servidor aceita (size > 50 → 400), e não a página default de
    // 20: um seletor que esconde o prompt que o usuário quer atrás de uma paginação que a tela não
    // oferece é um seletor quebrado. Acima de 50 prompts o tenant recebe os 50 mais recentes — para
    // chegar aos outros ele abre a tela de Prompts, que é paginada de verdade.
    const page = await promptService.list({ page: 0, size: MAX_PROMPTS_IN_SELECTOR })
    prompts.value = page.content ?? []
  } catch (e: unknown) {
    error.value = e instanceof Error ? e.message : 'Erro ao carregar prompts'
  } finally {
    loading.value = false
  }
}

onMounted(load)

// Trocar de sessão libera o seletor; o pai desmarca o id e recarrega a lista para não deixar um
// template de conversa anterior marcado numa conversa nova.
watch(() => props.disabled, (isDisabled) => {
  if (isDisabled) load()
})

defineExpose({ load, prompts })
</script>

<style scoped>
.prompt-selector :deep(.p-select) {
  border-radius: 12px;
  border-color: var(--p-content-border-color);
}
.prompt-selector :deep(.p-select:not(.p-disabled):hover) {
  border-color: var(--p-primary-400);
}
</style>
