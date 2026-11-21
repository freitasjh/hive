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

type GroupBuckets = Partial<Record<ChatHistoryGroupKey, ChatSessionDTO[]>>

type GroupByFn = <T, K extends PropertyKey>(
  items: Iterable<T>,
  keyOf: (item: T, index: number) => K
) => Partial<Record<K, T[]>>

/**
 * `Object.groupBy` (ES2024) num único passo, com fallback para reduce em
 * runtime sem a API. O resultado é null-prototype: leitura sempre com `?? []`.
 * modern-web-guidance `js`, consultado 2026-11-20.
 */
function bucketSessions(
  sessions: ChatSessionDTO[],
  keyOf: (session: ChatSessionDTO) => ChatHistoryGroupKey
): GroupBuckets {
  const groupBy = (Object as typeof Object & { groupBy?: GroupByFn }).groupBy
  if (typeof groupBy === "function") return groupBy(sessions, keyOf) as GroupBuckets

  return sessions.reduce<GroupBuckets>((acc, session) => {
    const key = keyOf(session)
    acc[key] = acc[key] ?? []
    acc[key].push(session)
    return acc
  }, Object.create(null) as GroupBuckets)
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
  const buckets = bucketSessions(sessions, (session) => {
    const updated = new Date(session.updateAt)
    if (Number.isNaN(updated.getTime())) return "older"
    const daysAgo = today - calendarDay(updated)
    if (daysAgo <= 0) return "today"
    if (daysAgo === 1) return "yesterday"
    if (daysAgo < 7) return "last7"
    return "older"
  })

  return CHAT_HISTORY_GROUP_ORDER.map((key) => ({ key, sessions: buckets[key] ?? [] })).filter(
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
