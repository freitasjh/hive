"use client"

import { useCallback, useEffect, useMemo, useRef, useState } from "react"
import type { ApiError } from "@/services/api"
import type {
  ChatMessageDTO,
  ChatRequestDTO,
  ChatResponseDTO,
  ChatSessionDTO,
  ModelChoice,
  ProviderCatalogEntry,
} from "@/services/chat.types"
import { agentAdminService } from "@/services/agent-admin.service"
import {
  chatService,
  type ChatPayloadDefaults,
  type ChatStreamMeta,
} from "@/services/chat.service"
import { promptService } from "@/services/prompt.service"
import { sessionService } from "@/services/session.service"
import type { PromptTemplateDTO } from "@/services/admin.types"
import { chatErrorKey, chatStreamErrorKey, useChatI18n } from "../i18n"
import { filterMessagesByQuery, filterSessionsByQuery } from "./chat-history"

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

  catalog: ProviderCatalogEntry[]
  catalogLoading: boolean
  catalogError: string | null
  /** Par padrão do tenant (ou derivado do catálogo quando o tenant não tem config). */
  tenantDefault: ModelChoice | null
  /** Escolha explícita da UI; null = usar o par efetivo. */
  selectedModel: ModelChoice | null
  /** Par que respondeu o turno / gravado na sessão — usado para exibir o modelo ativo. */
  effectiveModel: ModelChoice | null

  /** Termo da busca local do histórico (ver chat-history.ts — limitação documentada). */
  query: string
  /** Derivado de sessions + query. */
  filteredSessions: ChatSessionDTO[]
  /** Derivado de messages (já carregadas) + query. */
  matchingMessages: ChatMessageDTO[]
  /** Mensagem alvo do salto via scrollToMessage; limpa pelo ChatThread após o jump. */
  targetMessageId: string | null
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
  catalog: [],
  catalogLoading: true,
  catalogError: null,
  tenantDefault: null,
  selectedModel: null,
  effectiveModel: null,
  query: "",
  filteredSessions: [],
  matchingMessages: [],
  targetMessageId: null,
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
  selectModel: (choice: ModelChoice | null) => void
  setQuery: (value: string) => void
  renameSession: (id: string, title: string) => Promise<boolean>
  jumpToMessage: (messageId: string, sessionId?: string) => Promise<void>
  clearTargetMessage: () => void
}

function statusOf(error: unknown): number | undefined {
  return (error as ApiError | undefined)?.status
}

/**
 * Toda a lógica do Assistant vive aqui: prompt é opcional no primeiro turno
 * (backend aplica o fallback `system_fallback`) e, quando informado, fica travado
 * na sessão; a sessão responde pelo prompt a partir daí. Um seq protege contra
 * resposta atrasada trocar o prompt da conversa corrente.
 */
