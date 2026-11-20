import { useCallback, useState } from "react"

/**
 * i18n leve sem dependência nova (frontend/package.json não tem react-i18next).
 * Nenhum literal visível da tela de login vive fora destes dicionários.
 * Chaves prefixadas por `auth.`; pt-BR é o default, como no restante do projeto.
 */

export type AuthLocale = "pt-BR" | "en-US"

const ptBR = {
  "auth.document.title": "Entrar",
  "auth.login.title": "Entrar na sua conta",
  "auth.login.subtitle": "Informe seu usuário e senha para acessar o painel.",
  "auth.login.username.label": "Usuário",
  "auth.login.username.placeholder": "seu.usuario",
  "auth.login.password.label": "Senha",
  "auth.login.submit": "Entrar",
  "auth.login.submitting": "Entrando…",
  "auth.login.brand": "Atlas",
  "auth.login.hero.alt": "Rede hexagonal de agentes autônomos conectados representando a orquestração multi-agente do Hive",
  "auth.login.hero.eyebrow": "HIVE AI",
  "auth.login.hero.title": "Orquestração de sistemas multi-agentes autônomos",
  "auth.login.hero.description": "Conecte provedores de LLM, prompts e agentes especializados em uma arquitetura modular.",
  "auth.login.error.invalid": "Usuário ou senha incorretos. Confira e tente novamente.",
  "auth.login.error.unavailable": "Não foi possível entrar agora. Tente novamente em instantes.",
  "auth.login.error.network": "Sem conexão com o servidor. Verifique sua rede e tente novamente.",
  "auth.login.error.unknown": "Não foi possível entrar. Tente novamente.",
  "auth.nav.logout": "Sair",
  "auth.nav.loggingOut": "Saindo…",

  // Fase 4 — segundo fator (fora do shell, mesmo padrão de chaves `auth.`).
  "auth.mfa.document.title": "Verificação em duas etapas",
  "auth.mfa.title": "Verificação em duas etapas",
  "auth.mfa.subtitle": "Confirme que é você para concluir o acesso ao painel.",
  "auth.mfa.tab.code": "Código",
  "auth.mfa.tab.recovery": "Recuperação",
  "auth.mfa.code.label": "Código de 6 dígitos",
  "auth.mfa.code.placeholder": "000000",
  "auth.mfa.code.hint": "Digite o código gerado pelo seu aplicativo autenticador.",
  "auth.mfa.code.invalidLength": "Informe os 6 dígitos do código.",
  "auth.mfa.recovery.label": "Código de recuperação",
  "auth.mfa.recovery.placeholder": "abcd-1234",
  "auth.mfa.recovery.hint": "Use um código reserva gerado na configuração. Cada um funciona uma vez.",
  "auth.mfa.attempts": "Tentativas restantes",
  "auth.mfa.expires": "Expira em",
  "auth.mfa.expired": "O prazo deste desafio terminou. Volte ao login e tente novamente.",
  "auth.mfa.submit": "Verificar",
  "auth.mfa.submitting": "Verificando…",
  "auth.mfa.back": "Voltar ao login",
  "auth.mfa.error.invalid": "Código inválido. Confira e tente novamente.",
  "auth.mfa.error.tooMany": "Muitas tentativas incorretas. Acesso bloqueado temporariamente. Tente novamente mais tarde.",
  "auth.mfa.error.tenantMissing": "Não foi possível identificar a sessão. Volte ao login e tente novamente.",
  "auth.mfa.error.network": "Sem conexão com o servidor. Verifique sua rede e tente novamente.",
  "auth.mfa.error.unavailable": "Não foi possível concluir a verificação agora. Tente novamente em instantes.",
  "auth.mfa.error.unknown": "Não foi possível concluir a verificação. Tente novamente.",
  "auth.mfa.success": "Verificação concluída. Abrindo o painel…",

  "auth.nav.mfa.setup": "Configurar MFA",
  "auth.nav.mfa.disable": "Desativar MFA",
  "auth.mfa.setup.title": "Configurar autenticação em duas etapas",
  "auth.mfa.setup.description": "Aponte a câmera para o QR Code ou informe o segredo no seu aplicativo autenticador.",
  "auth.mfa.setup.qrAlt": "QR Code para configurar o aplicativo autenticador",
  "auth.mfa.setup.manual": "Entrada manual",
  "auth.mfa.setup.manualHint": "Não consegue ler o QR Code? Copie o segredo abaixo.",
  "auth.mfa.setup.secret": "Segredo",
  "auth.mfa.setup.copy": "Copiar",
  "auth.mfa.setup.copied": "Copiado",
  "auth.mfa.setup.codes": "Códigos de recuperação",
  "auth.mfa.setup.codesHint": "Guarde estes 8 códigos em local seguro. Cada um funciona uma única vez.",
  "auth.mfa.setup.confirmLabel": "Código de confirmação",
  "auth.mfa.setup.confirm": "Confirmar e ativar",
  "auth.mfa.setup.confirming": "Confirmando…",
  "auth.mfa.setup.finish": "Concluir",
  "auth.mfa.setup.cancel": "Cancelar",
  "auth.mfa.setup.error": "Não foi possível configurar o MFA. Tente novamente.",
  "auth.mfa.setup.success": "Autenticação em duas etapas ativada.",

  // Onboarding opcional pós-login (convite não bloqueante, estado só em memória).
  "auth.mfa.invite.label": "Convite de configuração de MFA",
  "auth.mfa.invite.optional": "Opcional",
  "auth.mfa.invite.title": "Configure a proteção da conta",
  "auth.mfa.invite.description": "Ative a verificação em duas etapas para proteger seu acesso. Você pode fazer isso agora ou depois.",
  "auth.mfa.invite.configure": "Configurar agora",
  "auth.mfa.invite.skip": "Pular",
  "auth.mfa.disable.title": "Desativar autenticação em duas etapas",
  "auth.mfa.disable.description": "Informe sua senha para remover o segredo e os códigos de recuperação.",
  "auth.mfa.disable.password": "Senha",
  "auth.mfa.disable.confirm": "Desativar MFA",
  "auth.mfa.disable.confirming": "Desativando…",
  "auth.mfa.disable.error": "Não foi possível desativar o MFA. Confira sua senha e tente novamente.",
  "auth.mfa.disable.success": "Autenticação em duas etapas desativada.",
  "auth.mfa.disable.cancel": "Cancelar",
} as const

