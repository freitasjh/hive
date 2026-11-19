"use client"

import { useState } from "react"
import type { DashboardViewId } from "@/components/shadcn/navigation"
import { AtlasAdminLayout } from "@/layouts"
import { HiveOverviewView } from "@/modules/hive/HiveOverviewView"
import { PlaceholderView } from "@/modules/hive/PlaceholderView"
import { VIEW_LABELS } from "@/modules/hive/overview.data"

export default function App() {
  const [activeView, setActiveView] = useState<DashboardViewId>("overview")

  return (
    <AtlasAdminLayout
      title={VIEW_LABELS[activeView]}
      activeView={activeView}
      onSelectView={setActiveView}
    >
      {activeView === "overview" ? (
        <HiveOverviewView />
      ) : (
        <PlaceholderView view={activeView} onBack={() => setActiveView("overview")} />
      )}
    </AtlasAdminLayout>
  )
}
