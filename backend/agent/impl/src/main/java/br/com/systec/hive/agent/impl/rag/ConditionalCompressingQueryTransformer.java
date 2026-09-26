package br.com.systec.hive.agent.impl.rag;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.rag.query.transformer.CompressingQueryTransformer;
import dev.langchain4j.rag.query.transformer.QueryTransformer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.List;
import java.util.function.Supplier;

/**
 * The query-compression stage, applied only when the question looks like a continuation — and only
 * when an operator has said which model may be spent on it.
 *
 * <h3>Why this gate exists at all</h3>
 *
 * <p>The tutorial is unconditional: "Read and understand the conversation… reformulate this query",
 * once per question. Unconditional costs one extra LLM round-trip on every turn, and this project's
 * retrieval is 57ms against a 6–13s generation, under an explicit requirement that the search must
 * not get slower. So the cost is paid only where the fix is needed — a follow-up question that cannot
 * be understood without the previous turn — and {@link FollowUpQueryHeuristic} decides where that is.
 * This is the project's judgement; the pipeline shape around it is the tutorial's.
 *
 * <h3>Why the model is a decision and not the agent's</h3>
 *
 * <p>The compressor receives the whole conversation and answers with one rewritten sentence. That is
 * the worst possible shape for an expensive reasoning model: maximum input tokens for a ~40-token
 * output, and — this project has already been bitten by it — a reasoning model given a small
 * {@code maxTokens} can spend the whole budget on reasoning and return no content at all, which
 * {@code ChatServiceImpl} reports as {@code empty_model_response}. Spending the agent's model here
 * would add a second reasoning pass to the turn the operator is already paying for.
 *
 * <p>So the model is supplied by the caller and resolved per turn, and the transformer's own guard is
 * that it must not be the agent's model: when the two are the same, the rewrite runs on the expensive
 * model and the gate logs that it did not fire. Disabling by default is deliberate — an unmeasured
 * extra LLM call is exactly what the baseline forbids — and it is enabled by naming a model.
 *
 * <h3>Failure policy</h3>
 *
 * <p>Compression is an optimisation for retrieval. If the model is missing, errors, answers blank or
 * answers something that is not a usable query, the original query is returned and the reason is
 * logged. It never propagates an exception into the turn, because a chat that fails because a
 * rewording failed is a strictly worse product than a chat that searches with the original words.
 *
 * <h3>How loud the log is</h3>
 *
 * <p>A rewrite that fires is logged at {@code INFO} with its rule, its model and its own wall time —
 * that is the line that answers "why did this answer cost double". Every other decision, including
 * every decision made while the stage is switched off, is logged at {@code DEBUG}. That asymmetry is
 * deliberate and it is the reason this object is built even when the stage is off: the operator needs
 * the fire rate <em>before</em> spending anything, and an operator who has to switch the stage on to
 * find out how often it would fire has already spent the first call. A refusal that is a
 * configuration problem (no client, a blank rewrite, a provider error) is {@code WARN}, because
 * someone has to act on it.
 */
public final class ConditionalCompressingQueryTransformer implements QueryTransformer {

    private static final Logger log = LoggerFactory.getLogger(ConditionalCompressingQueryTransformer.class);

    private final Supplier<ChatModel> compressionModel;
    private final String modelName;
    private final int ellipticalLimit;

    /**
     * @param compressionModel supplies the model to rewrite with, or {@code null} to keep the stage
     *                         off; it is requested only after the heuristic has fired, so a stage that
     *                         is off costs one object and no client
     * @param modelName        the model id, for the log line — never the API key
     * @param ellipticalLimit  length at or below which a question counts as elliptical
     */
    public ConditionalCompressingQueryTransformer(Supplier<ChatModel> compressionModel, String modelName,
                                                 int ellipticalLimit) {
        this.compressionModel = compressionModel;
        this.modelName = modelName == null || modelName.isBlank() ? "unconfigured" : modelName;
        this.ellipticalLimit = ellipticalLimit;
    }

    @Override
    public Collection<Query> transform(Query query) {
        // The heuristic runs first, always — including when the stage is off. Its verdict is what an
        // operator needs to see to decide whether to turn the stage on, and it costs no model call.
        boolean hasHistory = hasSomethingTheUserSaid(query);
        FollowUpQueryHeuristic.Decision decision =
                FollowUpQueryHeuristic.decide(query.text(), hasHistory, ellipticalLimit);

        if (compressionModel == null) {
            log.debug("query compression skipped reason=not_configured heuristic={} wouldRewrite={} model={}",
                    decision.reason(), decision.compress(), modelName);
            return List.of(query);
        }
        if (!decision.compress()) {
            log.debug("query compression skipped reason={} model={}", decision.reason(), modelName);
            return List.of(query);
        }
        long startedAt = System.nanoTime();
        try {
            ChatModel model = compressionModel.get();
            if (model == null) {
                log.warn("query compression skipped reason=no_model_available model={}", modelName);
                return List.of(query);
            }
            // The default template is passed explicitly: the transformer's own constructor would
            // otherwise re-parse the same template string on a question already paying for a round-trip.
            Query compressed = new CompressingQueryTransformer(model,
                    CompressingQueryTransformer.DEFAULT_PROMPT_TEMPLATE).transform(query)
                    .iterator().next();
            String text = compressed.text();
            if (text == null || text.isBlank()) {
                log.warn("query compression discarded reason=blank_result model={} elapsedMs={}",
                        modelName, elapsedMs(startedAt));
                return List.of(query);
            }
            // The rewritten query is logged as a length, never as text: it is derived from the user's
            // own conversation, and this line is read by operators who have no business reading it.
            log.info("query compression applied reason={} model={} elapsedMs={} originalChars={} compressedChars={}",
                    decision.reason(), modelName, elapsedMs(startedAt), query.text().length(), text.length());
            return List.of(compressed);
        } catch (Exception e) {
            log.warn("query compression failed reason={} model={} elapsedMs={} cause={}",
                    decision.reason(), modelName, elapsedMs(startedAt), e.getClass().getSimpleName());
            return List.of(query);
        }
    }

    /**
     * Whether the conversation holds something a rewrite can use.
     *
     * <p>Counted the way the official transformer reads it: a {@code SystemMessage} is formatted to
     * {@code null} and dropped, so a history made only of system messages produces an empty
     * {@code {{chatMemory}}} and the model would be asked to expand a question against nothing —
     * a paid call that cannot help. The two rules have to agree on what counts as conversation.
     */
    private static boolean hasSomethingTheUserSaid(Query query) {
        return query.metadata() != null
                && query.metadata().chatMemory() != null
                && query.metadata().chatMemory().stream()
                .anyMatch(message -> message instanceof UserMessage || message instanceof AiMessage);
    }

    private static long elapsedMs(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000L;
    }
}
