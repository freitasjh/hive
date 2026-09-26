<script setup lang="ts">
/**
 * TheFooterbar.vue
 * Global footer component for system info and quick links.
 */
import Badge from 'primevue/badge';

interface FooterLink {
  label: string;
  href: string;
}

const props = withDefaults(defineProps<{
  /** Application version (e.g., v1.0.0) */
  version: string;
  /** Environment name (e.g., Production, Staging) */
  environment: string;
  /** Visual indicator of backend connectivity */
  status?: 'online' | 'offline';
  /** Secondary navigation links */
  links?: FooterLink[];
}>(), {
  version: 'v1.0.0',
  environment: 'Production',
  status: 'online',
  links: () => []
});

const currentYear = new Date().getFullYear();
</script>

<template>
  <footer class="the-footerbar px-4 flex align-items-center justify-content-between">
    <div class="flex align-items-center gap-4">
      <!-- Copyright -->
      <span class="text-sm text-surface-500 font-medium">
        &copy; {{ currentYear }} Hive AI. All rights reserved.
      </span>

      <!-- Quick Links -->
      <div v-if="links.length" class="hidden sm:flex align-items-center gap-3 border-left-1 border-surface-300 pl-4">
        <a 
          v-for="link in links" 
          :key="link.label" 
          :href="link.href" 
          class="text-xs font-semibold text-surface-600 no-underline hover:text-primary transition-colors"
        >
          {{ link.label }}
        </a>
      </div>
    </div>

    <!-- System Info -->
    <div class="flex align-items-center gap-3">
      <div class="flex align-items-center gap-2 pr-3 border-right-1 border-surface-300">
        <span class="text-xs font-bold text-surface-400 uppercase tracking-widest">Status:</span>
        <Badge 
          :severity="status === 'online' ? 'success' : 'danger'" 
          :value="status.toUpperCase()" 
          class="text-[10px] font-bold"
        />
      </div>
      
      <div class="text-xs text-surface-600 font-bold">
        {{ version }} <span class="text-surface-300 mx-1">|</span> {{ environment }}
      </div>
    </div>
  </footer>
</template>

<style scoped>
.the-footerbar {
  height: var(--footer-height);
  z-index: var(--z-footer);
  background-color: var(--p-content-background);
  border-top: 1px solid var(--p-content-border-color);
  color: var(--p-text-muted-color);
}

:deep(.p-badge) {
  min-width: 4rem;
  border-radius: 6px;
}
</style>
