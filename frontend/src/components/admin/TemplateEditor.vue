<script setup lang="ts">
import { computed } from 'vue'
import Textarea from 'primevue/textarea'

const props = defineProps<{
  modelValue: string
}>()

const emit = defineEmits<{ (e: 'update:modelValue', v: string): void }>()

const VAR_RE_GLOBAL = /\{\{\s*([a-zA-Z_][a-zA-Z0-9_]*)\s*\}\}/g

const highlightedHtml = computed(() => {
  const text = props.modelValue ?? ''
  if (!text) return ''
  // escape html then wrap vars
  const escaped = text
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
  return escaped.replace(VAR_RE_GLOBAL, (m) => `<mark class="var-highlight">${m}</mark>`)
})

function onInput(e: Event) {
  emit('update:modelValue', (e.target as HTMLTextAreaElement).value)
}
</script>

<template>
  <div class="template-editor relative">
    <label for="template-textarea" class="text-sm font-medium text-900 mb-2 block">Template <span class="text-red-500">*</span></label>
    <!-- highlight overlay: hidden by default, shown behind textarea conceptually; simplified to preview below -->
    <Textarea
      id="template-textarea"
      :modelValue="modelValue"
      @update:modelValue="(v: string) => emit('update:modelValue', v)"
      @input="onInput"
      rows="10"
      class="w-full font-mono text-sm"
      placeholder="Olá {{nome}}, sua tarefa é: {{tarefa}}"
      data-testid="template-textarea"
      autoResize
    />
    <div class="mt-2 text-xs text-surface-500">Variáveis destacadas em <span class="var-highlight inline px-1 rounded">cyan</span>. Use &#123;&#123;var&#125;&#125; (letra/_ inicial).</div>
    <div v-if="modelValue" class="mt-3 border-1 border-surface-200 rounded p-3 bg-surface-50">
      <div class="text-xs font-semibold text-surface-600 mb-1">Highlight preview</div>
      <div class="font-mono text-sm white-space-pre-wrap line-height-3" v-html="highlightedHtml" data-testid="highlight-preview"></div>
    </div>
  </div>
</template>

<style scoped>
.var-highlight {
  background: #06b6d4;
  color: #ffffff;
  padding: 0.05rem 0.25rem;
  border-radius: 4px;
  font-weight: 600;
}
:deep(.var-highlight.inline) {
  background: #06b6d4;
  color: #fff;
}
</style>
