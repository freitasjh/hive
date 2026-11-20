/**
 * Decodificação local do claim `tenant-id` do access token.
 *
 * Contrato verificado na fonte (sem dependência npm nova):
 * - SecurityTokenServiceImpl.java:42 — `TENANT_CLAIM = "tenant-id"`;
 *   :189 — `withClaim(TENANT_CLAIM, user.getTenantId().toString())`.
 * - JWT é `header.payload.signature`; o payload é JSON em base64url decodificável
 *   sem a chave (a assinatura NÃO é verificada aqui — isso é papel do backend).
 * - atob: https://developer.mozilla.org/en-US/docs/Web/API/Window/atob
 *   (InvalidCharacterError em base64 inválido) — consultado em 2026-11-19.
 *
 * Segurança: nada é logado. O payload é usado apenas para ler o UUID do tenant;
 * assinatura, expiração e permissões continuam sendo validadas no servidor.
 */

const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i

function decodeBase64Url(segment: string): string {
  const base64 = segment.replace(/-/g, "+").replace(/_/g, "/")
  const padded = base64.padEnd(Math.ceil(base64.length / 4) * 4, "=")
  const binary = atob(padded)
  const bytes = Uint8Array.from(binary, (char) => char.charCodeAt(0))
  return new TextDecoder().decode(bytes)
}

/**
 * Extrai o UUID do claim `tenant-id`. Retorna `null` para qualquer token
 * malformado — o chamador decide como falhar (login não prossegue sem tenant).
 */
export function decodeTenantId(accessToken: string): string | null {
  if (typeof accessToken !== "string") return null
  const parts = accessToken.split(".")
  if (parts.length !== 3 || !parts[1]) return null

  try {
    const payload: unknown = JSON.parse(decodeBase64Url(parts[1]))
    if (!payload || typeof payload !== "object") return null
    const tenantId = (payload as Record<string, unknown>)["tenant-id"]
    if (typeof tenantId !== "string" || !UUID_PATTERN.test(tenantId)) return null
    return tenantId
  } catch {
    /* base64 inválido, JSON inválido ou TextDecoder indisponível */
    return null
  }
}
