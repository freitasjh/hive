package br.com.systec.hive.agent.impl.rag;

import br.com.systec.hive.agent.api.domain.RagContext;
import br.com.systec.hive.agent.impl.service.CredentialResolver;
import br.com.systec.hive.agent.impl.util.ProviderMessages;
import br.com.systec.hive.knowledge.api.domain.KbCitation;
import br.com.systec.hive.knowledge.api.domain.RagDiagnostic;
import br.com.systec.hive.knowledge.api.domain.RagOutcome;
import br.com.systec.hive.knowledge.api.domain.RagQuerySettings;
import br.com.systec.hive.knowledge.api.domain.RagRetrievalResult;
import br.com.systec.hive.knowledge.api.exceptions.KbDimensionMismatchException;
import br.com.systec.hive.knowledge.api.port.KbAgentLinkPort;
import br.com.systec.hive.knowledge.api.port.KbRetrievalPort;
import br.com.systec.hive.knowledge.api.port.RagDiagnosticRecorder;
import br.com.systec.hive.provider.api.AiProvider;
import br.com.systec.hive.provider.api.LlmClientFactory;
import br.com.systec.hive.provider.api.VendorCatalog;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.rag.AugmentationRequest;
import dev.langchain4j.rag.AugmentationResult;
import dev.langchain4j.rag.RetrievalAugmentor;
import dev.langchain4j.rag.query.Metadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

/**
 * Resolves the knowledge context of one chat turn and emits <b>exactly one</b> diagnostic for it,
 * whatever the path.
 *
 * <h3>Why this is its own class</h3>
 *
 * <p>Before this, {@code resolveRagContext} was a private method on the chat service, and the reason
 * {@code ChatServiceRagPipelineTest} existed as a class was that it could only reach the RAG
 * behaviour by constructing the entire service — eleven collaborators, a rate limiter, a session
 * store, a message store. Every assertion about retrieval therefore paid for a full chat turn, and
 * a test about a link lookup failure had to stub a provider to observe it.
 *
 * <p>Now the seam is a collaborator with one method, and each of the paths below is reachable
 * without a provider, a session or a message store.
 *
 * <h3>Why exactly one diagnostic, always</h3>
 *
 * <p>Before, {@code kbIds} empty, "base not in this tenant" and "nothing above the threshold" all
 * returned an empty context in silence, so a grep on the backend could not tell them apart — the only
 * visible symptom was the model claiming it had no context. Every return below goes through
 * {@link #emitRag}.
 *
 * <p>Two lines are emitted per turn that reaches the retrieval, and that is not redundancy: the
 * retrieval service also serves the playground, which has no agent, and it cannot report an
 * {@code agentId} it never sees. The rule that keeps the two honest is that the turn's line is
 * <b>derived</b> from the retrieval's result — see {@link RagRetrievalResult#toTurnDiagnostic} — and
 * adds only the two facts the retrieval is in no position to know: which agent asked, and how big the
 * context block was. The latency is the one field that legitimately differs: it is measured at each
 * level. This turn's number is the whole RAG resolution, so when the query compressor fires it is
 * included — which is the point, since that is the cost the operator is trying to account for.
 *
 * <h3>What the context is assembled from</h3>
 *
 * <p>The official pipeline ({@code DefaultRetrievalAugmentor}: transformer → router → retriever →
 * aggregator → injector) rather than a hand-built string. What leaves this method is the same four
 * things as before — was context used, which documents to cite, which outcome, which cut-off —
 * because the report is not what this class is about.
 */
@Component
public class RagTurnResolver {

    private static final Logger log = LoggerFactory.getLogger(RagTurnResolver.class);

    /**
     * How many chunks a chat turn may take.
     *
     * <p>Public so a test in another package can assert the cap this class actually searches with,
     * instead of repeating the number and drifting from it — the same number is asserted from
     * {@code ChatServiceImplTest}, which is not in this package.
     */
    public static final int RAG_MAX_RESULTS = 5;

    /**
     * Output budget for the query compressor, when one is configured.
     *
     * <p>The rewrite is a single sentence. The budget is not tight because this project has already
     * shipped the failure a tight budget causes: a reasoning model given a small {@code maxTokens}
     * spends all of it on reasoning and returns no content, which the turn reports as
     * {@code empty_model_response}. 256 leaves room for that reasoning and still returns the sentence.
     */
    static final int COMPRESSION_MAX_TOKENS = 256;

    private final KbRetrievalPort kbRetrieval;
    private final KbAgentLinkPort kbLinkPort;
    private final RagDiagnosticRecorder ragRecorder;
    private final LlmClientFactory<ChatModel> llmClientFactory;
    private final RagAugmentorFactory augmentorFactory;

