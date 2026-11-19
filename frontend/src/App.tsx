"use client"

import { useEffect } from "react"
import { useHashRoute, type RouteTab } from "@/hooks/use-hash-route"
import {
  AiAdminPage,
  OverviewPage,
  PlaceholderPage,
  PromptsAdminPage,
  VIEW_LABELS,
} from "@/modules/hive"

const ADMIN_VIEWS = new Set(["providers", "agents", "prompts"])

/**
 * Cada página embrulha a si mesma em `AtlasAdminTemplate` — DESIGN.md §3.
 * A URL é a única fonte de verdade: `#view/tab[/id]`. Menu usa `navigate`
 * (push); clique de aba e saída do editor usam `replaceRoute` (sem push).
 */
export default function App() {
  const [route, navigate, replaceRoute] = useHashRoute()
  const { view, tab, id } = route

  // Páginas admin definem "<View> · <Aba>"; demais views exibem só o rótulo da view.
  useEffect(() => {
    if (!ADMIN_VIEWS.has(view)) {
      document.title = VIEW_LABELS[view]
    }
  }, [view])

  if (view === "overview") {
    return <OverviewPage onSelectView={navigate} />
  }
  if (view === "providers" || view === "ai") {
    return (
      <AiAdminPage
        view="providers"
        tab={tab === "models" ? "models" : "credentials"}
        onTabChange={(next: RouteTab) => replaceRoute("providers", next === "models" ? "models" : "credentials")}
        onSelectView={navigate}
      />
    )
  }
  if (view === "agents") {
    return (
      <AiAdminPage
        view="agents"
        tab={tab === "credentials" ? "credentials" : "models"}
        onTabChange={(next: RouteTab) => replaceRoute("agents", next === "credentials" ? "credentials" : "models")}
        onSelectView={navigate}
      />
    )
  }
  if (view === "prompts" || view === "settings") {
    const activeView = view === "settings" ? "prompts" : view
    return (
      <PromptsAdminPage
        tab={tab === "editor" ? "editor" : "templates"}
        editorId={id}
        onTabChange={(next) =>
          replaceRoute(activeView, next, next === "editor" ? id ?? "new" : undefined)
        }
        onOpenEditor={(promptId) => navigate(activeView, "editor", promptId ?? "new")}
        onLeaveEditor={() => replaceRoute(activeView, "templates")}
        onSelectView={navigate}
      />
    )
  }
  return <PlaceholderPage view={view} onSelectView={navigate} />
}
