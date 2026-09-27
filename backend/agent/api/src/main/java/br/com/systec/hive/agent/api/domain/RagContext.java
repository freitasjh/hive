package br.com.systec.hive.agent.api.domain;

import br.com.systec.hive.knowledge.api.domain.KbCitation;
import br.com.systec.hive.knowledge.api.domain.RagOutcome;
import dev.langchain4j.data.message.ChatMessage;

import java.util.List;
import java.util.Objects;

/**
 * What the RAG resolution decided for one turn.
 *
 * <p>Public because three collaborators now hold it, not one: the resolver that produces it, the
 * gateway that puts it in the prompt, and the chat service that reports it to the client. It used to
 * be a private record inside the chat service, which is the same reason {@code resolveRagContext} had
 * no unit test of its own — testing it meant constructing the whole service to reach a value it
 * returned.
 *
 * <p>It carries a {@code dev.langchain4j} message because that is the object the official pipeline
 * produced and the object the prompt builder has to add; wrapping it in a project type would mean
 * unwrapping it again two lines later, and would make the size reported by the diagnostic and the
 * message actually injected two separate facts instead of one.
 *
 * <h3>Null policy — and it is shared with {@link TurnRequest} and {@link TurnReply}</h3>
 *
 * <p>One rule across the three records, because three different rules in three adjacent records is how
 * a reader stops trusting any of them: <b>a collection is defaulted, a value that carries meaning is
 * required</b>. So {@code citations} is defaulted — null and empty are the same fact — and
 * {@code outcome} is required, because a context with no outcome would reach the client as a turn
 * whose reason is unknown, and the whole point of this record is that the reason is never unknown.
 *
 * @param used         whether any knowledge reached the prompt
 * @param citations    the sources, in the order the model received them (most relevant first) —
 *                     derived from the injected contents, never from the raw retrieval result
 * @param injected     the message the pipeline produced, carrying the retrieved knowledge
 * @param injectedChars its size, which is what the diagnostic reports as {@code systemBlockChars}
 * @param outcome      why the retrieval ended the way it did; never {@code null}
 * @param threshold    the cut-off the search actually ran with, {@code null} if it resolved none —
 *                     which is a fact about the retrieval, not a missing value
 */
public record RagContext(boolean used, List<KbCitation> citations,
                         ChatMessage injected, int injectedChars,
                         RagOutcome outcome, Double threshold) {

    public RagContext {
        citations = citations == null ? List.of() : List.copyOf(citations);
        outcome = Objects.requireNonNull(outcome, "outcome");
    }

    public static RagContext empty(RagOutcome outcome) {
        return empty(outcome, null);
    }

    public static RagContext empty(RagOutcome outcome, Double threshold) {
        return new RagContext(false, List.of(), null, 0, outcome, threshold);
    }

    public static RagContext of(ChatMessage injected, int injectedChars,
                                List<KbCitation> citations, RagOutcome outcome, Double threshold) {
        return new RagContext(true, citations, injected, injectedChars, outcome, threshold);
    }

    /**
     * Never renders the injected block — only its size.
     *
     * <p>{@link #injected} is the customer's own document, verbatim, in the exact form the model read
     * it. This project has a tested rule that neither chunk text nor a query ever reaches a log line
     * ({@code RagDiagnostic} classifies rather than quotes, and
     * {@code KbRetrievalServiceTest.when_diagnosticLogged_then_neverCarriesQueryTextOrChunkContent}
     * holds it). A generated {@code toString()} would undo that rule from the other side, because this
     * record was private to one class until the seams were cut and is now a public type in
     * {@code agent/api} carried by three collaborators: one {@code log.debug} and the whole document
     * is in a log file, which cannot be un-leaked.
     *
     * <p>{@code injectedChars} is kept, and it is the field an operator actually needs — the diagnostic
     * reports the same number, so this string reads next to a trace line without contradicting it.
     */
    @Override
    public String toString() {
        return "RagContext[used=" + used
                + ", citations=" + citations.size()
                + ", injectedChars=" + injectedChars
                + ", outcome=" + outcome
                + ", threshold=" + threshold
                + "]";
    }
}
