package br.com.systec.hive.assistant.api.domain;

import br.com.systec.hive.ai.api.domain.TurnPrompt;

import java.util.Objects;

/**
 * Conversao aditiva de {@link PromptTemplate} para {@link TurnPrompt} (ADR-021, ajuste 7).
 *
 * <p>O template original fica no chamador — {@code TurnOutcomeMapper}, fingerprint e
 * {@code metadata.hive} continuam lendo dele. Aqui so se copia: sem fallback de texto (essa e a
 * politica de {@code assistant/impl}) e sem construtor posicional, porque {@code InferenceParams}
 * guarda {@code (temperature, topP, maxTokens)} e {@code TurnPrompt} guarda
 * {@code (temperature, maxTokens, topP)}.
 */
public final class TurnPrompts {

    private TurnPrompts() {
    }

    /** Copia fiel, campo por nome; parametros de inferencia ausentes viram limites {@code null}. */
    public static TurnPrompt from(PromptTemplate template) {
        Objects.requireNonNull(template, "template");
        InferenceParams params = template.getInferenceParams();
        return new TurnPrompt(
                template.getId(),
                template.getTemplate(),
                params == null ? null : params.getTemperature(),
                params == null ? null : params.getMaxTokens(),
                params == null ? null : params.getTopP());
    }
}
