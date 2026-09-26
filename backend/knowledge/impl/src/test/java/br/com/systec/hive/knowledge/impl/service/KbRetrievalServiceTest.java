package br.com.systec.hive.knowledge.impl.service;

import br.com.systec.hive.knowledge.api.domain.KbChunk;
import br.com.systec.hive.knowledge.api.domain.RagDiagnostic;
import br.com.systec.hive.knowledge.api.domain.RagOutcome;
import br.com.systec.hive.knowledge.api.domain.RagRetrievalResult;
import br.com.systec.hive.knowledge.api.exceptions.KbDimensionMismatchException;
import br.com.systec.hive.knowledge.api.port.KbEmbeddingPort;
import br.com.systec.hive.knowledge.api.port.RagDiagnosticRecorder;
import br.com.systec.hive.knowledge.impl.entity.KnowledgeBaseEntity;
import br.com.systec.hive.knowledge.impl.fake.PrefixAwareEmbeddingModel;
import br.com.systec.hive.knowledge.impl.repository.KbDocumentRepository;
import br.com.systec.hive.knowledge.impl.repository.KnowledgeBaseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KbRetrievalServiceTest {

    @Mock
    private KnowledgeBaseRepository knowledgeBaseRepository;

    @Mock
    private KbDocumentRepository documentRepository;

    @Mock
    private KbEmbeddingStore embeddingStore;

    @Mock
    private KbEmbeddingPort embeddingPort;

    private RecordingRecorder recorder;
    private Executor sameThread;
    private UUID tenantId;
    private UUID kbId;

    @BeforeEach
    void setUp() {
        recorder = new RecordingRecorder();
        sameThread = Runnable::run;
        tenantId = UUID.randomUUID();
        kbId = UUID.randomUUID();
    }

    private KbRetrievalService service(Executor executor) {
        return new KbRetrievalService(knowledgeBaseRepository, documentRepository, embeddingStore, embeddingPort,
                executor, recorder);
    }

    private KnowledgeBaseEntity kb(int dimension) {
        return kb("nomic-embed-text", dimension);
    }

    private KnowledgeBaseEntity kb(String model, int dimension) {
        KnowledgeBaseEntity entity = new KnowledgeBaseEntity();
        entity.setId(kbId);
        entity.setTenantId(tenantId);
        entity.setName("teste");
        entity.setEmbeddingModel(model);
        entity.setDimension(dimension);
        return entity;
    }

    // ------------------------------------------------- C2: the previously silent empty returns

    @Test
    void when_retrieveWithLinkedBaseMissingForTenant_then_reportsKbNotFoundWithTenantAndIds() {
        when(knowledgeBaseRepository.findByTenantIdAndIdIn(eq(tenantId), anyList())).thenReturn(List.of());

        RagRetrievalResult result = service(sameThread).retrieveWithOutcome(tenantId, List.of(kbId), "q", 5, 0.7);

        assertThat(result.outcome()).isEqualTo(RagOutcome.KB_NOT_FOUND_FOR_TENANT);
        assertThat(result.chunks()).isEmpty();
        assertThat(recorder.single().outcome()).isEqualTo(RagOutcome.KB_NOT_FOUND_FOR_TENANT);
        assertThat(recorder.single().tenantId()).isEqualTo(tenantId);
        assertThat(recorder.single().kbIds()).containsExactly(kbId);
        assertThat(recorder.single().reason()).isNotBlank();
    }

    @Test
    void when_retrieveWithoutLinkedBases_then_reportsNoLink() {
        RagRetrievalResult result = service(sameThread).retrieveWithOutcome(tenantId, List.of(), "q", 5, 0.7);

        assertThat(result.outcome()).isEqualTo(RagOutcome.NO_LINK);
        assertThat(recorder.single().outcome()).isEqualTo(RagOutcome.NO_LINK);
    }

    @Test
    void when_retrieveWithBlankQuery_then_reportsNoLink() {
        RagRetrievalResult result = service(sameThread).retrieveWithOutcome(tenantId, List.of(kbId), "  ", 5, 0.7);

        assertThat(result.outcome()).isEqualTo(RagOutcome.NO_LINK);
    }

    @Test
    void when_retrieveFindsNoChunkAboveThreshold_then_reportsThreshold() {
        when(knowledgeBaseRepository.findByTenantIdAndIdIn(eq(tenantId), anyList())).thenReturn(List.of(kb(768)));
        when(embeddingPort.embedQuery(eq(tenantId), any(), anyInt(), any())).thenReturn(new float[]{0.1f, 0.2f});
        when(embeddingStore.search(eq(tenantId), anyList(), any(), anyInt(), anyInt(), anyDouble()))
                .thenReturn(List.of());

        RagRetrievalResult result = service(sameThread).retrieveWithOutcome(tenantId, List.of(kbId), "q", 5, 0.7);

        assertThat(result.outcome()).isEqualTo(RagOutcome.NO_CHUNK_ABOVE_THRESHOLD);
        // the number reported is the number that ran, not the number requested
        assertThat(result.threshold()).isEqualTo(0.7);
        RagDiagnostic diagnostic = recorder.single();
        assertThat(diagnostic.chunks()).isZero();
        assertThat(diagnostic.threshold()).isEqualTo(0.7);
        assertThat(diagnostic.foundKbs()).isEqualTo(1);
    }

    /**
     * A {@code no_chunk_above_threshold} is unexplainable without the cut-off that produced it, so
     * the effective value travels with the result — including when the caller asked for nothing and
     * the service resolved one from the base's own model.
     */
    @Test
    void when_callerSendsNoScore_then_theEffectiveDefaultIsCarriedOnTheResult() {
        when(knowledgeBaseRepository.findByTenantIdAndIdIn(eq(tenantId), anyList())).thenReturn(List.of(kb(768)));
        when(embeddingPort.embedQuery(eq(tenantId), any(), anyInt(), any())).thenReturn(new float[]{0.1f});
        when(embeddingStore.search(eq(tenantId), anyList(), any(), anyInt(), anyInt(), anyDouble()))
                .thenReturn(List.of());

        // 0 means "not specified" — and the base here is nomic, so the resolved value is 0.60
        RagRetrievalResult result = service(sameThread).retrieveWithOutcome(tenantId, List.of(kbId), "q", 5, 0);

        assertThat(result.threshold()).isEqualTo(0.60);
        assertThat(recorder.single().threshold()).isEqualTo(0.60);
    }

    @Test
    void when_retrieveNeverResolvesACutOff_then_noThresholdIsInvented() {
        when(knowledgeBaseRepository.findByTenantIdAndIdIn(eq(tenantId), anyList())).thenReturn(List.of());

        RagRetrievalResult result = service(sameThread).retrieveWithOutcome(tenantId, List.of(kbId), "q", 5, 0.7);

        assertThat(result.threshold())
                .as("nothing was searched, so there is no cut-off to report")
                .isNull();
        assertThat(recorder.single().threshold()).isNull();
    }

    // ------------------------------------------------- the cut-off belongs to the model's row

    /**
     * The rule itself: the caller may ask for a number, and anything else is decided by the model
     * the base is indexed with — not by a constant that cannot know it.
     */
    @Test
    void when_theCutOffIsResolved_then_theModelsRowDecidesAndAnExplicitRequestWins() {
        assertThat(KbRetrievalService.thresholdFor("nomic-embed-text", 0))
                .as("measured on a real nomic base: 0.6219 relevant, 0.5972 not")
                .isEqualTo(0.60);
        assertThat(KbRetrievalService.thresholdFor("nomic-embed-text:v1.5", 0)).isEqualTo(0.60);
        assertThat(KbRetrievalService.thresholdFor("text-embedding-3-small", 0))
                .as("behaviour it has always had")
                .isEqualTo(0.70);
        assertThat(KbRetrievalService.thresholdFor("mxbai-embed-large", 0))
                .as("unmeasured family: the conservative value")
                .isEqualTo(0.70);
        assertThat(KbRetrievalService.thresholdFor(null, 0)).isEqualTo(0.70);

        // the playground forces a cut-off, and forcing one is allowed
        assertThat(KbRetrievalService.thresholdFor("nomic-embed-text", 0.55)).isEqualTo(0.55);
        assertThat(KbRetrievalService.thresholdFor("text-embedding-3-small", 0.55)).isEqualTo(0.55);
        // a negative score is as unset as a zero: the port promises "anything <= 0" and this is where
        // that promise is kept. A negative cut-off is not a thing a similarity can take, so treating
        // it as "resolve from the model" is the only reading that cannot silently return nothing.
        assertThat(KbRetrievalService.thresholdFor("nomic-embed-text", -1d))
                .as("the port documents <= 0 as unset")
                .isEqualTo(0.60);
        assertThat(KbRetrievalService.thresholdFor("text-embedding-3-small", -0.5d)).isEqualTo(0.70);
    }

    @Test
    void when_anOpenAiBaseIsSearchedWithoutAnExplicitCutOff_then_theHistoricalOneRuns() {
        when(knowledgeBaseRepository.findByTenantIdAndIdIn(eq(tenantId), anyList()))
                .thenReturn(List.of(kb("text-embedding-3-small", 1536)));
        when(embeddingPort.embedQuery(eq(tenantId), any(), anyInt(), any())).thenReturn(new float[]{0.1f});
        when(embeddingStore.search(eq(tenantId), anyList(), any(), anyInt(), anyInt(), anyDouble()))
                .thenReturn(List.of());

        RagRetrievalResult result = service(sameThread).retrieveWithOutcome(tenantId, List.of(kbId), "q", 5, 0);

        assertThat(result.threshold()).isEqualTo(0.70);
    }

    @Test
    void when_theCallerForcesACutOff_then_itIsTheOneThatRuns() {
        when(knowledgeBaseRepository.findByTenantIdAndIdIn(eq(tenantId), anyList())).thenReturn(List.of(kb(768)));
        when(embeddingPort.embedQuery(eq(tenantId), any(), anyInt(), any())).thenReturn(new float[]{0.1f});
        when(embeddingStore.search(eq(tenantId), anyList(), any(), anyInt(), anyInt(), anyDouble()))
                .thenReturn(List.of());

        RagRetrievalResult result = service(sameThread).retrieveWithOutcome(tenantId, List.of(kbId), "q", 5, 0.55);

        assertThat(result.threshold()).isEqualTo(0.55);
        assertThat(recorder.single().threshold()).isEqualTo(0.55);
    }

    /**
     * The facts the retrieval line reports have to reach whoever asks the retrieval, or the caller
     * ends up reporting its own version of them. This is the failure that sent the previous
     * investigation to the wrong layer: a {@code no_chunk_above_threshold} whose agent-level line
     * claimed no base had been found.
     */
    @Test
    void when_theSearchFindsNothing_then_theResultCarriesTheCountsAndTheReasonTheLineHad() {
        when(knowledgeBaseRepository.findByTenantIdAndIdIn(eq(tenantId), anyList())).thenReturn(List.of(kb(768)));
        when(embeddingPort.embedQuery(eq(tenantId), any(), anyInt(), any())).thenReturn(new float[]{0.1f});
        when(embeddingStore.search(eq(tenantId), anyList(), any(), anyInt(), anyInt(), anyDouble()))
                .thenReturn(List.of());

        RagRetrievalResult result = service(sameThread).retrieveWithOutcome(tenantId, List.of(kbId), "q", 5, 0);
        RagDiagnostic line = recorder.single();

        assertThat(result.foundKbs()).isEqualTo(line.foundKbs()).isEqualTo(1);
        assertThat(result.reason()).isEqualTo(line.reason());
        assertThat(result.cause()).isEqualTo(line.cause()).isNull();
        assertThat(result.elapsedMs()).isEqualTo(line.elapsedMs());
    }

    @Test
    void when_theSearchFails_then_theCauseReachesTheResultWithoutTheMessage() {
        when(knowledgeBaseRepository.findByTenantIdAndIdIn(eq(tenantId), anyList())).thenReturn(List.of(kb(768)));
        when(embeddingPort.embedQuery(eq(tenantId), any(), anyInt(), any()))
                .thenThrow(new IllegalStateException("embed failed for query=[PERGUNTA SIGILOSA]"));

        RagRetrievalResult result = service(sameThread).retrieveWithOutcome(tenantId, List.of(kbId), "q", 5, 0);

        assertThat(result.cause()).isEqualTo("IllegalStateException:unexpected");
        assertThat(String.valueOf(result)).doesNotContain("PERGUNTA SIGILOSA");
    }

    @Test
    void when_theBaseIsNotInTheTenant_then_theResultSaysSoInsteadOfLeavingItEmpty() {
        when(knowledgeBaseRepository.findByTenantIdAndIdIn(eq(tenantId), anyList())).thenReturn(List.of());

        RagRetrievalResult result = service(sameThread).retrieveWithOutcome(tenantId, List.of(kbId), "q", 5, 0);
        RagDiagnostic line = recorder.single();

        assertThat(result.foundKbs()).isZero().isEqualTo(line.foundKbs());
        assertThat(result.reason()).isEqualTo(line.reason());
    }

    // ------------------------------------------------- the query that reaches the provider

    @Test
    void when_retrieve_withPrefixRequiringModel_then_providerGetsTheQueryPrefixed() {
        PrefixAwareEmbeddingModel model = new PrefixAwareEmbeddingModel("nomic-embed-text", 768);
        KbRetrievalService service = serviceWithProvider(model);
        when(knowledgeBaseRepository.findByTenantIdAndIdIn(eq(tenantId), anyList())).thenReturn(List.of(kb(768)));
        when(embeddingStore.search(eq(tenantId), anyList(), any(), anyInt(), anyInt(), anyDouble()))
                .thenReturn(List.of());

        service.retrieveWithOutcome(tenantId, List.of(kbId), "como reinicio a busca?", 5, 0.7);

        assertThat(model.seenTexts())
                .as("a search is one query, and it is a query")
                .containsExactly(PrefixAwareEmbeddingModel.QUERY_PREFIX + "como reinicio a busca?");
    }

    @Test
    void when_retrieve_withOpenAiModel_then_providerGetsTheRawQuery() {
        PrefixAwareEmbeddingModel model = new PrefixAwareEmbeddingModel("text-embedding-3-small", 1536);
        KbRetrievalService service = serviceWithProvider(model);
        when(knowledgeBaseRepository.findByTenantIdAndIdIn(eq(tenantId), anyList()))
                .thenReturn(List.of(kb("text-embedding-3-small", 1536)));
        when(embeddingStore.search(eq(tenantId), anyList(), any(), anyInt(), anyInt(), anyDouble()))
                .thenReturn(List.of());

        service.retrieveWithOutcome(tenantId, List.of(kbId), "como reinicio a busca?", 5, 0.7);

        assertThat(model.seenTexts()).containsExactly("como reinicio a busca?");
    }

    /** The reachable version of the double-prefix bug: the question is user input. */
    @Test
    void when_theUserTypesThePrefixItself_then_theProviderStillSeesItOnce() {
        PrefixAwareEmbeddingModel model = new PrefixAwareEmbeddingModel("nomic-embed-text", 768);
        KbRetrievalService service = serviceWithProvider(model);
        when(knowledgeBaseRepository.findByTenantIdAndIdIn(eq(tenantId), anyList())).thenReturn(List.of(kb(768)));
        when(embeddingStore.search(eq(tenantId), anyList(), any(), anyInt(), anyInt(), anyDouble()))
                .thenReturn(List.of());

        service.retrieveWithOutcome(tenantId, List.of(kbId), "search_query: como reinicio a busca?", 5, 0.7);

        assertThat(model.seenTexts()).containsExactly("search_query: como reinicio a busca?");
    }

    /** Same service, but the embedding goes through the real convention instead of a mock. */
    private KbRetrievalService serviceWithProvider(PrefixAwareEmbeddingModel model) {
        KbEmbeddingModelFactory factory = mock(KbEmbeddingModelFactory.class);
        when(factory.create(any(), any())).thenReturn(model);
        return new KbRetrievalService(knowledgeBaseRepository, documentRepository, embeddingStore,
                new KbEmbeddingService(factory), sameThread, recorder);
    }

    @Test
    void when_retrieveSucceeds_then_reportsChunkCountAndLatency() {
        when(knowledgeBaseRepository.findByTenantIdAndIdIn(eq(tenantId), anyList())).thenReturn(List.of(kb(768)));
        when(embeddingPort.embedQuery(eq(tenantId), any(), anyInt(), any())).thenReturn(new float[]{0.1f, 0.2f});
        KbEmbeddingStore.ScoredRow row = new KbEmbeddingStore.ScoredRow();
        row.docId = UUID.randomUUID();
        row.chunkId = UUID.randomUUID();
        row.text = "confidential body";
        row.score = 0.93;
        when(embeddingStore.search(eq(tenantId), anyList(), any(), anyInt(), anyInt(), anyDouble()))
                .thenReturn(List.of(row));
        when(documentRepository.findByTenantIdAndIdIn(eq(tenantId), org.mockito.ArgumentMatchers.<java.util.Collection<UUID>>any())).thenReturn(List.of());

        RagRetrievalResult result = service(sameThread).retrieveWithOutcome(tenantId, List.of(kbId), "q", 5, 0.7);

        assertThat(result.outcome()).isEqualTo(RagOutcome.OK);
        assertThat(result.chunkCount()).isEqualTo(1);
        RagDiagnostic diagnostic = recorder.single();
        assertThat(diagnostic.outcome()).isEqualTo(RagOutcome.OK);
        assertThat(diagnostic.chunks()).isEqualTo(1);
        assertThat(diagnostic.threshold()).isEqualTo(0.7);
        assertThat(diagnostic.elapsedMs()).isNotNegative();
    }

    @Test
    void when_recorderFails_then_retrievalStillReturns() {
        KbRetrievalService service = new KbRetrievalService(knowledgeBaseRepository, documentRepository, embeddingStore,
                embeddingPort, sameThread, diagnostic -> {
                    throw new IllegalStateException("recorder down");
                });
        when(knowledgeBaseRepository.findByTenantIdAndIdIn(eq(tenantId), anyList())).thenReturn(List.of());

        assertThat(service.retrieveWithOutcome(tenantId, List.of(kbId), "q", 5, 0.7).outcome())
                .isEqualTo(RagOutcome.KB_NOT_FOUND_FOR_TENANT);
    }

    // ------------------------------------------------- the timeout branch: root cause, not just message

    @Test
    void when_futureGetTimesOut_then_reportsTimeoutWithRootCause() {
        Executor never = task -> { /* never runs the task: future.get hits the deadline */ };
        when(knowledgeBaseRepository.findByTenantIdAndIdIn(eq(tenantId), anyList())).thenReturn(List.of(kb(768)));

        RagRetrievalResult result = service(never).retrieveWithOutcome(tenantId, List.of(kbId), "q", 5, 0.7);

        assertThat(result.outcome()).isEqualTo(RagOutcome.TIMEOUT);
        RagDiagnostic diagnostic = recorder.single();
        assertThat(diagnostic.outcome()).isEqualTo(RagOutcome.TIMEOUT);
        // Root cause class name, not only getMessage(): a wrapped timeout often has no message.
        assertThat(diagnostic.cause()).contains("TimeoutException");
    }

    @Test
    void when_retrievalTaskFails_then_reportsErrorWithRootCause() {
        when(knowledgeBaseRepository.findByTenantIdAndIdIn(eq(tenantId), anyList())).thenReturn(List.of(kb(768)));
        when(embeddingPort.embedQuery(eq(tenantId), any(), anyInt(), any()))
                .thenThrow(new IllegalStateException("pgvector unavailable"));

        RagRetrievalResult result = service(sameThread).retrieveWithOutcome(tenantId, List.of(kbId), "q", 5, 0.7);

        assertThat(result.outcome()).isEqualTo(RagOutcome.ERROR);
        assertThat(recorder.single().cause()).contains("IllegalStateException");
    }

    @Test
    void when_classify_then_separatesTimeoutFromError() {
        assertThat(KbRetrievalService.classify(new TimeoutException())).isEqualTo(RagOutcome.TIMEOUT);
        assertThat(KbRetrievalService.classify(new InterruptedException())).isEqualTo(RagOutcome.TIMEOUT);
        assertThat(KbRetrievalService.classify(new java.util.concurrent.CancellationException()))
                .isEqualTo(RagOutcome.TIMEOUT);
        assertThat(KbRetrievalService.classify(
                new java.util.concurrent.ExecutionException(new RuntimeException(new TimeoutException()))))
                .isEqualTo(RagOutcome.TIMEOUT);
        assertThat(KbRetrievalService.classify(new java.util.concurrent.ExecutionException(new IllegalStateException("x"))))
                .isEqualTo(RagOutcome.ERROR);
    }

    @Test
    void when_dimensionMismatch_then_throwsAndReports() {
        KnowledgeBaseEntity other = kb(768);
        other.setId(UUID.randomUUID());
        other.setDimension(1536);
        when(knowledgeBaseRepository.findByTenantIdAndIdIn(eq(tenantId), anyList()))
                .thenReturn(List.of(kb(768), other));

        assertThatThrownBy(() -> service(sameThread).retrieveWithOutcome(tenantId, List.of(kbId), "q", 5, 0.7))
                .isInstanceOf(KbDimensionMismatchException.class);

        assertThat(recorder.single().outcome()).isEqualTo(RagOutcome.DIMENSION_MISMATCH);
    }

    // ------------------------------------------------- back-compat: retrieve keeps its signature

    @Test
    void when_retrieve_then_returnsOnlyChunks() {
        when(knowledgeBaseRepository.findByTenantIdAndIdIn(eq(tenantId), anyList())).thenReturn(List.of());
        assertThat(service(sameThread).retrieve(tenantId, List.of(kbId), "q", 5, 0.7)).isEmpty();
        assertThat(service(sameThread).retrieve(tenantId, List.of(), "q", 5, 0.7)).isEmpty();
    }

    @Test
    void when_recorderIsNull_then_noopFallback() {
        KbRetrievalService service = new KbRetrievalService(knowledgeBaseRepository, documentRepository, embeddingStore,
                embeddingPort, sameThread, null);
        when(knowledgeBaseRepository.findByTenantIdAndIdIn(eq(tenantId), anyList())).thenReturn(List.of());

        assertThat(service.retrieveWithOutcome(tenantId, List.of(kbId), "q", 5, 0.7).chunks()).isEmpty();
    }

    @Test
    void when_diagnosticLogged_then_neverCarriesQueryTextOrChunkContent() {
        when(knowledgeBaseRepository.findByTenantIdAndIdIn(eq(tenantId), anyList())).thenReturn(List.of(kb(768)));
        when(embeddingPort.embedQuery(eq(tenantId), any(), anyInt(), any())).thenReturn(new float[]{0.1f});
        KbEmbeddingStore.ScoredRow row = new KbEmbeddingStore.ScoredRow();
        row.docId = UUID.randomUUID();
        row.chunkId = UUID.randomUUID();
        row.text = "salary of the CEO is 1M";
        row.score = 0.99;
        when(embeddingStore.search(eq(tenantId), anyList(), any(), anyInt(), anyInt(), anyDouble()))
                .thenReturn(new ArrayList<>(List.of(row)));
        when(documentRepository.findByTenantIdAndIdIn(eq(tenantId), org.mockito.ArgumentMatchers.<java.util.Collection<UUID>>any())).thenReturn(List.of());

        service(sameThread).retrieveWithOutcome(tenantId, List.of(kbId), "PERGUNTA SIGILOSA", 5, 0.7);

        String line = LoggingRagDiagnosticRecorder.format(recorder.single());
        assertThat(line).doesNotContain("PERGUNTA SIGILOSA").doesNotContain("salary of the CEO");
        assertThat(line).contains("tenantId=").contains("kbIds=").contains("chunks=1");
    }

    /**
     * The leak path that matters: an exception whose own {@code getMessage()} quotes the user's
     * question and a document excerpt. A driver or an application-layer wrapper can do that, and
     * {@code causeOf} used to copy the message verbatim into the log line.
     */
    @Test
    void when_exceptionMessageCarriesQueryAndChunkText_then_neitherReachesTheLogNorTheResult() {
        when(knowledgeBaseRepository.findByTenantIdAndIdIn(eq(tenantId), anyList())).thenReturn(List.of(kb(768)));
        when(embeddingPort.embedQuery(eq(tenantId), any(), anyInt(), any())).thenThrow(new IllegalStateException(
                "embed failed for query=[PERGUNTA SIGILOSA] using chunk=[salary of the CEO is 1M]"));

        RagRetrievalResult result = service(sameThread)
                .retrieveWithOutcome(tenantId, List.of(kbId), "PERGUNTA SIGILOSA", 5, 0.7);

        assertThat(result.outcome()).isEqualTo(RagOutcome.ERROR);
        RagDiagnostic diagnostic = recorder.single();
        String line = LoggingRagDiagnosticRecorder.format(diagnostic);

        assertThat(line)
                .doesNotContain("PERGUNTA SIGILOSA")
                .doesNotContain("salary of the CEO")
                .doesNotContain("embed failed");
        assertThat(diagnostic.cause())
                .as("the diagnostic must stay actionable without quoting the failure message")
                .isEqualTo("IllegalStateException:unexpected");
        // the operator keeps the class and a code, so the line is still diagnosable
        assertThat(diagnostic.cause()).contains("IllegalStateException");
        assertThat(diagnostic.reason()).isNotBlank();
        assertThat(String.valueOf(result)).doesNotContain("PERGUNTA SIGILOSA");
    }

    @Test
    void when_causeOf_then_reportsClassAndClosedCodeWithoutQuotingTheMessage() {
        // timeout: the code must survive, the message must not
        assertThat(RagDiagnostic.causeOf(new IllegalStateException("boom",
                new RuntimeException("ignored", new java.net.http.HttpTimeoutException("read timed out")))))
                .isEqualTo("HttpTimeoutException:timeout");
        // connection refused and 401 stay actionable without a message
        assertThat(RagDiagnostic.causeOf(new java.net.ConnectException("Connection refused: host=10.0.0.1")))
                .isEqualTo("ConnectException:connection_refused");
        assertThat(RagDiagnostic.causeOf(new dev.langchain4j.exception.HttpException(401,
                "{\"error\":{\"message\":\"Incorrect API key provided: sk-live-PERGUNTA SIGILOSA\"}}")))
                .isEqualTo("HttpException:unauthorized");
        assertThat(RagDiagnostic.causeOf(new dev.langchain4j.exception.HttpException(429, "slow down")))
                .isEqualTo("HttpException:rate_limited");
        assertThat(RagDiagnostic.causeOf(new dev.langchain4j.exception.HttpException(503, "down")))
                .isEqualTo("HttpException:server_error");
        assertThat(RagDiagnostic.causeOf(new java.sql.SQLException("relation \"kb_embeddings_768\" does not exist")))
                .isEqualTo("SQLException:sql_error");
        // unknown shape: class only, never the message
        assertThat(RagDiagnostic.causeOf(new IllegalArgumentException("valor invalido: PERGUNTA SIGILOSA")))
                .isEqualTo("IllegalArgumentException:unexpected");
        assertThat(RagDiagnostic.causeOf(null)).isNull();
    }

    @Test
    void when_causeChainSelfReferences_then_terminates() {
        assertThat(RagDiagnostic.causeOf(new SelfCausedException()))
                .isEqualTo("SelfCausedException:unexpected");
    }

    @Test
    void when_causeChainFormsATwoNodeCycle_then_terminates() {
        // A <-> B: only a depth bound stops this, a self-reference check does not.
        MutualCause a = new MutualCause();
        MutualCause b = new MutualCause();
        a.other = b;
        b.other = a;

        assertThat(RagDiagnostic.causeOf(a)).isEqualTo("MutualCause:unexpected");
    }

    /** A throwable that points at itself would loop forever without the cycle guard. */
    private static final class SelfCausedException extends RuntimeException {
        @Override
        public synchronized Throwable getCause() {
            return this;
        }
    }

    private static final class MutualCause extends RuntimeException {
        private MutualCause other;

        @Override
        public synchronized Throwable getCause() {
            return other;
        }
    }

    @Test
    void when_dimensionMismatch_then_diagnosticIsNotWarnBecauseTheExceptionEsTheSignal() {
        KnowledgeBaseEntity other = kb(768);
        other.setId(UUID.randomUUID());
        other.setDimension(1536);
        when(knowledgeBaseRepository.findByTenantIdAndIdIn(eq(tenantId), anyList()))
                .thenReturn(List.of(kb(768), other));

        assertThatThrownBy(() -> service(sameThread).retrieveWithOutcome(tenantId, List.of(kbId), "q", 5, 0.7))
                .isInstanceOf(KbDimensionMismatchException.class)
                .as("the count the line reports must be readable off the exception too")
                .extracting(e -> ((KbDimensionMismatchException) e).foundKbs())
                .isEqualTo(2);

        // the outcome is never returned, only thrown: a WARN would duplicate the exception
        assertThat(LoggingRagDiagnosticRecorder.isWarning(RagOutcome.DIMENSION_MISMATCH)).isFalse();
        // the line is still emitted and still carries the ids the exception lacks
        RagDiagnostic diagnostic = recorder.single();
        assertThat(diagnostic.outcome()).isEqualTo(RagOutcome.DIMENSION_MISMATCH);
        assertThat(diagnostic.kbIds()).containsExactly(kbId);
        assertThat(diagnostic.foundKbs())
                .as("two bases were found and checked — reporting 0 would send the reader to the link table")
                .isEqualTo(2);
        assertThat(diagnostic.tenantId()).isEqualTo(tenantId);
        assertThat(diagnostic.reason()).isNotBlank();
        // nothing was thrown yet when the line is built, so there is no failure to classify
        assertThat(diagnostic.cause()).isNull();
    }

    @Test
    void when_severitySplit_then_warnsOnlyOnOutcomesNobodyEscalates() {
        assertThat(LoggingRagDiagnosticRecorder.isWarning(RagOutcome.KB_NOT_FOUND_FOR_TENANT)).isTrue();
        assertThat(LoggingRagDiagnosticRecorder.isWarning(RagOutcome.TIMEOUT)).isTrue();
        assertThat(LoggingRagDiagnosticRecorder.isWarning(RagOutcome.ERROR)).isTrue();

        // returned outcomes that are normal operation, not incidents
        assertThat(LoggingRagDiagnosticRecorder.isWarning(RagOutcome.OK)).isFalse();
        assertThat(LoggingRagDiagnosticRecorder.isWarning(RagOutcome.NO_LINK)).isFalse();
        assertThat(LoggingRagDiagnosticRecorder.isWarning(RagOutcome.NO_CHUNK_ABOVE_THRESHOLD)).isFalse();
        assertThat(LoggingRagDiagnosticRecorder.isWarning(RagOutcome.RAG_DISABLED)).isFalse();
        // thrown-only outcome: the exception escalates, the line only correlates
        assertThat(LoggingRagDiagnosticRecorder.isWarning(RagOutcome.DIMENSION_MISMATCH)).isFalse();
    }

    @Test
    void when_recorderIsUsedAcrossManyCalls_then_recordsEveryOutcome() {
        when(knowledgeBaseRepository.findByTenantIdAndIdIn(eq(tenantId), anyList())).thenReturn(List.of());
        KbRetrievalService service = service(sameThread);

        service.retrieveWithOutcome(tenantId, List.of(kbId), "a", 5, 0.7);
        service.retrieveWithOutcome(tenantId, List.of(), "b", 5, 0.7);

        assertThat(recorder.all).extracting(RagDiagnostic::outcome)
                .containsExactly(RagOutcome.KB_NOT_FOUND_FOR_TENANT, RagOutcome.NO_LINK);
    }

    @Test
    void when_loggingAdapterFormats_then_severitySplitFollowsOutcome() {
        assertThat(LoggingRagDiagnosticRecorder.format(
                RagDiagnostic.builder(RagOutcome.OK, null, tenantId).kbIds(List.of(kbId)).chunks(2).build()))
                .contains("outcome=ok").contains("chunks=2");
        assertThat(LoggingRagDiagnosticRecorder.format(
                RagDiagnostic.builder(RagOutcome.TIMEOUT, null, tenantId).kbIds(List.of(kbId)).build()))
                .contains("outcome=timeout");
        assertThat(LoggingRagDiagnosticRecorder.format(
                RagDiagnostic.builder(RagOutcome.NO_LINK, null, tenantId).build()))
                .contains("kbIds=none");
        assertThat(LoggingRagDiagnosticRecorder.format(null)).isNull();
    }

    @Test
    void when_kbChunkListThen_convertedAndBounded() {
        RagRetrievalResult result = RagRetrievalResult.of(RagOutcome.OK,
                List.of(new KbChunk()), 12L, null, 1, null, null);

        assertThat(result.hasChunks()).isTrue();
        assertThat(result.chunkCount()).isEqualTo(1);
        assertThat(result.elapsedMs()).isEqualTo(12L);
        assertThat(RagRetrievalResult.empty(RagOutcome.TIMEOUT, 1L, 0.60, 1, "deadline", null).chunks()).isEmpty();
        assertThat(RagRetrievalResult.empty(RagOutcome.TIMEOUT, 1L, 0.60, 1, "deadline", null).hasChunks()).isFalse();
    }

    private static final class RecordingRecorder implements RagDiagnosticRecorder {

        private final List<RagDiagnostic> all = new ArrayList<>();

        @Override
        public void record(RagDiagnostic diagnostic) {
            all.add(diagnostic);
        }

        private RagDiagnostic single() {
            assertThat(all).as("exactly one diagnostic expected").hasSize(1);
            return all.get(0);
        }
    }
}
