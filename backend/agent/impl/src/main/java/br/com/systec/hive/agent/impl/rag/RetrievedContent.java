package br.com.systec.hive.agent.impl.rag;

import br.com.systec.hive.knowledge.api.domain.KbChunk;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.ContentMetadata;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * One retrieved chunk, in the shape the official RAG pipeline passes between its stages: a
 * {@link TextSegment} plus the supplementary metadata the {@link dev.langchain4j.rag.content.Content}
 * contract defines.
 *
 * <h3>Why this is not {@code DefaultContent}</h3>
 *
 * <p>{@code DefaultContent.equals} compares the text segment only, on purpose: two chunks with the
 * same text are the same piece of knowledge. The official {@code ContentAggregator} relies on that —
 * {@code ReciprocalRankFuser} keeps its scores in a {@code Map<Content, Double>}, so equal contents
 * are the <em>same key</em> and the second occurrence is folded into the first.
 *
 * <p>That is right for fusion and wrong for us. We are not fusing: there is exactly one query and
 * one retriever, and the number of contents that comes out of the aggregator is the number of
 * chunks that goes into the prompt and into {@code citations}. Two chunks with identical text in
 * different documents (boilerplate, a repeated policy paragraph, a retried ingestion) would be
 * deduplicated by {@code DefaultContent}, the prompt would silently lose one of them, and
 * {@code citations} — which the client shows as "sources" — would stop matching what the model saw.
 * {@code RagRetrievalResult} keeps both chunks, so a loss here would be a contradiction between the
 * diagnostic and the prompt.
 *
 * <p>So this class deliberately does <b>not</b> override {@code equals}/{@code hashCode}: identity
 * equality is what makes the 1:1 chunk↔content correspondence hold through the aggregator. Nothing
 * downstream de-duplicates by content, so identity costs nothing and keeps the count honest.
 */
public final class RetrievedContent implements Content {

    /** Segment metadata key holding the source document's file name. */
    public static final String DOC_NAME = "doc_name";
    /** Segment metadata key holding the source document's id. */
    public static final String DOC_ID = "doc_id";
    /** Segment metadata key holding the chunk's own id. */
    public static final String CHUNK_ID = "chunk_id";

    private final TextSegment textSegment;
    private final Map<ContentMetadata, Object> metadata;
    private final double score;

    private RetrievedContent(TextSegment textSegment, Map<ContentMetadata, Object> metadata, double score) {
        this.textSegment = textSegment;
        this.metadata = metadata;
        this.score = score;
    }

    /**
     * Wraps a retrieved chunk. The score is kept twice on purpose: in {@link ContentMetadata#SCORE},
     * where the official components read it, and as {@link #score()}, because a ranker in this
     * codebase must not have to know that the value is also a map entry.
     */
    public static RetrievedContent of(KbChunk chunk) {
        Objects.requireNonNull(chunk, "chunk");
        String text = chunk.getText() == null ? "" : chunk.getText();
        // Only the keys that exist: the metadata rejects a null value, and a chunk without a document
        // id is still a chunk the model can read — losing it over a missing key would be a retrieval
        // decision made for the wrong reason.
        Metadata segmentMetadata = new Metadata()
                .put(DOC_NAME, chunk.getDocName() == null ? "document" : chunk.getDocName());
        if (chunk.getDocId() != null) {
            segmentMetadata.put(DOC_ID, chunk.getDocId().toString());
        }
        if (chunk.getChunkId() != null) {
            segmentMetadata.put(CHUNK_ID, chunk.getChunkId().toString());
        }
        return new RetrievedContent(
                TextSegment.from(text, segmentMetadata),
                Map.of(ContentMetadata.SCORE, chunk.getScore()),
                chunk.getScore());
    }

    /** Whether this chunk carries text a {@code TextSegment} can hold. */
    public static boolean isInjectable(KbChunk chunk) {
        return chunk != null && chunk.getText() != null && !chunk.getText().isBlank();
    }

    @Override
    public TextSegment textSegment() {
        return textSegment;
    }

    @Override
    public Map<ContentMetadata, Object> metadata() {
        return metadata;
    }

    /** Similarity the vector search reported for this chunk. */
    public double score() {
        return score;
    }

    /** The source document's file name, or {@code "document"} when the retrieval did not report one. */
    public String docName() {
        Object value = textSegment.metadata().toMap().get(DOC_NAME);
        return value == null ? "document" : String.valueOf(value);
    }

    /** The source document's id, or {@code null} when the retrieval did not report one. */
    public UUID docId() {
        return uuid(DOC_ID);
    }

    /** The chunk's own id, or {@code null} when the retrieval did not report one. */
    public UUID chunkId() {
        return uuid(CHUNK_ID);
    }

    private UUID uuid(String key) {
        Object value = textSegment.metadata().toMap().get(key);
        if (value == null) {
            return null;
        }
        try {
            return UUID.fromString(String.valueOf(value));
        } catch (IllegalArgumentException e) {
            // A malformed id is a retrieval defect, not a reason to lose the chunk: the text is what
            // the model needs, and the diagnostic already carries the count.
            return null;
        }
    }
}
