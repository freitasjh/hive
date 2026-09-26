package br.com.systec.hive.integrationtest;

import br.com.systec.hive.integrationtest.support.StubEmbeddingProviderConfig;
import br.com.systec.hive.integrationtest.support.TaskPrefixEmbeddingModel;
import br.com.systec.hive.integrationtest.support.WebIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.lessThan;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Regression test for the P0: a knowledge base on {@code nomic-embed-text} retrieves nothing useful
 * because no task prefix ever reached the model, and nothing in the suite noticed.
 *
 * <p>What makes it a regression test and not another green box: the stored vector is the one the
 * <b>prefixed</b> ingestion writes, and the query vector is produced by the real service through the
 * real convention. Remove the convention and the query lands in another space — the search returns
 * nothing and both tests below fail. The old suite could not do this: its stub returned one constant
 * vector, so every score was 1.0 whatever the code did.
 *
 * <p>Only the HTTP call to the provider is faked ({@link StubEmbeddingProviderConfig}).
 */
@Import(StubEmbeddingProviderConfig.class)
class KbEmbeddingConventionIT extends WebIntegrationTest {

    private static final String PASSWORD = "it-secret-pw";
    private static final String MODEL = "nomic-embed-text";
    private static final int DIMENSION = 768;
    private static final String CHUNK_TEXT = "reinicio do servico de busca do indice vetorial";
    private static final String RELATED_QUERY = "reinicio do servico de busca agora";
    private static final String UNRELATED_QUERY = "protein folding rules";

    @BeforeEach
    void setUp() {
        StubEmbeddingProviderConfig.reset();
    }

