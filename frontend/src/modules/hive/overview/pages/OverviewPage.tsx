"use client"

import type { DashboardViewId } from "@/components/shadcn/navigation"
import { AtlasAdminTemplate } from "@/layouts"
import { HiveOverviewView } from "../components/HiveOverviewView"
import { VIEW_LABELS } from "../data/overview.data"

export interface ViewPageProps {
  onSelectView?: (view: DashboardViewId) => void
}

/** Conteúdo da visão geral permanece intacto; apenas o wrapper do shell foi movido para cá. */
export function OverviewPage({ onSelectView }: ViewPageProps) {
  return (
    <AtlasAdminTemplate
      title={VIEW_LABELS.overview}
      activeView="overview"
      onSelectView={onSelectView}
    >
      <HiveOverviewView />
    </AtlasAdminTemplate>
  )
}
