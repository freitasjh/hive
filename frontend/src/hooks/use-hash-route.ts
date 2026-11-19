"use client"

import { useCallback, useEffect, useState } from "react"
import type { DashboardViewId } from "@/components/shadcn/navigation"

/**
 * Ids canônicos + aliases legados. Tipar como `Record<DashboardViewId, true>`
 * obriga o compilador a acusar qualquer id do union que ficar de fora.
 */
const VIEW_IDS = {
  overview: true,
  providers: true,
  agents: true,
  prompts: true,
  kanban: true,
  scheduler: true,
  transactions: true,
  cards: true,
  checkout: true,
  pdv: true,
  accounts: true,
  files: true,
  backoffice: true,
  "internal-admin": true,
  ai: true,
  settings: true,
} satisfies Record<DashboardViewId, true>

const ALIASES: Partial<Record<DashboardViewId, DashboardViewId>> = {
  ai: "providers",
  settings: "prompts",
}

const hasOwn = Object.prototype.hasOwnProperty

/**
 * Converte o fragmento da URL em uma view. Pura e sem dependência de browser.
 * `location.hash` já vem com `#` (MDN, Location.hash); sem `#` também é aceito.
 * Aliases `ai`/`settings` normalizam para `providers`/`prompts`. Qualquer outro
 * valor — inclusive `#/login`, `#/shadcn` e sub-rotas de aba — cai em `overview`.
 */
export function parseHash(hash: string): DashboardViewId {
  const token = hash.startsWith("#") ? hash.slice(1) : hash

  if (hasOwn.call(ALIASES, token)) {
    return ALIASES[token as DashboardViewId]!
  }
  if (hasOwn.call(VIEW_IDS, token)) {
    return token as DashboardViewId
  }
  return "overview"
}

/**
 * Roteamento nativo por hash: o hash é a única fonte de verdade. `navigate`
 * apenas escreve `location.hash`; o evento `hashchange` atualiza o estado.
 * Isso cobre deep-link no mount, back/forward e evita duas fontes de verdade.
 *
 * Fonte: MDN Window.hashchange event (addEventListener("hashchange", fn),
 * https://developer.mozilla.org/en-US/docs/Web/API/Window/hashchange_event,
 * consultado em 2026-11-19) e MDN Location.hash
 * (https://developer.mozilla.org/en-US/docs/Web/API/Location/hash).
 */
export function useHashRoute(): [DashboardViewId, (view: DashboardViewId) => void] {
  const [view, setView] = useState<DashboardViewId>(() =>
    parseHash(window.location.hash)
  )

  useEffect(() => {
    const handleHashChange = () => setView(parseHash(window.location.hash))
    window.addEventListener("hashchange", handleHashChange)
    return () => window.removeEventListener("hashchange", handleHashChange)
  }, [])

  const navigate = useCallback((next: DashboardViewId) => {
    const hash = `#${next}`
    if (window.location.hash !== hash) {
      window.location.hash = hash
    }
  }, [])

  return [view, navigate]
}
