package br.com.systec.hive.knowledge.impl.service;

import br.com.systec.hive.knowledge.api.domain.KbChunk;
import br.com.systec.hive.knowledge.api.domain.RagDiagnostic;
import br.com.systec.hive.knowledge.api.domain.RagOutcome;
import br.com.systec.hive.knowledge.api.domain.RagQuerySettings;
import br.com.systec.hive.knowledge.api.domain.RagRetrievalResult;
import br.com.systec.hive.knowledge.api.exceptions.KbDimensionMismatchException;
import br.com.systec.hive.knowledge.api.port.KbEmbeddingPort;
import br.com.systec.hive.knowledge.api.port.KbRetrievalPort;
import br.com.systec.hive.knowledge.api.port.KbVectorStore;
import br.com.systec.hive.knowledge.api.port.RagDiagnosticRecorder;
import br.com.systec.hive.knowledge.impl.entity.KbDocumentEntity;
import br.com.systec.hive.knowledge.impl.entity.KnowledgeBaseEntity;
import br.com.systec.hive.knowledge.impl.repository.KbDocumentRepository;
import br.com.systec.hive.knowledge.impl.repository.KnowledgeBaseRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Service
public class KbRetrievalService implements KbRetrievalPort {

    private static final Logger log = LoggerFactory.getLogger(KbRetrievalService.class);

    public static final int DEFAULT_MAX_RESULTS = 5;
    private static final long RETRIEVAL_TIMEOUT_SECONDS = 2L;

    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final KbDocumentRepository documentRepository;
    private final KbVectorStore vectorStore;
    private final KbEmbeddingPort embeddingPort;
    private final java.util.concurrent.Executor retrievalExecutor;
    private final RagDiagnosticRecorder diagnosticRecorder;

    public KbRetrievalService(KnowledgeBaseRepository knowledgeBaseRepository,
                              KbDocumentRepository documentRepository,
                              KbVectorStore vectorStore,
                              KbEmbeddingPort embeddingPort,
                              @Qualifier("kbRetrievalExecutor") java.util.concurrent.Executor retrievalExecutor,
                              RagDiagnosticRecorder diagnosticRecorder) {
        this.knowledgeBaseRepository = knowledgeBaseRepository;
        this.documentRepository = documentRepository;
        this.vectorStore = vectorStore;
        this.embeddingPort = embeddingPort;
        this.retrievalExecutor = retrievalExecutor;
        this.diagnosticRecorder = diagnosticRecorder == null ? RagDiagnosticRecorder.noop() : diagnosticRecorder;
    }

    @Override
    public List<KbChunk> retrieve(UUID tenantId, List<UUID> kbIds, String query, int maxResults,
                                  RagQuerySettings settings) {
        return retrieveWithOutcome(tenantId, kbIds, query, maxResults, settings).chunks();
    }

