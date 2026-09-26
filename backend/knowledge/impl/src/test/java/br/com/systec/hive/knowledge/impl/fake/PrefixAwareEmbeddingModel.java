package br.com.systec.hive.knowledge.impl.fake;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * {@link EmbeddingModel} double that reproduces the one property of a task-prefix model that broke
 * the knowledge retrieval: <b>text that arrives without its task prefix lands in a degenerate
 * space where nothing discriminates</b>.
 *
 * <p>It is a model of the <em>provider's contract</em>, not of our code, so it is allowed to know
 * the convention: a prefix-requiring family ({@code nomic*}) that receives an unprefixed text is
 * given a vector dominated by a constant direction plus a small amount of content. That is what the
 * real model does — and what the retrieval measured before the prefixes existed: relevant and
 * unrelated documents landing on top of each other.
 *
 * <p>With the prefix present the vector is a plain bag of content tokens, so the pair
 * "document vs its own question" scores high and an unrelated question scores near zero.
 *
 * <p>The prefix literals are written out on purpose instead of imported from
 * {@code EmbeddingTaskConvention}: this double encodes the model card, and if production ever
 * changes the string the test must fail rather than follow along.
 *
 * <p>Content tokens are hashed into dimensions {@code 1..n-1}; index {@code 0} is reserved for the
 * degenerate direction, which is what keeps the two subspaces exactly orthogonal.
 */
public class PrefixAwareEmbeddingModel implements EmbeddingModel {

    public static final String DOCUMENT_PREFIX = "search_document: ";
    public static final String QUERY_PREFIX = "search_query: ";

    /** Weight of the content in the degenerate space: small, so nothing discriminates there. */
    private static final double DEGENERATE_CONTENT_WEIGHT = 0.2;
    private static final double DEGENERATE_BASELINE_WEIGHT = Math.sqrt(1 - DEGENERATE_CONTENT_WEIGHT * DEGENERATE_CONTENT_WEIGHT);

    private final String modelName;
    private final int dimension;
    private final boolean prefixRequired;
    private final List<String> seenTexts = Collections.synchronizedList(new ArrayList<>());

    public PrefixAwareEmbeddingModel(String modelName, int dimension) {
        this.modelName = modelName == null ? "" : modelName;
        this.dimension = dimension;
        this.prefixRequired = this.modelName.toLowerCase(Locale.ROOT).contains("nomic");
    }

    @Override
    public Response<List<Embedding>> embedAll(List<TextSegment> segments) {
        List<Embedding> embeddings = new ArrayList<>(segments.size());
        for (TextSegment segment : segments) {
            String text = segment.text();
            seenTexts.add(text);
            embeddings.add(Embedding.from(vectorFor(text)));
        }
        return Response.from(embeddings);
    }

    /** Vector the double returns for {@code text}, following the rules described in the class doc. */
    public float[] vectorFor(String text) {
        String content = stripPrefix(text);
        float[] bag = tokenBag(content);
        if (prefixRequired && !hasPrefix(text)) {
            float[] degenerate = new float[dimension];
            degenerate[0] = (float) DEGENERATE_BASELINE_WEIGHT;
            for (int i = 0; i < dimension; i++) {
                degenerate[i] += (float) (DEGENERATE_CONTENT_WEIGHT * bag[i]);
            }
            return degenerate;
        }
        return bag;
    }

    /** Every text the double was asked to embed, in order. */
    public List<String> seenTexts() {
        synchronized (seenTexts) {
            return List.copyOf(seenTexts);
        }
    }

    public String lastSeenText() {
        synchronized (seenTexts) {
            return seenTexts.isEmpty() ? null : seenTexts.get(seenTexts.size() - 1);
        }
    }

    public boolean isPrefixRequired() {
        return prefixRequired;
    }

    public int dimension() {
        return dimension;
    }

    public static boolean hasPrefix(String text) {
        return text != null && (text.startsWith(DOCUMENT_PREFIX) || text.startsWith(QUERY_PREFIX));
    }

    public static String stripPrefix(String text) {
        if (text == null) {
            return "";
        }
        if (text.startsWith(DOCUMENT_PREFIX)) {
            return text.substring(DOCUMENT_PREFIX.length());
        }
        if (text.startsWith(QUERY_PREFIX)) {
            return text.substring(QUERY_PREFIX.length());
        }
        return text;
    }

    /** Cosine similarity, the same measure pgvector's {@code <=>} operator reports as a distance. */
    public static double cosine(float[] a, float[] b) {
        double dot = 0;
        double normA = 0;
        double normB = 0;
        for (int i = 0; i < Math.min(a.length, b.length); i++) {
            dot += (double) a[i] * b[i];
            normA += (double) a[i] * a[i];
            normB += (double) b[i] * b[i];
        }
        if (normA == 0 || normB == 0) {
            return 0;
        }
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    private float[] tokenBag(String content) {
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
            // a text with no tokens still has to produce a usable, non-zero vector
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