export type AuthMessageKey = keyof typeof ptBR

const enUS: Record<AuthMessageKey, string> = {
  "auth.document.title": "Sign in",
  "auth.login.title": "Sign in to your account",
  "auth.login.subtitle": "Enter your username and password to access the dashboard.",
  "auth.login.username.label": "Username",
  "auth.login.username.placeholder": "your.username",
  "auth.login.password.label": "Password",
  "auth.login.submit": "Sign in",
  "auth.login.submitting": "Signing in…",
  "auth.login.brand": "Atlas",
  "auth.login.hero.alt": "Hexagonal network of connected autonomous agents representing Hive multi-agent orchestration",
  "auth.login.hero.eyebrow": "HIVE AI",
  "auth.login.hero.title": "Orchestration of autonomous multi-agent systems",
  "auth.login.hero.description": "Connect LLM providers, prompts, and specialized agents in a modular architecture.",
  "auth.login.error.invalid": "Incorrect username or password. Check and try again.",
  "auth.login.error.unavailable": "Could not sign in right now. Try again shortly.",
  "auth.login.error.network": "No connection to the server. Check your network and try again.",
  "auth.login.error.unknown": "Could not sign in. Try again.",
  "auth.nav.logout": "Log out",
  "auth.nav.loggingOut": "Logging out…",

  "auth.mfa.document.title": "Two-step verification",
  "auth.mfa.title": "Two-step verification",
  "auth.mfa.subtitle": "Confirm it's you to finish signing in to the dashboard.",
  "auth.mfa.tab.code": "Code",
  "auth.mfa.tab.recovery": "Recovery",
  "auth.mfa.code.label": "6-digit code",
  "auth.mfa.code.placeholder": "000000",
  "auth.mfa.code.hint": "Enter the code from your authenticator app.",
  "auth.mfa.code.invalidLength": "Enter all 6 digits of the code.",
  "auth.mfa.recovery.label": "Recovery code",
  "auth.mfa.recovery.placeholder": "abcd-1234",
  "auth.mfa.recovery.hint": "Use a backup code created during setup. Each one works once.",
  "auth.mfa.attempts": "Attempts remaining",
  "auth.mfa.expires": "Expires in",
  "auth.mfa.expired": "This challenge has expired. Return to sign in and try again.",
  "auth.mfa.submit": "Verify",
  "auth.mfa.submitting": "Verifying…",
  "auth.mfa.back": "Back to sign in",
  "auth.mfa.error.invalid": "Invalid code. Check it and try again.",
  "auth.mfa.error.tooMany": "Too many incorrect attempts. Access is temporarily blocked. Try again later.",
  "auth.mfa.error.tenantMissing": "We couldn't identify this session. Return to sign in and try again.",
  "auth.mfa.error.network": "No connection to the server. Check your network and try again.",
  "auth.mfa.error.unavailable": "Verification isn't available right now. Try again shortly.",
  "auth.mfa.error.unknown": "Verification failed. Try again.",
  "auth.mfa.success": "Verification complete. Opening the dashboard…",

  "auth.nav.mfa.setup": "Set up MFA",
  "auth.nav.mfa.disable": "Disable MFA",
  "auth.mfa.setup.title": "Set up two-step verification",
  "auth.mfa.setup.description": "Point your camera at the QR code or enter the secret in your authenticator app.",
  "auth.mfa.setup.qrAlt": "QR code for the authenticator app",
  "auth.mfa.setup.manual": "Manual entry",
  "auth.mfa.setup.manualHint": "Can't scan the QR code? Copy the secret below.",
  "auth.mfa.setup.secret": "Secret",
  "auth.mfa.setup.copy": "Copy",
  "auth.mfa.setup.copied": "Copied",
  "auth.mfa.setup.codes": "Recovery codes",
  "auth.mfa.setup.codesHint": "Store these 8 codes safely. Each one works a single time.",
  "auth.mfa.setup.confirmLabel": "Confirmation code",
  "auth.mfa.setup.confirm": "Confirm and enable",
  "auth.mfa.setup.confirming": "Confirming…",
  "auth.mfa.setup.finish": "Finish",
  "auth.mfa.setup.cancel": "Cancel",
  "auth.mfa.setup.error": "Couldn't set up MFA. Try again.",
  "auth.mfa.setup.success": "Two-step verification enabled.",

  "auth.mfa.invite.label": "MFA setup invite",
  "auth.mfa.invite.optional": "Optional",
  "auth.mfa.invite.title": "Set up account protection",
  "auth.mfa.invite.description": "Enable two-step verification to protect your access. You can do it now or later.",
  "auth.mfa.invite.configure": "Set up now",
  "auth.mfa.invite.skip": "Skip",
  "auth.mfa.disable.title": "Disable two-step verification",
  "auth.mfa.disable.description": "Enter your password to remove the secret and recovery codes.",
  "auth.mfa.disable.password": "Password",
  "auth.mfa.disable.confirm": "Disable MFA",
  "auth.mfa.disable.confirming": "Disabling…",
  "auth.mfa.disable.error": "Couldn't disable MFA. Check your password and try again.",
  "auth.mfa.disable.success": "Two-step verification disabled.",
  "auth.mfa.disable.cancel": "Cancel",
}

const dictionaries: Record<AuthLocale, Record<AuthMessageKey, string>> = {
  "pt-BR": ptBR,
  "en-US": enUS,
}

export function detectAuthLocale(): AuthLocale {
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

export interface AuthI18n {
  locale: AuthLocale
  t: (key: AuthMessageKey) => string
}

export function useAuthI18n(): AuthI18n {
  const [locale] = useState<AuthLocale>(detectAuthLocale)
  const t = useCallback<AuthI18n["t"]>(
    (key) => dictionaries[locale][key] ?? ptBR[key] ?? key,
    [locale]
  )
  return { locale, t }
}
