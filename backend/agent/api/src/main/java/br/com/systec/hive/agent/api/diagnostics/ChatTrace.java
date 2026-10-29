package br.com.systec.hive.agent.api.diagnostics;

import br.com.systec.hive.provider.api.AiProvider;

import java.util.UUID;

/**
 * One line of telemetry per chat message: which prompt answered, with which model, how big the
 * prompt was, how long it took and what the provider billed.
 *
 * <p>Exists because a chat timeout reported as "the request failed" is not diagnosable: without
 * the effective {@code maxTokens} and the elapsed time nobody can tell a 1500-word request killed
 * by a 30 s ceiling from a provider outage.
 *
 * <p>Carries no prompt or answer text — only sizes and parameter values.
 *
 * <p>The retrieval fields are gone. They outlived the knowledge base by one batch, and while they
 * were there {@code ragThreshold=not_resolved} was an <b>assertion</b> in every line of production:
 * it said a similarity search ran and failed to resolve a cut-off. Nothing in this product
 * retrieves, so the value was unreachable by design and still printed as if it meant something. A
 * metric that cannot be true is worse than a missing one: it teaches the operator to read a line
 * whose vocabulary no longer describes the system.
 *
 * @param sessionId       chat session
 * @param tenantId        tenant the conversation belongs to ({@code null} when not known)
 * @param turnId          public turn id ({@code null} for the human chat flow)
 * @param clientId        client id of the caller ({@code null} for the human chat flow)
 * @param promptTemplateId template chosen for this conversation
 * @param provider        vendor enum
 * @param model           effective model name
 * @param maxTokens       effective generation ceiling, {@code null} when the provider default applies
 * @param topP            effective nucleus sampling, {@code null} when the provider default applies
 * @param temperature     effective temperature
 * @param historySize     messages replayed from history
 * @param promptChars     total characters of the assembled prompt
 * @param promptMessages  messages in the assembled prompt
 * @param elapsedMs       wall time of the provider generation call — a large value here means the
 *                        provider stalled, which is exactly the signal a chat timeout needs
 * @param promptTokens    provider-reported input tokens, {@code null} when not reported
 * @param completionTokens provider-reported output tokens, {@code null} when not reported
 * @param totalTokens     provider-reported total tokens, {@code null} when not reported
 * @param finishReason    provider finish reason, {@code null} when the call failed
 * @param failure         {@code SimpleName: message} of the failure, {@code null} on success
 */
public record ChatTrace(
        UUID sessionId,
        UUID tenantId,
        UUID turnId,
        String clientId,
        UUID promptTemplateId,
        AiProvider provider,
        String model,
        Integer maxTokens,
        Double topP,
        Double temperature,
        int historySize,
        int promptChars,
        int promptMessages,
        long elapsedMs,
        Integer promptTokens,
        Integer completionTokens,
        Integer totalTokens,
        String finishReason,
        String failure) {

    public static Builder builder(UUID sessionId, UUID promptTemplateId, AiProvider provider, String model) {
        return new Builder(sessionId, promptTemplateId, provider, model);
    }

    public static final class Builder {

        private final UUID sessionId;
        private UUID tenantId;
        private UUID turnId;
        private String clientId;
        private final UUID promptTemplateId;
        private final AiProvider provider;
        private final String model;
        private Integer maxTokens;
        private Double topP;
        private Double temperature;
        private int historySize;
        private int promptChars;
        private int promptMessages;
        private long elapsedMs;
        private Integer promptTokens;
        private Integer completionTokens;
        private Integer totalTokens;
        private String finishReason;
        private String failure;

        private Builder(UUID sessionId, UUID promptTemplateId, AiProvider provider, String model) {
            this.sessionId = sessionId;
            this.promptTemplateId = promptTemplateId;
            this.provider = provider;
            this.model = model;
        }

        public Builder tenantId(UUID value) {
            this.tenantId = value;
            return this;
        }

        public Builder turnId(UUID value) {
            this.turnId = value;
            return this;
        }

        public Builder clientId(String value) {
            this.clientId = value;
            return this;
        }

        public Builder agentLimits(Integer maxTokens, Double topP, Double temperature) {
            this.maxTokens = maxTokens;
            this.topP = topP;
            this.temperature = temperature;
            return this;
        }

        public Builder prompt(int historySize, int promptMessages, int promptChars) {
            this.historySize = historySize;
            this.promptMessages = promptMessages;
            this.promptChars = promptChars;
            return this;
        }

        public Builder elapsedMs(long value) {
            this.elapsedMs = value;
            return this;
        }

        public Builder usage(Integer promptTokens, Integer completionTokens, Integer totalTokens, String finishReason) {
            this.promptTokens = promptTokens;
            this.completionTokens = completionTokens;
            this.totalTokens = totalTokens;
            this.finishReason = finishReason;
            return this;
        }

        public Builder failure(String value) {
            this.failure = value;
            return this;
        }

        public ChatTrace build() {
            return new ChatTrace(sessionId, tenantId, turnId, clientId, promptTemplateId, provider, model, maxTokens, topP, temperature,
                    historySize, promptChars, promptMessages, elapsedMs, promptTokens, completionTokens,
                    totalTokens, finishReason, failure);
        }
    }
}
