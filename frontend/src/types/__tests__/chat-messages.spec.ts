import { describe, it, expect } from 'vitest'
import {
  MODEL_RETURNED_EMPTY_MESSAGE,
  MODEL_RETURNED_EMPTY_TITLE,
  RAG_REASON_MESSAGES,
  isRagReason,
  ragReasonMessage,
} from '../chat-messages'

describe('ragReasonMessage', () => {
  it('returns a distinct pt-BR sentence for every reason the backend can report', () => {
    const reasons = ['no_link', 'kb_not_found_for_tenant', 'no_chunk_above_threshold', 'timeout', 'error', 'dimension_mismatch'] as const
    const messages = reasons.map((r) => ragReasonMessage(r))
    expect(messages.every((m) => m !== null && m.length > 0)).toBe(true)
    // Uma tabela que repete a mesma frase para motivos diferentes não ajuda ninguém a agir.
    expect(new Set(messages).size).toBe(reasons.length)
  })

  it('tells the user to check dimensions when the bases disagree', () => {
    expect(ragReasonMessage('dimension_mismatch')).toContain('dimensões diferentes')
  })

  it('returns null for a successful turn', () => {
    expect(ragReasonMessage('ok')).toBeNull()
  })

  it('returns null when the module is disabled so the banner is not shown on every message', () => {
    expect(ragReasonMessage('rag_disabled')).toBeNull()
  })

  it('never leaks a machine code into the UI for an unknown reason', () => {
    expect(ragReasonMessage('brand_new_reason')).toBeNull()
    expect(ragReasonMessage(undefined)).toBeNull()
    expect(ragReasonMessage(null)).toBeNull()
    expect(ragReasonMessage(42)).toBeNull()
  })

  it('recognises only reasons it has copy for', () => {
    expect(isRagReason('no_link')).toBe(true)
    expect(isRagReason('ok')).toBe(false)
    expect(isRagReason('rag_disabled')).toBe(false)
  })
})

describe('empty model response copy', () => {
  it('names the token limit and the two ways out', () => {
    expect(MODEL_RETURNED_EMPTY_TITLE).toBe('Resposta vazia')
    expect(MODEL_RETURNED_EMPTY_MESSAGE).toContain('limite de tokens')
    expect(MODEL_RETURNED_EMPTY_MESSAGE).toContain('Aumente o limite de tokens')
  })
})

describe('RAG_REASON_MESSAGES', () => {
  it('never omits the reason code from the table', () => {
    expect(Object.keys(RAG_REASON_MESSAGES).sort()).toEqual([
      'dimension_mismatch',
      'error',
      'kb_not_found_for_tenant',
      'no_chunk_above_threshold',
      'no_link',
      'timeout',
    ])
  })
})
