import { afterEach, describe, expect, it, vi } from 'vitest'
import { decodeTenantId } from './jwt'

const TENANT = 'aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee'

function b64url(input: string): string {
  return btoa(input).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '')
}

function jwt(payload: Record<string, unknown> | string): string {
  const body = typeof payload === 'string' ? payload : JSON.stringify(payload)
  return `${b64url('{"alg":"HS256","typ":"JWT"}')}.${b64url(body)}.assinatura`
}

describe('decodeTenantId — claim tenant-id do access token', () => {
  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('extrai o UUID do payload base64url sem verificar assinatura', () => {
    expect(decodeTenantId(jwt({ 'tenant-id': TENANT, sub: 'freitasjh' }))).toBe(TENANT)
  })

  it('aceita UUID em maiúsculas (UUID.fromString do backend é case-insensitive)', () => {
    expect(decodeTenantId(jwt({ 'tenant-id': TENANT.toUpperCase() }))).toBe(TENANT.toUpperCase())
  })

  it('retorna null quando claim tenant-id está ausente', () => {
    expect(decodeTenantId(jwt({ sub: 'freitasjh' }))).toBeNull()
  })

  it('retorna null quando tenant-id não é UUID válido', () => {
    expect(decodeTenantId(jwt({ 'tenant-id': 'not-a-uuid' }))).toBeNull()
  })

  it('retorna null para token com menos de três segmentos', () => {
    expect(decodeTenantId('abc.def')).toBeNull()
    expect(decodeTenantId('abc')).toBeNull()
    expect(decodeTenantId('')).toBeNull()
  })

  it('retorna null para payload base64url inválido (atob lança)', () => {
    expect(decodeTenantId(`${b64url('{"alg":"HS256"}')}.@@@.assinatura`)).toBeNull()
  })

  it('retorna null para payload que não é JSON', () => {
    expect(decodeTenantId(jwt('{json-invalido'))).toBeNull()
  })

  it('nunca imprime o payload nem o token no console', () => {
    const spies = [
      vi.spyOn(console, 'log').mockImplementation(() => {}),
      vi.spyOn(console, 'info').mockImplementation(() => {}),
      vi.spyOn(console, 'warn').mockImplementation(() => {}),
      vi.spyOn(console, 'error').mockImplementation(() => {}),
    ]
    const token = jwt({ 'tenant-id': TENANT })

    decodeTenantId(token)
    decodeTenantId(`${b64url('{"alg":"HS256"}')}.@@@.assinatura`)

    for (const spy of spies) expect(spy).not.toHaveBeenCalled()
  })
})
