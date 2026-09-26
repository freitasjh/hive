import { describe, expect, it, vi, beforeEach } from 'vitest';
import { mount } from '@vue/test-utils';
import { reactive } from 'vue';
import { createPinia, setActivePinia } from 'pinia';
import PrimeVue from 'primevue/config';
import MainLayout from '../MainLayout.vue';
import TheSidebar from '../TheSidebar.vue';
import TheTopbar from '../TheTopbar.vue';

const mockRoute = reactive({ path: '/chat' });

vi.mock('vue-router', () => ({
  useRoute: () => mockRoute,
  useRouter: () => ({ push: vi.fn() }),
}));

describe('MainLayout.vue — navigation menu', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    mockRoute.path = '/chat';
  });

  const mountLayout = () =>
    mount(MainLayout, {
      global: {
        plugins: [PrimeVue, createPinia()],
        stubs: {
          'router-view': { template: '<div><slot /></div>' },
        },
      },
    });

  it('exposes Provedores nav item after Agentes', () => {
    const wrapper = mountLayout();
    const sidebar = wrapper.findComponent(TheSidebar);
    expect(sidebar.exists()).toBe(true);
    const items = sidebar.props('items') as Array<{ label: string; icon: string; to: string }>;
    expect(items).toContainEqual({ label: 'Provedores', icon: 'pi pi-key', to: '/settings/providers' });
    const labels = items.map((i) => i.label);
    expect(labels.indexOf('Provedores')).toBeGreaterThan(labels.indexOf('Agentes'));
  });

  it('exposes Bases de Conhecimento nav item at index 3 with pi-book', () => {
    const wrapper = mountLayout();
    const sidebar = wrapper.findComponent(TheSidebar);
    const items = sidebar.props('items') as Array<{ label: string; icon: string; to: string }>;
    expect(items).toContainEqual({ label: 'Bases de Conhecimento', icon: 'pi pi-book', to: '/knowledge' });
    const labels = items.map((i) => i.label);
    expect(labels.indexOf('Bases de Conhecimento')).toBe(3);
  });

  it('maps knowledge breadcrumb labels to PT-BR', async () => {
    mockRoute.path = '/knowledge/new';
    const wrapper = mountLayout();
    await wrapper.vm.$nextTick();
    const topbar = wrapper.findComponent(TheTopbar);
    const breadcrumbs = topbar.props('breadcrumbs') as Array<{ label: string }>;
    const labels = breadcrumbs.map((b) => b.label);
    expect(labels).toContain('Bases de Conhecimento');
    expect(labels).toContain('Novo');
  });
  it('maps settings/providers breadcrumb labels to PT-BR', async () => {
    mockRoute.path = '/settings/providers';
    const wrapper = mountLayout();
    await wrapper.vm.$nextTick();
    const topbar = wrapper.findComponent(TheTopbar);
    expect(topbar.exists()).toBe(true);
    const breadcrumbs = topbar.props('breadcrumbs') as Array<{ label: string }>;
    const labels = breadcrumbs.map((b) => b.label);
    expect(labels).toContain('Configurações');
    expect(labels).toContain('Provedores');
  });
});
