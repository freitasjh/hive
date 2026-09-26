package br.com.systec.hive.agent.api.diagnostics;

import br.com.systec.hive.provider.api.AiProvider;

import java.util.UUID;

/**
 * One line of telemetry per chat message: which agent answered, with which parameters, how big the
 * prompt was, whether RAG contributed, how long it took and what the provider billed.
 *
 * <p>Exists because a chat timeout reported as "the request failed" is not diagnosable: without
 * the effective {@code maxTokens} and the elapsed time nobody can tell a 1500-word request killed
 * by a 30 s ceiling from a provider outage.
 *
 * <p>Carries no prompt or answer text — only sizes and parameter values.
 *
 * @param sessionId       chat session
 * @param agentId         agent that answered
 * @param provider        vendor enum
 * @param model           effective model name
 * @param maxTokens       effective generation ceiling, {@code null} when the provider default applies
 * @param topP            effective nucleus sampling, {@code null} when the provider default applies
 * @param temperature     effective temperature
 * @param historySize     messages replayed from history
 * @param promptChars     total characters of the assembled prompt
 * @param promptMessages  messages in the assembled prompt
 * @param ragUsed         whether a knowledge context was injected
 * @param citationsCount  citations attached to the answer
 * @param ragOutcome      why the RAG resolution ended the way it did, {@code null} when unavailable
 * @param ragThreshold    similarity cut-off the RAG search actually applied, {@code null} when the
 *                        retrieval never resolved one. Reported so that a turn with no context can be
 *                        read: {@code no_chunk_above_threshold} is only explicable next to the
 *                        number that rejected the chunks.
 * @param elapsedMs       wall time of the provider generation call. Deliberately excludes the RAG
 *                        retrieval, which is bounded to 2 s and reported on its own diagnostic —
 *                        a large value here means the provider stalled, which is exactly the
 *                        signal a chat timeout needs.
 * @param promptTokens    provider-reported input tokens, {@code null} when not reported
 * @param completionTokens provider-reported output tokens, {@code null} when not reported
 * @param totalTokens     provider-reported total tokens, {@code null} when not reported
 * @param finishReason    provider finish reason, {@code null} when the call failed
 * @param failure         {@code SimpleName: message} of the failure, {@code null} on success
 */
public record ChatTrace(
        UUID sessionId,
        UUID agentId,
        AiProvider provider,
        String model,
        Integer maxTokens,
        Double topP,
        Double temperature,
        int historySize,
        int promptChars,
        int promptMessages,
        boolean ragUsed,
        int citationsCount,
        String ragOutcome,
        Double ragThreshold,
        long elapsedMs,
        Integer promptTokens,
        Integer completionTokens,
        Integer totalTokens,
        String finishReason,
        String failure) {

    public static Builder builder(UUID sessionId, UUID agentId, AiProvider provider, String model) {
        return new Builder(sessionId, agentId, provider, model);
    }

    public static final class Builder {

        private final UUID sessionId;
        private final UUID agentId;
        private final AiProvider provider;
        private final String model;
        private Integer maxTokens;
        private Double topP;
        private Double temperature;
        private int historySize;
        private int promptChars;
        private int promptMessages;
        private boolean ragUsed;
        private int citationsCount;
        private String ragOutcome;
        private Double ragThreshold;
        private long elapsedMs;
        private Integer promptTokens;
        private Integer completionTokens;
        private Integer totalTokens;
        private String finishReason;
        private String failure;

        private Builder(UUID sessionId, UUID agentId, AiProvider provider, String model) {
            this.sessionId = sessionId;
            this.agentId = agentId;
            this.provider = provider;
            this.model = model;
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

        public Builder rag(boolean ragUsed, int citationsCount, String ragOutcome, Double ragThreshold) {
            this.ragUsed = ragUsed;
            this.citationsCount = citationsCount;
            this.ragOutcome = ragOutcome;
            this.ragThreshold = ragThreshold;
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
            return new ChatTrace(sessionId, agentId, provider, model, maxTokens, topP, temperature,
                    historySize, promptChars, promptMessages, ragUsed, citationsCount, ragOutcome,
                    ragThreshold, elapsedMs, promptTokens, completionTokens, totalTokens, finishReason, failure);
        }
    }
}
