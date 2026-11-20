"use client"

import { AlertCircle } from "lucide-react"
import type { ChatControllerActions, ChatControllerState } from "../hooks/use-chat-controller"
import { useChatI18n } from "../i18n"
import { ChatComposer } from "./ChatComposer"
import { ChatThread } from "./ChatThread"
import { PromptSelector } from "./PromptSelector"
import { SessionSidebar } from "./SessionSidebar"

export interface ChatViewProps {
  state: ChatControllerState
  actions: ChatControllerActions
}

/** Apresentação pura: recebe estado e ações do hook, sem chamar serviço. */
export function ChatView({ state, actions }: ChatViewProps) {
  const { t } = useChatI18n()

  return (
    <div className="flex h-[calc(100svh-9rem)] min-h-[24rem] flex-col gap-3 px-4 pb-2 lg:h-[calc(100svh-10.5rem)] lg:px-6">
      <header className="flex shrink-0 flex-col gap-1">
        <h1 className="text-xl font-bold tracking-tight text-foreground sm:text-2xl">
          {t("chat.title")}
        </h1>
        <p className="text-xs text-muted-foreground">{t("chat.subtitle")}</p>
      </header>

      <div className="grid min-h-0 flex-1 grid-rows-[minmax(0,1fr)_auto] gap-3 lg:grid-cols-[18rem_minmax(0,1fr)] lg:grid-rows-1">
        <div
          data-chat-pane="conversation"
          className="order-1 flex min-h-0 min-w-0 flex-col gap-3 lg:order-none lg:col-start-2 lg:row-start-1"
        >
          <PromptSelector
            prompts={state.prompts}
            value={state.selectedPromptTemplateId}
            loading={state.promptsLoading}
            error={state.promptsError}
            locked={!!state.sessionId}
            disabled={state.sending}
            onChange={actions.selectPrompt}
          />

          {state.sendError ? (
            <div role="alert" className="flex shrink-0 items-start gap-2 rounded-lg border border-destructive/40 bg-destructive/10 px-3 py-2 text-sm text-destructive">
              <AlertCircle className="mt-0.5 size-4 shrink-0" aria-hidden="true" />
              <span>{state.sendError}</span>
            </div>
          ) : null}

          <ChatThread
            messages={state.messages}
            loading={state.historyLoading}
            error={state.historyError}
            generating={state.sending}
            canLoadOlder={!!state.sessionId && state.messagesTotalPages > 1}
            retryable={!!state.sendError}
            canUseStarter={!!state.selectedPromptTemplateId && !state.sending}
            onLoadOlder={actions.loadOlderMessages}
            onRetry={actions.retryLast}
            onStarter={actions.useStarter}
          />

          <ChatComposer
            value={state.input}
            onChange={actions.setInput}
            onSend={actions.send}
            disabled={state.sending || state.historyLoading}
            sending={state.sending}
            warning={state.notice}
          />
        </div>

        <div data-chat-pane="sidebar" className="order-2 max-h-40 min-h-0 lg:order-none lg:col-start-1 lg:row-start-1 lg:max-h-none">
          <SessionSidebar
            sessions={state.sessions}
            selectedId={state.sessionId}
            page={state.sessionsPage}
            totalPages={state.sessionsTotalPages}
            loading={state.sessionsLoading}
            error={state.sessionsError}
            onSelect={actions.selectSession}
            onNew={actions.newChat}
            onDelete={actions.removeSession}
            onPageChange={actions.goToSessionsPage}
          />
        </div>
      </div>
    </div>
  )
}

export default ChatView
