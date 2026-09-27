<template>
  <div class="chat-input-container p-3 rounded-2xl shadow-lg border-1 border-surface-200 flex flex-column gap-2">
    <div class="flex align-items-end gap-3">
      <div class="flex-grow-1 relative">
        <Textarea 
          v-model="modelValue" 
          autoResize 
          rows="1" 
          class="w-full border-none shadow-none focus:ring-0 text-base py-2 px-1 chat-textarea" 
          placeholder="Como posso ajudar você hoje?" 
          @keydown.enter.prevent="handleEnter"
          :disabled="disabled"
        />
      </div>
      <div class="flex gap-2">
        <Button
          :icon="disabled ? 'pi pi-spin pi-spinner' : 'pi pi-send'"
          class="rounded-xl shadow-md send-button"
          @click="emit('send')"
          :disabled="!modelValue.trim() || disabled"
        />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
/**
 * The composer: a text field and a send button, nothing else.
 *
 * It used to carry an attach button, a voice button and a row of suggestion tags. All three were
 * affordances with no handler behind them — attachment and voice are out of the product by decision,
 * and the suggestions were hardcoded strings that ignored the prompt the user selected, which
 * contradicted the only rule the product has. A control that looks clickable and does nothing is
 * worse than a control that does not exist, so they were removed rather than disabled.
 */
import { computed } from 'vue'
import Textarea from 'primevue/textarea'
import Button from 'primevue/button'

const props = defineProps<{
  modelValue: string
  disabled?: boolean
}>()

const emit = defineEmits(['update:modelValue', 'send'])

const modelValue = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val)
})

const handleEnter = (e: KeyboardEvent) => {
  if (e.shiftKey) return
  if (modelValue.value.trim() && !props.disabled) {
    emit('send')
  }
}
</script>

<style scoped>
.chat-input-container {
  transition: all 0.4s cubic-bezier(0.4, 0, 0.2, 1);
  background-color: var(--p-content-background);
  backdrop-filter: blur(12px);
  border-color: var(--p-content-border-color);
}

.chat-input-container:focus-within {
  transform: translateY(-4px);
  box-shadow: 0 25px 50px -12px rgba(0, 0, 0, 0.25);
  border-color: var(--p-primary-400);
}

.chat-textarea {
  background: transparent;
  resize: none;
  min-height: 42px;
  max-height: 200px;
  color: var(--p-text-color);
}

.send-button {
  width: 42px;
  height: 42px;
  background: linear-gradient(135deg, var(--p-primary-600) 0%, var(--p-primary-800) 100%);
  border: none;
  color: white;
}

.send-button:hover {
  background: linear-gradient(135deg, var(--p-primary-500) 0%, var(--p-primary-700) 100%);
  transform: scale(1.05);
}
</style>
