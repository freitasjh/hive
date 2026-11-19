"use client"

import { useEffect } from "react"
import { isTabbedView, useHashRoute, type RouteTab } from "@/hooks/use-hash-route"
import {
  AiAdminPage,
  OverviewPage,
  PlaceholderPage,
  PromptsAdminPage,
  VIEW_LABELS,
  type PlaceholderPageProps,
} from "@/modules/hive"

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
    if (!isTabbedView(view)) {
      document.title = VIEW_LABELS[view]
    }
  }, [view])

  if (view === "overview") {
    return <OverviewPage onSelectView={navigate} />
  }
  if (view === "providers") {
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
  if (view === "prompts") {
    return (
      <PromptsAdminPage
        tab={tab === "editor" ? "editor" : "templates"}
        editorId={id}
        onTabChange={(next) =>
          replaceRoute(view, next, next === "editor" ? id ?? "new" : undefined)
        }
        onOpenEditor={(promptId) => navigate(view, "editor", promptId ?? "new")}
        onLeaveEditor={() => replaceRoute(view, "templates")}
        onSelectView={navigate}
      />
    )
  }
  // parseRoute canonicaliza aliases: view nunca é "ai"/"settings" em runtime.
  return <PlaceholderPage view={view as PlaceholderPageProps["view"]} onSelectView={navigate} />
}
