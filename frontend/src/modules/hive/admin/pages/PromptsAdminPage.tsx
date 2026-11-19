"use client"

import { useCallback, useEffect, useState } from "react"
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/shadcn/ui/tabs"
import { Button } from "@/components/shadcn/ui/button"
import { Card, CardContent } from "@/components/shadcn/ui/card"
import { AtlasAdminTemplate } from "@/layouts"
import { useAdminI18n, type AdminMessageKey } from "../i18n"
import { PromptEditorContainer } from "../prompts/PromptEditorContainer"
import { PromptsContainer } from "../prompts/PromptsContainer"
import type { AdminPageProps } from "./AiAdminPage"

type PromptTab = "templates" | "editor"

export interface PromptsAdminPageProps extends AdminPageProps {
  /** Aba controlada pela URL. */
  tab?: PromptTab
  /** `new` ou uuid opaco; ausente fora do editor. */
  editorId?: string
  onTabChange?: (tab: PromptTab) => void
  onOpenEditor?: (id: string | null) => void
  onLeaveEditor?: () => void
}

const TAB_KEY: Record<PromptTab, AdminMessageKey> = {
  templates: "admin.tab.templates",
  editor: "admin.tab.editor",
}

const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i

/** Abriga templates e editor de prompts; alias `settings` mantém o comportamento legado. */
export function PromptsAdminPage({
  onSelectView,
  tab = "templates",
  editorId,
  onTabChange,
  onOpenEditor,
  onLeaveEditor,
}: PromptsAdminPageProps) {
  const { t } = useAdminI18n()
  const [loadFailed, setLoadFailed] = useState(false)

  const handleLoadError = useCallback(() => setLoadFailed(true), [])

  useEffect(() => {
    setLoadFailed(false)
  }, [editorId])

  useEffect(() => {
    document.title = t("admin.document.title", {
      view: t("admin.prompts.title"),
      tab: t(TAB_KEY[tab]),
    })
  }, [t, tab])

  const invalidFormat =
    editorId !== "new" && (!editorId || !UUID_RE.test(editorId))
  const invalidEditor = tab === "editor" && (invalidFormat || loadFailed)

  return (
    <AtlasAdminTemplate title={t("admin.prompts.title")} activeView="prompts" onSelectView={onSelectView}>
      <div className="flex flex-col gap-6 px-4 lg:px-6">
        <Tabs value={tab} onValueChange={(value) => onTabChange?.(value as PromptTab)}>
          <TabsList>
            <TabsTrigger value="templates">{t("admin.tab.templates")}</TabsTrigger>
            <TabsTrigger value="editor">{t("admin.tab.editor")}</TabsTrigger>
          </TabsList>
          <TabsContent value="templates">
            <PromptsContainer
              onNew={() => onOpenEditor?.(null)}
              onEdit={(id) => onOpenEditor?.(id)}
            />
          </TabsContent>
          <TabsContent value="editor">
            {invalidEditor ? (
              <Card>
                <CardContent className="flex flex-col items-start gap-4">
                  <p role="alert" aria-live="assertive" className="text-sm text-destructive">
                    {t("admin.editor.invalidId")}
                  </p>
                  <Button type="button" variant="outline" onClick={onLeaveEditor}>
                    {t("admin.editor.backToTemplates")}
                  </Button>
                </CardContent>
              </Card>
            ) : (
              <PromptEditorContainer
                promptId={editorId && editorId !== "new" ? editorId : null}
                onLoadError={handleLoadError}
                onSaved={() => onLeaveEditor?.()}
                onBack={() => onLeaveEditor?.()}
              />
            )}
          </TabsContent>
        </Tabs>
      </div>
    </AtlasAdminTemplate>
  )
}
