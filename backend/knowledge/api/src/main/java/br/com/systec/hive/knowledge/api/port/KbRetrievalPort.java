package br.com.systec.hive.knowledge.api.port;

import br.com.systec.hive.knowledge.api.domain.KbChunk;
import br.com.systec.hive.knowledge.api.domain.RagQuerySettings;
import br.com.systec.hive.knowledge.api.domain.RagRetrievalResult;

import java.util.List;
import java.util.UUID;

/**
 * The read side of a knowledge-base search, and the only place a similarity cut-off enters the
 * knowledge module.
 *
 * <p>The cut-off arrives as a {@link RagQuerySettings} — a policy, not a number. It used to arrive as
 * a {@code double} in which {@code 0} meant "resolve it from the base's model", and the same
 * {@code 0} also read as "no cut-off"; the type now says which of the two is meant. See
 * {@link RagQuerySettings} for why.
 */
public interface KbRetrievalPort {

    List<KbChunk> retrieve(UUID tenantId, List<UUID> kbIds, String query, int maxResults,
                           RagQuerySettings settings);

    /**
     * Same retrieval as {@link #retrieve}, but reports <em>why</em> the result is empty.
     *
     * <p>{@link #retrieve} collapses "base is not in this tenant", "nothing above the threshold"
     * and "timed out" into an empty list, which is indistinguishable for the caller. Callers that
     * need to explain an empty context to the user must use this overload.
     *
     * <p>{@code settings} behaves as in {@link #retrieve}. Both the number that ran and the reason
     * for the outcome come back on the result.
     */
    RagRetrievalResult retrieveWithOutcome(UUID tenantId, List<UUID> kbIds, String query,
                                          int maxResults, RagQuerySettings settings);
}
