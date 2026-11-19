"use client"

import { useHashRoute } from "@/hooks/use-hash-route"
import {
  AiAdminPage,
  OverviewPage,
  PlaceholderPage,
  PromptsAdminPage,
} from "@/modules/hive"

/**
 * Cada página embrulha a si mesma em `AtlasAdminTemplate` — DESIGN.md §3.
 * overview e placeholders intactos. Ids canônicos: providers/agents/prompts;
 * `ai` e `settings` permanecem como aliases legados para as mesmas páginas.
 * `key` força remount ao trocar providers ↔ agents: `Tabs` é uncontrolled (defaultValue).
 */
export default function App() {
  // location.hash é a única fonte de verdade; navigate escreve o hash.
  const [activeView, navigate] = useHashRoute()

  if (activeView === "overview") {
    return <OverviewPage onSelectView={navigate} />
  }
  if (activeView === "ai" || activeView === "providers") {
    return (
      <AiAdminPage
        key="providers"
        initialTab="credentials"
        onSelectView={navigate}
      />
    )
  }
  if (activeView === "agents") {
    return (
      <AiAdminPage key="agents" initialTab="models" onSelectView={navigate} />
    )
  }
  if (activeView === "settings" || activeView === "prompts") {
    return <PromptsAdminPage onSelectView={navigate} />
  }
  return <PlaceholderPage view={activeView} onSelectView={navigate} />
}
