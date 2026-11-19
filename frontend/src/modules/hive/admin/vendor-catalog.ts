import { Bot, Cloud, Monitor, Server, Sparkles, Zap, type LucideIcon } from "lucide-react"
import type { AiProvider } from "@/services/admin.types"
import type { AdminMessageKey } from "./i18n"

/** Porta de bkp/front_bkp/src/types/agent-admin.ts:1-158 (rótulos via i18n, ícones lucide). */

export const VENDOR_ORDER: readonly AiProvider[] = [
  "OPENAI",
  "GEMINI",
  "ANTHROPIC",
  "OLLAMA",
  "OPENCODE_GO",
  "OPENCODE_ZEN",
  "OPENAI_COMPATIBLE",
] as const

/** Allowlist dos providers cuja base URL pertence ao tenant (agent-admin.ts:19-26). */
const PROVIDERS_WITH_TENANT_BASE_URL: ReadonlySet<AiProvider> = new Set<AiProvider>([
  "OLLAMA",
  "OPENAI_COMPATIBLE",
])

export function acceptsTenantBaseUrl(provider: AiProvider): boolean {
  return PROVIDERS_WITH_TENANT_BASE_URL.has(provider)
}

export function isRemoteProvider(provider: AiProvider | null): boolean {
  return provider === "OPENCODE_GO" || provider === "OPENCODE_ZEN"
}

export interface VendorMeta {
  provider: AiProvider
  labelKey: AdminMessageKey
  descriptionKey: AdminMessageKey
  costKey: AdminMessageKey
  keyPlaceholderKey: AdminMessageKey
  icon: LucideIcon
  requiresKey: boolean
  keyPrefix: string | null
  defaultModel: string
  models: string[]
  defaultBaseUrl: string | null
  readOnlyBaseUrl: boolean
  remoteModels: boolean
  lgpdWarning: boolean
}

export const VENDOR_CATALOG: Record<AiProvider, VendorMeta> = {
  OPENAI: {
    provider: "OPENAI",
    labelKey: "admin.vendor.OPENAI.label",
    descriptionKey: "admin.vendor.OPENAI.description",
    costKey: "admin.vendor.OPENAI.cost",
    keyPlaceholderKey: "admin.vendor.OPENAI.keyPlaceholder",
    icon: Zap,
    requiresKey: true,
    keyPrefix: "sk-",
    defaultModel: "gpt-4o-mini",
    models: ["gpt-4o-mini", "gpt-4o", "gpt-4", "gpt-3.5-turbo"],
    defaultBaseUrl: null,
    readOnlyBaseUrl: false,
    remoteModels: false,
    lgpdWarning: false,
  },
  GEMINI: {
    provider: "GEMINI",
    labelKey: "admin.vendor.GEMINI.label",
    descriptionKey: "admin.vendor.GEMINI.description",
    costKey: "admin.vendor.GEMINI.cost",
    keyPlaceholderKey: "admin.vendor.GEMINI.keyPlaceholder",
    icon: Sparkles,
    requiresKey: true,
    keyPrefix: "AIza",
    defaultModel: "gemini-2.0-flash",
    models: ["gemini-2.0-flash", "gemini-1.5-pro", "gemini-1.5-flash"],
    defaultBaseUrl: null,
    readOnlyBaseUrl: false,
    remoteModels: false,
    lgpdWarning: false,
  },
  ANTHROPIC: {
    provider: "ANTHROPIC",
    labelKey: "admin.vendor.ANTHROPIC.label",
    descriptionKey: "admin.vendor.ANTHROPIC.description",
    costKey: "admin.vendor.ANTHROPIC.cost",
    keyPlaceholderKey: "admin.vendor.ANTHROPIC.keyPlaceholder",
    icon: Bot,
    requiresKey: true,
    keyPrefix: "sk-ant-",
    defaultModel: "claude-3-5-haiku-latest",
    models: ["claude-3-5-haiku-latest", "claude-3-5-sonnet-latest", "claude-3-opus-latest"],
    defaultBaseUrl: null,
    readOnlyBaseUrl: false,
    remoteModels: false,
    lgpdWarning: false,
  },
  OLLAMA: {
    provider: "OLLAMA",
    labelKey: "admin.vendor.OLLAMA.label",
    descriptionKey: "admin.vendor.OLLAMA.description",
    costKey: "admin.vendor.OLLAMA.cost",
    keyPlaceholderKey: "admin.vendor.OLLAMA.keyPlaceholder",
    icon: Monitor,
    requiresKey: false,
    keyPrefix: null,
    defaultModel: "llama3.2",
    models: ["llama3.2", "llama3.1", "mistral", "qwen2.5"],
    defaultBaseUrl: "http://localhost:11434",
    readOnlyBaseUrl: false,
    remoteModels: false,
    lgpdWarning: false,
  },
  OPENCODE_GO: {
    provider: "OPENCODE_GO",
    labelKey: "admin.vendor.OPENCODE_GO.label",
    descriptionKey: "admin.vendor.OPENCODE_GO.description",
    costKey: "admin.vendor.OPENCODE_GO.cost",
    keyPlaceholderKey: "admin.vendor.OPENCODE_GO.keyPlaceholder",
    icon: Cloud,
    requiresKey: true,
    keyPrefix: null,
    defaultModel: "mimo-v2.5",
    models: ["mimo-v2.5"],
    defaultBaseUrl: "https://opencode.ai/zen/go/v1",
    readOnlyBaseUrl: true,
    remoteModels: true,
    lgpdWarning: true,
  },
  OPENCODE_ZEN: {
    provider: "OPENCODE_ZEN",
    labelKey: "admin.vendor.OPENCODE_ZEN.label",
    descriptionKey: "admin.vendor.OPENCODE_ZEN.description",
    costKey: "admin.vendor.OPENCODE_ZEN.cost",
    keyPlaceholderKey: "admin.vendor.OPENCODE_ZEN.keyPlaceholder",
    icon: Cloud,
    requiresKey: true,
    keyPrefix: null,
    defaultModel: "claude-sonnet-4-5",
    models: ["claude-sonnet-4-5"],
    defaultBaseUrl: "https://opencode.ai/zen/v1",
    readOnlyBaseUrl: true,
    remoteModels: true,
    lgpdWarning: true,
  },
  OPENAI_COMPATIBLE: {
    provider: "OPENAI_COMPATIBLE",
    labelKey: "admin.vendor.OPENAI_COMPATIBLE.label",
    descriptionKey: "admin.vendor.OPENAI_COMPATIBLE.description",
    costKey: "admin.vendor.OPENAI_COMPATIBLE.cost",
    keyPlaceholderKey: "admin.vendor.OPENAI_COMPATIBLE.keyPlaceholder",
    icon: Server,
    requiresKey: true,
    keyPrefix: null,
    defaultModel: "mimo-v2.5",
    models: ["mimo-v2.5"],
    defaultBaseUrl: null,
    readOnlyBaseUrl: false,
    remoteModels: false,
    lgpdWarning: false,
  },
}

export function validateKeyPrefix(provider: AiProvider, apiKey: string): boolean {
  const meta = VENDOR_CATALOG[provider]
  if (!meta.requiresKey || !apiKey || !meta.keyPrefix) return true
  return apiKey.startsWith(meta.keyPrefix)
}
