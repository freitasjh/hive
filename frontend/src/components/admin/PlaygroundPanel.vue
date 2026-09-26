<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { useRouter } from 'vue-router'
import { extractVars, renderPreview } from '../../utils/promptVars'
import InputText from 'primevue/inputtext'
import Button from 'primevue/button'
import Card from 'primevue/card'
import Message from 'primevue/message'
import { useToast } from 'primevue/usetoast'

const props = withDefaults(defineProps<{
  template: string
  promptId?: string | null
  collapsible?: boolean
}>(), {
  promptId: null,
  collapsible: false,
})

const router = useRouter()
const toast = useToast()

const collapsed = ref(false)
const values = ref<Record<string, string>>({})

const vars = computed(() => extractVars(props.template))

watch(vars, (newVars) => {
  const next: Record<string, string> = {}
  for (const v of newVars) next[v] = values.value[v] ?? ''
  values.value = next
}, { immediate: true })

const rendered = computed(() => renderPreview(props.template, values.value))

const hasVars = computed(() => vars.value.length > 0)

function copy() {
  navigator.clipboard.writeText(rendered.value).then(() => {
    toast.add({ severity: 'success', summary: 'Copiado', detail: 'Preview copiado', life: 2000 })
  }).catch(() => {
    toast.add({ severity: 'error', summary: 'Erro', detail: 'Falha ao copiar', life: 2000 })
  })
}

function goChat() {
  if (props.promptId) {
    router.push({ path: '/chat', query: { promptId: props.promptId } })
  } else {
    toast.add({ severity: 'info', summary: 'Salve primeiro', detail: 'Salve o prompt antes de testar no chat', life: 3000 })
  }
}
</script>

<template>
  <Card class="playground-panel h-full">
    <template #title>
      <div class="flex align-items-center justify-content-between">
        <span class="text-base font-semibold">Playground</span>
        <div class="flex align-items-center gap-2">
          <span class="text-xs font-normal text-surface-500">mock local sem LLM</span>
          <Button v-if="collapsible" :icon="collapsed ? 'pi pi-chevron-down' : 'pi pi-chevron-up'" text rounded size="small" :aria-label="collapsed ? 'Expandir playground' : 'Recolher playground'" @click="collapsed = !collapsed" data-testid="toggle-playground" />
        </div>
      </div>
    </template>
    <template #content>
      <div v-if="collapsible && collapsed" class="text-sm text-surface-500 py-2" data-testid="playground-collapsed">Playground recolhido</div>
      <div v-else class="flex flex-column gap-4">
        <Message v-if="!template" severity="info" class="m-0">Digite um template com &#123;&#123;var&#125;&#125; para testar.</Message>

        <div v-else-if="!hasVars" class="text-sm text-surface-500 py-2">Nenhuma variável encontrada. Use &#123;&#123;nome&#125;&#125; no template.</div>

        <div v-else class="flex flex-column gap-3">
          <div v-for="v in vars" :key="v" class="flex flex-column gap-1">
            <label :for="'var-' + v" class="text-sm font-medium text-900">{{ v }}</label>
            <InputText :id="'var-' + v" v-model="values[v]" :placeholder="`Valor para ${v}`" class="w-full" :data-testid="`var-input-${v}`" />
          </div>
        </div>

        <div class="border-1 border-surface-200 rounded-lg p-3 bg-surface-50 min-h-6rem">
          <div class="text-xs font-semibold text-surface-600 mb-2 flex align-items-center justify-content-between">
            <span>Preview</span>
            <Button icon="pi pi-copy" text size="small" label="Copiar" @click="copy" data-testid="copy-btn" />
          </div>
          <pre class="m-0 white-space-pre-wrap text-sm text-900 line-height-3" data-testid="preview-text">{{ rendered || '—' }}</pre>
        </div>

        <Button label="Testar no Chat →" icon="pi pi-send" outlined class="w-full" @click="goChat" data-testid="go-chat-btn" />
      </div>
    </template>
  </Card>
</template>

<style scoped>
.min-h-6rem { min-height: 6rem; }
</style>
