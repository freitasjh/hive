import { useCallback, useState } from "react"

/** i18n leve, sem dependência nova. Chaves prefixadas por `chat.`; pt-BR default. */
export type ChatLocale = "pt-BR" | "en-US"

const ptBR = {
  "chat.title": "Assistant",
  "chat.common.loading": "Carregando…",
  "chat.subtitle": "Converse e receba a resposta do assistente.",
  "chat.document.title": "Assistant",
  "chat.prompt.label": "Prompt",
  "chat.prompt.placeholder": "Prompt (opcional)",
  "chat.prompt.locked": "Travado nesta conversa",
  "chat.composer.label": "Mensagem para o assistente",
  "chat.composer.placeholder": "Escreva sua mensagem…",
  "chat.composer.send": "Enviar",
  "chat.composer.sending": "Enviando…",
  "chat.composer.hint": "Enter envia · Shift+Enter quebra linha",
  "chat.composer.tooLong": "Mensagem muito longa — máximo de 4000 caracteres.",
  "chat.model.label": "Modelo e provedor",
  "chat.model.default": "Padrão do tenant",
  "chat.model.effective": "Efetivo: {model}",
  "chat.model.loading": "Carregando modelos…",
  "chat.model.unavailable": "Catálogo de modelos indisponível",
  "chat.model.unconfigured": "Sem credencial",
  "chat.model.sessionLocked": "Modelo travado nesta conversa",
  "chat.provider.OPENAI": "OpenAI",
  "chat.provider.GEMINI": "Gemini",
  "chat.provider.ANTHROPIC": "Anthropic",
  "chat.provider.OLLAMA": "Ollama",
  "chat.provider.OPENCODE_GO": "OpenCode GO",
  "chat.provider.OPENCODE_ZEN": "OpenCode Zen",
  "chat.provider.OPENAI_COMPATIBLE": "OpenAI compatível",
  "chat.thread.viewport": "Área de mensagens",
  "chat.thread.jumpToEnd": "Ir para o fim",
  "chat.thread.jumpToStart": "Ir para o início",
  "chat.thread.copy": "Copiar",
  "chat.thread.copied": "Copiado",
  "chat.thread.copyMessage": "Copiar mensagem",
  "chat.code.plain": "Código",
  "chat.code.copyUnavailable": "Código ainda sendo gerado",
  "chat.sessions.title": "Conversas",
  "chat.sessions.new": "Nova conversa",
  "chat.sessions.empty": "Nenhuma conversa ainda",
  "chat.sessions.emptyHint": "Envie a primeira mensagem.",
  "chat.sessions.loading": "Carregando conversas…",
  "chat.sessions.error": "Não foi possível carregar as conversas.",
  "chat.sessions.prev": "Conversas anteriores",
  "chat.sessions.next": "Próximas conversas",
  "chat.sessions.delete": "Remover conversa",
  "chat.sessions.page": "Página {n} de {total}",
  "chat.sessions.search.label": "Buscar conversas e mensagens",
  "chat.sessions.search.placeholder": "Buscar conversas ou mensagens…",
  "chat.sessions.search.limit": "Busca local: apenas conversas carregadas e mensagens já abertas nesta conversa.",
  "chat.sessions.search.noResults": "Nenhum resultado para esta busca.",
  "chat.sessions.group.today": "Hoje",
  "chat.sessions.group.yesterday": "Ontem",
  "chat.sessions.group.last7": "Últimos 7 dias",
  "chat.sessions.group.older": "Anteriores",
  "chat.sessions.messagesFound": "Mensagens encontradas",
  "chat.sessions.jumpMessage": "Ir para a mensagem",
  "chat.sessions.rename": "Renomear conversa",
  "chat.sessions.rename.title": "Título da conversa",
  "chat.sessions.rename.save": "Salvar",
  "chat.sessions.rename.cancel": "Cancelar",
  "chat.sessions.delete.confirm.title": "Excluir conversa?",
  "chat.sessions.delete.confirm.body": "Esta conversa será excluída permanentemente. Esta ação não pode ser desfeita.",
  "chat.sessions.delete.confirm.action": "Excluir",
  "chat.sessions.delete.confirm.cancel": "Cancelar",
  "chat.thread.aria": "Histórico de mensagens",
  "chat.thread.starters": "Sugestões de início",
  "chat.thread.empty": "Escreva sua pergunta.",
  "chat.thread.hint": "A resposta segue o prompt da conversa (selecionado ou padrão).",
  "chat.thread.loading": "Carregando histórico…",
  "chat.thread.error": "Não foi possível carregar o histórico.",
  "chat.thread.generating": "Gerando resposta…",
  "chat.thread.older": "Carregar mensagens antigas",
  "chat.thread.retry": "Tentar novamente",
  "chat.thread.you": "Você",
  "chat.thread.system": "Sistema",
  "chat.thread.assistant": "Assistente",
  "chat.error.auth": "Sua sessão expirou. Entre novamente para continuar.",
  "chat.error.notFound": "Esta conversa não existe mais. Abra outra conversa.",
  "chat.error.generic": "Não foi possível enviar a mensagem. Tente novamente.",
  "chat.error.promptLoad": "Não foi possível carregar os prompts.",
  "chat.disclaimer": "O assistente pode cometer erros. Confira informações importantes.",
} as const

