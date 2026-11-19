"use client"

import { useEffect } from "react"
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/shadcn/ui/tabs"
import type { DashboardViewId } from "@/components/shadcn/navigation"
import { AtlasAdminTemplate } from "@/layouts"
import { CredentialsContainer } from "../credentials/CredentialsContainer"
import { useAdminI18n, type AdminMessageKey } from "../i18n"
import { LlmConfigContainer } from "../llm/LlmConfigContainer"

export interface AdminPageProps {
  onSelectView?: (view: DashboardViewId) => void
}

type AiTab = "credentials" | "models"

export interface AiAdminPageProps extends AdminPageProps {
  /** View da URL — controla breadcrumb/título, independente da aba. */
  view?: "providers" | "agents"
  /** Aba controlada pela URL. */
  tab?: AiTab
  /** @deprecated use `tab`. Mantido para chamadas legadas. */
  initialTab?: AiTab
  onTabChange?: (tab: AiTab) => void
}

const TAB_KEY: Record<AiTab, AdminMessageKey> = {
  credentials: "admin.tab.credentials",
  models: "admin.tab.models",
}

/** Abriga credenciais + config LLM do tenant; alias `ai` mantém o comportamento legado. */
export function AiAdminPage({
  onSelectView,
  view,
  tab,
  initialTab,
  onTabChange,
}: AiAdminPageProps) {
  const { t } = useAdminI18n()
  const activeTab: AiTab = tab ?? initialTab ?? "credentials"
  const activeView: DashboardViewId = view ?? (activeTab === "models" ? "agents" : "providers")
  const title = activeView === "agents"
    ? t("admin.agents.title")
    : t("admin.credentials.title")

  useEffect(() => {
    document.title = t("admin.document.title", { view: title, tab: t(TAB_KEY[activeTab]) })
  }, [t, title, activeTab])

  return (
    <AtlasAdminTemplate
      title={title}
      activeView={activeView}
      onSelectView={onSelectView}
    >
      <div className="flex flex-col gap-6 px-4 lg:px-6">
        <Tabs
          value={activeTab}
          onValueChange={(value) => onTabChange?.(value as AiTab)}
        >
          <TabsList>
            <TabsTrigger value="credentials">{t("admin.tab.credentials")}</TabsTrigger>
            <TabsTrigger value="models">{t("admin.tab.models")}</TabsTrigger>
          </TabsList>
          <TabsContent value="credentials">
            <CredentialsContainer />
          </TabsContent>
          <TabsContent value="models">
            <LlmConfigContainer />
          </TabsContent>
        </Tabs>
      </div>
    </AtlasAdminTemplate>
  )
}
