"use client"

import type { DashboardViewId } from "@/components/shadcn/navigation"
import { AtlasAdminTemplate } from "@/layouts"
import { VIEW_LABELS } from "./overview.data"
import { PlaceholderView } from "./PlaceholderView"
import type { ViewPageProps } from "./OverviewPage"

export interface PlaceholderPageProps extends ViewPageProps {
  view: Exclude<DashboardViewId, "overview" | "ai" | "settings">
}

/** Demais ids do DashboardViewId continuam como placeholders — nenhum id novo foi criado. */
export function PlaceholderPage({ view, onSelectView }: PlaceholderPageProps) {
  return (
    <AtlasAdminTemplate title={VIEW_LABELS[view]} activeView={view} onSelectView={onSelectView}>
      <PlaceholderView view={view} onBack={() => onSelectView?.("overview")} />
    </AtlasAdminTemplate>
  )
}
