/**
 * Textos de tela do chat, centralizados como o resto do projeto (ver `types/knowledge.ts`).
 *
 * There is no i18n runtime in the frontend; the convention is a pt-BR constant table consumed by the
 * components, so a copy change never means hunting strings through templates and no component has
 * to invent its own wording for the same reason code.
 */

/**
 * Why the knowledge context was missing. Mirrors `RagOutcome.wireName()` on the backend — the
 * response carries this machine value, and the user gets the sentence it maps to.
 */
export type RagReason =
  | 'ok'
  | 'no_link'
  | 'kb_not_found_for_tenant'
  | 'no_chunk_above_threshold'
  | 'timeout'
  | 'error'
  | 'dimension_mismatch'
  | 'rag_disabled'

/**
 * Copy for each reason the context was not used.
 *
 * `rag_disabled` is intentionally absent: it means the deployment has no knowledge module, which is
 * not something the end user can act on and would only add noise to every single message.
 */
export type RagReasonWithCopy = Exclude<RagReason, 'ok' | 'rag_disabled'>

export const RAG_REASON_MESSAGES: Record<RagReasonWithCopy, string> = {
  no_link: 'Este agente não tem base de conhecimento vinculada, então a resposta foi enviada sem contexto.',
  kb_not_found_for_tenant:
    'A base de conhecimento vinculada não está disponível para esta organização, então a resposta foi enviada sem contexto.',
  no_chunk_above_threshold:
    'Nada na base de conhecimento foi relevante o bastante para esta pergunta, então a resposta foi enviada sem contexto.',
  timeout: 'A busca na base de conhecimento demorou demais e foi interrompida, então a resposta foi enviada sem contexto.',
  error: 'A busca na base de conhecimento falhou, então a resposta foi enviada sem contexto.',
  dimension_mismatch:
    'As bases de conhecimento vinculadas usam modelos de embedding de dimensões diferentes. Selecione bases com a mesma dimensão.',
}

/**
 * Reason the model returned no text at all. Sent as a 422 MODEL_RETURNED_EMPTY: the turn produced
 * nothing to save, and saying "falha ao enviar" would send the user looking for a network problem
 * that never happened.
 */
export const MODEL_RETURNED_EMPTY_TITLE = 'Resposta vazia'

export const MODEL_RETURNED_EMPTY_MESSAGE =
  'O modelo não devolveu texto porque o limite de tokens acabou antes da resposta. Aumente o limite de tokens do agente ou encurte a pergunta e envie de novo.'

/**
 * Narrows to the reasons that actually have copy. Deliberately not to the whole {@link RagReason}:
 * `ok` and `rag_disabled` are real reasons from the server, and treating them as "known" here would
 * let a lookup reach an index the table does not have.
 */
export function isRagReason(value: unknown): value is RagReasonWithCopy {
  return typeof value === 'string' && value in RAG_REASON_MESSAGES
}

/**
 * Copy for a reason the backend reported, or `null` when there is nothing worth telling the user —
 * a successful RAG turn, a disabled module, or a value this build does not know about. An unknown
 * reason is never rendered as raw text: the fallback would be a machine code leaking into the UI.
 */
export function ragReasonMessage(reason: unknown): string | null {
  if (reason === 'rag_disabled' || !isRagReason(reason)) {
    return null
  }
  return RAG_REASON_MESSAGES[reason]
}
