import { describe, it, expect } from 'vitest'
import {
  MODEL_RETURNED_EMPTY_MESSAGE,
  MODEL_RETURNED_EMPTY_TITLE,
} from '../chat-messages'

describe('empty model response copy', () => {
  it('names the token limit and the two ways out', () => {
    expect(MODEL_RETURNED_EMPTY_TITLE).toBe('Resposta vazia')
    expect(MODEL_RETURNED_EMPTY_MESSAGE).toContain('limite de tokens')
    expect(MODEL_RETURNED_EMPTY_MESSAGE).toContain('Aumente o limite de tokens')
  })
})
