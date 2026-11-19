# Hive AI — Component Template Specs (Chat)

Framework-agnostic contracts for reusable chat components. Implementations live in frontend/src/components (Vue 3 + PrimeVue) and React adapters consume the same contract.
Tokens: Pulse / Deep Indigo & Amber (docs/design/DESIGN_SYSTEM.md). No ad-hoc tokens.
Acessado em: 2026-10-29.

## Versioning
Spec version: 1.0.0 (MINOR bump on additive props, MAJOR on breaking).

## Shared types

```ts
export type ChatRole = 'USER' | 'ASSISTANT' | 'SYSTEM'
export type MessageStatus = 'sending' | 'streaming' | 'done' | 'error' | 'cancelled'
export type ChatSessionDTO = { id: string; title: string; updateAt: string }
```

---

## 1. ChatMessageBubble

Category: composite. Purpose: render a single chat message (user/assistant/system) with streaming support.

### Props

| Prop | Type | Default | Required | Notes |
|------|------|---------|----------|-------|
| role | ChatRole | — | yes | drives surface/alignment/icon |
| content | string | '' | yes | raw text/markdown; sanitized by adapter |
| status | MessageStatus | 'done' | no | streaming indicator |
| timestamp | Date or string | — | no | rendered locale-formatted |
| avatarUrl | string | — | no | assistant/user avatar |
| label | string | role-derived | no | override speaker label |
| labelKey | string | — | no | i18n key resolved by adapter |
| maxWidth | 'sm' or 'md' or 'lg' | 'md' | no | bubble width scale |
| showTimestamp | boolean | true | no | |
| showRoleLabel | boolean | true | no | |
| collapsible | boolean | false | no | long content show more |
| collapsedLines | number | 6 | no | lines before collapse |

### Slots
- default — override content rendering (markdown).
- header — above content (avatar + label region).
- footer — below content (timestamp, actions).
- actions — action toolbar slot (MessageActionToolbar).
- streaming-indicator — custom typing indicator.

### Events
- copy (content: string) — copy requested.
- retry (messageId: string) — retry requested (error state).
- collapse-toggle (expanded: boolean).

### States
default: surface per role; article + aria-label speaker.
hover: elevate (translateY -2px).
focus: ring amber 3px.
active: pressed.
disabled: n/a.
loading: typing dots / skeleton; aria-busy true.
error: warn tint border-left 3px amber; role alert (system).
empty: hidden (no bubble).
success: done check.
streaming: caret + aria-live polite; live region updates.

### Variants
role: USER (gradient primary, right-aligned, bottom-right radius 4px), ASSISTANT (surface, left-aligned, bottom-left radius 4px), SYSTEM (flat surface-50, left border 3px amber, no gradient).
maxWidth: sm (65%) / md (75%) / lg (85%).

### Composition
Compound: ChatMessageBubble.Header, .Body, .Footer.
Must not nest another bubble.

### A11y
Container role article, aria-labelledby label id.
Streaming content in aria-live polite region (debounced >=500ms to avoid spam).
prefers-reduced-motion: disable fade-in-up.
Contrast WCAG AA (amber on surface >=4.5:1).

### i18n keys
chat.message.role.user, chat.message.role.assistant, chat.message.role.system, chat.message.copy, chat.message.retry, chat.message.showMore, chat.message.showLess.

### Tokens (Pulse)
user: --p-primary-700..900 gradient, --p-primary-800 border.
assistant: --p-content-background, --p-content-border-color, --p-text-color.
system: --p-surface-50, --p-surface-200, --p-amber-500 left border, --p-amber-600 icon.
radius: 2xl, asymmetric 4px bottom.
shadow: --shadow-sm, hover --shadow-md.
focus ring: amber 3px @12%.

### Adapter Vue (PrimeVue)
```vue
<script setup lang="ts">
defineProps<ChatMessageBubbleProps>()
defineEmits<ChatMessageBubbleEvents>()
</script>
<template>
  <article :class="bubbleClasses" :aria-busy="status === 'streaming'">
    <slot name="header">...</slot>
    <div class="message-content" v-html="sanitized" aria-live="polite" />
    <slot name="footer" />
    <slot name="actions" />
  </article>
</template>
```
v-model not needed (display-only); content controlled.
markdown rendered via adapter (marked + DOMPurify), not in spec.

### Adapter React
Props as above; children maps to default slot; actions via render prop or children.

---

## 2. ChatComposer

Category: composite. Purpose: text input + send button for chat.

### Props
| Prop | Type | Default | Required |
|------|------|---------|----------|
| modelValue | string | '' | yes (controlled) |
| disabled | boolean | false | no |
| placeholder | string | — | no |
| placeholderKey | string | chat.composer.placeholder | no |
| maxLength | number | — | no |
| minRows | number | 1 | no |
| maxRows | number | 8 | no |
| submitOnEnter | boolean | true | no (Shift+Enter = newline) |
| showSendButton | boolean | true | no |
| sendDisabled | boolean | computed | no |
| ariaLabel / ariaLabelKey | string | — | no |

