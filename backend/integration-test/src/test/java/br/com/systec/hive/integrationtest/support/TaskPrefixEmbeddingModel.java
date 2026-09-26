package br.com.systec.hive.integrationtest.support;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The provider double used by the integration tests: it reproduces the one behaviour of a
 * task-prefix model that matters here, <b>text without its task prefix lands in a degenerate space
 * where nothing discriminates</b>, and it is the reason these tests can no longer pass on an
 * artificial score of 1.0.
 *
 * <p>The previous stub returned one constant vector for everything, so document and query were
 * always identical, cosine distance was always 0 and the assertion {@code score == 1.0} held no
 * matter what the code under test did — including while the retrieval was returning nothing useful
 * in production. This one derives the vector from the text, so an unrelated question lands near
 * zero and a matching one lands in between: the threshold has to actually do work.
 *
 * <p>Deliberately a copy of the unit-test double rather than a shared class: the two live in
 * different Maven modules, each with its own test scope. The algorithm is short and both sides state
 * why it exists; a shared test fixture would need its own published module for no benefit.
 *
 * <p>The prefix literals are written out instead of imported from production code, so that changing
 * the production constant breaks the tests instead of silently following along.
 */
public class TaskPrefixEmbeddingModel implements EmbeddingModel {

    public static final String DOCUMENT_PREFIX = "search_document: ";
    public static final String QUERY_PREFIX = "search_query: ";

    private static final double DEGENERATE_CONTENT_WEIGHT = 0.2;
    private static final double DEGENERATE_BASELINE_WEIGHT =
            Math.sqrt(1 - DEGENERATE_CONTENT_WEIGHT * DEGENERATE_CONTENT_WEIGHT);

    private final int dimension;
    private final boolean prefixRequired;
    private final List<String> sink;

    public TaskPrefixEmbeddingModel(String modelName, int dimension, List<String> sink) {
        boolean nomic = modelName != null && modelName.toLowerCase(Locale.ROOT).contains("nomic");
        this.dimension = dimension;
        this.prefixRequired = nomic;
        this.sink = sink;
    }

    @Override
    public Response<List<Embedding>> embedAll(List<TextSegment> segments) {
        List<Embedding> embeddings = new ArrayList<>(segments.size());
        for (TextSegment segment : segments) {
            sink.add(segment.text());
            embeddings.add(Embedding.from(vectorFor(segment.text())));
        }
        return Response.from(embeddings);
    }

    /**
     * The vector a stored chunk of {@code text} would have been written with by an ingestion that
     * already applies the convention. Tests seed {@code kb_embeddings_*} rows directly, so this is
     * how a row written by the fixed code is reproduced.
     */
    public static float[] documentVector(int dimension, String text) {
        return prefixedVector(dimension, DOCUMENT_PREFIX + text);
    }

    public float[] vectorFor(String text) {
        if (prefixRequired && !text.startsWith(DOCUMENT_PREFIX) && !text.startsWith(QUERY_PREFIX)) {
            float[] degenerate = new float[dimension];
            degenerate[0] = (float) DEGENERATE_BASELINE_WEIGHT;
            float[] content = prefixedVector(dimension, text);
            for (int i = 0; i < dimension; i++) {
                degenerate[i] += (float) (DEGENERATE_CONTENT_WEIGHT * content[i]);
            }
            return degenerate;
        }
        return prefixedVector(dimension, text);
    }

    /** What a prefixed text produces: the prefix steers the model, it is not part of the content. */
    private static float[] prefixedVector(int dimension, String text) {
        return contentVector(dimension, stripPrefix(text));
    }

    public static String stripPrefix(String text) {
        if (text.startsWith(DOCUMENT_PREFIX)) {
            return text.substring(DOCUMENT_PREFIX.length());
        }
        if (text.startsWith(QUERY_PREFIX)) {
            return text.substring(QUERY_PREFIX.length());
        }
        return text;
    }

    public static String vectorLiteral(float[] vector) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(vector[i]);
        }
        return sb.append(']').toString();
    }

    private static float[] contentVector(int dimension, String content) {
        float[] vector = new float[dimension];
        for (String token : tokenize(content)) {
            // index 0 is the degenerate direction, so content starts at 1
            vector[1 + Math.floorMod(token.hashCode(), dimension - 1)] += 1f;
        }
        float norm = 0;
        for (float value : vector) {
            norm += value * value;
        }
        if (norm == 0) {
            vector[1] = 1f;
            return vector;
        }
        for (int i = 0; i < dimension; i++) {
            vector[i] /= norm;
        }
        return vector;
    }

    private static List<String> tokenize(String text) {
        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (char c : text.toLowerCase(Locale.ROOT).toCharArray()) {
            if (Character.isLetterOrDigit(c)) {
                current.append(c);
            } else if (current.length() > 0) {
                tokens.add(current.toString());
                current.setLength(0);
            }
        }
        if (current.length() > 0) {
            tokens.add(current.toString());
        }
        return tokens;
    }
}
