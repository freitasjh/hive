package br.com.systec.hive.provider.api;

import java.util.UUID;

/**
 * Streaming counterpart of {@link LlmClientFactory}. Additive: the synchronous factory is unchanged
 * and an existing implementor of it stays valid (ADR-001 §4, D1/D8).
 *
 * <p>{@code maxTokens} and {@code topP} keep the same meaning as in {@code createClient}: generation
 * limits of the prompt template, with {@code null} left to the provider default.
 */
public interface LlmStreamingClientFactory<T> extends LlmClientFactory<T> {

    T createStreamingClient(AiProvider provider, String model, Double temperature, Integer maxTokens, Double topP,
                            String apiKey, String baseUrl);

    /**
     * Binds the streaming client to a session, mirroring {@link LlmClientFactory#createClientWithSession}.
     * The {@code default} keeps the frozen {@link #createStreamingClient} signature intact; factories with
     * no session concept inherit the delegation unchanged.
     */
    default T createStreamingClientWithSession(AiProvider provider, String model, Double temperature,
            Integer maxTokens, Double topP, String apiKey, String baseUrl, UUID sessionId) {
        return createStreamingClient(provider, model, temperature, maxTokens, topP, apiKey, baseUrl);
    }
}
