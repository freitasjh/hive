"use client"

import { useCallback, useEffect, useState } from "react"
import type { DashboardViewId } from "@/components/shadcn/navigation"

/** Abas que participam do deep-link de admin. */
export type RouteTab = "credentials" | "models" | "templates" | "editor"

/** Espelho da URL: view canônica + aba (quando houver) + id do prompt (quando editor). */
export interface Route {
  view: DashboardViewId
  tab?: RouteTab
  id?: string
}

/**
 * Ids canônicos + aliases legados. Tipar como `Record<DashboardViewId, true>`
 * obriga o compilador a acusar qualquer id do union que ficar de fora.
 */
const VIEW_IDS = {
  overview: true,
  providers: true,
  agents: true,
  chat: true,
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

/** Abas válidas por view. View sem aba (overview, placeholders) não entra no mapa. */
const TABS_BY_VIEW: Partial<Record<DashboardViewId, readonly RouteTab[]>> = {
  providers: ["credentials", "models"],
  agents: ["credentials", "models"],
  prompts: ["templates", "editor"],
}

/** Default preserva o comportamento anterior: agents abre em models. */
const DEFAULT_TAB: Partial<Record<DashboardViewId, RouteTab>> = {
  providers: "credentials",
  agents: "models",
  prompts: "templates",
}

/** Fonte única de verdade para views com abas (mesma origem de parseRoute). */
export function isTabbedView(view: DashboardViewId): boolean {
  return hasOwn.call(TABS_BY_VIEW, view)
}

const hasOwn = Object.prototype.hasOwnProperty

function segmentsOf(hash: string): string[] {
  const token = hash.startsWith("#") ? hash.slice(1) : hash
  return token.split("/").filter(Boolean)
}

/**
 * Converte o fragmento da URL em uma rota. Pura e sem dependência de browser.
 * Formato: `#<view>/<tab>[/<id>]` (`Location.hash` já vem com `#` — MDN).
 * Aliases de view são normalizados no 1º segmento; aba desconhecida cai na
 * default da view; view desconhecida cai em overview. `#prompts/editor` sem id
 * cai em templates — editor nunca aparece vazio.
 */
export function parseRoute(hash: string): Route {
  const segments = segmentsOf(hash)
  const first = segments[0] ?? ""

  let view: DashboardViewId
  if (hasOwn.call(ALIASES, first)) {
    view = ALIASES[first as DashboardViewId]!
  } else if (hasOwn.call(VIEW_IDS, first)) {
    view = first as DashboardViewId
  } else {
    return { view: "overview" }
  }

  const allowed = TABS_BY_VIEW[view]
  if (!allowed) return { view }

  const rawTab = segments[1]
  const tab = rawTab && allowed.includes(rawTab as RouteTab)
    ? (rawTab as RouteTab)
    : DEFAULT_TAB[view]
  if (!tab) return { view }

  // Editor exige id (new ou uuid); sem id, volta para a default da view.
  if (tab === "editor" && !segments[2]) {
    return { view, tab: DEFAULT_TAB[view] }
  }

  return { view, tab, id: tab === "editor" ? segments[2] : undefined }
}

/** Compat: parseHash devolve só a view, agora derivada de `parseRoute`. */
export function parseHash(hash: string): DashboardViewId {
  return parseRoute(hash).view
}

/** Serializa a rota no formato canônico `#view/tab[/id]`. */
export function buildHash(view: DashboardViewId, tab?: RouteTab, id?: string): string {
  if (!tab) return `#${view}`
  if (tab === "editor") return id ? `#${view}/editor/${id}` : `#${view}/editor/new`
  return `#${view}/${tab}`
}

/**
 * Roteamento nativo por hash: o hash é a única fonte de verdade. `navigate`
 * escreve `location.hash` (push); `replaceRoute` usa `history.replaceState`
 * (sem push e sem `hashchange`, MDN) e atualiza o estado local explicitamente.
 *
 * Fonte: MDN Window.hashchange
 * (https://developer.mozilla.org/en-US/docs/Web/API/Window/hashchange_event,
 * consultado em 2026-11-19), MDN Location.hash
 * (https://developer.mozilla.org/en-US/docs/Web/API/Location/hash) e MDN
 * History.replaceState (https://developer.mozilla.org/en-US/docs/Web/API/History/replaceState).
 */
export function useHashRoute(): [Route, (view: DashboardViewId, tab?: RouteTab, id?: string) => void, (view: DashboardViewId, tab?: RouteTab, id?: string) => void] {
  const [route, setRoute] = useState<Route>(() => parseRoute(window.location.hash))

  useEffect(() => {
    // Alias no 1º segmento é canonicalizado via replaceState (sem push e sem
    // hashchange), no mount e também em mudanças de hash. Estado local é
    // sincronizado na mão porque replaceState não dispara o evento.
    const syncFromHash = () => {
      const segments = segmentsOf(window.location.hash)
      const first = segments[0] ?? ""
      let hash = window.location.hash
      if (hasOwn.call(ALIASES, first)) {
        const canonical = ALIASES[first as DashboardViewId]!
        const rest = segments.slice(1).join("/")
        hash = `#${canonical}${rest ? `/${rest}` : ""}`
        if (window.location.hash !== hash) {
          window.history.replaceState(null, "", hash)
        }
      }
      setRoute(parseRoute(hash))
    }

    window.addEventListener("hashchange", syncFromHash)
    syncFromHash()
    return () => window.removeEventListener("hashchange", syncFromHash)
  }, [])

  const navigate = useCallback((view: DashboardViewId, tab?: RouteTab, id?: string) => {
    const hash = buildHash(view, tab, id)
    if (window.location.hash !== hash) {
      window.location.hash = hash
    }
  }, [])

  const replaceRoute = useCallback((view: DashboardViewId, tab?: RouteTab, id?: string) => {
    const hash = buildHash(view, tab, id)
    if (window.location.hash !== hash) {
      window.history.replaceState(null, "", hash)
    }
    setRoute(parseRoute(hash))
  }, [])

  return [route, navigate, replaceRoute]
}
