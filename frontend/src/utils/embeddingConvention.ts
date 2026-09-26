/**
 * The similarity cut-off a knowledge base is searched with, mirrored per embedding model.
 *
 * ## Why this file exists, and why it is a mirror
 *
 * The number is decided by the backend, in one place:
 * `EmbeddingTaskConvention.defaultThreshold(...)` in
 * `backend/knowledge/impl/src/main/java/br/com/systec/hive/knowledge/impl/service/EmbeddingTaskConvention.java`.
 * It is a column of that class's rule table because cosine similarity is only comparable inside the
 * space its own model produces — one cut-off for every model is wrong by construction, which is
 * exactly the bug this replaces: a hardcoded `0.7` in the playground painted a real `nomic-embed-text`
 * hit of 0.6219 in grey, as if it were noise.
 *
 * It is duplicated here rather than fetched because the playground's search response is a bare array
 * of chunks with nowhere to put a threshold, and changing that response shape to carry one is a
 * breaking change to every consumer for a *display* concern.
 *
 * ## What this is and is not allowed to do
 *
 * It is a **labelling** aid, not a filter. Every chunk in the search response already cleared the
 * backend's cut-off — the backend filtered before answering. So nothing here may decide that a
 * returned chunk is noise; the `below` branch exists only so a mismatch is visible rather than
 * painted as a good hit.
 *
 * If you change a number here, change it in the backend table in the same commit, or the playground
 * starts describing a threshold the product does not use.
 */

/** Measured on a real `nomic-embed-text` base: 0.6219 for a question it answers, 0.5972 for one it does not. */
export const NOMIC_SIMILARITY_THRESHOLD = 0.6

/** The value the project has always used, and the strict one for any model nobody has measured. */
export const DEFAULT_SIMILARITY_THRESHOLD = 0.7

/**
 * How far above its own cut-off a score has to be to read as a strong hit.
 *
 * Not a second cut-off: it is the width of the "strong" band, and 0.2 is the width the fixed bands
 * already had (0.9 and 0.7, i.e. 0.2 apart). Keeping the width means an OpenAI base renders exactly
 * as it always has, and only a base whose scores sit lower gets its bands moved down with it.
 */
export const STRONG_HIT_MARGIN = 0.2

/**
 * The cut-off `model` is searched with.
 *
 * Falls back to {@link DEFAULT_SIMILARITY_THRESHOLD} for an unknown or absent model, mirroring the
 * backend's conservative default: an unmeasured model must not get the benefit of a number tuned on
 * another family's score distribution.
 */
export function defaultSimilarityThreshold(model: string | null | undefined): number {
  const name = (model ?? '').toLowerCase()
  return name.includes('nomic') ? NOMIC_SIMILARITY_THRESHOLD : DEFAULT_SIMILARITY_THRESHOLD
}

/** How a retrieved score should read next to the cut-off its own model was searched with. */
export function scoreSeverity(
  score: number,
  model: string | null | undefined,
): 'success' | 'warn' | 'secondary' {
  const cutOff = defaultSimilarityThreshold(model)
  // Reached only if the backend ever hands back a chunk below its own cut-off. Kept so the
  // disagreement is visible instead of being shown as a confident hit.
  if (score < cutOff) return 'secondary'
  if (score >= cutOff + STRONG_HIT_MARGIN) return 'success'
  return 'warn'
}
