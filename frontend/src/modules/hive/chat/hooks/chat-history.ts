import type { ChatMessageDTO, ChatSessionDTO } from "@/services/chat.types"
import type { ChatLocale } from "../i18n"

/**
 * Busca e agrupamento do histórico — funções puras, sem serviço e sem estado.
 *
 * LIMITAÇÃO DOCUMENTADA: a busca é LOCAL. Ela cobre somente as sessões da página
 * atualmente carregada no sidebar e as mensagens já carregadas da sessão ativa
 * (primeira página + páginas antigas que o usuário pediu). Não varre páginas
 * antigas do servidor nem mensagens de outras sessões — o backend não expõe
 * busca textual (ChatSessionControllerV1: GET sessions / GET {id}/messages).
 * A cópia da limitação aparece na UI via `chat.sessions.search.limit`.
 */

export type ChatHistoryGroupKey = "today" | "yesterday" | "last7" | "older"

export const CHAT_HISTORY_GROUP_ORDER = ["today", "yesterday", "last7", "older"] as const

export interface ChatHistoryGroup {
  key: ChatHistoryGroupKey
  sessions: ChatSessionDTO[]
}

const DAY_MS = 24 * 60 * 60 * 1000

function normalizedTerm(query: string): string {
  return query.trim().toLowerCase()
}

export function filterSessionsByQuery(
  sessions: ChatSessionDTO[],
  query: string
): ChatSessionDTO[] {
  const term = normalizedTerm(query)
  if (!term) return sessions
  return sessions.filter((session) => session.title.toLowerCase().includes(term))
}

export function filterMessagesByQuery(
  messages: ChatMessageDTO[],
  query: string
): ChatMessageDTO[] {
  const term = normalizedTerm(query)
  if (!term) return messages
  return messages.filter((message) => message.content.toLowerCase().includes(term))
}

/** Dia civil local convertido em índice UTC — imune a DST. */
function calendarDay(date: Date): number {
  return Date.UTC(date.getFullYear(), date.getMonth(), date.getDate()) / DAY_MS
}

/**
 * Agrupa por `updateAt` usando o dia civil local. Ordem relativa do backend
 * (updateAt DESC) é preservada dentro de cada grupo; grupos vazios são omitidos.
 */
export function groupSessionsByDate(
  sessions: ChatSessionDTO[],
  reference: Date = new Date()
): ChatHistoryGroup[] {
  const today = calendarDay(reference)
  const groups = new Map<ChatHistoryGroupKey, ChatSessionDTO[]>(
    CHAT_HISTORY_GROUP_ORDER.map((key) => [key, []])
  )

  for (const session of sessions) {
    const updated = new Date(session.updateAt)
    let key: ChatHistoryGroupKey = "older"
    if (!Number.isNaN(updated.getTime())) {
      const daysAgo = today - calendarDay(updated)
      if (daysAgo <= 0) key = "today"
      else if (daysAgo === 1) key = "yesterday"
      else if (daysAgo < 7) key = "last7"
    }
    groups.get(key)!.push(session)
  }

  return CHAT_HISTORY_GROUP_ORDER.map((key) => ({ key, sessions: groups.get(key)! })).filter(
    (group) => group.sessions.length > 0
  )
}

/** Data curta no locale do documento (Intl, sem lib de data). */
export function formatSessionDate(iso: string, locale: ChatLocale): string {
  try {
    const date = new Date(iso)
    if (Number.isNaN(date.getTime())) return ""
    return new Intl.DateTimeFormat(locale, { day: "2-digit", month: "short" }).format(date)
  } catch {
    return ""
  }
}
