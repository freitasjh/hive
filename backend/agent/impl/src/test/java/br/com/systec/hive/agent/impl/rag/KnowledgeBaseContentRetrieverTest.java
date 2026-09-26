package br.com.systec.hive.agent.impl.rag;

import br.com.systec.hive.knowledge.api.domain.KbChunk;
import br.com.systec.hive.knowledge.api.domain.RagOutcome;
import br.com.systec.hive.knowledge.api.domain.RagRetrievalResult;
import br.com.systec.hive.knowledge.api.port.KbRetrievalPort;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.ContentMetadata;
import dev.langchain4j.rag.content.aggregator.DefaultContentAggregator;
import dev.langchain4j.rag.query.Query;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KnowledgeBaseContentRetrieverTest {

    @Mock
    private KbRetrievalPort retrieval;

    private UUID tenantId;
    private UUID kbId;
    private final List<RagRetrievalResult> published = new ArrayList<>();
    private Function<Query, Integer> maxResults;
    private Function<Query, Double> minScore;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        kbId = UUID.randomUUID();
        maxResults = query -> 5;
        minScore = query -> KbRetrievalPort.MIN_SCORE_FROM_KB_CONVENTION;
    }

    private KnowledgeBaseContentRetriever retriever(List<UUID> kbIds) {
        return KnowledgeBaseContentRetriever.of(retrieval, tenantId, kbIds, maxResults, minScore,
                published::add);
    }

    private static Query query(String text) {
        return Query.from(text);
    }

    /**
     * The tenant is a constructor argument and never a parameter of the search: the only way this
     * class can widen the scope of a retrieval is by rewriting the tenant at call time, and there is
     * no code path that does.
     */
    @Test
    void when_retrieve_then_searchesExactlyTheTenantAndBasesItWasBuiltFor() {
        when(retrieval.retrieveWithOutcome(any(), anyList(), any(), anyInt(), anyDouble()))
                .thenReturn(RagRetrievalResult.empty(RagOutcome.NO_CHUNK_ABOVE_THRESHOLD, 3L, 0.60, 1, "nada", null));

        retriever(List.of(kbId)).retrieve(query("pergunta"));

        verify(retrieval).retrieveWithOutcome(eq(tenantId), eq(List.of(kbId)), eq("pergunta"), eq(5),
                eq(KbRetrievalPort.MIN_SCORE_FROM_KB_CONVENTION));
    }

    /**
     * A base that belongs to another tenant comes back as {@code KB_NOT_FOUND_FOR_TENANT} with no
     * chunk. The retriever's part of the contract is that this reaches the caller as an empty list
     * <em>and</em> as that outcome — the tenant filter itself is the store's, and what this test
     * pins is that the retriever neither invents content nor swallows the reason.
     */
    @Test
    void when_basesAreNotInThisTenant_then_returnsNothingAndKeepsTheOutcome() {
        when(retrieval.retrieveWithOutcome(any(), anyList(), any(), anyInt(), anyDouble()))
                .thenReturn(RagRetrievalResult.empty(RagOutcome.KB_NOT_FOUND_FOR_TENANT, 2L, null, 0,
                        "linked knowledge bases do not exist for this tenant", null));

        List<Content> contents = retriever(List.of(UUID.randomUUID())).retrieve(query("pergunta"));

        assertThat(contents).isEmpty();
        assertThat(published).singleElement()
                .extracting(RagRetrievalResult::outcome)
                .isEqualTo(RagOutcome.KB_NOT_FOUND_FOR_TENANT);
    }

    /** Same for the "agent has no usable base" case: nothing to inject, and the reason survives. */
    @Test
    void when_noBaseIsLinked_then_returnsNothingAndReportsNoLink() {
        when(retrieval.retrieveWithOutcome(any(), anyList(), any(), anyInt(), anyDouble()))
                .thenReturn(RagRetrievalResult.empty(RagOutcome.NO_LINK, 1L, null, 0, "no knowledge base linked", null));

        assertThat(retriever(List.of()).retrieve(query("pergunta"))).isEmpty();
        assertThat(published).singleElement()
                .extracting(RagRetrievalResult::outcome)
                .isEqualTo(RagOutcome.NO_LINK);
    }

    /**
     * The cut-off is resolved per query, which is the whole point of the official
     * {@code dynamicMinScore}: it is not a number this class holds. The test uses two queries with
     * two different answers so a class that captured the value once would fail it.
     */
    @Test
    void when_minScoreIsDynamic_then_eachQueryIsSearchedWithItsOwnCutOff() {
        minScore = q -> "curta".equals(q.text()) ? 0.42 : KbRetrievalPort.MIN_SCORE_FROM_KB_CONVENTION;
        when(retrieval.retrieveWithOutcome(any(), anyList(), any(), anyInt(), anyDouble()))
                .thenReturn(RagRetrievalResult.empty(RagOutcome.OK, 1L, 0.42, 1, null, null));

        KnowledgeBaseContentRetriever retriever = retriever(List.of(kbId));
        retriever.retrieve(query("curta"));
        retriever.retrieve(query("longa"));

        ArgumentCaptor<Double> cutOffs = ArgumentCaptor.forClass(Double.class);
        verify(retrieval, times(2))
                .retrieveWithOutcome(eq(tenantId), eq(List.of(kbId)), any(), anyInt(), cutOffs.capture());
        assertThat(cutOffs.getAllValues()).containsExactly(0.42, KbRetrievalPort.MIN_SCORE_FROM_KB_CONVENTION);
    }

    @Test
    void when_maxResultsIsDynamic_then_eachQueryGetsItsOwnLimit() {
        maxResults = q -> q.text().length() > 5 ? 2 : 5;
        when(retrieval.retrieveWithOutcome(any(), anyList(), any(), anyInt(), anyDouble()))
                .thenReturn(RagRetrievalResult.empty(RagOutcome.OK, 1L, 0.60, 1, null, null));

        KnowledgeBaseContentRetriever retriever = retriever(List.of(kbId));
        retriever.retrieve(query("oi"));
        retriever.retrieve(query("pergunta longa"));

        ArgumentCaptor<Integer> limits = ArgumentCaptor.forClass(Integer.class);
        verify(retrieval, times(2))
                .retrieveWithOutcome(eq(tenantId), eq(List.of(kbId)), any(), limits.capture(), anyDouble());
        assertThat(limits.getAllValues()).containsExactly(5, 2);
    }

    /**
     * The official aggregator scores by position, so "ordered by relevance" is a contract this class
     * has to keep on its own. The fixture comes back from the port deliberately shuffled, which is the
     * only way to tell an implementation that sorts from one that trusts its input.
     */
    @Test
    void when_chunksArriveUnsorted_then_contentsComeBackMostRelevantFirst() {
        when(retrieval.retrieveWithOutcome(any(), anyList(), any(), anyInt(), anyDouble()))
                .thenReturn(RagRetrievalResult.of(RagOutcome.OK,
                        List.of(chunk("baixo", 0.61), chunk("alto", 0.93), chunk("medio", 0.77)),
                        5L, 0.60, 1, null, null));

        List<Content> contents = retriever(List.of(kbId)).retrieve(query("pergunta"));

        assertThat(contents).extracting(c -> c.textSegment().text())
                .containsExactly("alto", "medio", "baixo");
        assertThat(contents).extracting(c -> c.metadata().get(ContentMetadata.SCORE))
                .containsExactly(0.93, 0.77, 0.61);
    }

    /**
     * The count that leaves the retriever is the count that reaches the prompt and the citations. The
     * official aggregator folds equal contents into one — {@code ReciprocalRankFuser} keys by
     * {@code Content}, and the default implementation compares only the text — so two identical
     * chunks would silently become one. {@link RetrievedContent} is what makes the 1:1 hold.
     */
    @Test
    void when_twoChunksHaveIdenticalText_then_bothSurviveTheAggregator() {
        RagRetrievalResult result = RagRetrievalResult.of(RagOutcome.OK,
                List.of(chunk("texto igual", 0.9, "a.txt"), chunk("texto igual", 0.8, "b.txt")),
                4L, 0.60, 1, null, null);
        when(retrieval.retrieveWithOutcome(any(), anyList(), any(), anyInt(), anyDouble())).thenReturn(result);

        List<Content> contents = retriever(List.of(kbId)).retrieve(query("pergunta"));

        assertThat(new DefaultContentAggregator().aggregate(Map.of(Query.from("pergunta"), List.of(contents))))
                .as("the aggregator must not deduplicate two chunks from two documents")
                .hasSize(2);
        assertThat(result.chunkCount())
                .as("and the diagnostic still describes what the search returned")
                .isEqualTo(2);
    }

    @Test
    void when_portReturnsNothingAtAll_then_nothingIsInventedAndNothingIsPublished() {
        when(retrieval.retrieveWithOutcome(any(), anyList(), any(), anyInt(), anyDouble())).thenReturn(null);

        assertThat(retriever(List.of(kbId)).retrieve(query("pergunta"))).isEmpty();
        assertThat(published).as("a result that does not exist cannot be reported as one").isEmpty();
    }

    /** The report the turn reads is the retrieval's own object, not a reconstruction of it. */
    @Test
    void when_retrieve_then_publishesTheRetrievalsOwnResult() {        RagRetrievalResult result = RagRetrievalResult.of(RagOutcome.OK, List.of(chunk("trecho", 0.9)),
                8L, 0.60, 1, null, null);
        when(retrieval.retrieveWithOutcome(any(), anyList(), any(), anyInt(), anyDouble())).thenReturn(result);

        retriever(List.of(kbId)).retrieve(query("pergunta"));

        assertThat(published).containsExactly(result);
    }

    @Test
    void when_contentIsBuilt_then_carriesTheDocumentMetadataTheInjectorRenders() {
        when(retrieval.retrieveWithOutcome(any(), anyList(), any(), anyInt(), anyDouble()))
                .thenReturn(RagRetrievalResult.of(RagOutcome.OK, List.of(chunk("trecho", 0.9)),
                        1L, 0.60, 1, null, null));

        Content content = retriever(List.of(kbId)).retrieve(query("pergunta")).get(0);

        assertThat(content.textSegment().metadata().getString(RetrievedContent.DOC_NAME)).isEqualTo("manual.txt");
        assertThat(((RetrievedContent) content).docId()).isNotNull();
        assertThat(((RetrievedContent) content).chunkId()).isNotNull();
        assertThat(((RetrievedContent) content).score()).isEqualTo(0.9);
    }

    /**
     * A chunk with no text cannot be a {@code TextSegment} — the official type refuses blank text — and
     * an empty block tells the model nothing. It is left out of the prompt while the retrieval's own
     * report, which is what the diagnostic and the citations are built from, still describes it.
     */
    @Test
    void when_aChunkHasNoText_then_itIsNotOfferedToTheInjector() {
        RagRetrievalResult result = RagRetrievalResult.of(RagOutcome.OK,
                List.of(chunk("trecho", 0.9), chunk(null, 0.8), chunk("   ", 0.7)),
                1L, 0.60, 1, null, null);
        when(retrieval.retrieveWithOutcome(any(), anyList(), any(), anyInt(), anyDouble())).thenReturn(result);

        List<Content> contents = retriever(List.of(kbId)).retrieve(query("pergunta"));

        assertThat(contents).hasSize(1);
        assertThat(published).singleElement().extracting(RagRetrievalResult::chunkCount).isEqualTo(3);
    }

    private KbChunk chunk(String text, double score) {
        return chunk(text, score, "manual.txt");
    }

    private KbChunk chunk(String text, double score, String docName) {
        KbChunk chunk = new KbChunk();
        chunk.setDocId(UUID.randomUUID());
        chunk.setDocName(docName);
        chunk.setChunkId(UUID.randomUUID());
        chunk.setText(text);
        chunk.setScore(score);
        return chunk;
    }
}
