/**
 * Normaliza um payload paginado no `Page<T>` que o projeto declara.
 *
 * ## Por que isto existe
 *
 * O Spring Boot 3+ serializa `Page` assim:
 *
 * ```json
 * { "content": [ ... ], "page": { "size": 50, "number": 0, "totalElements": 56, "totalPages": 2 } }
 * ```
 *
 * O `totalElements` vem **aninhado dentro de `page`**, não no topo. A forma antiga (pré-3.0) era
 * achatada: `{ content, totalElements, totalPages, number, size }`.
 *
 * Um `res.data as Page<T>` passivo — que é o que a maior parte dos serviços fazia — entrega um
 * objeto em que `totalElements` e `totalPages` são `undefined`. Isso não dá erro: dá **contadores
 * silenciosamente errados**. Foi assim que o rodapé "Ver todos os N prompts" nunca aparecia no
 * seletor (o `N` caía no comprimento da página) e que o paginador do histórico de conversas
 * simplesmente **não renderizava** (`totalPages > 1` era `undefined > 1`, falso). Em um produto de
 * chat, achar conversa antiga é função principal.
 *
 * ## A regra
 *
 * **Nenhum serviço faz cast de payload paginado. Todos passam por aqui.** Um `as Page<T>` é uma
 * afirmação de que o servidor manda a forma que a gente espera, e o servidor mudou essa forma uma
 * vez já sem avisar.
 *
 * A forma achatada continua aceita de propósito: é a que boa parte dos testes de componente usa.
 * Trocar os dois de uma vez só encobriria justamente a regressão que esta função existe para não
 * deixar passar.
 */

export interface Page<T> {
  content: T[]
  totalElements: number
  totalPages: number
  number: number
  size: number
}

function asRecord(value: unknown): Record<string, unknown> | null {
  return value && typeof value === 'object' ? (value as Record<string, unknown>) : null
}

function num(value: unknown, fallback: number): number {
  return typeof value === 'number' && Number.isFinite(value) ? value : fallback
}

/**
 * @param data o corpo da resposta, como veio
 * @param fallbackSize `size` a usar quando o servidor não mandar (normalmente o `size` pedido)
 */
export function unwrapPage<T>(data: unknown, fallbackSize: number): Page<T> {
  const outer = asRecord(data)
  // `data` embrulhado: aceita o `{data: ...}` de quem já teve wrapper e o payload cru.
  const inner = outer && outer.data !== undefined ? asRecord(outer.data) : outer

  if (inner && Array.isArray(inner.content)) {
    const content = inner.content as T[]
    const meta = asRecord(inner.page) ?? inner
    return {
      content,
      totalElements: num(meta.totalElements, content.length),
      totalPages: num(meta.totalPages, 1),
      number: num(meta.number, 0),
      size: num(meta.size, fallbackSize)
    }
  }

  // `data` embrulhado cujo conteúdo é um array puro: sem metadados, o array é o universo.
  if (outer && Array.isArray(outer.data)) {
    const items = outer.data as T[]
    return { content: items, totalElements: items.length, totalPages: 1, number: 0, size: fallbackSize }
  }

  // Array puro: mesma ideia, sem wrapper.
  if (Array.isArray(data)) {
    const items = data as T[]
    return { content: items, totalElements: items.length, totalPages: 1, number: 0, size: fallbackSize }
  }

  // Último recurso: devolve o que veio. Não há como inventar metadados que o servidor não mandou.
  return (inner ?? outer ?? { content: [] }) as unknown as Page<T>
}
