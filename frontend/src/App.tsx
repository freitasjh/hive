"use client"

import { useCallback, useEffect } from "react"
import { isTabbedView, useHashRoute, type RouteTab } from "@/hooks/use-hash-route"
import { getToken } from "@/services/auth.service"
import { LoginPage } from "@/pages/LoginPage"
import {
  AiAdminPage,
  ChatPage,
  OverviewPage,
  PlaceholderPage,
  PromptsAdminPage,
  VIEW_LABELS,
} from "@/modules/hive"

/**
 * Cada página embrulha a si mesma em `AtlasAdminTemplate` — DESIGN.md §3.
 * A URL é a única fonte de verdade: `#view/tab[/id]`. Menu usa `navigate`
 * (push); clique de aba e saída do editor usam `replaceRoute` (sem push).
 */
export default function App() {
  const [route, navigate, replaceRoute] = useHashRoute()
  const { view, tab, id } = route
  const handleAuthenticated = useCallback(() => navigate("overview"), [navigate])
  // Token é lido a cada render: logout/401 mudam o hash e forçam nova leitura.
  const authenticated = Boolean(getToken())

  // Páginas admin definem "<View> · <Aba>"; demais views exibem só o rótulo da view.
  useEffect(() => {
    if (!isTabbedView(view)) {
      document.title = VIEW_LABELS[view]
    }
  }, [view])

  // Guard: rota protegida sem sessão volta para #login ANTES de qualquer shell
  // Atlas montar. `useLoginController` mantém o redirect inverso (token em
  // #login → overview). Todos os hooks estão acima do early return.
  useEffect(() => {
    if (!authenticated && view !== "login") navigate("login")
  }, [authenticated, view, navigate])

  if (!authenticated && view !== "login") return null

  // Login vive fora do shell Atlas: nenhuma sidebar/header é montada antes dele.
  if (view === "login") {
    return <LoginPage onAuthenticated={handleAuthenticated} />
  }
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
  if (view === "chat") {
    return <ChatPage onSelectView={navigate} />
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
  // Exaustivo: após os 6 ids acima, `view` é `never` — placeholder inalcançável.
  return <PlaceholderPage view={view} onSelectView={navigate} />
}