    @Test
    void when_search_aNomicBaseWithAMatchingQuestion_then_theChunkIsCited() throws Exception {
        UUID tenantId = UUID.randomUUID();
        seedTenantAndUser(tenantId, "convention", PASSWORD);
        String token = jwtTestHelper.generateToken("convention", tenantId);

        UUID kbId = createKb(token, "Runbook");
        UUID docId = seedDocumentAndChunk(tenantId, kbId, CHUNK_TEXT);

        mockMvc.perform(post("/v1/knowledge-bases/" + kbId + "/search")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"" + RELATED_QUERY + "\",\"topK\":5}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].docId").value(docId.toString()))
                .andExpect(jsonPath("$[0].score").value(greaterThan(0.7)))
                .andExpect(jsonPath("$[0].score").value(lessThan(0.99)));
    }

    @Test
    void when_search_aNomicBaseWithAnUnrelatedQuestion_then_nothingClearsTheCutOff() throws Exception {
        UUID tenantId = UUID.randomUUID();
        seedTenantAndUser(tenantId, "convention2", PASSWORD);
        String token = jwtTestHelper.generateToken("convention2", tenantId);

        UUID kbId = createKb(token, "Runbook");
        seedDocumentAndChunk(tenantId, kbId, CHUNK_TEXT);

        String body = mockMvc.perform(post("/v1/knowledge-bases/" + kbId + "/search")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"" + UNRELATED_QUERY + "\",\"topK\":5}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).isEqualTo("[]");
    }

    @Test
    void when_search_aNomicBase_then_theProviderOnlyEverSeesQueryPrefixedText() throws Exception {
        UUID tenantId = UUID.randomUUID();
        seedTenantAndUser(tenantId, "convention3", PASSWORD);
        String token = jwtTestHelper.generateToken("convention3", tenantId);

        UUID kbId = createKb(token, "Runbook");
        seedDocumentAndChunk(tenantId, kbId, CHUNK_TEXT);

        mockMvc.perform(post("/v1/knowledge-bases/" + kbId + "/search")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"" + RELATED_QUERY + "\",\"topK\":5}"))
                .andExpect(status().isOk());

        assertThat(StubEmbeddingProviderConfig.seenTexts())
                .isNotEmpty()
                .allSatisfy(text -> assertThat(text)
                        .as("a busca só embute consultas, e cada uma como consulta")
                        .startsWith(TaskPrefixEmbeddingModel.QUERY_PREFIX)
                        .doesNotStartWith(TaskPrefixEmbeddingModel.DOCUMENT_PREFIX));
    }

    // ------------------------------------------------------------------ the mixed-space detector

    /**
     * Ingestion is asynchronous, so a reindex that fails on some documents leaves a base holding
     * rows written under two conventions — with no error anywhere, and every score it returns
     * afterwards meaningless rather than merely bad. The {@code convention} key in the row metadata
     * is what turns that into something an operator can see; this runs the detection query from
     * {@link br.com.systec.hive.knowledge.impl.service.KbEmbeddingStore#metadataJson} against real
     * data.
     */
    @Test
    void when_aBaseHoldsRowsFromTwoConventions_then_theDetectionQueryFindsBoth() throws Exception {
        UUID tenantId = UUID.randomUUID();
        seedTenantAndUser(tenantId, "mixed", PASSWORD);
        String token = jwtTestHelper.generateToken("mixed", tenantId);

        UUID kbId = createKb(token, "Runbook");
        UUID freshDoc = seedDocumentAndChunk(tenantId, kbId, CHUNK_TEXT);
        // a document that predates the convention change and was never reindexed
        seedDocumentAndChunk(tenantId, kbId, CHUNK_TEXT + " legado", "plain");

        String stored = jdbcTemplate.queryForObject(
                "SELECT metadata->>'convention' FROM kb_embeddings_768"
                        + " WHERE tenant_id = ? AND kb_id = ? AND doc_id = ? AND chunk_index = 0",
                String.class, tenantId, kbId, freshDoc);
        assertThat(stored)
                .as("an ingested row states which space it was written in")
                .isEqualTo("nomic-task-prefix");

        List<Map<String, Object>> groups = jdbcTemplate.queryForList(
                "SELECT kb_id, metadata->>'convention' AS convention, count(*) AS chunks"
                        + " FROM kb_embeddings_768 WHERE tenant_id = ? GROUP BY 1, 2 ORDER BY 2",
                tenantId);

        assertThat(groups).hasSize(2);
        assertThat(groups).extracting(row -> row.get("convention"))
                .containsExactlyInAnyOrder("nomic-task-prefix", "plain");
    }

    // ------------------------------------------------------------------ helpers

    private UUID createKb(String token, String name) throws Exception {
        String body = mockMvc.perform(post("/v1/knowledge-bases")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\",\"embeddingModel\":\"" + MODEL + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(body.split("\"id\":\"")[1].split("\"")[0]);
    }

    private UUID seedDocumentAndChunk(UUID tenantId, UUID kbId, String text) {
        return seedDocumentAndChunk(tenantId, kbId, text, "nomic-task-prefix");
    }

    private UUID seedDocumentAndChunk(UUID tenantId, UUID kbId, String text, String convention) {
        UUID docId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO kb_document (id, tenant_id, kb_id, source_type, file_name, status)"
                        + " VALUES (?, ?, ?, ?, ?, ?)",
                docId, tenantId, kbId, "UPLOAD", "manual.txt", "READY");
        jdbcTemplate.update(
                "INSERT INTO kb_embeddings_768 (kb_id, tenant_id, doc_id, chunk_index, embedding, text, metadata)"
                        + " VALUES (?, ?, ?, ?, CAST(? AS vector), ?, CAST(? AS jsonb))",
                kbId, tenantId, docId, 0,
                TaskPrefixEmbeddingModel.vectorLiteral(TaskPrefixEmbeddingModel.documentVector(DIMENSION, text)),
                text,
                "{\"tenant_id\":\"" + tenantId + "\",\"kb_id\":\"" + kbId + "\",\"doc_id\":\"" + docId
                        + "\",\"chunk_index\":0,\"convention\":\"" + convention + "\"}");
        return docId;
    }
}