### Slots
- prefix — before textarea.
- suffix — after send button.
- send-icon — custom send icon.

### Events
- update:modelValue (value: string)
- send (value: string)
- keydown (e: KeyboardEvent)
- focus / blur.

### States
default, hover, focus (container lifts + border primary-400), active, disabled (textarea + button disabled), loading (send button spinner, aria-busy), error (border red + message), empty (send disabled), success.

### Variants
size: sm/md/lg (height/padding).
variant: surface (default) / ghost.

### A11y
Textarea aria-label from key; aria-multiline true.
Send button type submit, aria-label chat.composer.send.
Enter submits (unless Shift). Announce send via live region.
Focus trap not needed (not modal).

### i18n keys
chat.composer.placeholder, chat.composer.send, chat.composer.sending.

### Tokens
container: --p-content-background, --p-content-border-color, spacing-3.
focus-within: border --p-primary-400, translateY -4px, shadow 0 25px 50px -12px rgba(0,0,0,0.25).
textarea: transparent bg, --p-text-color, min-height 42px, max 200px.
send button: gradient --p-primary-600..800, 42x42, radius-xl.

### Adapter Vue
```vue
<script setup lang="ts">
const props = defineProps<ChatComposerProps>()
const emit = defineEmits<ChatComposerEvents>()
const model = computed({ get: () => props.modelValue, set: v => emit('update:modelValue', v) })
</script>
```
Use v-model binding; autoResize via PrimeVue Textarea.

---

## 3. ChatSidebar

Category: layout. Purpose: persistent conversation list with new-chat, pagination, delete confirm.

### Props
| Prop | Type | Default | Required |
|------|------|---------|----------|
| modelValue | boolean | — | yes (controlled open) |
| sessions | ChatSessionDTO[] | [] | yes |
| selectedId | string or null | null | no |
| loading | boolean | false | no |
| loadError | boolean | false | no |
| totalElements | number | 0 | no |
| totalPages | number | 0 | no |
| pageSize | number | 20 | no |
| currentPage | number | 0 | no |
| width | string | 320px | no |
| position | 'left' or 'right' | 'left' | no |
| dismissible | boolean | true | no |
| showFooter | boolean | true | no |

### Slots
- header — brand/logo.
- newChatButton — override new chat CTA.
- item — scoped { session, selected, onSelect, onDelete }.
- empty — empty state.
- error — error state with retry.
- loading — skeleton list.
- footer — settings/logout.

### Events
- update:modelValue (open: boolean)
- select (id: string)
- newChat
- delete (id: string) (after confirm)
- page (page: number)
- logout
- refresh.

### States
default, loading (3 skeletons), error (message + retry), empty (icon + copy + CTA), success (list), disabled (during delete).

### Variants
mode: drawer (overlay) / persistent (push layout, desktop).
density: comfortable / compact.

### A11y
Drawer role dialog / navigation landmark nav.
List role list; item role button with aria-current true when selected.
Delete requires confirmation dialog (role alertdialog).
Keyboard: Enter/Space select, Delete key triggers delete, Esc closes drawer.
Focus returns to trigger on close.

### i18n keys
chat.sidebar.title, chat.sidebar.newChat, chat.sidebar.recent, chat.sidebar.empty.title, chat.sidebar.empty.description, chat.sidebar.loadError, chat.sidebar.retry, chat.sidebar.delete.confirm.title, chat.sidebar.delete.confirm.body, chat.sidebar.delete.cancel, chat.sidebar.delete.confirm, chat.sidebar.settings, chat.sidebar.logout.

### Tokens
width 320px; drawer surface --p-surface-0; border-right --p-surface-100.
item selected: --p-primary-50 bg, --p-primary-200 border.
item hover: --p-surface-50.
delete: severity danger.

### Adapter Vue
Wrap PrimeVue Drawer/Dialog; expose refresh() via defineExpose.
Controlled open via v-model.

---

## 4. StarterPromptChips

Category: primitive/composite. Purpose: horizontal chip list of starter prompts.

### Props
| Prop | Type | Default | Required |
|------|------|---------|----------|
| prompts | { id: string; label: string; labelKey?: string; icon?: string }[] | [] | yes |
| disabled | boolean | false | no |
| wrap | boolean | true | no |
| size | 'sm' or 'md' | 'md' | no |
| variant | 'outline' or 'filled' or 'ghost' | 'outline' | no |
| maxVisible | number | — | no (overflow -> more) |

### Slots
- chip — scoped { prompt, select } override.
- empty.

### Events
- select (prompt: {id,label}).

### States
default, hover, focus, active, disabled, loading (skeleton chips), empty (hidden or placeholder), success.

### Variants
visual: outline/filled/ghost. Size sm/md.

### A11y
Container role list; chip role button, tabindex 0.
Keyboard: Enter/Space select; arrow keys navigate.
aria-label per chip.

### i18n keys
chat.starter.more, chat.starter.empty.

### Tokens
chip: border --p-surface-200, hover --p-primary-50/--p-primary-200, text --p-text-color.
radius full (pill) or --radius-lg.