export function useChatController(): {
  state: ChatControllerState
  actions: ChatControllerActions
} {
  const { t } = useChatI18n()
  const [state, setState] = useState<ChatControllerState>(INITIAL_STATE)
  const stateRef = useRef<ChatControllerState>(INITIAL_STATE)
  const seqRef = useRef(0)
  /** Stream em voo: abortado ao desmontar ou trocar de turno/sessão. */
  const streamRef = useRef<AbortController | null>(null)

  useEffect(() => () => streamRef.current?.abort(), [])

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

  /**
   * Catálogo + default do tenant carregados em paralelo.Endpoint novo
   * GET /v1/providers/catalog pode ainda não existir no backend: falha é
   * graceful — lista vazia e o seletor continua funcional com o default.
   */
  useEffect(() => {
    let alive = true
    // Endpoint recém-entregue: em builds que ainda não o têm, o serviço pode
    // não expor o método. Degradar para rejeição evita quebrar a tela do chat.
    const catalogRequest: Promise<ProviderCatalogEntry[]> =
      typeof agentAdminService.getProviderCatalog === "function"
        ? agentAdminService.getProviderCatalog()
        : Promise.reject(new Error("GET /providers/catalog indisponível"))
    Promise.allSettled([
      catalogRequest,
      typeof agentAdminService.getLlmConfig === "function"
        ? agentAdminService.getLlmConfig()
        : Promise.resolve(null),
    ]).then(([catalogResult, configResult]) => {
      if (!alive) return
      const entries = catalogResult.status === "fulfilled" ? catalogResult.value : []
      const config = configResult.status === "fulfilled" ? configResult.value : null
      const fallbackEntry = entries.find((entry) => entry.configured) ?? entries[0]
      const tenantDefault: ModelChoice | null =
        config?.provider && config.model
          ? { provider: config.provider, model: config.model }
          : fallbackEntry
            ? { provider: fallbackEntry.provider, model: fallbackEntry.defaultModel }
            : null
      patch({
        catalog: entries,
        catalogLoading: false,
        catalogError: catalogResult.status === "rejected" ? t("chat.model.unavailable") : null,
        tenantDefault,
        effectiveModel: stateRef.current.sessionId
          ? stateRef.current.effectiveModel
          : tenantDefault,
      })
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

    const promptTemplateId = current.selectedPromptTemplateId
    const sessionId = current.sessionId
    // Seq capturado ANTES do await: newChat/selectSession durante a chamada
    // invalida a resposta, que não pode reativar sessão antiga nem poluir o histórico.
    const seq = seqRef.current
    const stale = () => seq !== seqRef.current
    const userMessage: ChatMessageDTO = {
      id: `temp-${Date.now()}`,
      sessionId: sessionId ?? "pending",
      role: "USER",
      content: text,
      createAt: new Date().toISOString(),
    }
    // Placeholder criado ANTES do stream: thinking e texto são anexados a ele.
    const assistantId = `assistant-${Date.now()}`
    patch({
      input: "",
      sending: true,
      sendError: null,
      notice: null,
      lastUserMessage: text,
      messages: [
        ...current.messages,
        userMessage,
        {
          id: assistantId,
          sessionId: sessionId ?? "pending",
          role: "ASSISTANT",
          content: "",
          reasoning: "",
          createAt: new Date().toISOString(),
        },
      ],
    })

    const controller = new AbortController()
    streamRef.current = controller
    const updatePlaceholder = (partial: Partial<ChatMessageDTO>) => {
      if (stale()) return
      const after = stateRef.current
      patch({
        messages: after.messages.map((message) =>
          message.id === assistantId ? { ...message, ...partial } : message
        ),
      })
    }
    const refreshSessions = () => {
      sessionService
        .listSessions(0, SESSION_PAGE_SIZE)
        .then((page) => {
          if (stale()) return
          patch({
            sessions: page.content,
            sessionsPage: page.number,
            sessionsTotalPages: page.totalPages,
          })
        })
        .catch(() => undefined)
    }

    let request: ChatRequestDTO
    let baseline: ChatPayloadDefaults | null

    const finishWithAnswer = (answer: ChatResponseDTO) => {
      if (stale()) return
      const after = stateRef.current
      patch({
        sessionId: after.sessionId ?? answer.sessionId ?? null,
        messages: after.messages.map((message) =>
          message.id === assistantId
            ? {
                ...message,
                sessionId: answer.sessionId,
                role: answer.role || "ASSISTANT",
                content: answer.message,
                createAt: answer.timestamp,
              }
            : message
        ),
        historyError: null,
        effectiveModel:
          answer.provider && answer.model
            ? { provider: answer.provider, model: answer.model }
            : after.effectiveModel,
      })
      if (!sessionId && answer.sessionId) refreshSessions()
    }

    const showError = (error: unknown, safeMessage?: string) => {
      if (stale() || controller.signal.aborted) return
      const message = safeMessage ?? t(chatErrorKey(statusOf(error)))
      const after = stateRef.current
      patch({
        sendError: message,
        messages: [
          ...after.messages.filter((item) => item.id !== assistantId),
          {
            id: `system-${Date.now()}`,
            sessionId: after.sessionId ?? "",
            role: "SYSTEM",
            content: message,
            createAt: new Date().toISOString(),
          },
        ],
      })
    }

    // Hoistado: o catch precisa saber se algum frame chegou antes da falha.
    let streamed = false
    let failed = false

    try {
      request = { message: text }
      // Prompt ausente: omitir a chave (não enviar undefined/null). Backend resolve o fallback.
      if (promptTemplateId) request.promptTemplateId = promptTemplateId
      if (sessionId) request.sessionId = sessionId
      // Baseline: sessão aberta é autoritativa; nova conversa usa o default do tenant.
      baseline = sessionId
        ? current.effectiveModel ?? current.tenantDefault
        : current.tenantDefault
      const chosen = current.selectedModel

      // Trocar modelo em conversa existente é enganoso: o backend ignora o override
      // e a resposta viria de outro par. Volta ao efetivo e avisa (UI), enquanto
      // buildChatPayload é a única autoridade sobre o que vai na wire.
      if (sessionId && chosen && baseline) {
        if (chosen.provider !== baseline.provider || chosen.model !== baseline.model) {
          patch({ selectedModel: null, notice: t("chat.model.sessionLocked") })
        }
      } else if (chosen) {
        request.provider = chosen.provider
        request.model = chosen.model
      }

      // Ausência do método = feature flag/serviço legado: fallback direto.
      if (typeof chatService.streamMessage !== "function") {
        finishWithAnswer(await chatService.sendMessage(request, baseline))
        return
      }

      await chatService.streamMessage(
        request,
        baseline,
        {
          onThinking: (thought) => {
            if (stale() || !thought) return
            streamed = true
            const after = stateRef.current
            const placeholder = after.messages.find((item) => item.id === assistantId)
            if (placeholder) updatePlaceholder({ reasoning: (placeholder.reasoning ?? "") + thought })
          },
          onText: (chunk) => {
            if (stale() || !chunk) return
            streamed = true
            const after = stateRef.current
            const placeholder = after.messages.find((item) => item.id === assistantId)
            if (placeholder) updatePlaceholder({ content: placeholder.content + chunk })
          },
          onDone: (frameMeta) => {
            if (stale() || failed) return
            const after = stateRef.current
            const placeholder = after.messages.find((item) => item.id === assistantId)
            if (!placeholder) return
            // Emenda 3: o done pode trazer sessionId; timestamp e par efetivo
            // vêm do estado do turno quando ausentes no frame.
            const meta: ChatStreamMeta = {
              ...frameMeta,
              sessionId: frameMeta.sessionId ?? sessionId ?? after.sessionId ?? null,
              timestamp: frameMeta.timestamp ?? placeholder.createAt,
              provider: frameMeta.provider ?? baseline?.provider ?? null,
              model: frameMeta.model ?? baseline?.model ?? null,
            }
            streamed = true
            patch({
              sessionId: after.sessionId ?? meta.sessionId ?? null,
              messages: after.messages.map((item) =>
                item.id === assistantId
                  ? {
                      ...item,
                      sessionId: meta.sessionId && meta.sessionId !== "pending" ? meta.sessionId : item.sessionId,
                      createAt: meta.timestamp ?? item.createAt,
                    }
                  : item
              ),
              historyError: null,
              effectiveModel:
                meta.provider && meta.model
                  ? { provider: meta.provider, model: meta.model }
                  : after.effectiveModel,
            })
            if (!sessionId && meta.sessionId) refreshSessions()
          },
          onError: (code) => {
            if (stale() || controller.signal.aborted) return
            failed = true
            streamed = true
            const message = t(chatStreamErrorKey(code))
            const after = stateRef.current
            // Não comita resposta parcial: pensamento/texto podem conter dados
            // que o backend classificou como falha. Só a mensagem segura aparece.
            patch({
              sendError: message,
              messages: after.messages.filter((item) => item.id !== assistantId),
            })
          },
        },
        controller.signal
      )

      if (stale() || controller.signal.aborted) return
      // Stream fechou sem terminal: descarta o placeholder e avisa. Nunca
      // reenvia o turno — isso duplicaria a pergunta no histórico.
      if (!streamed && !failed) {
        const after = stateRef.current
        patch({
          sendError: t(chatStreamErrorKey("stream_failed")),
          messages: after.messages.filter((item) => item.id !== assistantId),
        })
      }
    } catch (error) {
      if (stale() || controller.signal.aborted) return
      const code = statusOf(error) === 0 ? (error as { code?: string }).code : undefined
      const timeout = code === "TIMEOUT"
      if (streamed) {
        // Turno já em andamento no backend: reenviar duplicaria a mensagem.
        const after = stateRef.current
        patch({
          sendError: t(timeout ? chatStreamErrorKey("timeout") : chatStreamErrorKey("stream_failed")),
          messages: after.messages.filter((item) => item.id !== assistantId),
        })
        return
      }
      const unavailable = code === "NETWORK_ERROR" || code === "STREAM_UNAVAILABLE"
      if (!unavailable) {
        showError(error, code === "TIMEOUT" ? t(chatStreamErrorKey("timeout")) : undefined)
        return
      }
      // Único reenvio permitido após iniciar o stream: falha de transporte
      // antes de qualquer frame chegar (o backend não processou o turno).
      try {
        finishWithAnswer(await chatService.sendMessage(request!, baseline!))
      } catch (fallbackError) {
        showError(fallbackError)
      }
    } finally {
      if (streamRef.current === controller) streamRef.current = null
      if (!stale()) patch({ sending: false })
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
      streamRef.current?.abort()
      patch({
        sessionId: id,
        messages: [],
        messagesPage: 0,
        messagesTotalPages: 1,
        historyLoading: true,
        historyError: null,
        sendError: null,
        sending: false,
        notice: null,
        // alvo de uma conversa anterior não pode saltar no histórico recém-aberto
        targetMessageId: null,
      })
      try {
        const page = await sessionService.getMessages(id, 0, MESSAGE_PAGE_SIZE)
        if (seq !== seqRef.current) return
        // A sessão é autoritativa quando responde; se getSession falhar, o prompt
        // já selecionado continua válido — apagá-lo travaria o send (selectPrompt
        // retorna cedo com sessão aberta) sem saída para o usuário.
        const fallbackPrompt = stateRef.current.selectedPromptTemplateId
        let promptTemplateId: string | null = fallbackPrompt
        let sessionModel: ModelChoice | null = null
        try {
          const session = await sessionService.getSession(id)
          if (seq === seqRef.current) {
            promptTemplateId = session.promptTemplateId ?? fallbackPrompt
            // Seletor pintado com o par efetivo da conversa (ChatSessionDTO:21-22).
            sessionModel =
              session.provider && session.model
                ? { provider: session.provider, model: session.model }
                : null
          }
        } catch {
          /* mantém prompt atual */
        }
        if (seq !== seqRef.current) return
        patch({
          messages: page.content,
          messagesPage: page.number,
          messagesTotalPages: page.totalPages,
          selectedPromptTemplateId: promptTemplateId,
          effectiveModel: sessionModel,
          selectedModel: null,
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
    streamRef.current?.abort()
    patch({
      sessionId: null,
      messages: [],
      messagesPage: 0,
      messagesTotalPages: 1,
      selectedPromptTemplateId: null,
      input: "",
      sendError: null,
      sending: false,
      historyError: null,
      notice: null,
      lastUserMessage: null,
      selectedModel: null,
      effectiveModel: stateRef.current.tenantDefault,
      targetMessageId: null,
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
  const selectModel = useCallback(
    (choice: ModelChoice | null) => patch({ selectedModel: choice, notice: null }),
    [patch]
  )

  const setQuery = useCallback((value: string) => patch({ query: value }), [patch])

  const clearTargetMessage = useCallback(() => patch({ targetMessageId: null }), [patch])

  /** Renomeio: PATCH + reflejo imediato na lista + recarga (fonte = servidor). */
  const renameSession = useCallback(
    async (id: string, title: string): Promise<boolean> => {
      const clean = title.trim()
      if (!clean) return false
      try {
        await sessionService.updateSession(id, clean)
        patch({
          sessions: stateRef.current.sessions.map((session) =>
            session.id === id ? { ...session, title: clean } : session
          ),
        })
        await goToSessionsPage(stateRef.current.sessionsPage)
        return true
      } catch (error) {
        patch({ notice: t(chatErrorKey(statusOf(error))) })
        return false
      }
    },
    [goToSessionsPage, patch, t]
  )

  /**
   * Salto para uma mensagem endereçável. Quando o resultado pertence a outra
   * sessão, carrega o histórico antes de mirar — o ChatThread só chama
   * scrollToMessage se o messageId já estiver montado no scroller.
   */
  const jumpToMessage = useCallback(
    async (messageId: string, sessionId?: string): Promise<void> => {
      if (sessionId && sessionId !== stateRef.current.sessionId) {
        await selectSession(sessionId)
        // newChat/outra seleção durante o await cancelou o seq: não deixar alvo órfão.
        if (stateRef.current.sessionId !== sessionId) return
      }
      patch({ targetMessageId: messageId })
    },
    [patch, selectSession]
  )

  // Derivados da busca local — memoizados para não recriar a lista a cada render.
  const filteredSessions = useMemo(
    () => filterSessionsByQuery(state.sessions, state.query),
    [state.sessions, state.query]
  )
  const matchingMessages = useMemo(
    () => (state.query.trim() ? filterMessagesByQuery(state.messages, state.query) : []),
    [state.messages, state.query]
  )
  const viewState = useMemo<ChatControllerState>(
    () => ({ ...state, filteredSessions, matchingMessages }),
    [state, filteredSessions, matchingMessages]
  )

  return {
    state: viewState,
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
      selectModel,
      setQuery,
      renameSession,
      jumpToMessage,
      clearTargetMessage,
    },
  }
}

export default useChatController
