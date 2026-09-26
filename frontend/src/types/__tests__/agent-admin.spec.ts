import { describe, it, expect } from 'vitest'
import { acceptsTenantBaseUrl, VENDOR_ORDER, type AiProvider } from '../agent-admin'

describe('acceptsTenantBaseUrl', () => {
  it('accepts only the providers whose base URL is owned by the tenant', () => {
    const accepted = VENDOR_ORDER.filter((p) => acceptsTenantBaseUrl(p))

    expect(accepted).toEqual(['OLLAMA', 'OPENAI_COMPATIBLE'])
  })

  it('rejects the fixed-vendor providers, whose base URL is a server-side constant', () => {
    const fixedVendor: AiProvider[] = ['OPENCODE_GO', 'OPENCODE_ZEN']
    fixedVendor.forEach((provider) => expect(acceptsTenantBaseUrl(provider)).toBe(false))
  })

  it('rejects the providers with no base URL at all', () => {
    const withoutBaseUrl: AiProvider[] = ['OPENAI', 'GEMINI', 'ANTHROPIC']
    withoutBaseUrl.forEach((provider) => expect(acceptsTenantBaseUrl(provider)).toBe(false))
  })
})
