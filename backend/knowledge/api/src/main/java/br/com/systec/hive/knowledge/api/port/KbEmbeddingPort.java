package br.com.systec.hive.knowledge.api.port;

import java.util.UUID;

/**
 * Turns text into a vector, in the two roles a knowledge base needs: the text that gets stored
 * and the text a user types.
 *
 * <p>The two roles are <b>not</b> interchangeable. Some models — {@code nomic-embed-text} among them
 * — map a document and a question into different sub-spaces and only line up when <em>each</em> side
 * carries its own task prefix ({@code search_document:} / {@code search_query:}). A single
 * {@code embed(...)} serving both roles cannot express that, and the failure is silent: retrieval
 * runs, returns chunks, and simply stops discriminating — relevant and unrelated documents land at
 * the same score. Hence two explicit methods: a caller has to declare which side it is on.
 *
 * <p>No {@code embed(...)} shorthand is kept on purpose. It was the ambiguity that let the whole
 * pipeline run for months with the wrong convention applied to both sides.
 *
 * <p><b>P2 risk, still open:</b> the task-prefix convention is not versioned per knowledge base.
 * Vectors written under one convention live in a different space than vectors written under
 * another, and a base holding both is not degraded — it is undefined: every score is meaningless.
 * Whenever the convention of a model's family changes, every base using it must be reindexed
 * whole. Until the schema carries a convention version per base, that stays a manual operation.
 */
public interface KbEmbeddingPort {

    /**
     * Embeds text that will be stored and later searched against.
     *
     * @param tenantId owner of the knowledge base, {@code null} for a global lookup
     * @param model    embedding model name
     * @param dimension expected vector length
     * @param text     chunk content
     * @return the vector, of exactly {@code dimension} components
     */
    float[] embedDocument(UUID tenantId, String model, int dimension, String text);

    /**
     * Embeds the search text of a retrieval.
     *
     * @param tenantId owner of the knowledge bases being searched, {@code null} for a global lookup
     * @param model    embedding model name
     * @param dimension expected vector length
     * @param text     the user's question
     * @return the vector, of exactly {@code dimension} components
     */
    float[] embedQuery(UUID tenantId, String model, int dimension, String text);

    /** Vector length a model produces. */
    int dimensionFor(String model);
}
