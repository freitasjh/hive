import { describe, it, expect } from 'vitest'
import {
  DEFAULT_SIMILARITY_THRESHOLD,
  NOMIC_SIMILARITY_THRESHOLD,
  STRONG_HIT_MARGIN,
  defaultSimilarityThreshold,
  scoreSeverity,
} from '../embeddingConvention'

/**
 * The playground's score colouring, pinned against the numbers measured on a real base.
 *
 * The regression these exist for: with a fixed 0.9/0.7 ladder, a `nomic-embed-text` hit of 0.6219 —
 * a question the base genuinely answers, measured, above that family's 0.60 cut-off — was rendered
 * in grey, the same colour as noise. A screen that paints correct answers as noise is a bug report
 * waiting to be filed against a system that is working.
 */
describe('defaultSimilarityThreshold', () => {
  it('gives the nomic family the cut-off measured for it', () => {
    expect(defaultSimilarityThreshold('nomic-embed-text')).toBe(0.6)
    expect(defaultSimilarityThreshold('nomic-embed-text:v1.5')).toBe(0.6)
    expect(defaultSimilarityThreshold('NOMIC-EMBED-TEXT')).toBe(0.6)
  })

  it('keeps the historical value for OpenAI', () => {
    expect(defaultSimilarityThreshold('text-embedding-3-small')).toBe(0.7)
    expect(defaultSimilarityThreshold('text-embedding-3-large')).toBe(0.7)
  })

  it('is conservative for an unknown or absent model, like the backend default', () => {
    for (const model of ['mxbai-embed-large', 'all-minilm-l6-v2', 'whatever-v9', '', null, undefined]) {
      expect(defaultSimilarityThreshold(model)).toBe(DEFAULT_SIMILARITY_THRESHOLD)
    }
  })
})

describe('scoreSeverity', () => {
  it('does not paint a measured nomic hit as noise', () => {
    // 0.6219 is the number measured on a real nomic base for a question it answers.
    expect(scoreSeverity(0.6219, 'nomic-embed-text')).not.toBe('secondary')
    expect(scoreSeverity(0.6219, 'nomic-embed-text')).toBe('warn')
  })

  it('still separates a strong nomic hit from a marginal one', () => {
    expect(scoreSeverity(0.85, 'nomic-embed-text')).toBe('success')
    expect(scoreSeverity(0.6, 'nomic-embed-text')).toBe('warn')
  })

  it('renders an OpenAI base exactly as before', () => {
    expect(scoreSeverity(0.92, 'text-embedding-3-small')).toBe('success')
    expect(scoreSeverity(0.75, 'text-embedding-3-small')).toBe('warn')
    expect(scoreSeverity(0.65, 'text-embedding-3-small')).toBe('secondary')
  })

  it('keeps the strong band the same width it always had', () => {
    // 0.2 is what the fixed ladder had (0.9 - 0.7); only the base of the band moves with the model.
    // Compared with a tolerance because the boundary is a sum of two doubles, not a stored value —
    // no score lands on it in practice, and pinning the exact sum would pin a rounding artefact.
    expect(STRONG_HIT_MARGIN).toBe(0.2)
    expect(DEFAULT_SIMILARITY_THRESHOLD + STRONG_HIT_MARGIN).toBeCloseTo(0.9, 10)
    expect(NOMIC_SIMILARITY_THRESHOLD + STRONG_HIT_MARGIN).toBeCloseTo(0.8, 10)
  })

  it('shows a score below its own model cut-off as noise rather than as a confident hit', () => {
    // The backend filters, so this should not happen. If it ever does, the disagreement is visible
    // instead of being displayed as a good result.
    expect(scoreSeverity(0.59, 'nomic-embed-text')).toBe('secondary')
  })
})
