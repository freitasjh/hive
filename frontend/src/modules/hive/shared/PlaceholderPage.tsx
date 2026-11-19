"use client"

import type { DashboardViewId } from "@/components/shadcn/navigation"
import { AtlasAdminTemplate } from "@/layouts"
import { VIEW_LABELS, type ViewPageProps } from "../overview"
import { PlaceholderView } from "./PlaceholderView"

export interface PlaceholderPageProps extends ViewPageProps {
  view: Exclude<DashboardViewId, "overview" | "ai" | "settings" | "providers" | "agents" | "prompts">
}

/** Demais ids do DashboardViewId continuam como placeholders — telas admin têm ids próprios. */
export function PlaceholderPage({ view, onSelectView }: PlaceholderPageProps) {
  return (
    <AtlasAdminTemplate title={VIEW_LABELS[view]} activeView={view} onSelectView={onSelectView}>
      <PlaceholderView view={view} onBack={() => onSelectView?.("overview")} />
    </AtlasAdminTemplate>
  )
}
