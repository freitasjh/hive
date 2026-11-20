"use client"

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
  // document.title desta rota é escrito somente por App.tsx via documentTitleFor.

  return (
    <AtlasAdminTemplate title={t("chat.title")} activeView="chat" onSelectView={onSelectView}>
      <ChatView state={state} actions={actions} />
    </AtlasAdminTemplate>
  )
}

export default ChatPage
