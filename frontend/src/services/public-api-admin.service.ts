import { ApiError, apiRequest, unwrapData } from "./api"
import type {
  PublicApiClient,
  PublicApiClientCreate,
  PublicApiClientPage,
  PublicClientPromptBinding,
  PublicApiClientRotation,
  PublicClientPinMode,
} from "./admin.types"

/**
 * ADR-003 — gestão admin de clients da API pública.
 *
 * Contrato verificado na fonte em 2026-11-22:
 * - `PublicApiClientControllerV1.java`: `@RequestMapping(RestPath.V1 + "/public-api/clients")`;
 *   front prefixa `/hive/v1` (`api.ts:46`) ⇒ `/hive/v1/public-api/clients`.
 * - `PublicApiClientResponseDTO.java`: `token_ttl_seconds`, `max_messages`,
 *   `max_message_chars`, `max_total_chars`, `revoked_at`, `expires_at` e
 *   `previous_secret_expires_at` em snake_case; `clientId`, `label`,
 *   `tenantId`, `grants`, `prompts` em camelCase.
 * - `PublicApiClientPageDTO.java`: `{content, page, size, totalElements, totalPages}`.
 * - `PublicApiClientCreateDTO.java`: request em camelCase e `@JsonIgnoreProperties(
 *   ignoreUnknown = false)` — `clientId`/`tenantId` enviados geram 400.
 * - `PublicApiClientCreatedDTO.java` / `RotatedDTO.java`: `client_secret` one-shot.
 *
 * Segurança: `toClient` reconstrói o item por allowlist. Nunca usa spread do payload,
 * portanto um vazamento futuro de `client_secret`/`*_hash` no backend NÃO entra no
 * estado da UI. O plaintext retornado por `create`/`rotate` é transient por contrato.
 */

export const PUBLIC_CLIENTS_PATH = "/public-api/clients"

function asRecord(value: unknown): Record<string, unknown> | null {
  return value && typeof value === "object" ? (value as Record<string, unknown>) : null
}

function str(value: unknown): string {
  return typeof value === "string" ? value : ""
}

function nullableString(value: unknown): string | null {
  return typeof value === "string" && value.trim() ? value : null
}

function nullableNumber(value: unknown): number | null {
  return typeof value === "number" && Number.isFinite(value) ? value : null
}

function numberOr(value: unknown, fallback: number): number {
  return typeof value === "number" && Number.isFinite(value) ? value : fallback
}

function toPrompt(value: unknown): PublicClientPromptBinding | null {
  const record = asRecord(value)
  if (!record || typeof record.promptCode !== "string") return null
  const pinMode: PublicClientPinMode =
    record.pinMode === "PINNED" ? "PINNED" : "FOLLOW_ACTIVE"
  return {
    promptCode: record.promptCode,
    pinMode,
    pinnedVersion: nullableNumber(record.pinnedVersion),
  }
}

/** Allowlist explícita: somente campos públicos sobrevivem. */
function toClient(value: unknown): PublicApiClient {
  const record = asRecord(value) ?? {}
  const prompts = Array.isArray(record.prompts)
    ? record.prompts.map(toPrompt).filter((prompt): prompt is PublicClientPromptBinding => prompt !== null)
    : []
  const grants = Array.isArray(record.grants)
    ? record.grants.filter((grant): grant is string => typeof grant === "string")
    : []

  return {
    clientId: str(record.clientId),
    label: str(record.label),
    tenantId: str(record.tenantId),
    grants,
    prompts,
    token_ttl_seconds: nullableNumber(record.token_ttl_seconds),
    max_messages: nullableNumber(record.max_messages),
    max_message_chars: nullableNumber(record.max_message_chars),
    max_total_chars: nullableNumber(record.max_total_chars),
    revoked_at: nullableString(record.revoked_at),
    expires_at: nullableString(record.expires_at),
    previous_secret_expires_at: nullableString(record.previous_secret_expires_at),
  }
}

function toPage(value: unknown, fallbackSize: number): PublicApiClientPage {
  const record = asRecord(unwrapData<unknown>(value))
  const content = Array.isArray(record?.content) ? record.content.map(toClient) : []
  return {
    content,
    page: numberOr(record?.page, 0),
    size: numberOr(record?.size, fallbackSize),
    totalElements: numberOr(record?.totalElements, content.length),
    totalPages: numberOr(record?.totalPages, 1),
  }
}

function extractSecret(value: unknown): string {
  const record = asRecord(unwrapData<unknown>(value))
  const secret = str(record?.client_secret)
  if (!secret) throw new ApiError("", 0, "SECRET_MISSING")
  return secret
}

function clientPath(clientId: string): string {
  return `${PUBLIC_CLIENTS_PATH}/${encodeURIComponent(clientId)}`
}

export const publicApiAdminService = {
  async list(page = 0, size = 20): Promise<PublicApiClientPage> {
    const body = await apiRequest<unknown>(PUBLIC_CLIENTS_PATH, { query: { page, size } })
    return toPage(body, size)
  },

  async get(clientId: string): Promise<PublicApiClient> {
    const body = await apiRequest<unknown>(clientPath(clientId))
    return toClient(unwrapData<unknown>(body))
  },

  /** `secret` só existe no retorno desta chamada e deve ir direto ao diálogo one-shot. */
  async create(payload: PublicApiClientCreate): Promise<{ client: PublicApiClient; secret: string }> {
    const body = await apiRequest<unknown>(PUBLIC_CLIENTS_PATH, { method: "POST", body: payload })
    return { client: toClient(body), secret: extractSecret(body) }
  },

  async rotate(clientId: string): Promise<PublicApiClientRotation> {
    const body = await apiRequest<unknown>(`${clientPath(clientId)}/rotate`, { method: "POST" })
    const record = asRecord(unwrapData<unknown>(body))
    return {
      clientId: str(record?.clientId) || clientId,
      secret: extractSecret(body),
      previousSecretExpiresAt: nullableString(record?.previous_secret_expires_at),
    }
  },

  async revoke(clientId: string): Promise<void> {
    await apiRequest<void>(`${clientPath(clientId)}/revoke`, { method: "POST" })
  },

  async updateGrants(clientId: string, grants: string[]): Promise<PublicApiClient> {
    const body = await apiRequest<unknown>(`${clientPath(clientId)}/grants`, {
      method: "PUT",
      body: { grants },
    })
    return toClient(body)
  },

  async updatePrompts(clientId: string, prompts: PublicClientPromptBinding[]): Promise<PublicApiClient> {
    const body = await apiRequest<unknown>(`${clientPath(clientId)}/prompts`, {
      method: "PUT",
      body: { prompts },
    })
    return toClient(body)
  },
}

export default publicApiAdminService
