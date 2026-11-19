/**
 * Normaliza payload paginado no `Page<T>` do projeto.
 * Porta de bkp/front_bkp/src/services/page.ts:33-84 — assinatura inalterada.
 *
 * Spring Boot 3 serializa `totalElements`/`totalPages` DENTRO de `page`; a forma achatada
 * legada continua aceita porque é a que os fixtures de teste usam.
 */

export interface Page<T> {
  content: T[]
  totalElements: number
  totalPages: number
  number: number
  size: number
}

function asRecord(value: unknown): Record<string, unknown> | null {
  return value && typeof value === "object" ? (value as Record<string, unknown>) : null
}

function num(value: unknown, fallback: number): number {
  return typeof value === "number" && Number.isFinite(value) ? value : fallback
}

export function unwrapPage<T>(data: unknown, fallbackSize: number): Page<T> {
  const outer = asRecord(data)
  const inner = outer && outer.data !== undefined ? asRecord(outer.data) : outer

  if (inner && Array.isArray(inner.content)) {
    const content = inner.content as T[]
    const meta = asRecord(inner.page) ?? inner
    return {
      content,
      totalElements: num(meta.totalElements, content.length),
      totalPages: num(meta.totalPages, 1),
      number: num(meta.number, 0),
      size: num(meta.size, fallbackSize),
    }
  }

  if (outer && Array.isArray(outer.data)) {
    const items = outer.data as T[]
    return { content: items, totalElements: items.length, totalPages: 1, number: 0, size: fallbackSize }
  }

  if (Array.isArray(data)) {
    const items = data as T[]
    return { content: items, totalElements: items.length, totalPages: 1, number: 0, size: fallbackSize }
  }

  const candidate = inner ?? outer
  if (candidate && Array.isArray(candidate.content)) {
    return {
      content: candidate.content as T[],
      totalElements: num(candidate.totalElements, (candidate.content as T[]).length),
      totalPages: num(candidate.totalPages, 1),
      number: num(candidate.number, 0),
      size: num(candidate.size, fallbackSize),
    }
  }
  return { content: [], totalElements: 0, totalPages: 1, number: 0, size: fallbackSize }
}
