<template>
  <div class="agent-selector">
    <div v-if="loading" class="flex flex-column gap-2">
      <Skeleton height="2.5rem" borderRadius="12px" />
    </div>
    <Message v-else-if="error" severity="error" :closable="false" class="m-0">
      {{ error }}
      <Button label="Tentar novamente" text size="small" class="ml-2" @click="() => loadAgents()" />
    </Message>
    <Select
      v-else
      :modelValue="modelValue"
      :options="agents"
      optionLabel="name"
      optionValue="id"
      placeholder="Selecione um agente"
      class="w-full"
      :disabled="disabled || loading"
      :loading="loading"
      showClear
      filter
      @update:modelValue="onSelect"
    >
      <template #value="slotProps">
        <div v-if="slotProps.value" class="flex align-items-center gap-2">
          <i class="pi pi-bolt text-primary"></i>
          <span>{{ getAgentName(slotProps.value) }}</span>
        </div>
        <span v-else class="text-surface-500">Selecione um agente</span>
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
import { ref, onMounted } from 'vue'
import Select from 'primevue/select'
import Skeleton from 'primevue/skeleton'
import Message from 'primevue/message'
import Button from 'primevue/button'
import { agentService } from '../services/agent.service'
import type { AgentDTO } from '../types/session'

const props = defineProps<{
  modelValue: string | null
  disabled?: boolean
}>()

const emit = defineEmits<{
  'update:modelValue': [value: string | null]
}>()

const agents = ref<AgentDTO[]>([])
const loading = ref(false)
const error = ref<string | null>(null)

function getAgentName(id: string): string {
  const found = agents.value.find(a => a.id === id)
  return found?.name ?? id
}

function onSelect(value: string | null) {
  emit('update:modelValue', value)
}

async function loadAgents() {
  loading.value = true
  error.value = null
  try {
    const page = await agentService.listAgents(0, 50)
    agents.value = page.content ?? []
  } catch (e: unknown) {
    const msg = e instanceof Error ? e.message : 'Erro ao carregar agentes'
    error.value = msg
  } finally {
    loading.value = false
  }
}

onMounted(loadAgents)

defineExpose({ loadAgents, agents })
</script>

<style scoped>
.agent-selector :deep(.p-select) {
  border-radius: 12px;
  border-color: var(--p-content-border-color);
}
.agent-selector :deep(.p-select:not(.p-disabled):hover) {
  border-color: var(--p-primary-400);
}
</style>
