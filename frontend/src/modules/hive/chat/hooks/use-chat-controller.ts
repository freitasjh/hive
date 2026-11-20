"use client"

import { useCallback, useEffect, useRef, useState } from "react"
import type { ApiError } from "@/services/api"
import type { ChatMessageDTO, ChatRequestDTO, ChatSessionDTO } from "@/services/chat.types"
import { chatService } from "@/services/chat.service"
import { promptService } from "@/services/prompt.service"
import { sessionService } from "@/services/session.service"
import type { PromptTemplateDTO } from "@/services/admin.types"
import { chatErrorKey, useChatI18n } from "../i18n"

const MESSAGE_LIMIT = 4000
const SESSION_PAGE_SIZE = 20
const MESSAGE_PAGE_SIZE = 50

export interface ChatControllerState {
  prompts: PromptTemplateDTO[]
  promptsLoading: boolean
  promptsError: string | null
  selectedPromptTemplateId: string | null

  sessions: ChatSessionDTO[]
  sessionsPage: number
  sessionsTotalPages: number
  sessionsLoading: boolean
  sessionsError: string | null

  sessionId: string | null
  messages: ChatMessageDTO[]
  messagesPage: number
  messagesTotalPages: number
  historyLoading: boolean
  historyError: string | null

  input: string
  sending: boolean
  sendError: string | null
  notice: string | null
  lastUserMessage: string | null
}

const INITIAL_STATE: ChatControllerState = {
  prompts: [],
  promptsLoading: true,
  promptsError: null,
  selectedPromptTemplateId: null,
  sessions: [],
  sessionsPage: 0,
  sessionsTotalPages: 1,
  sessionsLoading: true,
  sessionsError: null,
  sessionId: null,
  messages: [],
  messagesPage: 0,
  messagesTotalPages: 1,
  historyLoading: false,
  historyError: null,
  input: "",
  sending: false,
  sendError: null,
  notice: null,
  lastUserMessage: null,
}

export interface ChatControllerActions {
  selectPrompt: (id: string) => void
  setInput: (value: string) => void
  send: () => Promise<void>
  useStarter: (text: string) => Promise<void>
  selectSession: (id: string) => Promise<void>
  newChat: () => void
  removeSession: (id: string) => Promise<void>
  goToSessionsPage: (page: number) => Promise<void>
  loadOlderMessages: () => Promise<void>
  retryLast: () => Promise<void>
  dismissNotice: () => void
}

function statusOf(error: unknown): number | undefined {
  return (error as ApiError | undefined)?.status
}

/**
 * Toda a lógica do Assistant vive aqui: prompt é escolhido antes do primeiro
 * turno e travado depois; a sessão responde pelo prompt a partir daí. Um seq
 * protege contra resposta atrasada trocar o prompt da conversa corrente.
 */
