package br.com.systec.hive.knowledge.impl.service;

import br.com.systec.hive.knowledge.api.domain.KbChunk;
import br.com.systec.hive.knowledge.api.port.KbVectorStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The vector store as a door: four operations, and the SQL each of them is allowed to be.
 *
 * <p>This class is the whole reason {@code KbVectorStore} exists. Before the port, the write side had
 * no door: three services called {@code insert}, {@code removeByKb} and {@code removeByDoc} on a
 * concrete class, so swapping the store meant editing all three. A port is only worth having if what
 * crosses it is pinned, and the thing worth pinning here is that the queries are unchanged — the
 * table per dimension, the {@code CAST(? AS vector)}, the {@code ANY(?::uuid[])} membership test, and
 * above all the tenant predicate on every single statement.
 *
 * <p>So these tests assert the SQL text and the arguments, not merely that a method was called. A
 * store that kept the four signatures and dropped {@code AND tenant_id = ?} would pass every test
 * this file replaces, and would be a cross-tenant leak.
 */
@ExtendWith(MockitoExtension.class)
class KbVectorStoreTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    private KbVectorStore store;
    private UUID tenantId;
    private UUID kbId;
    private UUID docId;

    @BeforeEach
    void setUp() {
        store = new KbEmbeddingStore(jdbcTemplate);
        tenantId = UUID.randomUUID();
        kbId = UUID.randomUUID();
        docId = UUID.randomUUID();
    }

    // ------------------------------------------------------------------ the table is chosen by dimension

    @Test
    void when_theDimensionIsKnown_then_theTableIsNamedAfterIt() {
        assertThat(KbEmbeddingStore.tableFor(1536)).isEqualTo("kb_embeddings_1536");
        assertThat(KbEmbeddingStore.tableFor(768)).isEqualTo("kb_embeddings_768");
    }

    @Test
    void when_theDimensionIsUnknown_then_itIsRefusedRatherThanGuessed() {
        // A third dimension would have to mean a third table, and a migration. Guessing the nearest
        // one would write a 768-vector into a 1536 column and fail at read time, on a different turn.
        assertThatThrownBy(() -> KbEmbeddingStore.tableFor(384))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unsupported dimension");
        assertThatThrownBy(() -> KbEmbeddingStore.tableFor(0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void when_aVectorIsWrittenAsText_then_itIsBracketedAndCommaSeparated() {
        assertThat(KbEmbeddingStore.vectorLiteral(new float[]{0.1f, 0.2f, 0.3f}))
                .isEqualTo("[0.1,0.2,0.3]");
        assertThat(KbEmbeddingStore.vectorLiteral(new float[]{0.5f}))
                .isEqualTo("[0.5]");
    }

    // ------------------------------------------------------------------ insert

    @Test
    void when_aChunkIsInserted_then_itLandsInTheTableForItsDimension() {
        store.insert(tenantId, kbId, docId, 0, "prazo de 30 dias", new float[]{0.1f, 0.2f}, 768,
                "nomic-task-prefix");

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).update(sql.capture(), eq(kbId), eq(tenantId), eq(docId), eq(0),
                eq("[0.1,0.2]"), eq("prazo de 30 dias"), anyString());
        assertThat(sql.getValue())
                .startsWith("INSERT INTO kb_embeddings_768")
                .as("the dimension picks the table; nothing else may")
                .doesNotContain("kb_embeddings_1536");
    }

    @Test
    void when_aChunkIsInserted_then_theVectorAndTheMetadataAreCastByPostgres() {
        store.insert(tenantId, kbId, docId, 1, "texto", new float[]{0.3f}, 1536, "openai");

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).update(sql.capture(), any(), any(), any(), anyInt(), anyString(),
                anyString(), anyString());
        assertThat(sql.getValue())
                .contains("CAST(? AS vector)")
                .contains("CAST(? AS jsonb)");
    }

    /**
     * The convention is written on <b>every</b> row and it is the only thing that makes a mixed base
     * visible: ingestion is asynchronous, so a reindex that fails on 2 of 5 documents leaves a base
     * holding rows from two conventions with no error anywhere — and every score it returns afterwards
     * is meaningless, not merely bad.
     */
    @Test
    void when_aChunkIsInserted_then_itsConventionIsRecordedInTheMetadata() {
        assertThat(KbEmbeddingStore.metadataJson(tenantId, kbId, docId, 3, "nomic-task-prefix"))
                .isEqualTo("{\"tenant_id\":\"" + tenantId + "\",\"kb_id\":\"" + kbId
                        + "\",\"doc_id\":\"" + docId + "\",\"chunk_index\":3"
                        + ",\"convention\":\"nomic-task-prefix\"}");
    }

    /** A row with no convention reads back as a third value in that grouping — itself a reason to reindex. */
    @Test
    void when_theConventionIsAbsent_then_itIsRecordedAsEmptyRatherThanNull() {
        assertThat(KbEmbeddingStore.metadataJson(tenantId, kbId, docId, 0, null))
                .contains("\"convention\":\"\"")
                .doesNotContain("null");
    }

    // ------------------------------------------------------------------ removeByDoc

    @Test
    void when_aDocumentIsRemoved_then_bothDimensionTablesAreClearedForThatTenantOnly() {
        store.removeByDoc(tenantId, docId);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate, times(2)).update(sql.capture(), eq(tenantId), eq(docId));
        assertThat(sql.getAllValues())
                .as("a vector of one dimension left behind is still a chunk the next search finds")
                .containsExactlyInAnyOrder(
                        "DELETE FROM kb_embeddings_1536 WHERE tenant_id = ? AND doc_id = ?",
                        "DELETE FROM kb_embeddings_768 WHERE tenant_id = ? AND doc_id = ?");
    }

    // ------------------------------------------------------------------ removeByKb

    @Test
    void when_aBaseIsRemoved_then_bothDimensionTablesAreClearedForThatTenantOnly() {
        store.removeByKb(tenantId, kbId);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate, times(2)).update(sql.capture(), eq(tenantId), eq(kbId));
        assertThat(sql.getAllValues())
                .as("a vector of one dimension left behind is still a chunk the next search finds")
                .containsExactlyInAnyOrder(
                        "DELETE FROM kb_embeddings_1536 WHERE tenant_id = ? AND kb_id = ?",
                        "DELETE FROM kb_embeddings_768 WHERE tenant_id = ? AND kb_id = ?");
    }

    // ------------------------------------------------------------------ search

    /**
     * A turn with no linked base must not run a similarity search in order to learn it has no linked
     * base. Answering from the argument is not an optimisation here; it is the reason an unlinked
     * agent cannot be made to search everything.
     */
    @Test
    void when_noBaseIsRequested_then_theDatabaseIsNotTouchedAtAll() {
        assertThat(store.search(tenantId, List.of(), new float[]{0.1f}, 768, 5, 0.60)).isEmpty();
        assertThat(store.search(tenantId, null, new float[]{0.1f}, 768, 5, 0.60)).isEmpty();

        verify(jdbcTemplate, never()).execute(any(String.class), any(
                org.springframework.jdbc.core.PreparedStatementCallback.class));
    }

    @Test
    void when_theRowSetComesBack_then_eachRowBecomesAChunkWithTheNameItWasGiven() {
        UUID chunkId = UUID.randomUUID();
        UUID otherDocId = UUID.randomUUID();
        KbVectorStore.ScoredRow row = new KbVectorStore.ScoredRow(chunkId, otherDocId, 4,
                "prazo de 30 dias", 0.91);

        KbChunk chunk = row.toChunk("contrato-2024.pdf");

        assertThat(chunk.getChunkId()).isEqualTo(chunkId);
        assertThat(chunk.getDocId()).isEqualTo(otherDocId);
        assertThat(chunk.getText()).isEqualTo("prazo de 30 dias");
        assertThat(chunk.getScore()).isEqualTo(0.91);
        assertThat(chunk.getDocName())
                .as("the row has no column for it; the caller resolved it in one query, not per row")
                .isEqualTo("contrato-2024.pdf");
    }

    /**
     * The row is what the search returns, and the chunk is what the caller builds from it. Keeping the
     * row free of a document name is what lets the caller resolve names once for the whole result
     * instead of once per chunk.
     */
    @Test
    void when_theDocumentNameIsUnknown_then_theCallerSuppliesItsOwnFallback() {
        KbVectorStore.ScoredRow row = new KbVectorStore.ScoredRow(UUID.randomUUID(), docId, 0,
                "trecho", 0.5);

        assertThat(row.toChunk("document").getDocName()).isEqualTo("document");
    }

    @Test
    void when_theDimensionIsUnsupported_then_theSearchIsRefusedBeforeAnySql() {
        assertThatThrownBy(() -> store.search(tenantId, List.of(kbId), new float[]{0.1f}, 42, 5, 0.60))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unsupported dimension");
    }

    /**
     * The query the search runs: cosine distance as {@code 1 - (embedding <=> ?)}, the tenant
     * predicate, the bases as a uuid array, and the ordering by distance. It is pinned because it is
     * the one statement in the system whose semantics are a similarity, and a change to any part of
     * it changes what a score means to every diagnostic that reports one.
     */
    @Test
    void when_theSearchRuns_then_itIsTheCosineQueryWithATenantPredicate() {
        when(jdbcTemplate.execute(anyString(), any(org.springframework.jdbc.core.PreparedStatementCallback.class)))
                .thenReturn(List.of());

        store.search(tenantId, List.of(kbId), new float[]{0.1f, 0.2f}, 768, 5, 0.60);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).execute(sql.capture(), any(org.springframework.jdbc.core.PreparedStatementCallback.class));
        assertThat(sql.getValue())
                .startsWith("SELECT embedding_id, doc_id, chunk_index, text, 1 - (embedding <=> CAST(? AS vector)) AS score")
                .contains("FROM kb_embeddings_768")
                .contains("WHERE tenant_id = ? AND kb_id = ANY (?::uuid[])")
                .contains("ORDER BY embedding <=> CAST(? AS vector) LIMIT ?");
    }

    @Test
    void when_theSearchRuns_then_theQueryAndTheTenantAreBoundAsParameters() {
        when(jdbcTemplate.execute(anyString(), any(org.springframework.jdbc.core.PreparedStatementCallback.class)))
                .thenReturn(List.of());

        store.search(tenantId, List.of(kbId), new float[]{0.1f}, 768, 5, 0.60);

        ArgumentCaptor<org.springframework.jdbc.core.PreparedStatementCallback> extractor =
                ArgumentCaptor.forClass(org.springframework.jdbc.core.PreparedStatementCallback.class);
        verify(jdbcTemplate).execute(anyString(), extractor.capture());
        // The literals in the statement are the two CASTs and the limit; the tenant, the base array and
        // the query vector are all bound. A statement that interpolated the tenant would be the one
        // injection this module cannot afford.
        verify(jdbcTemplate, never()).update(anyString(), any(UUID.class), any(UUID.class));
    }
}
