package br.com.systec.hive.agent.impl.service;

import br.com.systec.hive.agent.api.diagnostics.ChatTrace;
import br.com.systec.hive.agent.api.diagnostics.ChatTraceRecorder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Logging adapter for {@link ChatTraceRecorder}: one flat key=value line per chat message.
 *
 * <p>INFO on success, WARN when the turn failed — a failed turn is the line an operator greps
 * for, and it is also the only place that records which model and which {@code maxTokens} were in
 * effect when the generation stalled.
 */
@Component
public class LoggingChatTraceRecorder implements ChatTraceRecorder {

    private static final Logger log = LoggerFactory.getLogger(LoggingChatTraceRecorder.class);

    @Override
    public void record(ChatTrace trace) {
        if (trace == null) {
            return;
        }
        String line = format(trace);
        if (trace.failure() == null) {
            log.info(line);
        } else {
            log.warn(line);
        }
    }

    static String format(ChatTrace trace) {
        StringBuilder sb = new StringBuilder(220);
        sb.append("chat turn sessionId=").append(trace.sessionId())
                .append(" agentId=").append(trace.agentId())
                .append(" provider=").append(trace.provider())
                .append(" model=").append(trace.model())
                .append(" maxTokens=").append(orProviderDefault(trace.maxTokens()))
                .append(" topP=").append(orProviderDefault(trace.topP()))
                .append(" temperature=").append(orProviderDefault(trace.temperature()))
                .append(" historySize=").append(trace.historySize())
                .append(" promptMessages=").append(trace.promptMessages())
                .append(" promptChars=").append(trace.promptChars())
                .append(" ragUsed=").append(trace.ragUsed())
                .append(" citationsCount=").append(trace.citationsCount())
                .append(" ragOutcome=").append(trace.ragOutcome() == null ? "n/a" : trace.ragOutcome())
                .append(" ragThreshold=").append(orNotResolved(trace.ragThreshold()))
                .append(" elapsedMs=").append(trace.elapsedMs())
                .append(" promptTokens=").append(orNotReported(trace.promptTokens()))
                .append(" completionTokens=").append(orNotReported(trace.completionTokens()))
                .append(" totalTokens=").append(orNotReported(trace.totalTokens()))
                .append(" finishReason=").append(trace.finishReason() == null ? "n/a" : trace.finishReason());
        if (trace.failure() != null) {
            sb.append(" failure=").append(trace.failure());
        }
        return sb.toString();
    }

    /**
     * Label for a sampling <b>ceiling</b> the agent did not send. The provider then applies its own
     * default, so {@code provider-default} is the accurate description of that field.
     */
    private static String orProviderDefault(Number value) {
        return value == null ? "provider-default" : String.valueOf(value);
    }

    /**
     * Label for a token <b>count</b>. A count has no default: the provider either reported it or did
     * not. Printing {@code provider-default} here claimed a value existed, hiding the fact that the
     * provider omitted usage entirely — which is exactly the case when a turn stalls.
     */
    private static String orNotReported(Number value) {
        return value == null ? "not_reported" : String.valueOf(value);
    }

    /**
     * Label for the similarity cut-off of a RAG search. There is no default: either a retrieval
     * resolved one and this is the number that ran, or the turn never reached the vector search.
     * Printing {@code provider-default} here would be a lie — nothing defaulted it.
     */
    private static String orNotResolved(Number value) {
        return value == null ? "not_resolved" : String.valueOf(value);
    }
}
