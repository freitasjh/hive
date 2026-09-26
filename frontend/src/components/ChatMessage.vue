<template>
  <div :class="['flex w-full mb-4', role === 'USER' ? 'justify-content-end' : 'justify-content-start', 'fade-in-up transition-all duration-300 ease-in-out']">
    <div :class="['message-bubble p-4 shadow-sm rounded-2xl relative max-w-70 md:max-w-60 lg:max-w-50 border-1', 
                role === 'USER' ? 'user-msg' : 'assistant-msg']">
      
      <div class="flex align-items-center gap-2 mb-2">
        <div v-if="role === 'ASSISTANT'" class="assistant-icon-container w-2rem h-2rem rounded-lg flex align-items-center justify-content-center shadow-sm">
           <i class="pi pi-bolt text-sm"></i>
        </div>
        <span class="text-xs font-bold uppercase tracking-wider" 
             :class="role === 'USER' ? 'user-role-label' : 'assistant-role-label'">
          {{ role === 'USER' ? 'Você' : 'Hive Assistant' }}
        </span>
      </div>

      <div class="message-content text-sm md:text-base line-height-3 break-words overflow-hidden" v-html="sanitizedContent"></div>
      
      <div class="flex align-items-center justify-content-end gap-2 mt-2 opacity-60">
        <span class="text-xs font-medium">{{ formattedTime }}</span>
        <i v-if="role === 'USER'" class="pi pi-check-circle text-xs"></i>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import DOMPurify from 'dompurify'

const props = defineProps<{
  role: 'USER' | 'ASSISTANT'
  content: string
  timestamp: Date | string
}>()

const sanitizedContent = computed(() => DOMPurify.sanitize(props.content))

const formattedTime = computed(() => {
  const date = typeof props.timestamp === 'string' ? new Date(props.timestamp) : props.timestamp
  return new Intl.DateTimeFormat('pt-BR', { hour: '2-digit', minute: '2-digit' }).format(date)
})
</script>

<style scoped>
.message-bubble {
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

.message-bubble:hover {
  transform: translateY(-2px);
  box-shadow: var(--shadow-md);
}

.assistant-icon-container {
  background-color: var(--p-primary-color);
  color: var(--p-primary-contrast-color);
}

.user-role-label {
  color: var(--p-primary-100);
}

.assistant-role-label {
  color: var(--p-primary-color);
  opacity: 0.8;
}

.user-msg {
  background: linear-gradient(135deg, var(--p-primary-700) 0%, var(--p-primary-900) 100%);
  color: #FFFFFF;
  border-color: var(--p-primary-800);
  border-bottom-right-radius: 4px !important;
}

.assistant-msg {
  background-color: var(--p-content-background);
  backdrop-filter: blur(8px);
  color: var(--p-text-color);
  border-color: var(--p-content-border-color);
  border-bottom-left-radius: 4px !important;
}

@keyframes fadeInUp {
  from {
    opacity: 0;
    transform: translateY(12px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.fade-in-up {
  animation: fadeInUp 0.5s cubic-bezier(0.22, 1, 0.36, 1) forwards;
}

.max-w-70 { max-width: 85%; }

@media (min-width: 768px) {
  .max-w-60 { max-width: 75%; }
}

@media (min-width: 1024px) {
  .max-w-50 { max-width: 65%; }
}
</style>
