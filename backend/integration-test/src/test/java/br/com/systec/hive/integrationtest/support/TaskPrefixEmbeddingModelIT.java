package br.com.systec.hive.integrationtest.support;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The double must keep the failure mode alive.
 *
 * <p>Its whole value is that it does not score everything 1.0. If a change to it made every pair
 * look alike, or made it stop caring about the prefix, every integration test in this module would
 * keep passing while the retrieval was broken — which is precisely how the constant-vector stub
 * hid the original bug. So the double gets tested too.
 *
 * <p><b>Named {@code *IT} on purpose.</b> This module's surefire is skipped by the
 * {@code integration} profile, so a {@code *Test} here would not run in
 * {@code mvn verify -pl integration-test -Pintegration} — the gate the other tests depend on would
 * be unguarded. The suffix ties it to that gate.
 */
class TaskPrefixEmbeddingModelIT {

    private static final int DIMENSION = 768;
    private static final String CHUNK = "reinicio do servico de busca do indice vetorial";
    private static final String RELATED = "reinicio do servico de busca agora";
    private static final String UNRELATED = "protein folding rules";

    private final List<String> seen = new ArrayList<>();

    private TaskPrefixEmbeddingModel nomic() {
        return new TaskPrefixEmbeddingModel("nomic-embed-text", DIMENSION, seen);
    }

    private static double cosine(float[] a, float[] b) {
        double dot = 0;
        double normA = 0;
        double normB = 0;
        for (int i = 0; i < a.length; i++) {
            dot += (double) a[i] * b[i];
            normA += (double) a[i] * a[i];
            normB += (double) b[i] * b[i];
        }
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    @Test
    void when_bothSidesArePrefixed_then_relevanceSeparatesFromNoise() {
        TaskPrefixEmbeddingModel model = nomic();
        float[] stored = model.vectorFor(TaskPrefixEmbeddingModel.DOCUMENT_PREFIX + CHUNK);
        float[] query = model.vectorFor(TaskPrefixEmbeddingModel.QUERY_PREFIX + RELATED);
        float[] noise = model.vectorFor(TaskPrefixEmbeddingModel.DOCUMENT_PREFIX + UNRELATED);

        assertThat(cosine(stored, query)).isGreaterThan(0.7);
        assertThat(cosine(stored, noise)).isLessThan(0.2);
    }

    @Test
    void when_thePrefixIsMissing_then_theQueryFallsOutOfTheSearchCutOff() {
        TaskPrefixEmbeddingModel model = nomic();
        float[] stored = model.vectorFor(TaskPrefixEmbeddingModel.DOCUMENT_PREFIX + CHUNK);
        float[] unprefixed = model.vectorFor(RELATED);

        assertThat(cosine(stored, unprefixed))
                .as("this is the regression the integration tests exist to catch")
                .isLessThan(0.7);
    }

    @Test
    void when_bothSidesAreUnprefixed_then_nothingDiscriminates() {
        TaskPrefixEmbeddingModel model = nomic();
        float[] stored = model.vectorFor(CHUNK);
        float[] related = model.vectorFor(RELATED);
        float[] noise = model.vectorFor(UNRELATED);

        assertThat(cosine(stored, related) - cosine(stored, noise)).isLessThan(0.1);
        assertThat(cosine(stored, noise)).isGreaterThan(0.7);
    }

    @Test
    void when_aModelDoesNotRequirePrefixes_then_rawTextIsUsedAsIs() {
        TaskPrefixEmbeddingModel model = new TaskPrefixEmbeddingModel("text-embedding-3-small", 1536, seen);
        float[] stored = model.vectorFor(CHUNK);
        float[] query = model.vectorFor(RELATED);

        assertThat(cosine(stored, query)).isGreaterThan(0.7);
    }

    @Test
    void when_documentVectorIsBuilt_then_itMatchesWhatTheProviderReturnsForAPrefixedChunk() {
        TaskPrefixEmbeddingModel model = nomic();

        assertThat(TaskPrefixEmbeddingModel.documentVector(DIMENSION, CHUNK))
                .containsExactly(model.vectorFor(TaskPrefixEmbeddingModel.DOCUMENT_PREFIX + CHUNK));
    }

    @Test
    void when_vectorLiteralIsBuilt_then_everyComponentIsThere() {
        float[] vector = TaskPrefixEmbeddingModel.documentVector(DIMENSION, CHUNK);

        assertThat(TaskPrefixEmbeddingModel.vectorLiteral(vector))
                .startsWith("[0.")
                .endsWith("]")
                .doesNotContain("NaN");
    }

    @Test
    void when_textHasNoTokens_then_aUsableNonZeroVectorIsStillReturned() {
        float[] vector = nomic().vectorFor("???");

        assertThat(vector).hasSize(DIMENSION);
        assertThat(cosine(vector, vector)).isGreaterThan(0.99);
    }
}
