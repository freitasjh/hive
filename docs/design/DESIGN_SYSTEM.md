# Hive AI - Design System Specification (Deep Indigo & Amber)

This document defines the visual foundation and component contracts for the Hive AI platform.

## 1. Design Token Hierarchy

### 1.1 Primitive Tokens (Aura Preset Reference)
These tokens represent the raw values from the Deep Indigo & Amber theme.

| Category | Token | Light Value | Dark Value |
|----------|-------|-------------|------------|
| **Color** | `indigo` (Primary) | `indigo-900` (#1A237E) | `indigo-400` (#5C6BC0) |
| **Color** | `amber` (Accent) | `amber-500` (#FFC107) | `amber-400` (#FFCA28) |
| **Surface** | `surface` (Base) | `surface-0` (#FFFFFF) | `surface-900` (#0F172A) |
| **Surface** | `surface` (Alt) | `surface-50` (#F8FAFC) | `surface-950` (#020617) |

### 1.2 Semantic Tokens
Purpose-mapped tokens that bridge design intent and implementation.

| Token | Reference | Usage |
|-------|-----------|-------|
| `color.brand.primary` | `var(--p-primary-color)` | Main identity color |
| `color.brand.accent` | `var(--p-highlight-background)` | Action highlights (Amber) |
| `color.bg.canvas` | `var(--p-surface-50)` | Main application background |
| `color.bg.surface` | `var(--p-surface-0)` | Cards, Modals, Topbar |
| `color.text.primary` | `var(--p-surface-900)` | Primary body text |
| `color.text.muted` | `var(--p-surface-500)` | Secondary/less important text |
| `spacing.page.gutter` | `var(--p-spacing-4)` | Standard outer padding (16px) |
| `font.family.base` | `'Inter', sans-serif` | Global typography |

### 1.3 Layout Component Tokens
Scoped tokens for structural consistency.

| Token | Value | Application |
|-------|-------|-------------|
| `topbar.height` | `64px` | Height of the Global Topbar |
| `sidebar.width.expanded` | `280px` | Navigation Sidebar (Open) |
| `sidebar.width.collapsed` | `68px` | Navigation Sidebar (Collapsed) |
| `footer.height` | `48px` | Height of the Global Footer |

---

## 2. Global Layout Component APIs

### 2.1 `TheTopbar.vue`
The global header providing context, branding, and user actions.

**Interface:**
```typescript
interface BreadcrumbItem {
  label: string;
  to?: string;
  icon?: string;
}

interface TheTopbarProps {
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
}
```

### 2.2 `TheFooterbar.vue`
Global footer for system information and support links.

**Interface:**
```typescript
interface FooterLink {
  label: string;
  href: string;
}

interface TheFooterbarProps {
  /** Application version (e.g., v1.0.0) */
  version: string;
  /** Environment name (e.g., Production, Staging) */
  environment: string;
  /** Visual indicator of backend connectivity */
  status?: 'online' | 'offline';
  /** Secondary navigation links */
  links?: FooterLink[];
}
```

### 2.3 `TheSidebar.vue`
Global navigation for switching between main functional modules.

**Interface:**
```typescript
interface NavItem {
  label: string;
  icon: string;
  to: string;
  badge?: string;
}

interface TheSidebarProps {
  /** Controls the collapse/expand state */
  isCollapsed?: boolean;
  /** List of main navigation sections */
  items: NavItem[];
  /** The key/to of the currently active route section */
  activeSection?: string;
}
```

---

## 3. Visual Consistency & Accessibility
- **Contrasts**: Always ensure that Amber accents maintain WCAG AA contrast (4.5:1) against surface backgrounds.
- **Dark Mode**: All components must use CSS variables (`--p-*`) to automatically switch values based on the `.p-dark` selector.
- **States**: Every interactive element (nav links, profile menu) must define explicit `:hover`, `:active`, and `:focus-visible` states.
