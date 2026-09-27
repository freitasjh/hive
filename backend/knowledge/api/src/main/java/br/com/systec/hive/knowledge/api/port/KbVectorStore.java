package br.com.systec.hive.knowledge.api.port;

import br.com.systec.hive.knowledge.api.domain.KbChunk;

import java.util.List;
import java.util.UUID;

/**
 * The vector store as a <b>door, on both sides</b>: everything that reads or writes a chunk
 * embedding goes through here.
 *
 * <h3>Why this exists</h3>
 *
 * <p>The read side already had a door ({@link KbRetrievalPort}, a service-level port) and the write
 * side had none. {@code insert}, {@code removeByKb} and {@code removeByDoc} were called directly, by
 * name, on a concrete {@code KbEmbeddingStore} class from three different services — the ingestion
 * service, the document service and the knowledge-base service. That is what made moving off
 * pgvector an expensive proposition: swapping the store meant editing every one of those call sites
 * rather than one binding, and it is the reason the ingestion path could be rewritten with the read
 * path already behind a port while the write path stayed welded to a class.
 *
 * <p>With this interface the three services depend on the operation, not on the vendor, and the
 * implementation below is the only class that knows a vector column is a {@code vector} cast in a
 * table named after the dimension.
 *
 * <h3>What is deliberately not here</h3>
 *
 * <p>No policy. {@link #search} takes an already-resolved cut-off because the caller of a search is
 * {@link KbRetrievalPort}, whose job is exactly that resolution; a store has no business deciding
 * what "close enough" means for a model it does not know. And no chunk-to-document-name lookup: that
 * is a {@code kb_document} question, and the store has no business reading another table.
 *
 * <p>Not an {@code EmbeddingStore}: that SPI is five abstract methods, its {@code add} changed return
 * type between 1.19.0 and 1.20 (so the {@code embedding_id = chunkId} reconciliation the citations
 * depend on could not be written against it), and its {@code removeAll(Filter)} is a {@code default}
 * that throws {@code UnsupportedOperationException} — which would turn a delete by knowledge base
 * into a silent no-op. A door this project owns is a door it can keep.
 */
public interface KbVectorStore {

    /**
     * The nearest chunks to {@code query}, most similar first.
     *
     * <p>An empty {@code kbIds} is answered with an empty list without touching the database — a
     * turn with no linked base must not run a similarity search to learn it has no linked base.
     *
     * @param minScore the cut-off to apply, <b>already resolved</b> by the caller. Rows scoring below
     *                 it are dropped here, not by the caller, because a store is what can see them.
     */
    List<ScoredRow> search(UUID tenantId, List<UUID> kbIds, float[] query, int dimension,
                           int maxResults, double minScore);

    /**
     * Stores one chunk's embedding.
     *
     * @param convention which task-prefix rule produced this vector, written on <b>every</b> row: it
     *                   is the only thing that makes a half-failed asynchronous reindex visible,
     *                   because nothing else in the row records it
     */
    void insert(UUID tenantId, UUID kbId, UUID docId, int chunkIndex, String text, float[] vector,
                int dimension, String convention);

    /** Drops every chunk of one knowledge base, across all dimensions. */
    void removeByKb(UUID tenantId, UUID kbId);

    /** Drops every chunk of one document, across all dimensions. */
    void removeByDoc(UUID tenantId, UUID docId);

    /**
     * One row of a vector search, before the document it belongs to has been named.
     *
     * <p>{@code docName} is absent on purpose: the row comes from a table that has no column for it,
     * and the caller already loads the names in one query rather than per row. {@link #toChunk} takes
     * the name it resolved, or the caller's fallback.
     */
    record ScoredRow(UUID chunkId, UUID docId, int chunkIndex, String text, double score) {

        public KbChunk toChunk(String docName) {
            KbChunk chunk = new KbChunk();
            chunk.setChunkId(chunkId);
            chunk.setDocId(docId);
            chunk.setDocName(docName);
            chunk.setText(text);
            chunk.setScore(score);
            return chunk;
        }
    }
}
