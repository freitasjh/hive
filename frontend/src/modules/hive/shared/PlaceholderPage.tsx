"use client"

import type { DashboardViewId } from "@/components/shadcn/navigation"
import { AtlasAdminTemplate } from "@/layouts"
import { VIEW_LABELS, type ViewPageProps } from "../overview"
import { PlaceholderView } from "./PlaceholderView"

export interface PlaceholderPageProps extends ViewPageProps {
  /** Todas as telas do union têm página própria; o Exclude resulta em `never`. */
  view: Exclude<DashboardViewId, "overview" | "login" | "providers" | "agents" | "chat" | "prompts">
}

/** Contrato de placeholder remanescente: nenhuma view do union o alcança. */
export function PlaceholderPage({ view, onSelectView }: PlaceholderPageProps) {
  return (
    <AtlasAdminTemplate title={VIEW_LABELS[view]} activeView={view} onSelectView={onSelectView}>
      <PlaceholderView view={view} onBack={() => onSelectView?.("overview")} />
    </AtlasAdminTemplate>
  )
}
