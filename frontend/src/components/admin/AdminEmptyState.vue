<script setup lang="ts">
import Button from 'primevue/button'

defineProps<{
  icon?: string
  title: string
  description?: string
  ctaLabel?: string
}>()

const emit = defineEmits<{ (e: 'cta'): void }>()
</script>

<template>
  <div class="flex flex-column align-items-center justify-content-center py-8 px-4 text-center border-1 border-dashed border-surface-200 rounded-2xl bg-surface-0">
    <div class="w-4rem h-4rem border-circle bg-primary-50 flex align-items-center justify-content-center mb-3">
      <i :class="icon ?? 'pi pi-inbox'" class="text-2xl text-primary"></i>
    </div>
    <h3 class="text-lg font-semibold text-900 mb-2 m-0">{{ title }}</h3>
    <p v-if="description" class="text-sm text-surface-500 mb-4 max-w-30rem line-height-3">{{ description }}</p>
    <Button v-if="ctaLabel" :label="ctaLabel" icon="pi pi-plus" @click="emit('cta')" data-testid="empty-cta" />
  </div>
</template>