export type ChatMessageKey = keyof typeof ptBR

const enUS: Record<ChatMessageKey, string> = {
  "chat.title": "Assistant",
  "chat.common.loading": "Loading…",
  "chat.subtitle": "Chat and get the answer from the assistant.",
  "chat.document.title": "Assistant",
  "chat.prompt.label": "Prompt",
  "chat.prompt.placeholder": "Prompt (optional)",
  "chat.prompt.locked": "Locked for this conversation",
  "chat.composer.label": "Message to the assistant",
  "chat.composer.placeholder": "Type your message…",
  "chat.composer.send": "Send",
  "chat.composer.sending": "Sending…",
  "chat.composer.hint": "Enter sends · Shift+Enter adds a line",
  "chat.composer.tooLong": "Message too long — 4000 characters maximum.",
  "chat.model.label": "Model and provider",
  "chat.model.default": "Tenant default",
  "chat.model.effective": "Effective: {model}",
  "chat.model.loading": "Loading models…",
  "chat.model.unavailable": "Model catalog unavailable",
  "chat.model.unconfigured": "Not configured",
  "chat.model.sessionLocked": "Model locked for this conversation",
  "chat.provider.OPENAI": "OpenAI",
  "chat.provider.GEMINI": "Gemini",
  "chat.provider.ANTHROPIC": "Anthropic",
  "chat.provider.OLLAMA": "Ollama",
  "chat.provider.OPENCODE_GO": "OpenCode GO",
  "chat.provider.OPENCODE_ZEN": "OpenCode Zen",
  "chat.provider.OPENAI_COMPATIBLE": "OpenAI compatible",
  "chat.thread.viewport": "Messages area",
  "chat.thread.jumpToEnd": "Scroll to end",
  "chat.thread.jumpToStart": "Scroll to start",
  "chat.thread.copy": "Copy",
  "chat.thread.copied": "Copied",
  "chat.thread.copyMessage": "Copy message",
  "chat.code.plain": "Code",
  "chat.code.copyUnavailable": "Code still being generated",
  "chat.sessions.title": "Conversations",
  "chat.sessions.new": "New conversation",
  "chat.sessions.empty": "No conversations yet",
  "chat.sessions.emptyHint": "Send the first message.",
  "chat.sessions.loading": "Loading conversations…",
  "chat.sessions.error": "Could not load conversations.",
  "chat.sessions.prev": "Previous conversations",
  "chat.sessions.next": "Next conversations",
  "chat.sessions.delete": "Remove conversation",
  "chat.sessions.page": "Page {n} of {total}",
  "chat.sessions.search.label": "Search conversations and messages",
  "chat.sessions.search.placeholder": "Search conversations or messages…",
  "chat.sessions.search.limit": "Local search: only loaded conversations and messages already opened in this conversation.",
  "chat.sessions.search.noResults": "No results for this search.",
  "chat.sessions.group.today": "Today",
  "chat.sessions.group.yesterday": "Yesterday",
  "chat.sessions.group.last7": "Last 7 days",
  "chat.sessions.group.older": "Older",
  "chat.sessions.messagesFound": "Matching messages",
  "chat.sessions.jumpMessage": "Go to message",
  "chat.sessions.rename": "Rename conversation",
  "chat.sessions.rename.title": "Conversation title",
  "chat.sessions.rename.save": "Save",
  "chat.sessions.rename.cancel": "Cancel",
  "chat.sessions.delete.confirm.title": "Delete conversation?",
  "chat.sessions.delete.confirm.body": "This conversation will be permanently deleted. This action cannot be undone.",
  "chat.sessions.delete.confirm.action": "Delete",
  "chat.sessions.delete.confirm.cancel": "Cancel",
  "chat.thread.aria": "Message history",
  "chat.thread.starters": "Starter prompts",
  "chat.thread.empty": "Type your question.",
  "chat.thread.hint": "The answer follows the conversation prompt (selected or default).",
  "chat.thread.loading": "Loading history…",
  "chat.thread.error": "Could not load the history.",
  "chat.thread.generating": "Generating answer…",
  "chat.thread.older": "Load older messages",
  "chat.thread.retry": "Try again",
  "chat.thread.you": "You",
  "chat.thread.system": "System",
  "chat.thread.assistant": "Assistant",
  "chat.error.auth": "Your session expired. Sign in again to continue.",
  "chat.error.notFound": "This conversation no longer exists. Open another one.",
  "chat.error.generic": "Could not send the message. Try again.",
  "chat.error.promptLoad": "Could not load prompts.",
  "chat.disclaimer": "The assistant can make mistakes. Check important information.",
}

