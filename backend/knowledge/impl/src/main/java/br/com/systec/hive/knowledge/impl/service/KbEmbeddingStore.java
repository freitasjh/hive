package br.com.systec.hive.knowledge.impl.service;

import br.com.systec.hive.knowledge.api.port.KbVectorStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * The only {@link KbVectorStore} there is: chunk embeddings in PostgreSQL, one table per dimension.
 *
 * <p>Every piece of vendor knowledge in the knowledge module is in this class — the {@code vector}
 * cast, the {@code <=>} distance operator, the table named after the dimension, the {@code ANY(?)
 * ::uuid[]} membership test. Nothing else in the codebase writes or reads a vector, and the port
 * ({@code KbVectorStore}) is what lets that stay true while three services call it by operation
 * instead of by class.
 */
@Repository
public class KbEmbeddingStore implements KbVectorStore {

    private final JdbcTemplate jdbcTemplate;

    public KbEmbeddingStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public static String tableFor(int dimension) {
        if (dimension == 1536) {
            return "kb_embeddings_1536";
        }
        if (dimension == 768) {
            return "kb_embeddings_768";
        }
        throw new IllegalArgumentException("unsupported dimension: " + dimension);
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

    @Override
    public void insert(UUID tenantId, UUID kbId, UUID docId, int chunkIndex, String text, float[] vector,
                       int dimension, String convention) {
        String table = tableFor(dimension);
        String metadata = metadataJson(tenantId, kbId, docId, chunkIndex, convention);
        jdbcTemplate.update("INSERT INTO " + table
                + " (embedding_id, kb_id, tenant_id, doc_id, chunk_index, embedding, text, metadata)"
                + " VALUES (uuid_generate_v4(), ?, ?, ?, ?, CAST(? AS vector), ?, CAST(? AS jsonb))",
                kbId, tenantId, docId, chunkIndex, vectorLiteral(vector), text, metadata);
    }

    /**
     * The JSONB written next to every chunk.
     *
     * <p>{@code convention} records which task-prefix rule produced this vector. It is the only
     * thing that makes a mixed base visible: ingestion is asynchronous, so a reindex that fails on
     * 2 of 5 documents leaves a base holding rows from two conventions and no error anywhere —
     * every score it returns afterwards is meaningless, not merely bad. The column already exists,
     * so this costs no migration. To find the damage:
     *
     * <pre>{@code
     * SELECT kb_id, metadata->>'convention' AS convention, count(*)
     * FROM kb_embeddings_768            -- and kb_embeddings_1536
     * WHERE tenant_id = ?
     * GROUP BY 1, 2
     * ORDER BY 1;
     * -- more than one row per kb_id: mixed space, reindex the whole base
     * }</pre>
     *
     * <p>Rows written before this field existed have no {@code convention} key and read back as
     * {@code null}, which is a third value in that grouping — itself a reason to reindex.
     */
    static String metadataJson(UUID tenantId, UUID kbId, UUID docId, int chunkIndex, String convention) {
        return "{\"tenant_id\":\"" + tenantId + "\",\"kb_id\":\"" + kbId
                + "\",\"doc_id\":\"" + docId + "\",\"chunk_index\":" + chunkIndex
                + ",\"convention\":\"" + (convention == null ? "" : convention) + "\"}";
    }

    @Override
    public void removeByKb(UUID tenantId, UUID kbId) {
        for (String table : List.of("kb_embeddings_1536", "kb_embeddings_768")) {
            jdbcTemplate.update("DELETE FROM " + table + " WHERE tenant_id = ? AND kb_id = ?", tenantId, kbId);
        }
    }

    @Override
    public void removeByDoc(UUID tenantId, UUID docId) {
        for (String table : List.of("kb_embeddings_1536", "kb_embeddings_768")) {
            jdbcTemplate.update("DELETE FROM " + table + " WHERE tenant_id = ? AND doc_id = ?", tenantId, docId);
        }
    }

    @Override
    public List<ScoredRow> search(UUID tenantId, List<UUID> kbIds, float[] query, int dimension, int maxResults, double minScore) {
        if (kbIds == null || kbIds.isEmpty()) {
            return List.of();
        }
        String table = tableFor(dimension);
        String literal = vectorLiteral(query);
        String sql = "SELECT embedding_id, doc_id, chunk_index, text,"
                + " 1 - (embedding <=> CAST(? AS vector)) AS score"
                + " FROM " + table
                + " WHERE tenant_id = ? AND kb_id = ANY (?::uuid[])"
                + " ORDER BY embedding <=> CAST(? AS vector) LIMIT ?";
        return jdbcTemplate.execute(sql, (PreparedStatement ps) -> {
            Connection conn = ps.getConnection();
            Array arr = conn.createArrayOf("uuid", kbIds.toArray());
            try {
                ps.setString(1, literal);
                ps.setObject(2, tenantId);
                ps.setArray(3, arr);
                ps.setString(4, literal);
                ps.setInt(5, maxResults);
                List<ScoredRow> rows = new ArrayList<>();
                try (var rs = ps.executeQuery()) {
                    while (rs.next()) {
                        double score = rs.getDouble("score");
                        if (score < minScore) {
                            continue;
                        }
                        ScoredRow row = new ScoredRow(
                                UUID.fromString(rs.getString("embedding_id")),
                                UUID.fromString(rs.getString("doc_id")),
                                rs.getInt("chunk_index"),
                                rs.getString("text"),
                                score);
                        rows.add(row);
                    }
                }
                return rows;
            } finally {
                try {
                    arr.free();
                } catch (Exception ignored) {
                }
            }
        });
    }

    public static String localeRoot() {
        return Locale.ROOT.getLanguage();
    }
}