export function useChatController(): {
  state: ChatControllerState
  actions: ChatControllerActions
} {
  const { t } = useChatI18n()
  const [state, setState] = useState<ChatControllerState>(INITIAL_STATE)
  const stateRef = useRef<ChatControllerState>(INITIAL_STATE)
  const seqRef = useRef(0)

  const patch = useCallback((partial: Partial<ChatControllerState>) => {
    const next = { ...stateRef.current, ...partial }
    stateRef.current = next
    setState(next)
  }, [])

  useEffect(() => {
    let alive = true
    promptService
      .list({ page: 0, size: 50, status: "ACTIVE" })
      .then((page) => {
        if (alive) patch({ prompts: page.content, promptsLoading: false, promptsError: null })
      })
      .catch(() => {
        if (alive) patch({ promptsLoading: false, promptsError: t("chat.error.promptLoad") })
      })

    sessionService
      .listSessions(0, SESSION_PAGE_SIZE)
      .then((page) => {
        if (alive) {
          patch({
            sessions: page.content,
            sessionsPage: page.number,
            sessionsTotalPages: page.totalPages,
            sessionsLoading: false,
            sessionsError: null,
          })
        }
      })
      .catch((error: unknown) => {
        if (alive) {
          patch({ sessionsLoading: false, sessionsError: t(chatErrorKey(statusOf(error))) })
        }
      })

    return () => {
      alive = false
    }
  }, [patch, t])

  const selectPrompt = useCallback(
    (id: string) => {
      const current = stateRef.current
      if (current.sessionId) return // prompt travado: a sessão é autoritativa
      patch({ selectedPromptTemplateId: id, notice: null })
    },
    [patch]
  )

  const setInput = useCallback((value: string) => patch({ input: value }), [patch])

  const send = useCallback(async () => {
    const current = stateRef.current
    const text = current.input.trim()
    if (!text || current.sending) return
    if (text.length > MESSAGE_LIMIT) {
      patch({ notice: t("chat.composer.tooLong") })
      return
    }
    if (!current.selectedPromptTemplateId) {
      patch({ notice: t("chat.warn.selectPrompt") })
      return
    }

    const promptTemplateId = current.selectedPromptTemplateId
    const sessionId = current.sessionId
    // Seq capturado ANTES do await: newChat/selectSession durante a chamada
    // invalida a resposta, que não pode reativar sessão antiga nem poluir o histórico.
    const seq = seqRef.current
    const userMessage: ChatMessageDTO = {
      id: `temp-${Date.now()}`,
      sessionId: sessionId ?? "pending",
      role: "USER",
      content: text,
      createAt: new Date().toISOString(),
    }
    patch({
      input: "",
      sending: true,
      sendError: null,
      notice: null,
      lastUserMessage: text,
      messages: [...current.messages, userMessage],
    })

    try {
      const request: ChatRequestDTO = { promptTemplateId, message: text }
      if (sessionId) request.sessionId = sessionId
      const answer = await chatService.sendMessage(request)
      if (seq !== seqRef.current) return
      const after = stateRef.current
      const assistantMessage: ChatMessageDTO = {
        id: `assistant-${Date.now()}`,
        sessionId: answer.sessionId,
        role: answer.role || "ASSISTANT",
        content: answer.message,
        createAt: answer.timestamp,
      }
      patch({
        sessionId: after.sessionId ?? answer.sessionId ?? null,
        messages: [...after.messages, assistantMessage],
        historyError: null,
      })

      if (!sessionId && answer.sessionId) {
        // nova sessão: sidebar precisa refletir o primeiro turno
        sessionService
          .listSessions(0, SESSION_PAGE_SIZE)
          .then((page) => {
            if (seq !== seqRef.current) return
            patch({
              sessions: page.content,
              sessionsPage: page.number,
              sessionsTotalPages: page.totalPages,
            })
          })
          .catch(() => undefined)
      }
    } catch (error) {
      if (seq !== seqRef.current) return
      const message = t(chatErrorKey(statusOf(error)))
      const after = stateRef.current
      patch({
        sendError: message,
        messages: [
          ...after.messages,
          {
            id: `system-${Date.now()}`,
            sessionId: after.sessionId ?? "",
            role: "SYSTEM",
            content: message,
            createAt: new Date().toISOString(),
          },
        ],
      })
    } finally {
      patch({ sending: false })
    }
  }, [patch, t])

  const useStarter = useCallback(
    async (text: string) => {
      patch({ input: text })
      await send()
    },
    [patch, send]
  )

  const selectSession = useCallback(
    async (id: string) => {
      const seq = ++seqRef.current
      patch({
        sessionId: id,
        messages: [],
        messagesPage: 0,
        messagesTotalPages: 1,
        historyLoading: true,
        historyError: null,
        sendError: null,
        notice: null,
      })
      try {
        const page = await sessionService.getMessages(id, 0, MESSAGE_PAGE_SIZE)
        if (seq !== seqRef.current) return
        // A sessão é autoritativa quando responde; se getSession falhar, o prompt
        // já selecionado continua válido — apagá-lo travaria o send (selectPrompt
        // retorna cedo com sessão aberta) sem saída para o usuário.
        const fallbackPrompt = stateRef.current.selectedPromptTemplateId
        let promptTemplateId: string | null = fallbackPrompt
        try {
          const session = await sessionService.getSession(id)
          if (seq === seqRef.current) promptTemplateId = session.promptTemplateId ?? fallbackPrompt
        } catch {
          /* mantém prompt atual */
        }
        if (seq !== seqRef.current) return
        patch({
          messages: page.content,
          messagesPage: page.number,
          messagesTotalPages: page.totalPages,
          selectedPromptTemplateId: promptTemplateId,
          historyLoading: false,
        })
      } catch (error) {
        if (seq !== seqRef.current) return
        patch({
          historyError: t(chatErrorKey(statusOf(error))),
          historyLoading: false,
        })
      }
    },
    [patch, t]
  )

  const newChat = useCallback(() => {
    seqRef.current += 1
    patch({
      sessionId: null,
      messages: [],
      messagesPage: 0,
      messagesTotalPages: 1,
      selectedPromptTemplateId: null,
      input: "",
      sendError: null,
      historyError: null,
      notice: null,
      lastUserMessage: null,
    })
  }, [patch])

  const goToSessionsPage = useCallback(
    async (page: number) => {
      patch({ sessionsLoading: true, sessionsError: null })
      try {
        const result = await sessionService.listSessions(page, SESSION_PAGE_SIZE)
        patch({
          sessions: result.content,
          sessionsPage: result.number,
          sessionsTotalPages: result.totalPages,
          sessionsLoading: false,
        })
      } catch (error) {
        patch({ sessionsError: t(chatErrorKey(statusOf(error))), sessionsLoading: false })
      }
    },
    [patch, t]
  )

  const loadOlderMessages = useCallback(async () => {
    const current = stateRef.current
    if (!current.sessionId || current.messagesPage + 1 >= current.messagesTotalPages) return
    patch({ historyLoading: true, historyError: null })
    try {
      const page = await sessionService.getMessages(
        current.sessionId,
        current.messagesPage + 1,
        MESSAGE_PAGE_SIZE
      )
      patch({
        messages: [...page.content, ...stateRef.current.messages],
        messagesPage: page.number,
        messagesTotalPages: page.totalPages,
        historyLoading: false,
      })
    } catch (error) {
      patch({ historyError: t(chatErrorKey(statusOf(error))), historyLoading: false })
    }
  }, [patch, t])

  const removeSession = useCallback(
    async (id: string) => {
      try {
        await sessionService.deleteSession(id)
        if (stateRef.current.sessionId === id) newChat()
        await goToSessionsPage(stateRef.current.sessionsPage)
      } catch (error) {
        patch({ notice: t(chatErrorKey(statusOf(error))) })
      }
    },
    [goToSessionsPage, newChat, patch, t]
  )

  const retryLast = useCallback(async () => {
    const current = stateRef.current
    if (!current.lastUserMessage || current.sending) return
    let messages = [...current.messages]
    const last = messages[messages.length - 1]
    if (last?.role === "SYSTEM") messages = messages.slice(0, -1)
    const previous = messages[messages.length - 1]
    if (previous?.role === "USER" && previous.id.startsWith("temp-")) messages = messages.slice(0, -1)
    patch({
      messages,
      input: current.lastUserMessage,
      sendError: null,
      lastUserMessage: null,
    })
    await send()
  }, [patch, send])

  const dismissNotice = useCallback(() => patch({ notice: null }), [patch])

  return {
    state,
    actions: {
      selectPrompt,
      setInput,
      send,
      useStarter,
      selectSession,
      newChat,
      removeSession,
      goToSessionsPage,
      loadOlderMessages,
      retryLast,
      dismissNotice,
    },
  }
}

export default useChatController
