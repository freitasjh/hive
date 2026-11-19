"use client"

import { useState } from "react"
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/shadcn/ui/tabs"
import { AtlasAdminTemplate } from "@/layouts"
import { useAdminI18n } from "../i18n"
import { PromptEditorContainer } from "../prompts/PromptEditorContainer"
import { PromptsContainer } from "../prompts/PromptsContainer"
import type { AdminPageProps } from "./AiAdminPage"

/** Abriga templates e editor de prompts; alias `settings` mantém o comportamento legado. */
export function PromptsAdminPage({ onSelectView }: AdminPageProps) {
  const { t } = useAdminI18n()
  const [tab, setTab] = useState("templates")
  const [editorId, setEditorId] = useState<string | null>(null)

  function openEditor(id: string | null) {
    setEditorId(id)
    setTab("editor")
  }

  return (
    <AtlasAdminTemplate title={t("admin.prompts.title")} activeView="prompts" onSelectView={onSelectView}>
      <div className="flex flex-col gap-6 px-4 lg:px-6">
        <Tabs value={tab} onValueChange={setTab}>
          <TabsList>
            <TabsTrigger value="templates">{t("admin.tab.templates")}</TabsTrigger>
            <TabsTrigger value="editor">{t("admin.tab.editor")}</TabsTrigger>
          </TabsList>
          <TabsContent value="templates">
            <PromptsContainer
              onNew={() => openEditor(null)}
              onEdit={(id) => openEditor(id)}
            />
          </TabsContent>
          <TabsContent value="editor">
            <PromptEditorContainer
              promptId={editorId}
              onSaved={() => setTab("templates")}
              onBack={() => setTab("templates")}
            />
          </TabsContent>
        </Tabs>
      </div>
    </AtlasAdminTemplate>
  )
}
