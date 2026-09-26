<script setup lang="ts">
/**
 * TheTopbar.vue
 * Global header component for navigation context and user actions.
 */
import { ref } from 'vue';
import { useRouter } from 'vue-router';
import { useAuthStore } from '../stores/auth.store';
import Breadcrumb from 'primevue/breadcrumb';
import Avatar from 'primevue/avatar';
import Button from 'primevue/button';
import Menu from 'primevue/menu';

interface BreadcrumbItem {
  label: string;
  to?: string;
  icon?: string;
}

const props = withDefaults(defineProps<{
  /** Application logo source */
  logoUrl?: string;
  /** Name to display in the profile section */
  userName: string;
  /** URL for the user's avatar image */
  userAvatar?: string;
  /** Navigation trail items */
  breadcrumbs?: BreadcrumbItem[];
  /** If true, the topbar will remain at the top of the viewport */
  isSticky?: boolean;
  /** If true, shows the theme toggle switcher */
  showThemeToggle?: boolean;
}>(), {
  logoUrl: '/favicon.svg',
  breadcrumbs: () => [],
  isSticky: true,
  showThemeToggle: true
});

const router = useRouter();
const authStore = useAuthStore();
const menu = ref();

const userMenuItems = ref([
  { label: 'Configurações', icon: 'pi pi-cog' },
  { 
    label: 'Sair', 
    icon: 'pi pi-sign-out', 
    command: () => {
      authStore.logout();
      router.push('/login');
    }
  }
]);

const toggleUserMenu = (event: Event) => {
  menu.value.toggle(event);
};

const isDark = ref(document.documentElement.classList.contains('p-dark'));

const toggleTheme = () => {
  isDark.value = !isDark.value;
  if (isDark.value) {
    document.documentElement.classList.add('p-dark');
  } else {
    document.documentElement.classList.remove('p-dark');
  }
};
</script>

<template>
  <header 
    class="the-topbar px-4 flex align-items-center justify-content-between rounded-2xl shadow-sm mx-3 mt-3"
    :class="{ 'sticky-topbar': isSticky }"
  >
    <div class="flex align-items-center gap-4">
      <!-- Branding -->
      <router-link to="/" class="flex align-items-center gap-2 no-underline text-900">
        <img :src="logoUrl" alt="Hive Logo" class="w-2rem h-2rem" />
        <span class="text-xl font-bold tracking-tight">Hive <span class="text-primary">AI</span></span>
      </router-link>

      <!-- Breadcrumbs -->
      <Breadcrumb :model="breadcrumbs" class="hidden md:block">
        <template #item="{ item }">
          <router-link v-if="item.to" :to="item.to" class="no-underline text-surface-500 hover:text-primary transition-colors">
            <span :class="item.icon" class="mr-2" v-if="item.icon"></span>
            <span class="text-sm font-medium">{{ item.label }}</span>
          </router-link>
          <span v-else class="text-surface-900 font-bold text-sm">
             {{ item.label }}
          </span>
        </template>
      </Breadcrumb>
    </div>

    <div class="flex align-items-center gap-3">
      <!-- Theme Toggle Slot -->
      <slot name="theme-toggle">
        <Button 
          v-if="showThemeToggle" 
          :icon="isDark ? 'pi pi-sun' : 'pi pi-moon'" 
          rounded 
          text 
          severity="secondary"
          @click="toggleTheme" 
          v-tooltip.bottom="'Alternar Tema'"
          class="w-3rem h-3rem"
        />
      </slot>

      <!-- User Profile -->
      <div 
        class="flex align-items-center gap-2 cursor-pointer p-2 rounded-2xl hover:bg-surface-50 transition-colors"
        @click="toggleUserMenu"
      >
        <div class="text-right hidden sm:block">
          <p class="m-0 text-sm font-bold text-900">{{ userName }}</p>
          <p class="m-0 text-xs text-surface-500">Membro</p>
        </div>
        <Avatar 
          :image="userAvatar" 
          :label="!userAvatar ? userName.charAt(0) : undefined" 
          shape="circle" 
          class="border-2 border-primary shadow-sm" 
        />
      </div>
      <Menu ref="menu" :model="userMenuItems" :popup="true" class="rounded-2xl shadow-lg border-1 border-surface-100" />
    </div>
  </header>
</template>

<style scoped>
.the-topbar {
  height: 72px;
  z-index: var(--z-topbar);
  background-color: var(--p-content-background);
  border: 1px solid var(--p-content-border-color);
  color: var(--p-text-color);
  transition: all 0.3s ease;
}

.sticky-topbar {
  position: sticky;
  top: 1rem;
}

:deep(.p-breadcrumb) {
  background: transparent;
  border: none;
  padding: 0;
}

:deep(.p-breadcrumb-list) {
  gap: 0.5rem;
}

:deep(.p-menu) {
  padding: 0.5rem;
}

:deep(.p-menu-item-link) {
  border-radius: 12px;
}
</style>
