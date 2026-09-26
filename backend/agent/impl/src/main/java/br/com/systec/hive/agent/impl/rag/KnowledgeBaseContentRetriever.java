package br.com.systec.hive.agent.impl.rag;

import br.com.systec.hive.knowledge.api.domain.KbChunk;
import br.com.systec.hive.knowledge.api.domain.RagRetrievalResult;
import br.com.systec.hive.knowledge.api.port.KbRetrievalPort;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * The official {@code ContentRetriever} of a Hive chat turn, delegating to the knowledge module
 * instead of to an {@code EmbeddingStore}.
 *
 * <h3>Why not {@code EmbeddingStoreContentRetriever}</h3>
 *
 * <p>That retriever is the right implementation for a <em>single</em> embedding store: the store is
 * a collaborator, the tenant is a {@code Filter}, and the cut-off is a number. Hive is none of those
 * three. The knowledge to search is chosen per turn by the agent (an agent linked to zero bases has
 * to answer "no_link" instead of searching everything), the tenant filter is enforced in SQL and
 * cannot be expressed as a metadata filter over a shared collection, and the similarity cut-off is
 * a property of the <b>base's embedding model</b> — a number the caller cannot know, which is why
 * {@link KbRetrievalPort#MIN_SCORE_FROM_KB_CONVENTION} exists and why a constant in this module was
 * a bug once already.
 *
 * <p>What this class does keep is the shape the official retriever has: {@code dynamicMaxResults} and
 * {@code dynamicMinScore} resolved <em>per query</em>, at the moment of the search, from functions —
 * never from a constant baked into the class. See {@link #of} for what the defaults resolve to here.
 *
 * <h3>What the retrieval reports is not lost</h3>
 *
 * <p>The official interface returns a bare {@code List<Content>}: it cannot say <em>why</em> it is
 * empty, and an empty list is indistinguishable from "base not in this tenant", "nothing above the
 * cut-off" and "timed out". Hive reports all four ({@code RagOutcome}) and the operator needs them,
 * so the {@code RagRetrievalResult} is handed to the {@code sink} as it comes back. The list returned
 * here and the diagnostic that describes it are then the same observation by construction, rather than
 * two reconstructions of it that can disagree.
 */
public final class KnowledgeBaseContentRetriever implements ContentRetriever {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeBaseContentRetriever.class);

    /**
     * Cut-off resolution for a turn: returns the score this search should run with.
     *
     * <p>Not a constant on purpose. Returning {@link KbRetrievalPort#MIN_SCORE_FROM_KB_CONVENTION}
     * (or anything {@code <= 0}) is the documented way of saying "you do not get to choose: resolve
     * it from the base's model", and the knowledge module is the only layer that can read that model.
     * A number hardcoded here would be a third copy of a cut-off that is already wrong for one of the
     * model families, in a class that cannot even tell which model it is searching.
     *
     * <p>What the hook is for, stated honestly: this is the same shape the official retriever uses, so
     * a cut-off that really does vary per query (by user, by session) is a one-line change here instead
     * of a new retrieval path. <b>It does not vary per query today</b> — the only production caller
     * passes query-independent values, because the number that matters is a function of the base's
     * embedding model and is resolved inside the knowledge module. Reading "resolved per query" as an
     * existing behaviour would be wrong; it is an extension point, nothing more.
     */
    private final Function<Query, Double> dynamicMinScore;

    private final Function<Query, Integer> dynamicMaxResults;
    private final KbRetrievalPort retrieval;
    private final UUID tenantId;
    private final List<UUID> kbIds;
    private final Consumer<RagRetrievalResult> sink;

    private KnowledgeBaseContentRetriever(KbRetrievalPort retrieval, UUID tenantId, List<UUID> kbIds,
                                          Function<Query, Integer> dynamicMaxResults,
                                          Function<Query, Double> dynamicMinScore,
                                          Consumer<RagRetrievalResult> sink) {
        this.retrieval = Objects.requireNonNull(retrieval, "retrieval");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.kbIds = List.copyOf(Objects.requireNonNull(kbIds, "kbIds"));
        this.dynamicMaxResults = Objects.requireNonNull(dynamicMaxResults, "dynamicMaxResults");
        this.dynamicMinScore = Objects.requireNonNull(dynamicMinScore, "dynamicMinScore");
        this.sink = Objects.requireNonNull(sink, "sink");
    }

    /**
     * A retriever for one turn of one agent.
     *
     * @param kbIds            bases linked to the agent <b>and already scoped to the tenant</b>; the
     *                         retriever passes them through unchanged and never widens them
     * @param dynamicMaxResults how many chunks this query may take, resolved now
     * @param dynamicMinScore   how low a score this query accepts, resolved now
     * @param sink             receives the retrieval's own report, including the empty outcomes
     */
    public static KnowledgeBaseContentRetriever of(KbRetrievalPort retrieval, UUID tenantId, List<UUID> kbIds,
                                                   Function<Query, Integer> dynamicMaxResults,
                                                   Function<Query, Double> dynamicMinScore,
                                                   Consumer<RagRetrievalResult> sink) {
        return new KnowledgeBaseContentRetriever(retrieval, tenantId, kbIds, dynamicMaxResults,
                dynamicMinScore, sink);
    }

    @Override
    public List<Content> retrieve(Query query) {
        int maxResults = dynamicMaxResults.apply(query);
        double minScore = dynamicMinScore.apply(query);
        RagRetrievalResult result = retrieval.retrieveWithOutcome(tenantId, kbIds, query.text(),
                maxResults, minScore);
        if (result == null) {
            // Reported as a failure by the turn, not here: a port that returns nothing at all has no
            // outcome to publish, and inventing one here would put a word in the diagnostic's mouth.
            return List.of();
        }
        sink.accept(result);
        return toContents(result);
    }

    /**
     * Chunks to contents, most relevant first.
     *
     * <p>The sort is not decoration: the official {@code ContentAggregator} treats position as
     * relevance ({@code ReciprocalRankFuser} scores by rank, and re-ranking components read the head
     * of the list), so "ordered by relevance" is a contract this method has to keep on its own
     * instead of inheriting from whatever the store happened to return. It is a stable sort, so two
     * chunks with the same score keep the store's order.
     *
     * <p>A chunk with no text is left out: the official {@code TextSegment} refuses blank text, and an
     * empty block in the prompt carries no knowledge. It is not silently dropped — the count is
     * logged — and the diagnostic and the citations keep describing the retrieval as it was, because
     * both are derived from the retrieval's own result rather than from this list.
     */
    private static List<Content> toContents(RagRetrievalResult result) {
        List<KbChunk> ranked = new ArrayList<>(result.chunks());
        ranked.sort(Comparator.comparingDouble(KbChunk::getScore).reversed());
        List<Content> contents = new ArrayList<>(ranked.size());
        int empty = 0;
        for (KbChunk chunk : ranked) {
            if (RetrievedContent.isInjectable(chunk)) {
                contents.add(RetrievedContent.of(chunk));
            } else {
                empty++;
            }
        }
        if (empty > 0) {
            log.warn("kb retrieval dropped empty chunks from the prompt chunks={} dropped={} outcome={}",
                    ranked.size(), empty, result.outcome().wireName());
        }
        return List.copyOf(contents);
    }
}
