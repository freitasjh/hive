"use client"

import { useEffect } from "react"
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/shadcn/ui/tabs"
import type { DashboardViewId } from "@/components/shadcn/navigation"
import { AtlasAdminTemplate } from "@/layouts"
import { CredentialsContainer } from "../credentials/CredentialsContainer"
import { useAdminI18n, type AdminMessageKey } from "../i18n"
import { LlmConfigContainer } from "../llm/LlmConfigContainer"
import { PublicClientsContainer } from "../public-clients/PublicClientsContainer"

export interface AdminPageProps {
  onSelectView?: (view: DashboardViewId) => void
}

type AiTab = "credentials" | "models" | "public-clients"

export interface AiAdminPageProps extends AdminPageProps {
  /** Aba controlada pela URL. */
  tab?: AiTab
  /** @deprecated use `tab`. Mantido para chamadas legadas. */
  initialTab?: AiTab
  onTabChange?: (tab: AiTab) => void
}

const TAB_KEY: Record<AiTab, AdminMessageKey> = {
  credentials: "admin.tab.credentials",
  models: "admin.tab.models",
  "public-clients": "admin.tab.publicClients",
}

/** Abriga credenciais + configuração de credencial/modelo do tenant da view providers. */
export function AiAdminPage({
  onSelectView,
  tab,
  initialTab,
  onTabChange,
}: AiAdminPageProps) {
  const { t } = useAdminI18n()
  const activeTab: AiTab = tab ?? initialTab ?? "credentials"
  // Abas com domínio próprio definem seu título; 'credentials' mantém o legado.
  const title = activeTab === "models"
    ? t("admin.tab.models")
    : activeTab === "public-clients"
      ? t("admin.publicClients.title")
      : t("admin.credentials.title")

  useEffect(() => {
    document.title = t("admin.document.title", {
      view: t("admin.credentials.title"),
      tab: t(TAB_KEY[activeTab]),
    })
  }, [t, activeTab])

  return (
    <AtlasAdminTemplate
      title={title}
      activeView="providers"
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
            <TabsTrigger value="public-clients">{t("admin.tab.publicClients")}</TabsTrigger>
          </TabsList>
          <TabsContent value="credentials">
            <CredentialsContainer />
          </TabsContent>
          <TabsContent value="models">
            <LlmConfigContainer />
          </TabsContent>
          <TabsContent value="public-clients">
            <PublicClientsContainer />
          </TabsContent>
        </Tabs>
      </div>
    </AtlasAdminTemplate>
  )
}
