# Frontend Coding Standards — Hive AI

## Component Conventions

- **Composition API ONLY** — `<script setup lang="ts">`. Options API is forbidden.
- Components are presentation-only. Business logic goes in composables or services.
- File naming: `PascalCase.vue` for components, `camelCase.ts` for services/stores.
- Component tests: `src/components/__tests__/{ComponentName}.spec.ts`.

## State Management (Pinia)

- Stores in `src/stores/` using `defineStore` with setup function syntax.
- Auth store: `auth.store.ts` — manages JWT token in localStorage (`hive_token`).
- Only create stores for globally shared state (auth, theme). Local state stays in components.

## API Layer

- Axios instance: `src/services/api.ts` — baseURL `/hive/v1`.
- Auto-injects `Authorization: Bearer {token}` via request interceptor.
- Auto-redirects to `/login` on 401 response.
- Vite proxy: `/hive` → `http://localhost:8080/hive` (rewrite strips `/hive` prefix).
- Service classes: `src/services/{domain}.service.ts` — wrap API calls.

## Routing

- Vue Router with `createWebHistory`.
- Auth guard: redirects to `/login` if `requiresAuth` meta and no token.
- Lazy-loaded views via `() => import('../views/{View}.vue')`.

## Theming & Design System

- PrimeVue Aura preset with Deep Indigo + Amber custom palette.
- Dark mode: `.p-dark` CSS selector (via PrimeVue config).
- Design tokens in `docs/design/DESIGN_SYSTEM.md`.
- CSS variables: `--p-primary-color`, `--p-highlight-background`, `--p-surface-*`.
- Utility CSS: PrimeFlex (`primeflex/primeflex.css`).
- Icons: PrimeIcons (`primeicons/primeicons.css`).

## Testing

- **Framework**: Vitest + vue-test-utils.
- **Environment**: jsdom (configured in `vite.config.ts`).
- **Location**: `src/components/__tests__/*.spec.ts`.
- **Pattern**: `describe('{Component}', () => { it('renders correctly', ...) })`.
- **Run**: `npm run test` (vitest run).
- **Build check**: `npm run build` (vue-tsc + vite build).

## Security

- Sanitize rich text with DOMPurify before rendering (`v-html` if absolutely necessary).
- Never store secrets in source code. JWT stored in localStorage (acceptable for this app).
- No `console.log` with sensitive data in production.