### Adapter Vue
Render PrimeVue Chip or Button text; emit select.

---

## 5. MessageActionToolbar

Category: composite. Purpose: row of actions on a message (copy, retry, like, regenerate, delete).

### Props
| Prop | Type | Default | Required |
|------|------|---------|----------|
| actions | ('copy' or 'retry' or 'regenerate' or 'like' or 'dislike' or 'delete' or 'share')[] | ['copy'] | no |
| visible | 'always' or 'hover' or 'focus' | 'hover' | no |
| disabled | boolean | false | no |
| size | 'sm' or 'md' | 'sm' | no |
| orientation | 'horizontal' or 'vertical' | 'horizontal' | no |
| messageId | string | — | yes (for events) |
| content | string | — | no (for copy) |

### Slots
- action — scoped { action, trigger } override.
- extra.

### Events
- action (action: string, messageId: string).
- copy (content), retry, regenerate, like, dislike, delete, share.

### States
default (hidden until hover/focus), hover, focus, active, disabled, loading (per-action spinner), error (toast), empty, success (copied check).

### Variants
size sm/md; orientation.

### A11y
Toolbar role toolbar, aria-label chat.message.actions.
Buttons aria-label from i18n; aria-pressed for like/dislike.
Keyboard: Tab through actions; Enter/Space trigger; Esc hides.
Focus visible ring.

### i18n keys
chat.actions.copy, chat.actions.retry, chat.actions.regenerate, chat.actions.like, chat.actions.dislike, chat.actions.delete, chat.actions.share, chat.actions.copied.

### Tokens
icon buttons text --p-surface-500, hover --p-primary-color.
copied success: --p-green-500 (or primary) check.

### Adapter Vue
Use PrimeVue Button text rounded; tooltip.

---

## 6. LoadingSkeleton

Category: primitive. Purpose: skeleton placeholder for loading states (message rows, sidebar items).

### Props
| Prop | Type | Default | Required |
|------|------|---------|----------|
| variant | 'text' or 'circle' or 'rect' or 'message-row' or 'sidebar-item' | 'text' | no |
| width | string or number | '100%' | no |
| height | string or number | — | no |
| count | number | 1 | no |
| animated | boolean | true | no |
| ariaLabel / ariaLabelKey | string | chat.loading | no |

### Slots
- default — override shape.

### Events
none.

### States
default (animated pulse), static (no animation when prefers-reduced-motion or animated=false), error (n/a), empty.

### Variants
shape variants above; size via width/height.

### A11y
Container role status, aria-busy true, aria-label.
Inner shapes aria-hidden true.
Respect prefers-reduced-motion: disable shimmer.

### i18n keys
chat.loading, chat.loadingMessages.

### Tokens
base: --p-surface-200; shimmer: --p-surface-100/--p-surface-300.
radius: --radius-md/--radius-full.

### Adapter Vue
Wrap PrimeVue Skeleton; add aria-busy.

---

## Evidence / Deepsearch citations

- shadcn/ui conversational primitives (Message, Bubble, MessageScroller): https://ui.shadcn.com/docs/changelog/2026-06-chat-components — acessado 2026-10-29. Padrao: bubble surface scoped, aria-busy during streaming, live region for new rows.
- shadcn Bubble component: https://ui.shadcn.com/docs/components/base/bubble — acessado 2026-10-29. Padrao: collapsible show more, focus states in dark mode.
- MUI X Chat loading/empty states: https://mui.com/x/react-chat/display/loading-and-empty-states/ — acessado 2026-10-29. Padrao: skeleton while loading, empty state content.
- MDN ARIA live regions: https://developer.mozilla.org/en-US/docs/Web/Accessibility/ARIA/Guides/Live_regions — acessado 2026-10-29. Padrao: aria-live polite/assertive, aria-atomic.
- Adrian Roselli More Accessible Skeletons: http://adrianroselli.com/2020/11/more-accessible-skeletons.html — acessado 2026-10-29. Padrao: aria-busy on container, shapes aria-hidden, Loading text.
- A11y Collective live regions guide: https://www.a11y-collective.com/blog/aria-live/ — acessado 2026-10-29.

## Adapter notes (Vue 3 / PrimeVue)
Use script setup lang ts, Composition API.
Controlled state via props + emits (v-model where applicable).
Slots map to Vue named slots; scoped slots for item/chip.
PrimeVue components: Drawer, Dialog, Button, Textarea, Skeleton, Message, ScrollPanel, Paginator, Tooltip, Chip.
i18n via vue-i18n t(); no literals.
Dark mode via .p-dark.

## Anti-patterns (Pulse)
No Vuetify. No inline token values. No boolean prop combos (use variants). No business logic in UI.

## Downstream
frontend-vue-specialist: implement Vue 3 adapters.
frontend-react-specialist: implement React 18 adapters.
qa-engineer: test states listed per component.
ux-designer: visual truth; this spec consumes their parecer (streaming, persistent sidebar, actions, a11y).
