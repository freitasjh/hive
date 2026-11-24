import type { PublicApiClientCreate } from "@/services/admin.types"
import type { AdminMessageKey } from "../i18n"
import type { PublicClientsFormState } from "../state"

/** Catálogo fechado de grants — o mesmo validado no writer do backend. */
export const PUBLIC_GRANTS: readonly string[] = ["PUBLIC_CHAT_COMPLETE"]

/** Estado inicial do formulário de criação — sem segredo e sem campos do servidor. */
export const emptyPublicClientForm: PublicClientsFormState = {
  label: "",
  grants: [],
  prompts: [],
  tokenTtlSeconds: "",
  maxMessages: "",
  maxMessageChars: "",
  maxTotalChars: "",
}

function optionalPositiveInt(value: string): number | null {
  const trimmed = value.trim()
  if (!trimmed) return null
  if (!/^\d+$/.test(trimmed)) return null
  const parsed = Number(trimmed)
  return Number.isSafeInteger(parsed) && parsed >= 1 ? parsed : null
}

/**
 * Validação local espelha as restrições do `PublicApiClientCreateDTO`:
 * label obrigatório, grants não vazios, promptCode obrigatório, PINNED exige
 * versão >= 1, TTL entre 60 e 3600 e limites inteiros positivos.
 * Devolve a CHAVE i18n do primeiro erro — o Container traduz.
 */
export function validatePublicClientForm(form: PublicClientsFormState): AdminMessageKey | null {
  if (!form.label.trim()) return "admin.publicClients.validationLabel"
  if (form.grants.length === 0) return "admin.publicClients.validationGrants"

  for (const prompt of form.prompts) {
    if (!prompt.promptCode.trim()) return "admin.publicClients.validationPromptCode"
    if (prompt.pinMode === "PINNED") {
      const version = prompt.pinnedVersion
      if (version === null || !Number.isInteger(version) || version < 1) {
        return "admin.publicClients.validationPinnedVersion"
      }
    }
  }

  const limits = [form.maxMessages, form.maxMessageChars, form.maxTotalChars]
  if (limits.some((value) => value.trim() && optionalPositiveInt(value) === null)) {
    return "admin.publicClients.validationLimits"
  }
  if (form.tokenTtlSeconds.trim()) {
    const ttl = optionalPositiveInt(form.tokenTtlSeconds)
    if (ttl === null || ttl < 60 || ttl > 3600) return "admin.publicClients.validationLimits"
  }
  return null
}

/** Chamado somente após `validatePublicClientForm` devolver `null`. */
export function toCreatePayload(form: PublicClientsFormState): PublicApiClientCreate {
  const payload: PublicApiClientCreate = {
    label: form.label.trim(),
    grants: [...form.grants],
    prompts: form.prompts.map((prompt) => ({
      promptCode: prompt.promptCode.trim(),
      pinMode: prompt.pinMode,
      pinnedVersion: prompt.pinMode === "PINNED" ? prompt.pinnedVersion : null,
    })),
  }
  const tokenTtlSeconds = optionalPositiveInt(form.tokenTtlSeconds)
  const maxMessages = optionalPositiveInt(form.maxMessages)
  const maxMessageChars = optionalPositiveInt(form.maxMessageChars)
  const maxTotalChars = optionalPositiveInt(form.maxTotalChars)
  if (tokenTtlSeconds !== null) payload.tokenTtlSeconds = tokenTtlSeconds
  if (maxMessages !== null) payload.maxMessages = maxMessages
  if (maxMessageChars !== null) payload.maxMessageChars = maxMessageChars
  if (maxTotalChars !== null) payload.maxTotalChars = maxTotalChars
  return payload
}