    /**
     * Model allowed to rewrite a follow-up question, or {@code null}/blank to keep the stage off.
     *
     * <p>Off by default on purpose: enabling it adds one LLM call to the turns that look like
     * continuations, and an unmeasured extra call is exactly what the latency baseline forbids. It is
     * also never the agent's own model — see {@link #compressionModel}.
     */
    private final String queryCompressionModel;

    /**
     * Length at or below which a question counts as elliptical for the query compressor.
     *
     * <p>Configurable because it was measured on a hand-labelled set, not on production traffic: see
     * {@link FollowUpQueryHeuristic} for the numbers and for what a wrong value costs.
     */
    private final int ellipticalQuestionLimit;

    public RagTurnResolver(KbRetrievalPort kbRetrieval,
                           KbAgentLinkPort kbLinkPort,
                           RagDiagnosticRecorder ragRecorder,
                           LlmClientFactory<ChatModel> llmClientFactory,
                           @Value("${hive.rag.query-compression-model:}") String queryCompressionModel,
                           @Value("${hive.rag.elliptical-limit:"
                                   + FollowUpQueryHeuristic.ELLIPTICAL_LIMIT + "}") int ellipticalQuestionLimit) {
        this.kbRetrieval = kbRetrieval;
        this.kbLinkPort = kbLinkPort;
        this.ragRecorder = ragRecorder == null ? RagDiagnosticRecorder.noop() : ragRecorder;
        this.llmClientFactory = llmClientFactory;
        this.augmentorFactory = new RagAugmentorFactory();
        this.queryCompressionModel = queryCompressionModel;
        this.ellipticalQuestionLimit = ellipticalQuestionLimit;
    }

    /**
     * What one turn needs in order to resolve its context.
     *
     * <p>A record rather than eight positional arguments, for the same reason the turn request is
     * one: this method's parameters are two {@code UUID}s, two {@code String}s and two provider-typed
     * values, and nothing at the call site says which {@code UUID} was the agent.
     *
     * @param tenantId   tenant every step is scoped to
     * @param agentId    agent being answered, reported on the diagnostic
     * @param sessionId  session, carried into the augmentor's metadata
     * @param question   the turn's own message, as the user wrote it
     * @param provider   effective vendor, for the query compressor's client
     * @param agentModel the agent's own model, which the compressor must not be
     * @param credential resolved credential, for the compressor's client
     * @param conversation the whole conversation as saved, in the provider's message type. The turn's
     *                    own question is in it — this class is what decides what the compressor is
     *                    allowed to read, see {@link #conversationBefore}
     */
    public record RagRequest(UUID tenantId, UUID agentId, UUID sessionId, String question,
                             AiProvider provider, String agentModel,
                             CredentialResolver.ResolvedCredential credential,
                             List<dev.langchain4j.data.message.ChatMessage> conversation) {

        public RagRequest {
            conversation = conversation == null ? List.of() : List.copyOf(conversation);
        }
    }

