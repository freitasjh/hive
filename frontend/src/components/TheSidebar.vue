<script setup lang="ts">
/**
 * TheSidebar.vue
 * Global navigation sidebar for switching between main functional modules.
 */
import { computed } from 'vue';
import Button from 'primevue/button';

interface NavItem {
  label: string;
  icon: string;
  to: string;
  badge?: string;
}

const props = withDefaults(defineProps<{
  /** Controls the collapse/expand state */
  isCollapsed?: boolean;
  /** List of main navigation sections */
  items: NavItem[];
  /** The key/to of the currently active route section */
  activeSection?: string;
}>(), {
  isCollapsed: false,
  items: () => []
});

const emit = defineEmits(['update:isCollapsed']);

const toggleSidebar = () => {
  emit('update:isCollapsed', !props.isCollapsed);
};

const sidebarWidth = computed(() => 
  props.isCollapsed ? 'var(--sidebar-width-collapsed)' : 'var(--sidebar-width-expanded)'
);
</script>

<template>
  <nav 
    class="the-sidebar flex flex-column h-full transition-all transition-duration-300"
    :style="{ width: sidebarWidth }"
  >
    <!-- Toggle Button -->
    <div class="flex justify-content-end p-3">
      <Button 
        :icon="isCollapsed ? 'pi pi-chevron-right' : 'pi pi-chevron-left'" 
        text 
        severity="secondary" 
        class="border-round-xl w-3rem h-3rem" 
        @click="toggleSidebar" 
      />
    </div>

    <!-- Navigation Items -->
    <div class="flex flex-column gap-2 px-3 flex-grow-1 overflow-y-auto">
      <router-link 
        v-for="item in items" 
        :key="item.to" 
        :to="item.to" 
        class="nav-item flex align-items-center gap-3 p-3 rounded-2xl no-underline transition-all transition-duration-200 cursor-pointer"
        :class="{ 'active': activeSection === item.to || activeSection?.startsWith(item.to) }"
      >
        <i :class="[item.icon, 'text-xl']"></i>
        <span v-if="!isCollapsed" class="text-sm font-bold white-space-nowrap">
          {{ item.label }}
        </span>
        <div v-if="!isCollapsed && item.badge" class="nav-badge ml-auto text-xs px-2 py-1 rounded-lg font-bold">
          {{ item.badge }}
        </div>
      </router-link>
    </div>

    <!-- Footer Actions (Optional) -->
    <div class="mt-auto p-3 flex flex-column gap-2 border-top-1 border-surface-200">
      <div 
        class="flex align-items-center gap-3 p-3 rounded-2xl cursor-pointer hover:bg-surface-100 text-surface-600 transition-colors"
        v-tooltip.right="isCollapsed ? 'Documentação' : undefined"
      >
        <i class="pi pi-book text-xl"></i>
        <span v-if="!isCollapsed" class="text-sm font-semibold">Documentação</span>
      </div>
    </div>
  </nav>
</template>

<style scoped>
.the-sidebar {
  z-index: var(--z-sidebar);
  background-color: var(--p-content-background);
  border-right: 1px solid var(--p-content-border-color);
}

.nav-item {
  color: var(--p-surface-600);
}

.nav-item:hover {
  background-color: var(--p-surface-100);
  color: var(--p-surface-900);
  transform: translateX(4px);
}

.nav-item.active {
  background-color: var(--p-primary-color);
  color: var(--p-primary-contrast-color);
  box-shadow: 0 8px 16px -4px rgba(var(--p-primary-500-rgb), 0.3);
}

.nav-badge {
  background-color: var(--p-primary-color);
  color: var(--p-primary-contrast-color);
}

/* Tooltip style if needed */
:deep(.p-tooltip-text) {
  font-size: 0.75rem;
  font-weight: 600;
  border-radius: 6px;
  background: var(--p-surface-900);
}
</style>
