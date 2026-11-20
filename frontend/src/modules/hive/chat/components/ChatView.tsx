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
    <div className="flex flex-col gap-4 px-4 lg:px-6">
      <header className="flex flex-col gap-1">
        <h1 className="text-xl font-bold tracking-tight text-foreground sm:text-2xl">
          {t("chat.title")}
        </h1>
        <p className="text-xs text-muted-foreground">{t("chat.subtitle")}</p>
      </header>

      <div className="grid min-h-0 gap-4 lg:grid-cols-[18rem_minmax(0,1fr)]">
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

        <div className="flex min-w-0 flex-col gap-3">
          <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
            <div className="w-full sm:max-w-80">
              <PromptSelector
                prompts={state.prompts}
                value={state.selectedPromptTemplateId}
                loading={state.promptsLoading}
                error={state.promptsError}
                locked={!!state.sessionId}
                disabled={state.sending}
                onChange={actions.selectPrompt}
              />
            </div>
          </div>

          {state.sendError ? (
            <div role="alert" className="flex items-start gap-2 rounded-md border border-destructive/40 bg-destructive/10 px-3 py-2 text-sm text-destructive">
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
      </div>
    </div>
  )
}

export default ChatView
