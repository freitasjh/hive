import { describe, it, expect } from 'vitest';
import { mount } from '@vue/test-utils';
import TheFooterbar from '../TheFooterbar.vue';
import PrimeVue from 'primevue/config';
import Badge from 'primevue/badge';

describe('TheFooterbar.vue', () => {
  const mountComponent = (props = {}) => {
    return mount(TheFooterbar, {
      props: {
        version: 'v1.0.0',
        environment: 'Production',
        ...props
      },
      global: {
        plugins: [PrimeVue],
        components: {
          Badge
        }
      }
    });
  };

  it('renders version and environment', () => {
    const wrapper = mountComponent({ version: 'v2.0.0', environment: 'Staging' });
    expect(wrapper.text()).toContain('v2.0.0');
    expect(wrapper.text()).toContain('Staging');
  });

  it('renders copyright with current year', () => {
    const wrapper = mountComponent();
    const currentYear = new Date().getFullYear();
    expect(wrapper.text()).toContain(`© ${currentYear} Hive AI`);
  });

  it('renders status badge correctly', () => {
    const wrapper = mountComponent({ status: 'online' });
    const badge = wrapper.findComponent(Badge);
    expect(badge.props('severity')).toBe('success');
    expect(badge.props('value')).toBe('ONLINE');
  });

  it('renders quick links when provided', () => {
    const links = [
      { label: 'Docs', href: '/docs' },
      { label: 'Support', href: '/support' }
    ];
    const wrapper = mountComponent({ links });
    expect(wrapper.text()).toContain('Docs');
    expect(wrapper.text()).toContain('Support');
    const anchors = wrapper.findAll('a');
    expect(anchors[0].attributes('href')).toBe('/docs');
    expect(anchors[1].attributes('href')).toBe('/support');
  });
});
