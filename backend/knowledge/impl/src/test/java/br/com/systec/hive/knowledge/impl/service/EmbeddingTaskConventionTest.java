package br.com.systec.hive.knowledge.impl.service;

import br.com.systec.hive.commons.testsupport.HardcodedSimilarityCutOffGuard;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The table itself, read one row at a time. Kept apart from {@link KbEmbeddingServiceTest} so that
 * adding a model family is a change to a test that says what the rule is, not a side effect of a
 * test that was exercising the plumbing.
 */
class EmbeddingTaskConventionTest {

    // The literals are on purpose: they are what the model card says, so a change in production
    // breaks this test instead of being followed along by it.
    private static final String DOCUMENT_PREFIX = "search_document: ";
    private static final String QUERY_PREFIX = "search_query: ";

    @Test
    void when_nomicFamily_then_documentAndQueryGetTheirOwnPrefix() {
        // pinned as literals, not against the production constants: a constant that drifts from the
        // model card has to break a test, and a test that only compares constants cannot see it
        assertThat(EmbeddingTaskConvention.NOMIC_DOCUMENT_PREFIX).isEqualTo("search_document: ");
        assertThat(EmbeddingTaskConvention.NOMIC_QUERY_PREFIX).isEqualTo("search_query: ");
        assertThat(EmbeddingTaskConvention.documentPrefix("nomic-embed-text")).isEqualTo("search_document: ");
        assertThat(EmbeddingTaskConvention.queryPrefix("nomic-embed-text")).isEqualTo("search_query: ");
    }

    @Test
    void when_nomicVariantWithTagOrDifferentCase_then_sameRule() {
        assertThat(EmbeddingTaskConvention.documentPrefix("nomic-embed-text:v1.5")).isNotEmpty();
        assertThat(EmbeddingTaskConvention.queryPrefix("NOMIC-EMBED-TEXT")).isNotEmpty();
    }

    @Test
    void when_openAiFamily_then_noPrefixOnEitherSide() {
        assertThat(EmbeddingTaskConvention.documentPrefix("text-embedding-3-small")).isEmpty();
        assertThat(EmbeddingTaskConvention.queryPrefix("text-embedding-3-large")).isEmpty();
    }

    @Test
    void when_otherFamiliesOrUnknownModel_then_noPrefix() {
        for (String model : new String[]{"mxbai-embed-large", "all-minilm-l6-v2", "bge-base-en-v1.5",
                "minilm", "cohere-embed-v3", "whatever-v9"}) {
            assertThat(EmbeddingTaskConvention.documentPrefix(model)).as(model).isEmpty();
            assertThat(EmbeddingTaskConvention.queryPrefix(model)).as(model).isEmpty();
        }
    }

    @Test
    void when_modelIsNullOrBlank_then_plain() {
        assertThat(EmbeddingTaskConvention.documentPrefix(null)).isEmpty();
        assertThat(EmbeddingTaskConvention.queryPrefix(null)).isEmpty();
        assertThat(EmbeddingTaskConvention.conventionId("")).isEqualTo(EmbeddingTaskConvention.PLAIN);
        assertThat(EmbeddingTaskConvention.conventionId("text-embedding-3-small"))
                .isEqualTo(EmbeddingTaskConvention.PLAIN);
        assertThat(EmbeddingTaskConvention.describe("text-embedding-3-small"))
                .isEqualTo(EmbeddingTaskConvention.PLAIN);
    }

    @Test
    void when_describeNomic_then_carriesBothPrefixesForTheLog() {
        assertThat(EmbeddingTaskConvention.describe("nomic-embed-text"))
                .isEqualTo("nomic-task-prefix document='search_document: ' query='search_query: '");
    }

    @Test
    void when_applyPrefix_then_prefixIsPrependedVerbatim() {
        assertThat(EmbeddingTaskConvention.apply(QUERY_PREFIX, "pergunta"))
                .isEqualTo("search_query: pergunta");
        assertThat(EmbeddingTaskConvention.apply("", "pergunta")).isEqualTo("pergunta");
        assertThat(EmbeddingTaskConvention.apply(null, "pergunta")).isEqualTo("pergunta");
    }

    @Test
    void when_textIsNull_then_applyLeavesItNullForTheCallerToReject() {
        assertThat(EmbeddingTaskConvention.apply(QUERY_PREFIX, null)).isNull();
    }

    /** The query is user input: someone can type the prefix themselves. */
    @Test
    void when_textAlreadyCarriesItsOwnPrefix_then_itIsNotPrefixedTwice() {
        String once = EmbeddingTaskConvention.apply(QUERY_PREFIX, "como reinicio a busca?");
        String typed = EmbeddingTaskConvention.apply(QUERY_PREFIX, "search_query: como reinicio a busca?");

        assertThat(typed).isEqualTo("search_query: como reinicio a busca?").isEqualTo(once);
        assertThat(EmbeddingTaskConvention.apply(QUERY_PREFIX, typed)).isEqualTo(once);
        assertThat(EmbeddingTaskConvention.apply(DOCUMENT_PREFIX, DOCUMENT_PREFIX + "chunk"))
                .isEqualTo("search_document: chunk");
    }

