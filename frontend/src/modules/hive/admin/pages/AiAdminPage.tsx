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

/** `ai` (app-sidebar.tsx:57-62) passa a abrigar credenciais + config LLM do tenant. */
export function AiAdminPage({ onSelectView }: AdminPageProps) {
  const { t } = useAdminI18n()

  return (
    <AtlasAdminTemplate title={t("admin.credentials.title")} activeView="ai" onSelectView={onSelectView}>
      <div className="flex flex-col gap-6 px-4 lg:px-6">
        <Tabs defaultValue="credentials">
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
