package br.com.systec.hive.knowledge.api.port;

import br.com.systec.hive.knowledge.api.domain.KbChunk;
import br.com.systec.hive.knowledge.api.domain.RagRetrievalResult;

import java.util.List;
import java.util.UUID;

public interface KbRetrievalPort {

    /**
     * "The caller did not choose a cut-off" — the service resolves one from the base's model
     * (see the convention table).
     *
     * <p>This is the value callers pass, and the whole reason it is named here: the number that used
     * to be hardcoded in two places was wrong for one of the model families, and a constant nobody
     * owns is how it stayed wrong. It is also the value the previous implementation already
     * interpreted as "unset", so passing it changes no behaviour other than making the choice
     * explicit at every call site.
     *
     * <p>Consequence to keep in mind: a caller that wants <em>no</em> cut-off cannot ask for one with
     * this parameter. That was already the case and stays so — an unset score is a far more likely
     * accident than a deliberate "return everything", and the threshold is also reported on the
     * result, so forcing {@code 0} would make the number in the log a fiction.
     */
    double MIN_SCORE_FROM_KB_CONVENTION = 0d;

    List<KbChunk> retrieve(UUID tenantId, List<UUID> kbIds, String query, int maxResults, double minScore);

    /**
     * Same retrieval as {@link #retrieve}, but reports <em>why</em> the result is empty.
     *
     * <p>{@link #retrieve} collapses "base is not in this tenant", "nothing above the threshold"
     * and "timed out" into an empty list, which is indistinguishable for the caller. Callers that
     * need to explain an empty context to the user must use this overload.
     *
     * <p>{@code minScore} behaves as in {@link #retrieve}: {@link #MIN_SCORE_FROM_KB_CONVENTION}
     * (or anything {@code <= 0}) means "resolve it from the base's model", and any positive value is
     * an explicit override that is used verbatim. Both the number that ran and the reason for the
     * outcome come back on the result.
     */
    RagRetrievalResult retrieveWithOutcome(UUID tenantId, List<UUID> kbIds, String query,
                                          int maxResults, double minScore);
}