    /**
     * The other role's prefix is <b>kept and prefixed over</b>, not stripped and not rejected: the
     * text belongs to the user, so rewriting it loses content and failing on it turns harmless input
     * into a dead retrieval. The anomaly is reported instead — see the test below.
     */
    @Test
    void when_textCarriesTheOtherRolesPrefix_then_itIsStillPrefixedForItsOwnRole() {
        String query = EmbeddingTaskConvention.apply(QUERY_PREFIX, DOCUMENT_PREFIX + "como reinicio?");
        String document = EmbeddingTaskConvention.apply(DOCUMENT_PREFIX, QUERY_PREFIX + "trecho");

        assertThat(query).isEqualTo("search_query: search_document: como reinicio?");
        assertThat(document).isEqualTo("search_document: search_query: trecho");
    }

    @Test
    void when_textCarriesTheOtherRolesPrefix_then_itIsFlaggedSoTheOperatorSeesIt() {
        assertThat(EmbeddingTaskConvention.hasForeignRolePrefix(QUERY_PREFIX, DOCUMENT_PREFIX + "x")).isTrue();
        assertThat(EmbeddingTaskConvention.hasForeignRolePrefix(DOCUMENT_PREFIX, QUERY_PREFIX + "x")).isTrue();
        // its own prefix, or no prefix at all, is not an anomaly
        assertThat(EmbeddingTaskConvention.hasForeignRolePrefix(QUERY_PREFIX, QUERY_PREFIX + "x")).isFalse();
        assertThat(EmbeddingTaskConvention.hasForeignRolePrefix(QUERY_PREFIX, "como reinicio?")).isFalse();
        // nothing expected (a family without prefixes) means nothing to be foreign to
        assertThat(EmbeddingTaskConvention.hasForeignRolePrefix("", QUERY_PREFIX + "x")).isFalse();
        assertThat(EmbeddingTaskConvention.hasForeignRolePrefix(null, null)).isFalse();
    }

    // ------------------------------------------------------------------ the cut-off column

    /**
     * Pinned as literals, for the reason the prefixes above are: the numbers are what was measured
     * on a real base, so a change in production has to break this test instead of being followed
     * along by it.
     */
    @Test
    void when_nomicFamily_then_theMeasuredCutOffApplies() {
        // 0.6219 for a question the base answers, 0.5972 for one it does not. A 0.70 cut-off throws
        // the first away; a 0.60 one keeps the second out.
        assertThat(EmbeddingTaskConvention.defaultThreshold("nomic-embed-text")).isEqualTo(0.60);
        assertThat(EmbeddingTaskConvention.defaultThreshold("nomic-embed-text:v1.5")).isEqualTo(0.60);
        assertThat(EmbeddingTaskConvention.defaultThreshold("NOMIC-EMBED-TEXT")).isEqualTo(0.60);
    }

    @Test
    void when_openAiFamily_then_theHistoricalCutOffIsKept() {
        assertThat(EmbeddingTaskConvention.defaultThreshold("text-embedding-3-small")).isEqualTo(0.70);
        assertThat(EmbeddingTaskConvention.defaultThreshold("text-embedding-3-large")).isEqualTo(0.70);
    }

    /**
     * Nobody measured this family, so it gets the value nobody is tempted to lower. A base on an
     * unknown model must not be served a cut-off tuned on a different model's score distribution.
     */
    @Test
    void when_familyIsUnknownOrTheModelIsNull_then_theConservativeCutOffApplies() {
        for (String model : new String[]{"mxbai-embed-large", "all-minilm-l6-v2", "bge-base-en-v1.5",
                "cohere-embed-v3", "whatever-v9", "", null}) {
            assertThat(EmbeddingTaskConvention.defaultThreshold(model))
                    .as(String.valueOf(model))
                    .isEqualTo(0.70);
        }
    }

    @Test
    void when_theCutOffConstantsThen_theyMatchTheRowsThatUseThem() {
        assertThat(EmbeddingTaskConvention.NOMIC_SIMILARITY_THRESHOLD).isEqualTo(0.60);
        assertThat(EmbeddingTaskConvention.DEFAULT_SIMILARITY_THRESHOLD).isEqualTo(0.70);
    }

    /**
     * The defect this column exists to prevent, as an executable guard.
     *
     * <p>The cut-off used to be a constant in two classes at once ({@code RAG_MIN_SCORE} and
     * {@code DEFAULT_MIN_SCORE}), both holding the same number and neither knowing which model it
     * applied to — so it was wrong for one family and nobody could tell from the code. A third
     * hardcoded number next to the retrieval would be that bug again, so the two classes that
     * decide and pass the cut-off are checked for one rather than trusted. This table itself is the
     * one place a number is allowed to live.
     *
     * <p>The pattern and the assertion live in {@code HardcodedSimilarityCutOffGuard}, together with
     * the test that proves the pattern still fires — a copy of a regex is a second thing to get
     * wrong, and the previous copy did not match {@code 0.70}, which is the spelling used here.
     */
    @Test
    void when_theDecisionPathIsScanned_then_noCutOffIsHardcodedNextToTheTable() throws IOException {
        Path serviceDir = Path.of("src/main/java/br/com/systec/hive/knowledge/impl/service");
        for (String file : List.of("KbRetrievalService.java", "KnowledgeBaseServiceImpl.java")) {
            HardcodedSimilarityCutOffGuard.assertNoHardcodedCutOff(serviceDir.resolve(file));
        }
    }
}
