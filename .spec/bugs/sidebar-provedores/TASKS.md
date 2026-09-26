# Bugfix — Menu Provedores ausente (sidebar-provedores)

> Workflow: Bugfix Fast-Track — B1→B4 concluído
> Severidade: 🟡 Média (página órfã, só via URL direta)
> Camada: **Frontend** · Data: 2026-09-25 · Status: **✅ Concluído**

## Bug
Rota `/settings/providers` (`CredentialsView.vue`) existia mas sem entrada no menu lateral — página órfã.

## Causa raiz
`frontend/src/components/MainLayout.vue:41-45` (`navItems`) listava só Chat/Prompts/Agentes; `labelMap` do breadcrumb sem `settings`/`providers`.

## Solução aplicada
- `MainLayout.vue`: +`{ label: 'Provedores', icon: 'pi pi-key', to: '/settings/providers' }` após Agentes (sem gate client-side — backend enforce; débito P2 existente).
- `labelMap`: `settings: 'Configurações'`, `providers: 'Provedores'`.
- NOVO `components/__tests__/MainLayout.spec.ts` (2 asserts).

## Testes
- `npm run build` ok; `npm run test` 18 files/116 verdes.
- Review: APPROVED zero achados. Visual DevTools: item visível, breadcrumb `Home > Configurações > Provedores`, 0 console errors.
- Evidências: `.spec/bugs/sidebar-provedores/harness/` (screenshots do QA).

## Riscos
Nenhum (declarativo). Gate visual por permissão = débito P2 existente.
