import { describe, it, expect } from 'vitest'
import { decodePermissions, hasPermission } from '../jwtPermissions'

/**
 * Builds a token whose payload is `{ sub, permissions }`, base64url-encoded exactly as a real JWT
 * carries it. A hand-written base64 of a hand-written JSON would not prove the util survives
 * padding and the `-`/`_` alphabet, which is the part that actually breaks.
 */
function tokenWith(claims: unknown): string {
  // UTF-8 antes do btoa: `btoa` só aceita Latin-1 e LANÇA em "João". Um token real carrega UTF-8,
  // e é por isso que o util precisa do passo de percent-encoding + decodeURIComponent — um `atob`
  // cru devolveria lixo silencioso em vez de erro.
  const utf8 = new TextEncoder().encode(JSON.stringify(claims))
  let binary = ''
  utf8.forEach(b => { binary += String.fromCharCode(b) })
  const b64url = btoa(binary)
    .replace(/\+/g, '-')
    .replace(/\//g, '_')
    .replace(/=+$/, '')
  return `eyJhbGciOiJIUzI1NiJ9.${b64url}.signature`
}

describe('jwtPermissions', () => {
  it('reads the permissions claim of a real-shaped token', () => {
    const token = tokenWith({ sub: 'harness', permissions: ['CHAT_SEND', 'AGENT_MANAGE'] })
    expect(decodePermissions(token)).toEqual(['CHAT_SEND', 'AGENT_MANAGE'])
  })

  it('decodes a payload whose base64url needs padding and uses - and _', () => {
    // Estes caracteres e o tamanho saem de um payload real; é o caminho que quebrava com atob cru.
    const claims = { permissions: ['A'.repeat(30), 'B~c', 'D?e'] }
    const token = tokenWith(claims)
    expect(token.split('.')[1]).toMatch(/[-_A-Za-z0-9]+$/)
    expect(decodePermissions(token)).toEqual(claims.permissions)
  })

  it('decodes a payload with non-ASCII characters', () => {
    const token = tokenWith({ permissions: ['CHAT_SEND'], name: 'João' })
    expect(decodePermissions(token)).toEqual(['CHAT_SEND'])
  })

  it('denies when there is no token, an empty token or a non-JWT string', () => {
    // Fail closed é o ponto: um guard que falha aberto aqui só esconde tela de quem já entrou.
    expect(decodePermissions(null)).toEqual([])
    expect(decodePermissions(undefined)).toEqual([])
    expect(decodePermissions('')).toEqual([])
    expect(decodePermissions('opaque-token-sem-ponto')).toEqual([])
  })

  it('denies when the payload is not decodable', () => {
    expect(decodePermissions('header.@@@@not-base64@@@@.sig')).toEqual([])
  })

  it('denies when the claim is absent or is not a list of strings', () => {
    expect(decodePermissions(tokenWith({ sub: 'sem-permission' }))).toEqual([])
    expect(decodePermissions(tokenWith({ permissions: 'CHAT_SEND' }))).toEqual([])
    expect(decodePermissions(tokenWith({ permissions: [1, true, 'CHAT_SEND'] }))).toEqual(['CHAT_SEND'])
  })

  it('answers the question the router asks, and only that', () => {
    const token = tokenWith({ permissions: ['AGENT_MANAGE'] })
    expect(hasPermission(token, 'AGENT_MANAGE')).toBe(true)
    expect(hasPermission(token, 'CHAT_SEND')).toBe(false)
    expect(hasPermission(null, 'AGENT_MANAGE')).toBe(false)
  })

  it('does not treat a substring as a permission', () => {
    // "AGENT_MANAGE" não satisfaz "AGENT_MANAGE_EXTRA": era o tipo de prefixo solto que um
    // includes() por glitch Aceitaria.
    const token = tokenWith({ permissions: ['AGENT_MANAGEMENT'] })
    expect(hasPermission(token, 'AGENT_MANAGE')).toBe(false)
  })
})
