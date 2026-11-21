import { useCallback, useState } from "react"

/** i18n leve, sem dependência nova. Chaves prefixadas por `chat.`; pt-BR default. */
export type ChatLocale = "pt-BR" | "en-US"

const ptBR = {
  "chat.title": "Assistant",
  "chat.common.loading": "Carregando…",
  "chat.subtitle": "Escolha um prompt, converse e receba a resposta dele.",
  "chat.document.title": "Assistant",
  "chat.prompt.label": "Prompt",
  "chat.prompt.placeholder": "Escolha um prompt",
  "chat.prompt.locked": "Travado nesta conversa",
  "chat.warn.selectPrompt": "Escolha um prompt antes de enviar.",
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
  "chat.sessions.emptyHint": "Escolha um prompt e envie a primeira mensagem.",
  "chat.sessions.loading": "Carregando conversas…",
  "chat.sessions.error": "Não foi possível carregar as conversas.",
  "chat.sessions.prev": "Conversas anteriores",
  "chat.sessions.next": "Próximas conversas",
  "chat.sessions.delete": "Remover conversa",
  "chat.sessions.page": "Página {n} de {total}",
  "chat.thread.aria": "Histórico de mensagens",
  "chat.thread.starters": "Sugestões de início",
  "chat.thread.empty": "Escolha um prompt e escreva sua pergunta.",
  "chat.thread.hint": "A resposta segue exatamente o prompt selecionado.",
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
  "chat.subtitle": "Pick a prompt, chat, and get the answer guided by it.",
  "chat.document.title": "Assistant",
  "chat.prompt.label": "Prompt",
  "chat.prompt.placeholder": "Pick a prompt",
  "chat.prompt.locked": "Locked for this conversation",
  "chat.warn.selectPrompt": "Pick a prompt before sending.",
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
  "chat.sessions.emptyHint": "Pick a prompt and send the first message.",
  "chat.sessions.loading": "Loading conversations…",
  "chat.sessions.error": "Could not load conversations.",
  "chat.sessions.prev": "Previous conversations",
  "chat.sessions.next": "Next conversations",
  "chat.sessions.delete": "Remove conversation",
  "chat.sessions.page": "Page {n} of {total}",
  "chat.thread.aria": "Message history",
  "chat.thread.starters": "Starter prompts",
  "chat.thread.empty": "Pick a prompt and type your question.",
  "chat.thread.hint": "The answer follows exactly the prompt you picked.",
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
