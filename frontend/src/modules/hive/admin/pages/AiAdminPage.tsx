"use client"

import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/shadcn/ui/tabs"
import type { DashboardViewId } from "@/components/shadcn/navigation"
import { AtlasAdminTemplate } from "@/layouts"
import { CredentialsContainer } from "../credentials/CredentialsContainer"
import { useAdminI18n } from "../i18n"
import { LlmConfigContainer } from "../llm/LlmConfigContainer"

export interface AdminPageProps {
  onSelectView?: (view: DashboardViewId) => void
}

export interface AiAdminPageProps extends AdminPageProps {
  /** `credentials` para `providers`/`ai`; `models` para `agents`. */
  initialTab?: "credentials" | "models"
}

/** Abriga credenciais + config LLM do tenant; alias `ai` mantém o comportamento legado. */
export function AiAdminPage({ onSelectView, initialTab = "credentials" }: AiAdminPageProps) {
  const { t } = useAdminI18n()
  const activeView: DashboardViewId = initialTab === "models" ? "agents" : "providers"
  const title = activeView === "agents"
    ? t("admin.agents.title")
    : t("admin.credentials.title")

  return (
    <AtlasAdminTemplate
      title={title}
      activeView={activeView}
      onSelectView={onSelectView}
    >
      <div className="flex flex-col gap-6 px-4 lg:px-6">
        <Tabs defaultValue={initialTab}>
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
