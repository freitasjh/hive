package br.com.systec.hive.knowledge.api.domain;

import java.util.List;
import java.util.UUID;

/**
 * Outcome of a knowledge-base retrieval, carrying the chunks plus everything known about why an
 * empty result is empty. Never carries the user query nor the chunk text — diagnostics must not leak
 * content.
 *
 * <p><b>Why the count, the reason and the cause live here and not in the caller.</b> Whoever reports
 * a RAG attempt at turn level needs to say the same thing the retrieval said. When the caller had to
 * reconstruct them, it reconstructed them wrong: it reported {@code foundKbs=0} and
 * {@code reason="empty retrieval"} for an outcome whose actual cause was a threshold that rejected
 * everything, and that false line is what sent the investigation to the wrong layer. The facts
 * therefore travel with the result, and {@link #toTurnDiagnostic} is the only supported way to
 * derive the turn-level view of them.
 *
 * @param outcome    why the retrieval ended this way
 * @param chunks     retrieved chunks, never {@code null}
 * @param elapsedMs  wall time the retrieval itself took
 * @param threshold  similarity cut-off the search <b>actually applied</b>, {@code null} when the
 *                   retrieval never got as far as resolving one. It is the effective value, not the
 *                   requested one: the caller may leave it unspecified and the service resolves it
 *                   from the base's model, and whoever reports the outcome has to name the number
 *                   that ran, or a {@code no_chunk_above_threshold} cannot be explained by the
 *                   operator.
 * @param foundKbs   how many of the requested bases exist for the tenant
 * @param reason     human-readable reason, safe to log, {@code null} on success
 * @param cause      {@code SimpleName:code} of the failure, {@code null} when none
 */
public record RagRetrievalResult(RagOutcome outcome, List<KbChunk> chunks, long elapsedMs,
                                 Double threshold, int foundKbs, String reason, String cause) {

    public RagRetrievalResult {
        chunks = chunks == null ? List.of() : List.copyOf(chunks);
    }

    /**
     * A retrieval that returned chunks.
     *
     * <p>The only factory that takes chunks, and it takes <b>every</b> fact: there is deliberately no
     * shorter overload. An overload that leaves {@code foundKbs}, {@code reason} and {@code cause} at
     * zero and null builds exactly the false observation this record exists to stop — a
     * {@code no_chunk_above_threshold} whose line would claim no base was found and give no reason.
     * A caller that has nothing to report writes {@code 0}/{@code null} on purpose, which is
     * legible; a factory that does it for you is not.
     */
    public static RagRetrievalResult of(RagOutcome outcome, List<KbChunk> chunks, long elapsedMs,
                                        Double threshold, int foundKbs, String reason, String cause) {
        return new RagRetrievalResult(outcome, chunks, elapsedMs, threshold, foundKbs, reason, cause);
    }

    /** A retrieval that returned nothing, with everything the retrieval knew about why. */
    public static RagRetrievalResult empty(RagOutcome outcome, long elapsedMs, Double threshold,
                                           int foundKbs, String reason, String cause) {
        return new RagRetrievalResult(outcome, List.of(), elapsedMs, threshold, foundKbs, reason, cause);
    }

    /**
     * The same observation, seen from a chat turn: this retrieval's outcome and counts, plus the
     * agent it served and the size of the context block it produced.
     *
     * <p>Deliberately derived rather than rebuilt. The two lines a turn emits — the retrieval's and
     * the agent's — must agree on everything this record knows; the only facts the turn adds are
     * {@code agentId} and {@code systemBlockChars}, which only the turn can know. The caller may
     * override the latency afterwards, because a turn is longer than the search it contains.
     */
    public RagDiagnostic toTurnDiagnostic(UUID agentId, UUID tenantId, List<UUID> kbIds,
                                          int systemBlockChars) {
        return RagDiagnostic.builder(outcome, agentId, tenantId)
                .kbIds(kbIds)
                .foundKbs(foundKbs)
                .chunks(chunkCount())
                .threshold(threshold)
                .elapsedMs(elapsedMs)
                .systemBlockChars(systemBlockChars)
                .classifiedCause(cause)
                .reason(reason)
                .build();
    }

    public boolean hasChunks() {
        return !chunks.isEmpty();
    }

    public int chunkCount() {
        return chunks.size();
    }
}
