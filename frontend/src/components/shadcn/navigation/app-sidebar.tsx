"use client"

import * as React from "react"
import {
  IconDashboard,
  IconInnerShadowTop,
  IconKey,
  IconRobot,
  IconSparkles,
  IconMessage2Ai,
} from "@tabler/icons-react"

import { NavMain } from "@/components/shadcn/navigation/nav-main"
import { NavSecondary } from "@/components/shadcn/navigation/nav-secondary"
import { NavUser } from "@/components/shadcn/navigation/nav-user"
import {
  Sidebar,
  SidebarContent,
  SidebarFooter,
  SidebarHeader,
  SidebarMenu,
  SidebarMenuButton,
  SidebarMenuItem,
  useSidebar,
} from "@/components/shadcn/ui/sidebar"

/** Apenas telas que existem no Hive. Ids de template e aliases legados foram removidos. */
export type DashboardViewId = "overview" | "login" | "providers" | "agents" | "chat" | "prompts"

const data = {
  user: {
    name: "Atlas Admin",
    email: "admin@atlas.com.br",
    avatar: "",
  },
  navMain: [
    {
      id: "overview" as DashboardViewId,
      title: "Visão geral",
      url: "#",
      icon: IconDashboard,
    },
    {
      id: "providers" as DashboardViewId,
      title: "Provedores e credenciais",
      url: "#providers",
      icon: IconKey,
      badge: "AI",
    },
    {
      id: "agents" as DashboardViewId,
      title: "Config LLM",
      url: "#agents",
      icon: IconRobot,
      badge: "AI",
    },
    {
      id: "chat" as DashboardViewId,
      title: "Assistant",
      url: "#chat",
      icon: IconMessage2Ai,
      badge: "AI",
    },
  ],
  /**
   * Ajuda/Busca (url "#", sem id) e o grupo Documentos (Faturas/Contratos/
   * Integrações) foram removidos: não navegam e não existem no Hive.
   */
  navSecondary: [
    {
      id: "prompts" as DashboardViewId,
      title: "Prompts",
      url: "#prompts",
      icon: IconSparkles,
    },
  ],
}

interface AppSidebarProps extends React.ComponentProps<typeof Sidebar> {
  activeView?: DashboardViewId
  onSelectView?: (view: DashboardViewId) => void
  onNewEntry?: () => void
}

export function AppSidebar({
  activeView = "overview",
  onSelectView,
  onNewEntry,
  ...props
}: AppSidebarProps) {
  const { isMobile, setOpenMobile } = useSidebar()

  const handleSelect = React.useCallback(
    (viewId: DashboardViewId) => {
      onSelectView?.(viewId)
      if (isMobile) {
        setOpenMobile(false)
      }
    },
    [isMobile, onSelectView, setOpenMobile]
  )

  const navMainItems = data.navMain.map((item) => ({
    ...item,
    isActive: activeView === item.id,
    onClick: () => {
      if (item.id) {
        handleSelect(item.id as DashboardViewId)
      }
    },
  }))

  return (
    <Sidebar collapsible="offcanvas" {...props}>
      <SidebarHeader>
        <SidebarMenu>
          <SidebarMenuItem>
            <SidebarMenuButton
              asChild
              className="data-[slot=sidebar-menu-button]:p-1.5! hover:bg-transparent"
            >
              <a href="#" className="flex items-center gap-2.5">
                <div className="flex size-7 items-center justify-center rounded-lg bg-primary text-primary-foreground shadow-xs">
                  <IconInnerShadowTop className="size-4" />
                </div>
                <div className="flex flex-col">
                  <span className="text-sm font-semibold tracking-tight">Atlas</span>
                  <span className="text-[10px] font-medium uppercase tracking-wider text-muted-foreground">Workspace</span>
                </div>
              </a>
            </SidebarMenuButton>
          </SidebarMenuItem>
        </SidebarMenu>
      </SidebarHeader>
      <SidebarContent>
        <NavMain
          items={navMainItems}
          onNewEntry={onNewEntry ?? (() => onSelectView?.("overview"))}
        />
        <NavSecondary
          items={data.navSecondary.map((item) => ({
            ...item,
            isActive: "id" in item && activeView === item.id,
            onClick:
              "id" in item
                ? () => handleSelect(item.id as DashboardViewId)
                : undefined,
          }))}
          className="mt-auto"
        />
      </SidebarContent>
      <SidebarFooter>
        <NavUser user={data.user} />
      </SidebarFooter>
    </Sidebar>
  )
}
