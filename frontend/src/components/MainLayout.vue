<script setup lang="ts">
/**
 * MainLayout.vue
 * Orchestrates the global layout components: Topbar, Sidebar, and Footerbar.
 * Provides the main content area with a router-view.
 */
import { ref, computed, onMounted, onUnmounted } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { useAuthStore } from '../stores/auth.store';
import Button from 'primevue/button';
import TheTopbar from './TheTopbar.vue';
import TheSidebar from './TheSidebar.vue';
import TheFooterbar from './TheFooterbar.vue';
import { breadcrumbCustomLabel } from '../utils/breadcrumbOverride';

const route = useRoute();
void useRouter();
const authStore = useAuthStore();

const isSidebarCollapsed = ref(false);
const isMobile = ref(false);

function updateResponsive() {
  if (typeof window !== 'undefined') {
    isMobile.value = window.innerWidth < 768
    if (window.innerWidth < 768) {
      isSidebarCollapsed.value = true
    }
  }
}

onMounted(() => {
  updateResponsive()
  window.addEventListener('resize', updateResponsive)
})
onUnmounted(() => {
  if (typeof window !== 'undefined') window.removeEventListener('resize', updateResponsive)
})

// Navigation items for the sidebar
const navItems = ref([
  { label: 'Chat', icon: 'pi pi-comments', to: '/chat' },
  { label: 'Prompts', icon: 'pi pi-bolt', to: '/prompts' },
  { label: 'Agentes', icon: 'pi pi-users', to: '/agents' },
  { label: 'Bases de Conhecimento', icon: 'pi pi-book', to: '/knowledge' },
  { label: 'Provedores', icon: 'pi pi-key', to: '/settings/providers' }
]);

// Breadcrumbs logic based on route meta or path
const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i
const breadcrumbs = computed(() => {
  const paths = route.path.split('/').filter(p => p);
  const items: any[] = [{ label: 'Home', to: '/', icon: 'pi pi-home' }];
  
  let currentPath = '';
  paths.forEach((path, index) => {
    currentPath += `/${path}`;
    const isUuid = UUID_RE.test(path)
    const isLast = index === paths.length - 1
    let label: string
    if (isUuid) {
      if (breadcrumbCustomLabel.value && isLast) {
        label = breadcrumbCustomLabel.value
      } else if (paths[index + 1] === 'edit' || paths[index - 1] === 'agents') {
        label = breadcrumbCustomLabel.value ?? 'Editar'
      } else {
        return
      }
    } else if (path === 'edit' && UUID_RE.test(paths[index - 1] ?? '')) {
      return
    } else {
      const labelMap: Record<string, string> = { agents: 'Agentes', prompts: 'Prompts', chat: 'Chat', new: 'Novo', settings: 'Configurações', providers: 'Provedores', knowledge: 'Bases de Conhecimento' }
      label = labelMap[path] ?? path.charAt(0).toUpperCase() + path.slice(1)
    }
    items.push({
      label,
      to: isLast || isUuid ? undefined : currentPath
    });
  });
  
  return items;
});

const userName = computed(() => authStore.user?.name || 'Usuário');
const userAvatar = computed(() => authStore.user?.avatar);
</script>

<template>
  <div class="main-layout flex flex-column h-screen overflow-hidden">
    <!-- Topbar -->
    <TheTopbar 
      :user-name="userName" 
      :user-avatar="userAvatar" 
      :breadcrumbs="breadcrumbs"
      class="flex-shrink-0"
    />

    <div class="flex flex-grow-1 overflow-hidden p-3 gap-3" :class="{ 'mobile-layout': isMobile }">
      <!-- Sidebar -->
      <TheSidebar 
        v-model:is-collapsed="isSidebarCollapsed"
        :items="navItems"
        :active-section="route.path"
        class="flex-shrink-0 rounded-2xl shadow-sm"
        :class="{ 'sidebar-mobile': isMobile, 'sidebar-mobile-hidden': isMobile && isSidebarCollapsed }"
      />
      <Button
        v-if="isMobile && isSidebarCollapsed"
        icon="pi pi-bars"
        rounded
        severity="secondary"
        class="mobile-menu-btn"
        aria-label="Abrir menu"
        @click="isSidebarCollapsed = false"
      />
      <div
        v-if="isMobile && !isSidebarCollapsed"
        class="mobile-overlay"
        @click="isSidebarCollapsed = true"
      />

      <!-- Main Content Area -->
      <main class="main-content flex-grow-1 flex flex-column overflow-hidden rounded-2xl shadow-sm">
        <div class="content-container flex-grow-1 overflow-y-auto p-4">
          <router-view v-slot="{ Component }">
            <transition name="fade" mode="out-in">
              <component :is="Component" />
            </transition>
          </router-view>
        </div>

        <!-- Footerbar -->
        <TheFooterbar 
          version="v1.0.0" 
          environment="Development"
          :links="[
            { label: 'Documentação', href: '#' },
            { label: 'Suporte', href: '#' }
          ]"
          class="flex-shrink-0"
        />
      </main>
    </div>
  </div>
</template>

<style scoped>
.main-layout {
  background-color: var(--p-content-background);
  color: var(--p-text-color);
}

.main-content {
  background-color: var(--p-content-background);
  border: 1px solid var(--p-content-border-color);
}

.content-container {
  /* Ensure content doesn't get hidden behind sticky elements if any */
  position: relative;
}

/* Page Transitions */
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.2s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}

/* Custom scrollbar for the main content */
.content-container::-webkit-scrollbar {
  width: 8px;
}

.content-container::-webkit-scrollbar-track {
  background: transparent;
}

.content-container::-webkit-scrollbar-thumb {
  background: var(--p-surface-200);
  border-radius: 10px;
}

.content-container::-webkit-scrollbar-thumb:hover {
  background: var(--p-surface-300);
}

.mobile-menu-btn {
  position: fixed;
  bottom: 1.5rem;
  left: 1rem;
  z-index: 1100;
  box-shadow: 0 4px 12px rgba(0,0,0,0.15);
}

.mobile-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0,0,0,0.35);
  z-index: 1050;
}

.sidebar-mobile {
  position: fixed;
  left: 0;
  top: 72px;
  bottom: 0;
  z-index: 1060;
  height: auto !important;
  border-radius: 0 1rem 1rem 0 !important;
  box-shadow: 4px 0 16px rgba(0,0,0,0.12);
}

.sidebar-mobile-hidden {
  display: none;
}

@media (min-width: 768px) {
  .mobile-menu-btn,
  .mobile-overlay {
    display: none !important;
  }
  .sidebar-mobile {
    position: relative;
    left: auto;
    top: auto;
    bottom: auto;
    height: 100% !important;
    border-radius: 1rem !important;
    box-shadow: none;
  }
  .sidebar-mobile-hidden {
    display: flex;
  }
}

@media (max-width: 767px) {
  .mobile-layout {
    position: relative;
  }
  .main-content {
    width: 100%;
  }
}
</style>
