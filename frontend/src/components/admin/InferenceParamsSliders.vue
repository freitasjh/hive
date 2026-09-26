<script setup lang="ts">
import { computed } from 'vue'
import Slider from 'primevue/slider'
import InputNumber from 'primevue/inputnumber'

const props = withDefaults(defineProps<{
  temperature?: number | null
  topP?: number | null
  maxTokens?: number | null
}>(), {
  temperature: null,
  topP: null,
  maxTokens: null,
})

const emit = defineEmits<{
  (e: 'update:temperature', v: number | null): void
  (e: 'update:topP', v: number | null): void
  (e: 'update:maxTokens', v: number | null): void
}>()

const temp = computed({
  get: () => props.temperature ?? 0.7,
  set: (v: number | null) => emit('update:temperature', v),
})
const topPVal = computed({
  get: () => props.topP ?? 0.9,
  set: (v: number | null) => emit('update:topP', v),
})
const maxTok = computed({
  get: () => props.maxTokens ?? 2048,
  set: (v: number | null) => emit('update:maxTokens', v),
})
</script>

<template>
  <div class="flex flex-column gap-4">
    <div class="flex flex-column gap-2">
      <label class="text-sm font-medium text-900">Temperature (0–2)</label>
      <div class="flex align-items-center gap-3">
        <Slider v-model="temp" :min="0" :max="2" :step="0.1" class="flex-1" data-testid="slider-temp" />
        <InputNumber v-model="temp" :min="0" :max="2" :step="0.1" :minFractionDigits="1" :maxFractionDigits="1" showButtons class="w-8rem" data-testid="input-temp" />
      </div>
    </div>
    <div class="flex flex-column gap-2">
      <label class="text-sm font-medium text-900">Top P (0–1)</label>
      <div class="flex align-items-center gap-3">
        <Slider v-model="topPVal" :min="0" :max="1" :step="0.05" class="flex-1" data-testid="slider-topp" />
        <InputNumber v-model="topPVal" :min="0" :max="1" :step="0.05" :minFractionDigits="2" :maxFractionDigits="2" showButtons class="w-8rem" data-testid="input-topp" />
      </div>
    </div>
    <div class="flex flex-column gap-2">
      <label class="text-sm font-medium text-900">Max Tokens (256–4096)</label>
      <div class="flex align-items-center gap-3">
        <Slider v-model="maxTok" :min="256" :max="4096" :step="128" class="flex-1" data-testid="slider-maxtok" />
        <InputNumber v-model="maxTok" :min="256" :max="4096" :step="128" showButtons class="w-8rem" data-testid="input-maxtok" />
      </div>
    </div>
  </div>
</template>
