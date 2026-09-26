import { describe, it, expect, vi, beforeEach } from 'vitest';
import { mount } from '@vue/test-utils';
import { createPinia, setActivePinia } from 'pinia';
import TheTopbar from '../TheTopbar.vue';
import PrimeVue from 'primevue/config';
import Breadcrumb from 'primevue/breadcrumb';
import Avatar from 'primevue/avatar';
import Menu from 'primevue/menu';

// Mock vue-router
vi.mock('vue-router', () => ({
  useRouter: () => ({
    push: vi.fn()
  })
}));

describe('TheTopbar.vue', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
  });

  const mountComponent = (props = {}) => {
    return mount(TheTopbar, {
      props: {
        userName: 'John Doe',
        ...props
      },
      global: {
        plugins: [PrimeVue, createPinia()],
        components: {
          Breadcrumb,
          Avatar,
          Menu,
          'router-link': { template: '<a><slot /></a>' }
        }
      }
    });
  };

  it('renders branding correctly', () => {
    const wrapper = mountComponent();
    expect(wrapper.text()).toContain('Hive');
    expect(wrapper.find('img').attributes('src')).toBe('/favicon.svg');
  });

  it('renders user name and avatar', () => {
    const wrapper = mountComponent({ userName: 'Jane Doe' });
    expect(wrapper.text()).toContain('Jane Doe');
    expect(wrapper.findComponent(Avatar).exists()).toBe(true);
  });

  it('renders breadcrumbs when provided', () => {
    const breadcrumbs = [
      { label: 'Home', to: '/' },
      { label: 'Chat' }
    ];
    const wrapper = mountComponent({ breadcrumbs });
    expect(wrapper.findComponent(Breadcrumb).exists()).toBe(true);
    expect(wrapper.text()).toContain('Home');
    expect(wrapper.text()).toContain('Chat');
  });

  it('toggles user menu on click', async () => {
    const wrapper = mountComponent();
    const profileSection = wrapper.find('.cursor-pointer');
    
    // Mock the menu toggle
    const menuComponent = wrapper.findComponent(Menu);
    const toggleSpy = vi.spyOn(menuComponent.vm, 'toggle');
    
    await profileSection.trigger('click');
    expect(toggleSpy).toHaveBeenCalled();
  });
});