    @Override
    public RagRetrievalResult retrieveWithOutcome(UUID tenantId, List<UUID> kbIds, String query,
                                                 int maxResults, RagQuerySettings settings) {
        long startedAt = System.nanoTime();
        if (kbIds == null || kbIds.isEmpty() || query == null || query.isBlank()) {
            return publish(tenantId, kbIds, RagOutcome.NO_LINK, List.of(), 0, null, startedAt, null,
                    "no knowledge base linked or empty query");
        }
        List<KnowledgeBaseEntity> kbs = knowledgeBaseRepository.findByTenantIdAndIdIn(tenantId, kbIds);
        if (kbs.isEmpty()) {
            // Distinguishes "the agent is linked to bases that do not belong to this tenant (or do
            // not exist)" from "the base is empty". Previously both returned List.of() in silence.
            return publish(tenantId, kbIds, RagOutcome.KB_NOT_FOUND_FOR_TENANT, List.of(), 0, null,
                    startedAt, null, "linked knowledge bases do not exist for this tenant");
        }
        int dimension = kbs.get(0).getDimension();
        for (KnowledgeBaseEntity kb : kbs) {
            if (kb.getDimension() != dimension) {
                int foundKbs = kbs.size();
                // Thrown, so there is no result to carry the observation: the line is the only record,
                // and the exception has to carry the same count for whoever builds the turn's line.
                record(tenantId, kbIds, RagOutcome.DIMENSION_MISMATCH, foundKbs, 0, null, startedAt, null,
                        "linked knowledge bases have different embedding dimensions");
                throw new KbDimensionMismatchException(foundKbs);
            }
        }
        String model = kbs.get(0).getEmbeddingModel();
        int limit = maxResults <= 0 ? DEFAULT_MAX_RESULTS : Math.min(maxResults, DEFAULT_MAX_RESULTS);
        double threshold = thresholdFor(model, settings);
        CompletableFuture<List<KbChunk>> future = CompletableFuture.supplyAsync(
                () -> doRetrieve(tenantId, kbIds, query, model, dimension, limit, threshold), retrievalExecutor);
        try {
            List<KbChunk> chunks = future.get(RETRIEVAL_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (chunks == null || chunks.isEmpty()) {
                return publish(tenantId, kbIds, RagOutcome.NO_CHUNK_ABOVE_THRESHOLD, List.of(), kbs.size(),
                        threshold, startedAt, null,
                        "vector search returned no chunk above the similarity threshold");
            }
            return publish(tenantId, kbIds, RagOutcome.OK, chunks, kbs.size(), threshold, startedAt, null, null);
        } catch (Exception e) {
            future.cancel(true);
            RagOutcome outcome = classify(e);
            RagRetrievalResult failed = publish(tenantId, kbIds, outcome, List.of(), kbs.size(), threshold,
                    startedAt, e,
                    outcome == RagOutcome.TIMEOUT
                            ? "retrieval exceeded " + RETRIEVAL_TIMEOUT_SECONDS + "s"
                            : "retrieval failed, falling back to no context");
            log.warn("kb retrieval fallback tenantId={} kbIds={} outcome={} cause={}",
                    tenantId, kbIds, outcome.wireName(), failed.cause());
            return failed;
        }
    }

    /**
     * The cut-off this search runs with: the caller's own number when it stated one, otherwise the
     * convention row of the base's model.
     *
     * <p>Resolved here, and not by the caller, because this is the only place that knows the model:
     * the scores of a base are only comparable inside the space its own model produces, so a number
     * chosen without knowing the model is a guess. The resolved value travels back on the result and
     * on the diagnostic, which is what lets a {@code no_chunk_above_threshold} be read.
     *
     * <p>The distinction between "stated one" and "did not" is carried by the type
     * ({@link RagQuerySettings.Exact} against {@link RagQuerySettings.ResolveFromKbConvention}) rather
     * than by reading a number and guessing from its sign — which is how a caller asking for a real
     * {@code 0} used to be answered with somebody else's cut-off. {@code Exact} refuses a value
     * outside {@code (0,1]} at construction, so nothing invalid can reach this method to be
     * "helpfully" corrected here.
     *
     * <p>Written as a {@code switch} over the sealed interface, and that is the point: a third way to
     * express a cut-off stops compiling here instead of falling through to the convention table and
     * quietly becoming somebody else's number.
     *
     * <p><b>A null is tolerated here on purpose</b>, and this is the one place where that is true —
     * {@link RagQuerySettings}, {@code RagContext}, {@code TurnRequest} and {@code TurnReply} all
     * <em>reject</em> null. The difference is what null means in each. For those three it is a defect:
     * nobody resolved a cut-off, or nobody resolved a context, and there is no honest reading of that.
     * Here, "nobody chose" is a <b>state</b> — it is the very ambiguity the sealed type exists to make
     * explicit, and it is the same reading the old {@code 0} sentinel carried. Rejecting it would force
     * a caller holding no opinion to build a policy object just to say nothing, and would break the
     * playground, which has no cut-off of its own to offer. If you are reading this while comparing it
     * to the null policy of the records: this is not an inconsistency to be tidied up, it is the rule
     * applied to the one case where the answer is known.
     */
    static double thresholdFor(String model, RagQuerySettings settings) {
        RagQuerySettings.Threshold threshold = settings == null ? null : settings.threshold();
        if (threshold == null) {
            // "Nobody said" is the only reading a null can have, and it is the reading it always had.
            threshold = new RagQuerySettings.ResolveFromKbConvention();
        }
        return switch (threshold) {
            case RagQuerySettings.Exact exact -> exact.value();
            case RagQuerySettings.ResolveFromKbConvention convention ->
                    EmbeddingTaskConvention.defaultThreshold(model);
        };
    }

    /**
     * A wall-clock expiry is {@code TIMEOUT}; everything else is {@code ERROR}. An
     * {@link ExecutionException} wrapping a timeout is still a timeout — the deadline was hit
     * inside the executor, not the {@code future.get} call.
     */
    static RagOutcome classify(Exception e) {
        if (e instanceof TimeoutException || e instanceof CancellationException) {
            return RagOutcome.TIMEOUT;
        }
        if (e instanceof InterruptedException) {
            return RagOutcome.TIMEOUT;
        }
        Throwable cause = e instanceof ExecutionException && e.getCause() != null ? e.getCause() : e;
        for (Throwable current = cause; current != null; current = current.getCause()) {
            if (current instanceof TimeoutException || current instanceof java.net.http.HttpTimeoutException
                    || current instanceof java.net.SocketTimeoutException) {
                return RagOutcome.TIMEOUT;
            }
        }
        return RagOutcome.ERROR;
    }

    /**
     * Emits the retrieval-level line and hands the same observation back to the caller, so the
     * result and the log can never describe two different retrievals.
     */
    private RagRetrievalResult publish(UUID tenantId, List<UUID> kbIds, RagOutcome outcome,
                                       List<KbChunk> chunks, int foundKbs, Double threshold,
                                       long startedAt, Throwable cause, String reason) {
        // A null here would publish a diagnostic whose chunk count is 0 for a retrieval that has
        // chunks, which is the one thing this record must never say.
        RagDiagnostic diagnostic = record(tenantId, kbIds, outcome, foundKbs,
                Objects.requireNonNull(chunks, "chunks").size(), threshold, startedAt, cause, reason);
        return RagRetrievalResult.of(outcome, chunks, diagnostic.elapsedMs(), threshold, foundKbs,
                reason, diagnostic.cause());
    }

    private RagDiagnostic record(UUID tenantId, List<UUID> kbIds, RagOutcome outcome, int foundKbs, int chunks,
                                 Double threshold, long startedAt, Throwable cause, String reason) {
        RagDiagnostic diagnostic = RagDiagnostic.builder(outcome, null, tenantId)
                .kbIds(kbIds)
                .foundKbs(foundKbs)
                .chunks(chunks)
                .threshold(threshold)
                .elapsedMs(elapsedMs(startedAt))
                .cause(cause)
                .reason(reason)
                .build();
        try {
            diagnosticRecorder.record(diagnostic);
        } catch (Exception e) {
            // A diagnostics sink must never break retrieval.
            log.warn("rag diagnostic recorder failed outcome={} tenantId={}", outcome.wireName(), tenantId, e);
        }
        return diagnostic;
    }

    private static long elapsedMs(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000L;
    }

    private List<KbChunk> doRetrieve(UUID tenantId, List<UUID> kbIds, String query,
                                      String model, int dimension, int limit, double threshold) {
        float[] vector = embeddingPort.embedQuery(tenantId, model, dimension, query);
        List<KbVectorStore.ScoredRow> rows =
                vectorStore.search(tenantId, kbIds, vector, dimension, limit, threshold);
        Set<UUID> docIds = new HashSet<>();
        for (KbVectorStore.ScoredRow row : rows) {
            docIds.add(row.docId());
        }
        Map<UUID, String> names = new HashMap<>();
        if (!docIds.isEmpty()) {
            for (KbDocumentEntity doc : documentRepository.findByTenantIdAndIdIn(tenantId, docIds)) {
                names.put(doc.getId(), doc.getFileName());
            }
        }
        List<KbChunk> chunks = new ArrayList<>();
        for (KbVectorStore.ScoredRow row : rows) {
            chunks.add(row.toChunk(names.getOrDefault(row.docId(), "document")));
        }
        return chunks;
    }
}
