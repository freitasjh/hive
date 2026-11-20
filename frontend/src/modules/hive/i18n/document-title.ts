import type { DashboardViewId } from "@/components/shadcn/navigation"
import {
  authMessage,
  detectAuthLocale,
  type AuthLocale,
  type AuthMessageKey,
} from "../auth/i18n"
import { chatMessage, type ChatMessageKey } from "../chat/i18n"
import { VIEW_LABELS } from "../overview/data/overview.data"

/**
 * Dicionário único de `document.title`. Reusa as chaves já localizadas de
 * auth/chat — nenhuma string desses módulos é redefinida aqui. Overview não tem
 * chave própria; o título en é adicionado abaixo, sem tocar HIVE_OVERVIEW_COPY
 * (que continua pt-only para a UI, fora do escopo).
 *
 * Rotas tabbed (providers/prompts) NÃO passam por aqui: AiAdminPage e
 * PromptsAdminPage definem `admin.document.title` com aba própria.
 */

// AuthLocale, ChatLocale e AdminLocale são a mesma união: "pt-BR" | "en-US".
type DocumentTitleLocale = AuthLocale

const OVERVIEW_TITLE: Record<DocumentTitleLocale, string> = {
  "pt-BR": VIEW_LABELS.overview,
  "en-US": "Overview",
}

type TitleSource =
  | { module: "auth"; key: AuthMessageKey }
  | { module: "chat"; key: ChatMessageKey }
  | { module: "overview" }

const TITLE_SOURCE: Partial<Record<DashboardViewId, TitleSource>> = {
  overview: { module: "overview" },
  login: { module: "auth", key: "auth.document.title" },
  mfa: { module: "auth", key: "auth.mfa.document.title" },
  chat: { module: "chat", key: "chat.document.title" },
}

/** Mesma precedência dos hooks: `hive_locale` > `documentElement.lang`. */
export function detectDocumentTitleLocale(): DocumentTitleLocale {
  return detectAuthLocale()
}

export function documentTitleFor(view: DashboardViewId): string {
  const locale = detectDocumentTitleLocale()
  const source = TITLE_SOURCE[view]

  // Fallback defensivo: view tabbed não tem título próprio neste helper.
  if (!source) return view

  if (source.module === "overview") return OVERVIEW_TITLE[locale]
  if (source.module === "auth") return authMessage(locale, source.key)
  return chatMessage(locale, source.key)
}
