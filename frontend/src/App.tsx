"use client"

import { useState } from "react"
import type { DashboardViewId } from "@/components/shadcn/navigation"
import { AiAdminPage } from "@/modules/hive/admin/pages/AiAdminPage"
import { PromptsAdminPage } from "@/modules/hive/admin/pages/PromptsAdminPage"
import { OverviewPage } from "@/modules/hive/OverviewPage"
import { PlaceholderPage } from "@/modules/hive/PlaceholderPage"

/**
 * Cada página embrulha a si mesma em `AtlasAdminTemplate` — DESIGN.md §3.
 * overview intacto; `ai` e `settings` passam a abrigar as telas administrativas do Hive.
 */
export default function App() {
  const [activeView, setActiveView] = useState<DashboardViewId>("overview")

  if (activeView === "overview") {
    return <OverviewPage onSelectView={setActiveView} />
  }
  if (activeView === "ai") {
    return <AiAdminPage onSelectView={setActiveView} />
  }
  if (activeView === "settings") {
    return <PromptsAdminPage onSelectView={setActiveView} />
  }
  return <PlaceholderPage view={activeView} onSelectView={setActiveView} />
}