    public RagContext resolve(RagRequest request) {
        long startedAt = System.nanoTime();
        UUID tenantId = request.tenantId();
        UUID agentId = request.agentId();
        if (kbRetrieval == null || kbLinkPort == null) {
            emitRag(RagDiagnostic.builder(RagOutcome.RAG_DISABLED, agentId, tenantId)
                    .reason("knowledge module not wired")
                    .build(), startedAt);
            return RagContext.empty(RagOutcome.RAG_DISABLED);
        }
        String question = request.question();
        if (question == null || question.isBlank()) {
            emitRag(RagDiagnostic.builder(RagOutcome.NO_LINK, agentId, tenantId)
                    .reason("empty question")
                    .build(), startedAt);
            return RagContext.empty(RagOutcome.NO_LINK);
        }
        List<UUID> kbIds;
        try {
            kbIds = kbLinkPort.linkedKbIds(agentId, tenantId);
        } catch (Exception e) {
            emitRag(RagDiagnostic.builder(RagOutcome.ERROR, agentId, tenantId)
                    .reason("agent->knowledge base link lookup failed")
                    .cause(e)
                    .build(), startedAt);
            return RagContext.empty(RagOutcome.ERROR);
        }
        if (kbIds == null || kbIds.isEmpty()) {
            emitRag(RagDiagnostic.builder(RagOutcome.NO_LINK, agentId, tenantId)
                    .kbIds(List.of())
                    .reason("agent has no knowledge base linked")
                    .build(), startedAt);
            return RagContext.empty(RagOutcome.NO_LINK);
        }

        // The retrieval's own report is published by the retriever as it runs, because the official
        // interface cannot carry it. Reading it from here — rather than rebuilding it from the contents
        // the augmentor returns — is what keeps the outcome, the counts and the cut-off describing the
        // search that actually happened.
        AtomicReference<RagRetrievalResult> published = new AtomicReference<>();
        Function<dev.langchain4j.rag.query.Query, Integer> maxResults = query -> RAG_MAX_RESULTS;
        // The cut-off is stated as a policy, not as a number: "you do not get to choose" is a decision,
        // and expressing it as 0 made it indistinguishable from a decision to filter nothing.
        Function<dev.langchain4j.rag.query.Query, RagQuerySettings> settings =
                query -> RagQuerySettings.fromKbConvention();
        KnowledgeBaseContentRetriever retriever = KnowledgeBaseContentRetriever.of(
                kbRetrieval, tenantId, kbIds, maxResults, settings, published::set);
        RetrievalAugmentor augmentor = augmentorFactory.create(retriever,
                queryTransformer(request));

        AugmentationResult augmentation;
        try {
            dev.langchain4j.data.message.UserMessage userMessage =
                    new dev.langchain4j.data.message.UserMessage(question);
            augmentation = augmentor.augment(new AugmentationRequest(userMessage,
                    Metadata.from(userMessage, request.sessionId(),
                            conversationBefore(request.conversation(), question))));
        } catch (KbDimensionMismatchException e) {
            // The thrown outcome never produces a result, so this line is built here. It has to carry
            // what the retrieval's own line carried — including how many bases were found, which is
            // on the exception precisely so a mismatch between two existing bases is not reported as
            // a lookup that found nothing. Reaches this catch unwrapped because the augmentor routes
            // one query to one retriever on the calling thread; a change to the routing (several
            // retrievers) would wrap it in a CompletionException and this line would stop firing,
            // which is why the IT asserts the outcome rather than the absence of an exception.
            emitRag(RagDiagnostic.builder(RagOutcome.DIMENSION_MISMATCH, agentId, tenantId)
                    .kbIds(kbIds)
                    .foundKbs(e.foundKbs())
                    .reason("linked knowledge bases have different embedding dimensions")
                    .cause(e)
                    .build(), startedAt);
            throw e;
        } catch (Exception e) {
            emitRag(RagDiagnostic.builder(RagOutcome.ERROR, agentId, tenantId)
                    .kbIds(kbIds)
                    .reason("knowledge retrieval failed")
                    .cause(e)
                    .build(), startedAt);
            return RagContext.empty(RagOutcome.ERROR);
        }

        RagRetrievalResult retrieval = published.get();
        if (retrieval == null) {
            emitRag(RagDiagnostic.builder(RagOutcome.ERROR, agentId, tenantId)
                    .kbIds(kbIds)
                    .reason("retrieval port returned no result")
                    .build(), startedAt);
            return RagContext.empty(RagOutcome.ERROR);
        }
        // Both conditions, not one. A retrieval can report chunks and still inject nothing — a chunk
        // with no text cannot become a Content at all — and the augmentor answers an empty list by
        // returning the message it was given, which here is the question itself. Without this guard the
        // turn would add the question to the prompt a second time, claim ragUsed=true with no knowledge
        // behind it, and report the length of the question as the size of the context block.
        if (!retrieval.hasChunks() || augmentation.contents().isEmpty()) {
            emitRag(retrieval.toTurnDiagnostic(agentId, tenantId, kbIds, RagDiagnostic.NO_SYSTEM_BLOCK),
                    startedAt);
            return RagContext.empty(retrieval.outcome(), retrieval.threshold());
        }
        dev.langchain4j.data.message.ChatMessage injected = augmentation.chatMessage();
        String injectedText = ProviderMessages.textOf(injected);
        int injectedChars = injectedText == null ? 0 : injectedText.length();
        emitRag(retrieval.toTurnDiagnostic(agentId, tenantId, kbIds, injectedChars), startedAt);
        // Citations come from what was injected, not from what the search returned: a citation is a
        // claim to the user about where the answer came from, and it may not name a chunk the model
        // never saw. The two differ whenever a chunk is dropped for having no text.
        return RagContext.of(injected, injectedChars, citationsOf(augmentation.contents()),
                retrieval.outcome(), retrieval.threshold());
    }

    /**
     * The citations of the contents that were actually injected.
     *
     * <p>A content this pipeline produced is a {@link RetrievedContent}, and the identity is what makes
     * the mapping exact — the same object the model was given. A content of any other type is logged
     * and skipped rather than cited: a citation the client shows as a source has to point at something
     * in the prompt, and a content this class cannot attribute is not something it can claim.
     */
    private static List<KbCitation> citationsOf(List<dev.langchain4j.rag.content.Content> contents) {
        List<KbCitation> citations = new ArrayList<>(contents.size());
        for (dev.langchain4j.rag.content.Content content : contents) {
            if (content instanceof RetrievedContent retrieved) {
                KbCitation citation = new KbCitation();
                citation.setDocId(retrieved.docId());
                citation.setDocName(retrieved.docName());
                citation.setChunkId(retrieved.chunkId());
                citation.setScore(retrieved.score());
                citations.add(citation);
            } else {
                log.warn("rag content without a citation source type={} — the model saw it and the "
                        + "client cannot be told where it came from", content.getClass().getName());
            }
        }
        return List.copyOf(citations);
    }

