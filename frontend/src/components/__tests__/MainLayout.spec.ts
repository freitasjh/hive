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

  it('exposes Chat, Prompts, Provedores and the tenant model — and no agent or knowledge', () => {
    const wrapper = mountLayout();
    const sidebar = wrapper.findComponent(TheSidebar);
    expect(sidebar.exists()).toBe(true);
    const items = sidebar.props('items') as Array<{ label: string; icon: string; to: string }>;
    const labels = items.map((i) => i.label);

    // Prompts é a tela principal depois do chat: o prompt é o que a conversa escolhe.
    expect(labels).toEqual(['Chat', 'Prompts', 'Provedores', 'Modelo do tenant']);
    expect(items).toContainEqual({ label: 'Prompts', icon: 'pi pi-bolt', to: '/prompts' });
    // O default do tenant tem tela própria; não é mais uma coluna do editor de agente.
    expect(items).toContainEqual({ label: 'Modelo do tenant', icon: 'pi pi-cog', to: '/settings/llm' });
    expect(labels).not.toContain('Agentes');
    expect(labels).not.toContain('Bases de Conhecimento');
  });

  it('maps settings/llm breadcrumb labels to PT-BR', async () => {
    mockRoute.path = '/settings/llm';
    const wrapper = mountLayout();
    await wrapper.vm.$nextTick();
    const topbar = wrapper.findComponent(TheTopbar);
    const breadcrumbs = topbar.props('breadcrumbs') as Array<{ label: string }>;
    const labels = breadcrumbs.map((b) => b.label);
    expect(labels).toContain('Configurações');
    expect(labels).toContain('Modelo do tenant');
  });

  it('maps prompts breadcrumb labels to PT-BR', async () => {
    mockRoute.path = '/prompts/new';
    const wrapper = mountLayout();
    await wrapper.vm.$nextTick();
    const topbar = wrapper.findComponent(TheTopbar);
    const breadcrumbs = topbar.props('breadcrumbs') as Array<{ label: string }>;
    const labels = breadcrumbs.map((b) => b.label);
    expect(labels).toContain('Prompts');
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
