"use client"

import { useEffect } from "react"
import type { DashboardViewId } from "@/components/shadcn/navigation"
import { AtlasAdminTemplate } from "@/layouts"
import { ChatView } from "../components/ChatView"
import { useChatController } from "../hooks/use-chat-controller"
import { useChatI18n } from "../i18n"

export interface ChatPageProps {
  onSelectView?: (view: DashboardViewId) => void
}

/** Ponto de entrada da rota #chat. Lógica vive no hook; página só compõe. */
export function ChatPage({ onSelectView }: ChatPageProps) {
  const { state, actions } = useChatController()
  const { t } = useChatI18n()

  useEffect(() => {
    document.title = t("chat.document.title")
  }, [t])

  return (
    <AtlasAdminTemplate title={t("chat.title")} activeView="chat" onSelectView={onSelectView}>
      <ChatView state={state} actions={actions} />
    </AtlasAdminTemplate>
  )
}

export default ChatPage
