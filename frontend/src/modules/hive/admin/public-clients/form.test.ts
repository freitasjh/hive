import { describe, expect, it } from 'vitest'
import { emptyPublicClientForm, PUBLIC_GRANTS, toCreatePayload, validatePublicClientForm } from './form'

describe('public clients form', () => {
  it('catálogo de grants é fechado: somente PUBLIC_CHAT_COMPLETE', () => {
    expect(PUBLIC_GRANTS).toEqual(['PUBLIC_CHAT_COMPLETE'])
  })

  it('valida rótulo, grants, prompt e versão fixada', () => {
    expect(validatePublicClientForm(emptyPublicClientForm)).toBe('admin.publicClients.validationLabel')

    const noGrants = { ...emptyPublicClientForm, label: 'Chat' }
    expect(validatePublicClientForm(noGrants)).toBe('admin.publicClients.validationGrants')

    const noPromptCode = {
      ...noGrants,
      grants: ['PUBLIC_CHAT_COMPLETE'],
      prompts: [{ promptCode: '', pinMode: 'FOLLOW_ACTIVE' as const, pinnedVersion: null }],
    }
    expect(validatePublicClientForm(noPromptCode)).toBe('admin.publicClients.validationPromptCode')

    const noVersion = {
      ...noPromptCode,
      prompts: [{ promptCode: 'greeting', pinMode: 'PINNED' as const, pinnedVersion: null }],
    }
    expect(validatePublicClientForm(noVersion)).toBe('admin.publicClients.validationPinnedVersion')
  })

  it('valida limites numéricos e faixa do TTL', () => {
    const base = {
      ...emptyPublicClientForm,
      label: 'Chat',
      grants: ['PUBLIC_CHAT_COMPLETE'],
    }
    expect(validatePublicClientForm({ ...base, tokenTtlSeconds: '10' })).toBe(
      'admin.publicClients.validationLimits'
    )
    expect(validatePublicClientForm({ ...base, maxMessages: '0' })).toBe(
      'admin.publicClients.validationLimits'
    )
    expect(validatePublicClientForm({ ...base, tokenTtlSeconds: '900', maxMessages: '20' })).toBeNull()
  })

  it('monta payload somente com campos preenchidos, sem clientId/tenant', () => {
    const payload = toCreatePayload({
      label: '  Chat do site  ',
      grants: ['PUBLIC_CHAT_COMPLETE'],
      prompts: [{ promptCode: 'greeting', pinMode: 'PINNED', pinnedVersion: 2 }],
      tokenTtlSeconds: '900',
      maxMessages: '',
      maxMessageChars: '',
      maxTotalChars: '',
    })

    expect(payload).toEqual({
      label: 'Chat do site',
      grants: ['PUBLIC_CHAT_COMPLETE'],
      prompts: [{ promptCode: 'greeting', pinMode: 'PINNED', pinnedVersion: 2 }],
      tokenTtlSeconds: 900,
    })
    expect(payload).not.toHaveProperty('clientId')
    expect(payload).not.toHaveProperty('tenantId')
    expect(payload).not.toHaveProperty('rate_limit_per_minute')
  })
})
