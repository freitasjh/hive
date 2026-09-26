package br.com.systec.hive.knowledge.impl.service;

import br.com.systec.hive.knowledge.api.exceptions.KbInvalidUrlException;
import br.com.systec.hive.knowledge.impl.fake.PrefixAwareEmbeddingModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The task-prefix contract, asserted on the text that actually reaches the provider.
 *
 * <p>Asserting "the port method was called" is what hid this bug: the method was called, with the
 * right role, and the text still went to nomic without the prefix its model card requires. Every
 * assertion here therefore reads {@link PrefixAwareEmbeddingModel#lastSeenText()}, and the
 * behavioural ones compare real similarities instead of trusting the strings.
 */
@ExtendWith(MockitoExtension.class)
class KbEmbeddingServiceTest {

    private static final String NOMIC = "nomic-embed-text";
    private static final String OPENAI = "text-embedding-3-small";
    private static final String CHUNK = "reinicio do servico de busca";
    private static final String QUESTION = "reinicio do servico de busca agora";
    private static final String UNRELATED = "protein folding";
    /**
     * The cut-off this model's base will actually be searched with — read from the table, not
     * written here. Comparing the vectors against a number the retrieval does not use would let this
     * test stay green while the product rejected every relevant pair.
     */
    private static final double SEARCH_CUT_OFF = EmbeddingTaskConvention.defaultThreshold(NOMIC);

    @Mock
    private KbEmbeddingModelFactory modelFactory;

    private UUID tenantId;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
    }

    private PrefixAwareEmbeddingModel wire(String modelName) {
        PrefixAwareEmbeddingModel model =
                new PrefixAwareEmbeddingModel(modelName, KbEmbeddingService.dimensionOf(modelName));
        when(modelFactory.create(any(), any())).thenReturn(model);
        return model;
    }

    private KbEmbeddingService service() {
        return new KbEmbeddingService(modelFactory);
    }

    // ------------------------------------------------------- the convention, per family and per role

    @Test
    void when_embedDocument_withPrefixRequiringModel_then_providerGetsDocumentPrefix() {
        PrefixAwareEmbeddingModel model = wire(NOMIC);

        service().embedDocument(tenantId, NOMIC, model.dimension(), CHUNK);

        assertThat(model.lastSeenText()).isEqualTo(PrefixAwareEmbeddingModel.DOCUMENT_PREFIX + CHUNK);
    }

    @Test
    void when_embedQuery_withPrefixRequiringModel_then_providerGetsQueryPrefix() {
        PrefixAwareEmbeddingModel model = wire(NOMIC);

        service().embedQuery(tenantId, NOMIC, model.dimension(), CHUNK);

        assertThat(model.lastSeenText()).isEqualTo(PrefixAwareEmbeddingModel.QUERY_PREFIX + CHUNK);
    }

    @Test
    void when_embed_withPrefixRequiringModelVariant_then_conventionStillApplies() {
        PrefixAwareEmbeddingModel model = wire("nomic-embed-text:latest");

        service().embedDocument(tenantId, "nomic-embed-text:latest", model.dimension(), CHUNK);
        service().embedQuery(tenantId, "nomic-embed-text:latest", model.dimension(), CHUNK);

        assertThat(model.seenTexts()).containsExactly(
                PrefixAwareEmbeddingModel.DOCUMENT_PREFIX + CHUNK,
                PrefixAwareEmbeddingModel.QUERY_PREFIX + CHUNK);
    }

    @Test
    void when_embed_withOpenAiModel_then_providerGetsRawTextOnBothRoles() {
        PrefixAwareEmbeddingModel model = wire(OPENAI);

        service().embedDocument(tenantId, OPENAI, model.dimension(), CHUNK);
        service().embedQuery(tenantId, OPENAI, model.dimension(), QUESTION);

        // a prefix here is out-of-distribution text for this family and measurably degrades it
        assertThat(model.seenTexts()).containsExactly(CHUNK, QUESTION);
    }

    @Test
    void when_embed_withUnknownModel_then_providerGetsRawTextOnBothRoles() {
        PrefixAwareEmbeddingModel model = wire("meu-modelo-de-embedding-v1");

        service().embedDocument(tenantId, "meu-modelo-de-embedding-v1", model.dimension(), CHUNK);
        service().embedQuery(tenantId, "meu-modelo-de-embedding-v1", model.dimension(), QUESTION);

        assertThat(model.seenTexts()).containsExactly(CHUNK, QUESTION);
    }

    @Test
    void when_embed_withNullModel_then_fallsBackToTheDefaultFamilyConvention() {
        PrefixAwareEmbeddingModel model = wire(OPENAI);

        service().embedDocument(tenantId, null, model.dimension(), CHUNK);
        service().embedQuery(tenantId, null, model.dimension(), QUESTION);

        assertThat(model.seenTexts()).containsExactly(CHUNK, QUESTION);
    }

    @Test
    void when_embed_withNullText_then_failsWithoutReachingTheProvider() {
        assertThatThrownBy(() -> service().embedDocument(tenantId, NOMIC, 768, null))
                .isInstanceOf(IllegalArgumentException.class);
        verify(modelFactory, never()).create(any(), any());
    }

    @Test
    void when_providerReturnsAnotherDimension_then_rejected() {
        wire(NOMIC);

        assertThatThrownBy(() -> service().embedQuery(tenantId, NOMIC, 1536, QUESTION))
                .isInstanceOf(KbInvalidUrlException.class)
                .hasMessageContaining("dimension mismatch");
    }

    @Test
    void when_dimensionFor_then_keepsTheFamilySizes() {
        KbEmbeddingService service = service();
        assertThat(service.dimensionFor(null)).isEqualTo(1536);
        assertThat(service.dimensionFor(OPENAI)).isEqualTo(1536);
        assertThat(service.dimensionFor(NOMIC)).isEqualTo(768);
        assertThat(KbEmbeddingService.dimensionOf("nomic-embed-text:latest")).isEqualTo(768);
    }

    // ------------------------------------------------------- the behaviour the prefixes buy

    @Test
    void when_bothSidesCarryTheirPrefix_then_theRelevantChunkSeparatesFromTheUnrelatedOne() {
        PrefixAwareEmbeddingModel model = wire(NOMIC);
        KbEmbeddingService service = service();
        int dimension = KbEmbeddingService.dimensionOf(NOMIC);

        float[] stored = service.embedDocument(tenantId, NOMIC, dimension, CHUNK);
        float[] query = service.embedQuery(tenantId, NOMIC, dimension, QUESTION);
        float[] unrelated = service.embedDocument(tenantId, NOMIC, dimension, UNRELATED);

        double relevant = PrefixAwareEmbeddingModel.cosine(stored, query);
        double noise = PrefixAwareEmbeddingModel.cosine(stored, unrelated);
        assertThat(relevant)
                .as("a matching question must clear the search cut-off")
                .isGreaterThan(SEARCH_CUT_OFF);
        assertThat(relevant - noise)
                .as("the whole point: relevance must separate from noise, not merely exist")
                .isGreaterThan(0.5);
    }

    @Test
    void when_theQueryPrefixIsMissing_then_theMatchingChunkFallsUnderTheCutOff() {
        PrefixAwareEmbeddingModel model = wire(NOMIC);
        KbEmbeddingService service = service();
        int dimension = KbEmbeddingService.dimensionOf(NOMIC);

        // the document was written by the fixed code; the query arrives unprefixed (the regression)
        float[] stored = service.embedDocument(tenantId, NOMIC, dimension, CHUNK);
        float[] queryWithoutPrefix = model.vectorFor(QUESTION);

        assertThat(PrefixAwareEmbeddingModel.cosine(stored, queryWithoutPrefix))
                .as("a missing prefix puts the two sides in different spaces: the retrieval finds nothing")
                .isLessThan(SEARCH_CUT_OFF);
    }

    /**
     * Guards the double, not the code: it is the reason the assertions above mean anything. If the
     * fake ever became "always discriminating", every test here would keep passing while the product
     * was broken — which is exactly how the previous constant-vector stub hid this bug.
     */
    @Test
    void when_bothSidesArriveWithoutTheirPrefix_then_theSeparationCollapses() {
        // no wire() on purpose: the texts go straight into the double, which is the pre-fix code path
        PrefixAwareEmbeddingModel model =
                new PrefixAwareEmbeddingModel(NOMIC, KbEmbeddingService.dimensionOf(NOMIC));

        float[] stored = model.vectorFor(CHUNK);
        float[] query = model.vectorFor(QUESTION);
        float[] unrelated = model.vectorFor(UNRELATED);

        double relevant = PrefixAwareEmbeddingModel.cosine(stored, query);
        double noise = PrefixAwareEmbeddingModel.cosine(stored, unrelated);
        assertThat(relevant - noise)
                .as("without the prefix, relevant and unrelated are the same score: that was the bug")
                .isLessThan(0.1);
        assertThat(noise)
                .as("and everything looks alike, so any cut-off below it retrieves junk")
                .isGreaterThan(SEARCH_CUT_OFF);
    }
}
