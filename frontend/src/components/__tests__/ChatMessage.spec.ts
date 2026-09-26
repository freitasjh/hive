import { describe, it, expect } from 'vitest';
import { mount } from '@vue/test-utils';
import ChatMessage from '../ChatMessage.vue';

describe('ChatMessage.vue', () => {
  const props = {
    role: 'USER' as const,
    content: 'Hello, world!',
    timestamp: new Date('2025-01-01T12:00:00Z')
  };

  const mountComponent = (customProps = {}) => {
    return mount(ChatMessage, {
      props: {
        ...props,
        ...customProps
      }
    });
  };

  it('renders user message correctly', () => {
    const wrapper = mountComponent();
    expect(wrapper.text()).toContain('Hello, world!');
    expect(wrapper.text()).toContain('Você');
    expect(wrapper.find('.user-msg').exists()).toBe(true);
  });

  it('renders assistant message correctly', () => {
    const wrapper = mountComponent({ role: 'ASSISTANT' });
    expect(wrapper.text()).toContain('Hello, world!');
    expect(wrapper.text()).toContain('Hive Assistant');
    expect(wrapper.find('.assistant-msg').exists()).toBe(true);
    expect(wrapper.find('.assistant-icon-container').exists()).toBe(true);
  });

  it('sanitizes content', () => {
    const wrapper = mountComponent({ content: '<script>alert("xss")</script><b>Bold</b>' });
    expect(wrapper.html()).toContain('<b>Bold</b>');
    expect(wrapper.html()).not.toContain('<script>');
  });

  it('formats timestamp correctly', () => {
    const wrapper = mountComponent();
    // The exact format might depend on the locale, but it should contain 12:00 or similar
    expect(wrapper.text()).toMatch(/\d{2}:\d{2}/);
  });
});
