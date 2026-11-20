import { beforeEach, describe, expect, it } from 'vitest'
import {
  TENANT_STORAGE_KEY,
  TOKEN_STORAGE_KEY,
  clearSession,
  clearTenantId,
  clearToken,
  getTenantId,
  getToken,
  hasToken,
  setTenantId,
  setToken,
} from './token.storage'

describe('token.storage — chave única', () => {
  beforeEach(() => {
    localStorage.clear()
  })

  it('usa exclusivamente hive_token e nunca guarda senha', () => {
    expect(TOKEN_STORAGE_KEY).toBe('hive_token')

    setToken('token-opaco')

    const keys: string[] = []
    for (let i = 0; i < localStorage.length; i += 1) {
      keys.push(localStorage.key(i) ?? '')
    }
    expect(keys).toEqual(['hive_token'])
    expect(localStorage.getItem(TOKEN_STORAGE_KEY)).toBe('token-opaco')
    for (const key of keys) {
      expect(key.toLowerCase()).not.toContain('password')
      expect(key.toLowerCase()).not.toContain('senha')
    }
  })

  it('getToken/hasToken/clearToken operam na mesma chave', () => {
    expect(getToken()).toBeNull()
    expect(hasToken()).toBe(false)

    setToken('abc123')
    expect(getToken()).toBe('abc123')
    expect(hasToken()).toBe(true)

    clearToken()
    expect(getToken()).toBeNull()
    expect(hasToken()).toBe(false)
  })
})

describe('token.storage — tenant e sessão', () => {
  const TENANT = 'aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee'

  beforeEach(() => {
    localStorage.clear()
  })

  it('usa chave própria hive_tenant_id, distinta de hive_token', () => {
    expect(TENANT_STORAGE_KEY).toBe('hive_tenant_id')
    expect(TENANT_STORAGE_KEY).not.toBe(TOKEN_STORAGE_KEY)
  })

  it('getTenantId/setTenantId/clearTenantId operam na mesma chave', () => {
    expect(getTenantId()).toBeNull()

    setTenantId(TENANT)
    expect(getTenantId()).toBe(TENANT)
    expect(localStorage.getItem(TENANT_STORAGE_KEY)).toBe(TENANT)

    clearTenantId()
    expect(getTenantId()).toBeNull()
  })

  it('setToken aceita tenant opcional e persiste ambas as chaves', () => {
    setToken('jwt-novo', TENANT)

    expect(getToken()).toBe('jwt-novo')
    expect(getTenantId()).toBe(TENANT)
    expect(storedKeys()).toEqual([TOKEN_STORAGE_KEY, TENANT_STORAGE_KEY])
  })

  it('setToken sem tenant não cria hive_tenant_id', () => {
    setToken('jwt-sem-tenant')

    expect(getToken()).toBe('jwt-sem-tenant')
    expect(getTenantId()).toBeNull()
    expect(storedKeys()).toEqual([TOKEN_STORAGE_KEY])
  })

  it('clearSession limpa token e tenant e não guarda senha', () => {
    setToken('jwt-opaco', TENANT)

    clearSession()

    expect(getToken()).toBeNull()
    expect(getTenantId()).toBeNull()
    expect(storedKeys()).toEqual([])
    for (const key of storedKeys()) {
      expect(key.toLowerCase()).not.toContain('password')
      expect(key.toLowerCase()).not.toContain('senha')
    }
  })

  it('renovação substitui token e tenant antigos (rotação)', () => {
    const TENANT_2 = '11111111-2222-4333-8444-555555555555'
    setToken('jwt-antigo', TENANT)
    setToken('jwt-novo', TENANT_2)

    expect(getToken()).toBe('jwt-novo')
    expect(getTenantId()).toBe(TENANT_2)
    expect(storedKeys().sort()).toEqual([TENANT_STORAGE_KEY, TOKEN_STORAGE_KEY].sort())
  })
})

function storedKeys(): string[] {
  const keys: string[] = []
  for (let i = 0; i < localStorage.length; i += 1) keys.push(localStorage.key(i) ?? '')
  return keys
}
