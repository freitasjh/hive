<script setup lang="ts">
import { ref, computed, nextTick } from 'vue'
import Button from 'primevue/button'
import Tag from 'primevue/tag'
import { agentAdminService } from '../services/agent-admin.service'
import type { AiProvider, ValidateResponse } from '../types/agent-admin'

const props = defineProps<{
  provider: AiProvider
  model: string
  baseUrl?: string
}>()

const emit = defineEmits<{
  tested: [result: ValidateResponse | { ok: false; reason: string }]
}>()

type TestState = 'idle' | 'testing' | 'ok' | 'fail'

const state = ref<TestState>('idle')
const latencyMs = ref<number | null>(null)

const canTest = computed(() => !!props.model?.trim() && state.value !== 'testing')

async function runTest() {
  if (!props.model?.trim()) return
  state.value = 'testing'
  latencyMs.value = null
  // yield so the testing state paints before the network call resolves
  await nextTick()
  try {
    const result = await agentAdminService.validate({
      provider: props.provider,
      model: props.model.trim(),
      baseUrl: props.baseUrl || undefined,
    })
    latencyMs.value = result.latencyMs
    state.value = result.ok ? 'ok' : 'fail'
    emit('tested', result)
  } catch {
    state.value = 'fail'
    emit('tested', { ok: false, reason: 'validation_failed' })
  }
}
</script>

<template>
  <div class="connection-tester flex align-items-center gap-2">
    <Button
      label="Testar conexão"
      icon="pi pi-bolt"
      outlined
      data-testid="test-btn"
      :disabled="!canTest"
      :loading="state === 'testing'"
      @click="runTest"
    />
    <span data-testid="test-status" aria-live="polite" class="text-sm">
      <span v-if="state === 'testing'">Testando…</span>
      <Tag
        v-else-if="state === 'ok'"
        data-testid="test-status-ok"
        value="Conexão OK"
        severity="success"
        icon="pi pi-check-circle"
      />
      <Tag
        v-else-if="state === 'fail'"
        data-testid="test-status-fail"
        value="Falha na conexão — verifique a chave e o modelo. Salvamento liberado com aviso."
        severity="warn"
        icon="pi pi-exclamation-triangle"
      />
      <span v-if="state === 'ok' && latencyMs !== null" class="text-surface-500 ml-1">({{ latencyMs }}ms)</span>
    </span>
  </div>
</template>
