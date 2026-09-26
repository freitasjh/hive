package br.com.systec.hive.provider.api;

import java.util.UUID;

/**
 * Factory of LLM chat clients keyed by vendor.
 *
 * <p>{@code maxTokens} and {@code topP} are the per-agent generation limits. {@code null} means
 * "not configured" and must be left to the provider default — never coerced to {@code 0}, which
 * would make the provider generate nothing or reject the request.
 */
public interface LlmClientFactory<T> {

    T createClient(AiProvider provider, String model, Double temperature, Integer maxTokens, Double topP,
                   String apiKey, String baseUrl);

    /**
     * Binds the client to a conversation session (stable {@code x-opencode-session}
     * header per chat session on GO/ZEN). Defaults to {@link #createClient} for
     * vendors/factories with no session concept.
     */
    default T createClientWithSession(AiProvider provider, String model, Double temperature,
            Integer maxTokens, Double topP, String apiKey, String baseUrl, UUID sessionId) {
        return createClient(provider, model, temperature, maxTokens, topP, apiKey, baseUrl);
    }
}
