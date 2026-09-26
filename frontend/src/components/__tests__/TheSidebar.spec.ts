import { describe, it, expect } from 'vitest';
import { mount } from '@vue/test-utils';
import TheSidebar from '../TheSidebar.vue';
import PrimeVue from 'primevue/config';
import Button from 'primevue/button';
import Tooltip from 'primevue/tooltip';

describe('TheSidebar.vue', () => {
  const items = [
    { label: 'Chat', icon: 'pi pi-comments', to: '/chat' },
    { label: 'Prompts', icon: 'pi pi-bolt', to: '/prompts' }
  ];

  const mountComponent = (props = {}) => {
    return mount(TheSidebar, {
      props: {
        items,
        ...props
      },
      global: {
        plugins: [PrimeVue],
        components: {
          Button
        },
        directives: {
          tooltip: Tooltip
        },
        stubs: {
          'router-link': { template: '<a><slot /></a>' }
        }
      }
    });
  };

  it('renders navigation items', () => {
    const wrapper = mountComponent();
    expect(wrapper.text()).toContain('Chat');
    expect(wrapper.text()).toContain('Prompts');
  });

  it('highlights the active section', () => {
    const wrapper = mountComponent({ activeSection: '/chat' });
    const activeItem = wrapper.find('.nav-item.active');
    expect(activeItem.exists()).toBe(true);
    expect(activeItem.text()).toContain('Chat');
  });

  it('emits update:isCollapsed when toggle button is clicked', async () => {
    const wrapper = mountComponent({ isCollapsed: false });
    const toggleBtn = wrapper.findComponent(Button);
    await toggleBtn.trigger('click');
    expect(wrapper.emitted('update:isCollapsed')).toBeTruthy();
    expect(wrapper.emitted('update:isCollapsed')![0]).toEqual([true]);
  });

  it('hides labels when collapsed', () => {
    const wrapper = mountComponent({ isCollapsed: true });
    expect(wrapper.text()).not.toContain('Chat');
    expect(wrapper.text()).not.toContain('Prompts');
  });

  it('shows badge when provided', () => {
    const itemsWithBadge = [
      { label: 'Chat', icon: 'pi pi-comments', to: '/chat', badge: '3' }
    ];
    const wrapper = mountComponent({ items: itemsWithBadge, isCollapsed: false });
    expect(wrapper.text()).toContain('3');
  });
});
