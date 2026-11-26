package br.com.systec.hive.assistant.impl.diagnostics;

/**
 * Converte a contagem inclusiva do provider em buckets excludentes para
 * {@code langfuse.observation.usage_details} (ADR-005, decisão do operador 2026-11-25).
 *
 * <p>O Langfuse armazena o atributo sem normalizar: se o provider reporta input com cache inclusivo,
 * o custo superestima. Nenhum preço é calculado aqui — só contagens.
 */
public final class UsageBucketsNormalizer {

    private UsageBucketsNormalizer() {
    }

    public static Buckets of(Integer promptTokens, Integer cachedTokens, Integer completionTokens,
                             Integer totalTokens) {
        int prompt = valueOrZero(promptTokens);
        int completion = valueOrZero(completionTokens);
        Integer cached = cachedTokens == null || cachedTokens <= 0 ? null : Math.min(cachedTokens, prompt);
        int input = prompt - (cached == null ? 0 : cached);
        int total = input + completion + (cached == null ? 0 : cached);
        return new Buckets(input, completion, cached, total);
    }

    private static int valueOrZero(Integer value) {
        return value == null ? 0 : Math.max(0, value);
    }

    public record Buckets(int input, int output, Integer inputCachedTokens, int total) {

        public String usageDetails() {
            StringBuilder json = new StringBuilder("{\"input\":").append(input)
                    .append(",\"output\":").append(output);
            if (inputCachedTokens != null) {
                json.append(",\"input_cached_tokens\":").append(inputCachedTokens);
            }
            return json.append(",\"total\":").append(total).append('}').toString();
        }
    }
}
