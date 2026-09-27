/**
 * Textos de tela do chat, centralizados como o resto do projeto.
 *
 * There is no i18n runtime in the frontend; the convention is a pt-BR constant table consumed by the
 * components, so a copy change never means hunting strings through templates and no component has
 * to invent its own wording for the same reason code.
 */

/**
 * Reason the model returned no text at all. Sent as a 422 MODEL_RETURNED_EMPTY: the turn produced
 * nothing to save, and saying "falha ao enviar" would send the user looking for a network problem
 * that never happened.
 */
export const MODEL_RETURNED_EMPTY_TITLE = 'Resposta vazia'

export const MODEL_RETURNED_EMPTY_MESSAGE =
  'O modelo não devolveu texto porque o limite de tokens acabou antes da resposta. Aumente o limite de tokens do prompt ou encurte a pergunta e envie de novo.'

/**
 * The tenant has no default LLM, so there is nothing to send the turn to. There is no global
 * fallback and nothing is created automatically: a conversation answered by a model nobody chose is
 * worse than a conversation that refuses and says where to fix it.
 *
 * Only the TITLE lives here. The body is whatever the server sent — it already names the screen, and
 * two wordings for one error code is how a UI starts lying about a backend.
 */
export const LLM_CONFIG_NOT_FOUND_TITLE = 'Provedor e modelo não configurados'
