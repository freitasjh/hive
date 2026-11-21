package br.com.systec.hive.ai.api.domain;

import java.util.UUID;

/**
 * O que o turno de IA precisa saber sobre o prompt — e nada do dominio persistido do assistant.
 *
 * <p>ADR-021 §3: {@code PromptTemplate} e uma entidade do assistant (code/version/status/tenant).
 * Se ela entrasse em {@code TurnRequest} aqui, {@code ai/api} dependeria de {@code assistant/api}.
 * Este record e a copia pura que cruza a fronteira: o chamador converte antes de montar o turno e
 * continua dono do template original, de que deriva fingerprint, {@code metadata.hive} e o trace.
 *
 * <p><h3>Ordem dos campos</h3>
 * <p>{@code (templateId, text, temperature, maxTokens, topP)} — diferente de
 * {@code InferenceParams (temperature, topP, maxTokens)}. Por isso a conversao e sempre feita pela
 * fabrica nomeada {@code TurnPrompts.from(...)}, nunca por um construtor posicional.
 *
 * @param templateId  id do template persistido, preservado no diagnostico do turno
 * @param text        texto do system prompt; branco significa "o chamador aplica o fallback"
 * @param temperature limite de inferencia, {@code null} = default do provider
 * @param maxTokens   limite de inferencia, {@code null} = default do provider
 * @param topP        limite de inferencia, {@code null} = default do provider
 */
public record TurnPrompt(UUID templateId, String text, Double temperature, Integer maxTokens, Double topP) {
}