const dictionaries: Record<ChatLocale, Record<ChatMessageKey, string>> = {
  "pt-BR": ptBR,
  "en-US": enUS,
}

/** Consulta síncrona das mesmas chaves, para compositores fora de hooks (ex.: document.title). */
export function chatMessage(locale: ChatLocale, key: ChatMessageKey): string {
  return dictionaries[locale][key] ?? ptBR[key] ?? key
}

export function detectChatLocale(): ChatLocale {
  try {
    const stored = localStorage.getItem("hive_locale")
    if (stored === "pt-BR" || stored === "en-US") return stored
  } catch {
    /* sem storage: default */
  }
  if (typeof document !== "undefined" && document.documentElement.lang.toLowerCase().startsWith("en")) {
    return "en-US"
  }
  return "pt-BR"
}

export interface ChatI18n {
  locale: ChatLocale
  t: (key: ChatMessageKey, params?: Record<string, string | number>) => string
}

export function useChatI18n(): ChatI18n {
  const [locale] = useState<ChatLocale>(detectChatLocale)
  const t = useCallback<ChatI18n["t"]>(
    (key, params) => {
      const template = chatMessage(locale, key)
      if (!params) return template
      return template.replace(/\{(\w+)\}/g, (match, name: string) =>
        name in params ? String(params[name]) : match
      )
    },
    [locale]
  )
  return { locale, t }
}

/** Erro orientado: 401/404 viram instrução; demais usam detail já redacionado por api.ts. */
export function chatErrorKey(status?: number): ChatMessageKey {
  if (status === 401) return "chat.error.auth"
  if (status === 404) return "chat.error.notFound"
  return "chat.error.generic"
}
