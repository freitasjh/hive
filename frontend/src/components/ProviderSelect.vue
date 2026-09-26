<script setup lang="ts">
import { computed } from 'vue'
import { VENDOR_CATALOG, VENDOR_ORDER } from '../types/agent-admin'
import type { AiProvider } from '../types/agent-admin'

const props = withDefaults(
  defineProps<{
    modelValue: AiProvider
    includeMigrated?: boolean
  }>(),
  { includeMigrated: false },
)

const emit = defineEmits<{
  'update:modelValue': [value: AiProvider]
}>()

const visibleVendors = computed(() =>
  VENDOR_ORDER.filter((p) => {
    const meta = VENDOR_CATALOG[p]
    if (!meta.hiddenFromCreate && !props.includeMigrated) return true
    if (props.includeMigrated) return true
    return props.modelValue === p
  }),
)

function select(provider: AiProvider) {
  emit('update:modelValue', provider)
}
</script>

<template>
  <div class="provider-select flex flex-column gap-2" role="radiogroup" aria-label="Provedor de IA">
    <label
      v-for="provider in visibleVendors"
      :key="provider"
      :data-testid="`provider-option-${provider}`"
      class="provider-card flex gap-3 p-3 border-round surface-border border-1 cursor-pointer"
      :class="{ 'border-primary': modelValue === provider }"
    >
      <input
        type="radio"
        name="ai-provider"
        :value="provider"
        :checked="modelValue === provider"
        :aria-label="`${VENDOR_CATALOG[provider].label} — ${VENDOR_CATALOG[provider].description}`"
        @change="select(provider)"
        class="mt-1"
      />
      <i :class="VENDOR_CATALOG[provider].icon" class="text-xl mt-1" aria-hidden="true" />
      <span class="flex flex-column gap-1">
        <span class="font-medium">{{ VENDOR_CATALOG[provider].label }}</span>
        <span class="text-sm text-surface-500">{{ VENDOR_CATALOG[provider].description }}</span>
        <span class="text-xs text-surface-500" :data-testid="`provider-cost-${provider}`">{{ VENDOR_CATALOG[provider].costHint }}</span>
        <span
          v-if="VENDOR_CATALOG[provider].readOnlyBaseUrl && VENDOR_CATALOG[provider].defaultBaseUrl"
          class="text-xs text-surface-500 font-mono"
          :data-testid="`provider-baseurl-${provider}`"
        >{{ VENDOR_CATALOG[provider].defaultBaseUrl }}</span>
      </span>
    </label>
  </div>
</template>

<style scoped>
.provider-card:has(input:checked) {
  border-color: var(--p-primary-color);
}
.provider-card:focus-within {
  outline: 2px solid var(--p-primary-color);
  outline-offset: 2px;
}
</style>
