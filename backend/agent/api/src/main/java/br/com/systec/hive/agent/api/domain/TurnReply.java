package br.com.systec.hive.agent.api.domain;

import java.util.Objects;

/**
 * What one turn's generation produced, measured in the shape the trace needs.
 *
 * <p>Only sizes and counts, never the prompt and never the answer text: this record is built from
 * text the user wrote and hands it straight to a log line, so anything textual in here would be a
 * leak waiting for a formatter change.
 *
 * <p>A provider failure is a <b>return value</b> ({@link Status#FAILED}), not a thrown exception, and
 * that is the load-bearing decision of this type. The turn's telemetry needs three things about a
 * call that failed — the prompt size, and the fact that this particular failure happened — and a
 * thrown exception carries the last two but not the first, because the prompt was assembled inside
 * the call that failed. Throwing would have forced the caller to record the trace with a
 * reconstructed prompt size, which is exactly the kind of second opinion about a call that already
 * happened that this project's diagnosis lines were rewritten to avoid. So the failure comes back as
 * data, with {@link #failure()} holding the provider's own exception, unwrapped and unmodified, for
 * the caller to classify and to log.
 *
 * <p>Two states are values for the same reason: {@link Status#EMPTY_RESPONSE} needs the provider's
 * {@code finishReason} (a reasoning model that spent its budget reports {@code LENGTH}, an empty
 * string reports {@code null}) and its token usage, both of which live only on the response object.
 *
 * <h3>Null policy — shared with {@link RagContext} and {@link TurnRequest}</h3>
 *
 * <p>Collections are defaulted; a value that carries meaning is required. So {@link #failure} is
 * required on a {@link Status#FAILED} reply and this record coerces nothing else — a {@code null} here
 * is a defect in the gateway, and saying so at construction beats a trace that reports a failure
 * without saying which.
 *
 * @param status           how the generation ended
 * @param text             the reply; {@code null} and meaningless unless {@code status} is
 *                         {@link Status#ANSWERED}
 * @param promptMessages   messages in the assembled prompt, for the trace
 * @param promptChars      characters in the assembled prompt, for the trace
 * @param promptTokens     provider-reported input tokens, {@code null} when not reported
 * @param completionTokens provider-reported output tokens, {@code null} when not reported
 * @param totalTokens      provider-reported total tokens, {@code null} when not reported
 * @param finishReason     provider finish reason name, {@code null} when the provider reported none
 * @param failure          the provider's own exception, unwrapped, when {@code status} is
 *                         {@link Status#FAILED}; {@code null} otherwise
 */
public record TurnReply(Status status, String text, int promptMessages, int promptChars,
                        Integer promptTokens, Integer completionTokens, Integer totalTokens,
                        String finishReason, Throwable failure) {

    public enum Status {
        /** The model returned usable text. */
        ANSWERED,
        /**
         * The provider answered 200 with no usable text. Not a crash and not a configuration error:
         * this project has shipped it as a data-integrity 409 before, and the label the operator
         * needs is exactly that it happened on purpose.
         */
        EMPTY_RESPONSE,
        /** The call did not complete. {@link #failure()} says why. */
        FAILED
    }

    public static TurnReply answered(String text, int promptMessages, int promptChars,
                                     Integer promptTokens, Integer completionTokens,
                                     Integer totalTokens, String finishReason) {
        return new TurnReply(Status.ANSWERED, text, promptMessages, promptChars,
                promptTokens, completionTokens, totalTokens, finishReason, null);
    }

    public static TurnReply emptyResponse(int promptMessages, int promptChars,
                                          Integer promptTokens, Integer completionTokens,
                                          Integer totalTokens, String finishReason) {
        return new TurnReply(Status.EMPTY_RESPONSE, null, promptMessages, promptChars,
                promptTokens, completionTokens, totalTokens, finishReason, null);
    }

    /**
     * A call that did not complete, carrying the prompt it would have sent.
     *
     * <p>The prompt measurements are kept because they were real: the prompt <em>was</em> assembled
     * and the provider <em>was</em> asked. A trace that reported zero for both would describe a turn
     * that sent nothing, and the operator reading a provider failure is precisely the person who
     * needs to know how large the request was.
     */
    public static TurnReply failed(int promptMessages, int promptChars, Throwable failure) {
        return new TurnReply(Status.FAILED, null, promptMessages, promptChars,
                null, null, null, null, Objects.requireNonNull(failure, "failure"));
    }

    /**
     * Never renders the failure — only its class name.
     *
     * <p>{@link #failure} is the provider's own exception, and a LangChain4j {@code HttpException}
     * carries the raw provider payload in its message. That body is echoed into this project's logs by
     * design, and it is the reason {@code RagDiagnostic} classifies rather than quotes. A generated
     * {@code toString()} would undo both: one failed assertion, one framework dump, one stray
     * {@code log.debug} and the whole provider response — request echo included — lands in a log file.
     *
     * <p>The class name is kept because it is the same word the classification uses, so this string
     * stays useful for reading a trace next to the exception it came from.
     */
    @Override
    public String toString() {
        return "TurnReply[status=" + status
                + ", textChars=" + (text == null ? 0 : text.length())
                + ", promptMessages=" + promptMessages
                + ", promptChars=" + promptChars
                + ", promptTokens=" + promptTokens
                + ", completionTokens=" + completionTokens
                + ", totalTokens=" + totalTokens
                + ", finishReason=" + finishReason
                + ", failure=" + (failure == null ? null : failure.getClass().getSimpleName())
                + "]";
    }
}
