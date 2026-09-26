package br.com.systec.hive.integrationtest;

import br.com.systec.hive.integrationtest.support.StubEmbeddingProviderConfig;
import br.com.systec.hive.integrationtest.support.TaskPrefixEmbeddingModel;
import br.com.systec.hive.integrationtest.support.WebIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.lessThan;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Playground search against real pgvector.
 *
 * <p>Only the embedding provider is faked ({@link StubEmbeddingProviderConfig}); the service, the
 * task-prefix convention, the tenant filter and the similarity query are all real. The stored chunk
 * is seeded with the vector the fixed ingestion would have written — prefixed document side — and the
 * query goes through the real service, so a query that loses its prefix lands in another space and
 * the assertions below fail. That is the whole point: with the old constant-vector stub every score
 * was 1.0 and this test proved nothing.
 */
@Import(StubEmbeddingProviderConfig.class)
class KnowledgeSearchIT extends WebIntegrationTest {

    private static final String PASSWORD = "it-secret-pw";
    private static final int DIMENSION = 1536;
    private static final String CHUNK_TEXT = "manual chunk text";
    private static final String RELATED_QUERY = "manual chunk";
    private static final String UNRELATED_QUERY = "protein folding rules";

    @BeforeEach
    void setUp() {
        StubEmbeddingProviderConfig.reset();
    }

    private String tokenFor(UUID tenantId, String username) {
        seedTenantAndUser(tenantId, username, PASSWORD);
        return jwtTestHelper.generateToken(username, tenantId);
    }

    private UUID createKb(String token, String name) throws Exception {
        String response = mockMvc.perform(post("/v1/knowledge-bases")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(response.split("\"id\":\"")[1].split("\"")[0]);
    }

    private UUID seedDocumentAndChunk(UUID tenantId, UUID kbId, String text) {
        UUID docId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO kb_document (id, tenant_id, kb_id, source_type, file_name, status) VALUES (?, ?, ?, ?, ?, ?)",
                docId, tenantId, kbId, "UPLOAD", "manual.txt", "READY");
        jdbcTemplate.update(
                "INSERT INTO kb_embeddings_1536 (kb_id, tenant_id, doc_id, chunk_index, embedding, text, metadata)"
                        + " VALUES (?, ?, ?, ?, CAST(? AS vector), ?, CAST(? AS jsonb))",
                kbId, tenantId, docId, 0,
                TaskPrefixEmbeddingModel.vectorLiteral(TaskPrefixEmbeddingModel.documentVector(DIMENSION, text)), text,
                "{\"tenant_id\":\"" + tenantId + "\",\"kb_id\":\"" + kbId + "\",\"doc_id\":\"" + docId
                        + "\",\"chunk_index\":0}");
        return docId;
    }

    private String search(UUID tenantId, String token, UUID kbId, String query) throws Exception {
        return mockMvc.perform(post("/v1/knowledge-bases/" + kbId + "/search")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"" + query + "\",\"topK\":5}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    void when_search_with_seededChunk_then_cited() throws Exception {
        UUID tenantId = UUID.randomUUID();
        String token = tokenFor(tenantId, "search");

        UUID kbId = createKb(token, "Docs");
        UUID docId = seedDocumentAndChunk(tenantId, kbId, CHUNK_TEXT);

        mockMvc.perform(post("/v1/knowledge-bases/" + kbId + "/search")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"" + RELATED_QUERY + "\",\"topK\":5}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].docId").value(docId.toString()))
                .andExpect(jsonPath("$[0].text").value(CHUNK_TEXT))
                // a real similarity, not the 1.0 a constant-vector stub always produced
                .andExpect(jsonPath("$[0].score").value(greaterThan(0.7)))
                .andExpect(jsonPath("$[0].score").value(lessThan(0.99)));

        // this base is on text-embedding-3-small, which takes raw text on both sides
        assertThat(StubEmbeddingProviderConfig.seenTexts()).containsExactly(RELATED_QUERY);
    }

    /** The cut-off has teeth: a question with nothing to do with the chunk retrieves nothing. */
    @Test
    void when_search_with_unrelatedQuery_then_nothingClearsTheCutOff() throws Exception {
        UUID tenantId = UUID.randomUUID();
        String token = tokenFor(tenantId, "search-unrelated");

        UUID kbId = createKb(token, "Docs");
        seedDocumentAndChunk(tenantId, kbId, CHUNK_TEXT);

        assertThat(search(tenantId, token, kbId, UNRELATED_QUERY)).isEqualTo("[]");
    }

    @Test
    void when_search_with_noChunks_then_empty() throws Exception {
        UUID tenantId = UUID.randomUUID();
        String token = tokenFor(tenantId, "search2");
        UUID kbId = createKb(token, "Empty");

        assertThat(search(tenantId, token, kbId, "anything")).isEqualTo("[]");
    }
}