    /**
     * The conversation the compressor is allowed to read: everything before the question being asked.
     *
     * <p>The turn's own message is already in the saved history — it is saved before the RAG
     * resolution — and the compressor's template takes the conversation and the new query as two
     * separate inputs. Leaving the current question in both means the model reads it twice and pays
     * for it twice, in the one stage whose cost this project is trying to keep down. Stripping it is
     * therefore this class's job, not the caller's: a caller that forgot would be silently billed
     * twice per follow-up, and nothing would say so.
     */
    private static List<dev.langchain4j.data.message.ChatMessage> conversationBefore(
            List<dev.langchain4j.data.message.ChatMessage> conversation, String question) {
        List<dev.langchain4j.data.message.ChatMessage> previous = new ArrayList<>(conversation);
        while (!previous.isEmpty()) {
            dev.langchain4j.data.message.ChatMessage last = previous.get(previous.size() - 1);
            if (last instanceof dev.langchain4j.data.message.UserMessage user
                    && user.singleText().equals(question)) {
                previous.remove(previous.size() - 1);
                continue;
            }
            break;
        }
        return List.copyOf(previous);
    }

    /**
     * The query compressor of this turn — always built, including when the stage is off.
     *
     * <p>Building it costs one object; skipping it would cost the operator the one thing they need
     * before switching the feature on, which is how often it <em>would</em> fire. With the stage off
     * the transformer still runs the heuristic and logs the verdict at {@code DEBUG}, so the fire rate
     * is observable without a single extra LLM call. "Off" is expressed by the supplier being absent,
     * and the supplier is only asked for after the heuristic has fired — a turn that needs no rewrite
     * never builds a model client.
     */
    private dev.langchain4j.rag.query.transformer.QueryTransformer queryTransformer(RagRequest request) {
        String model = queryCompressionModel == null || queryCompressionModel.isBlank()
                ? null
                : queryCompressionModel;
        return new ConditionalCompressingQueryTransformer(
                model == null ? null : () -> compressionModel(request), model, ellipticalQuestionLimit);
    }

    /**
     * The cheap model the rewrite may be spent on, or {@code null} when it must not be spent at all.
     *
     * <p>Three refusals, each for a reason an operator can act on:
     * <ul>
     *   <li><b>same model as the agent</b> — the rewrite would run on the reasoning model the turn is
     *       already paying for, which is the worst case: whole conversation in, ~40 tokens out, plus
     *       the risk that a small {@code maxTokens} makes it return nothing (this project's
     *       {@code empty_model_response}). Naming a different model is the fix.</li>
     *   <li><b>not in the vendor's catalog</b> — the call would fail at request time; better to search
     *       with the original words than to fail the turn.</li>
     *   <li><b>no credential for the provider</b> — already resolved for the agent, so a failure here
     *       is a configuration problem worth a log line, not an exception.</li>
     * </ul>
     */
    private ChatModel compressionModel(RagRequest request) {
        AiProvider provider = request.provider();
        if (request.agentModel() != null && request.agentModel().equals(queryCompressionModel)) {
            log.warn("query compression disabled reason=same_model_as_agent model={} provider={}",
                    queryCompressionModel, provider);
            return null;
        }
        if (!VendorCatalog.supports(provider, queryCompressionModel)) {
            log.warn("query compression disabled reason=model_not_in_catalog model={} provider={}",
                    queryCompressionModel, provider);
            return null;
        }
        try {
            return llmClientFactory.createClient(provider, queryCompressionModel, 0.0,
                    COMPRESSION_MAX_TOKENS, null, request.credential().apiKey(), request.credential().baseUrl());
        } catch (Exception e) {
            log.warn("query compression disabled reason=client_unavailable model={} provider={} cause={}",
                    queryCompressionModel, provider, e.getClass().getSimpleName());
            return null;
        }
    }

    /**
     * Stamps the turn's own wall time on the line and hands it to the sink.
     *
     * <p>Unconditional, and that is the point: the retrieval's latency is already on its own line, so
     * a turn line reporting it back — or reporting {@code 0} because the search was fast enough to
     * measure as zero — is a number that describes neither scope.
     */
    private void emitRag(RagDiagnostic diagnostic, long startedAt) {
        try {
            ragRecorder.record(diagnostic.withElapsedMs(elapsedMs(startedAt)));
        } catch (Exception e) {
            log.warn("rag diagnostic recorder failed agentId={}", diagnostic.agentId(), e);
        }
    }

    private static long elapsedMs(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000L;
    }
}
