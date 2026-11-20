import { beforeEach, describe, expect, it } from 'vitest'
import {
  TOKEN_STORAGE_KEY,
  clearToken,
  getToken,
  hasToken,
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
