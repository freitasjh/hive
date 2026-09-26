package br.com.systec.hive.integrationtest;

import br.com.systec.hive.integrationtest.support.StubEmbeddingProviderConfig;
import br.com.systec.hive.integrationtest.support.TaskPrefixEmbeddingModel;
import br.com.systec.hive.integrationtest.support.WebIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.lessThan;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The similarity cut-off, exercised end to end against real pgvector — the part of it that only
 * shows up in a real vector search, which is that a number has to actually decide something.
 *
 * <p><b>Why the texts are engineered.</b> A chunk that is merely "related" scores around 0.9 in the
 * double this module uses, well clear of any cut-off, so such a test passes before the fix and after
 * it and proves nothing. These are built to land on the measured numbers of a real
 * {@code nomic-embed-text} base, where relevance and noise sit only ~0.02 apart:
 *
 * <pre>
 * relevant long question        0.6481
 * relevant short question       0.6219
 * unrelated ("banco de dados")  0.5972
 * unrelated ("protein folding") 0.5851
 * </pre>
 *
 * <p>With a bag-of-words geometry, {@code cosine = shared / sqrt(distinct_doc * distinct_query)}, so
 * the pairs below are chosen rather than searched for: eight distinct tokens in the chunk, and a
 * question that shares five of them plus three more (0.625), or plus four more (0.589). The first
 * clears the 0.60 cut-off this family needs; the second is 0.011 below it. The margin is genuinely
 * narrow — that is the measurement being pinned, not a comfortable number.
 *
 * <p>Only the provider is faked ({@link StubEmbeddingProviderConfig}): the cut-off resolution, the
 * service, the task-prefix convention, the tenant filter and the {@code <=>} query are all real.
 */
@Import(StubEmbeddingProviderConfig.class)
class KbSimilarityThresholdIT extends WebIntegrationTest {

    private static final String PASSWORD = "it-secret-pw";
    private static final String NOMIC = "nomic-embed-text";
    private static final String OPENAI = "text-embedding-3-small";
    private static final int NOMIC_DIMENSION = 768;
    private static final int OPENAI_DIMENSION = 1536;

    /** Eight distinct tokens: the whole chunk's weight is its eight unit components. */
    private static final String CHUNK = "reinicio servico busca indice vetorial manual operacao backup";

    /** Five of the chunk's eight, plus three: 5 / sqrt(8 * 8) = 0.625. */
    private static final String QUESTION_ABOVE_THE_NOMIC_CUT_OFF =
            "reinicio servico busca indice vetorial logs alerta falha";

    /** The same five, plus four: 5 / sqrt(8 * 9) = 0.589, which is under 0.60. */
    private static final String QUESTION_BELOW_THE_NOMIC_CUT_OFF =
            "reinicio servico busca indice vetorial relatorio latencia fila cache";

    @BeforeEach
    void setUp() {
        StubEmbeddingProviderConfig.reset();
    }

    @Test
    void when_aNomicBaseIsAskedTheMeasuredRelevantQuestion_then_itIsCited() throws Exception {
        UUID tenantId = UUID.randomUUID();
        String token = tokenFor(tenantId, "threshold-above");
        UUID kbId = createKb(token, "Runbook", NOMIC);
        UUID docId = seedDocumentAndChunk(tenantId, kbId, CHUNK, NOMIC_DIMENSION);

        mockMvc.perform(search(kbId, token, QUESTION_ABOVE_THE_NOMIC_CUT_OFF))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].docId").value(docId.toString()))
                // the measured number, not the artificial 1.0 a constant-vector stub always produced
                .andExpect(jsonPath("$[0].score").value(greaterThan(0.60)))
                .andExpect(jsonPath("$[0].score").value(greaterThan(0.62)))
                .andExpect(jsonPath("$[0].score").value(lessThan(0.63)));
    }

    /**
     * The half of the same pair that a 0.70 cut-off would have got right by accident, and a 0.60 one
     * must still get right: 0.589 is 0.011 under the family's measured cut-off, so the chunk is
     * correctly left out. Move the number to 0.58 and this test fails on the retrieval, not on a
     * rounding difference.
     */
    @Test
    void when_aNomicBaseIsAskedTheMeasuredUnrelatedQuestion_then_nothingIsCited() throws Exception {
        UUID tenantId = UUID.randomUUID();
        String token = tokenFor(tenantId, "threshold-below");
        UUID kbId = createKb(token, "Runbook", NOMIC);
        seedDocumentAndChunk(tenantId, kbId, CHUNK, NOMIC_DIMENSION);

        assertThat(searchBody(kbId, token, QUESTION_BELOW_THE_NOMIC_CUT_OFF)).isEqualTo("[]");
    }

    /**
     * The reason the cut-off is resolved from the model's row and not held in one place: the chunk,
     * the question and the geometry are <b>identical</b> to the test above, so the only thing that
     * differs is which model the base is indexed with. The nomic base cites it; the OpenAI base does
     * not, because 0.70 is the right number for that family and was the right number here all along.
     *
     * <p>Before the fix both bases ran at 0.70 and the nomic one rejected a pair the measurement
     * calls relevant — the bug, in one assertion.
     */
    @Test
    void when_theSameChunkAndQuestionAreSearchedOnAnOpenAiBase_then_theSamePairIsRejected() throws Exception {
        UUID tenantId = UUID.randomUUID();
        String token = tokenFor(tenantId, "threshold-openai");
        UUID kbId = createKb(token, "Docs", OPENAI);
        seedDocumentAndChunk(tenantId, kbId, CHUNK, OPENAI_DIMENSION);

        assertThat(searchBody(kbId, token, QUESTION_ABOVE_THE_NOMIC_CUT_OFF))
                .as("0.625 is above the nomic cut-off and below the one OpenAI has always used")
                .isEqualTo("[]");
    }

    /**
     * The control for the test above: the OpenAI cut-off is not simply rejecting everything, it is
     * rejecting pairs it has no reason to accept. A question this base really answers still comes
     * back.
     */
    @Test
    void when_anOpenAiBaseIsAskedSomethingItReallyHas_then_itIsCited() throws Exception {
        UUID tenantId = UUID.randomUUID();
        String token = tokenFor(tenantId, "threshold-openai-hit");
        UUID kbId = createKb(token, "Docs", OPENAI);
        UUID docId = seedDocumentAndChunk(tenantId, kbId, "reinicio servico busca indice",
                OPENAI_DIMENSION);

        mockMvc.perform(search(kbId, token, "reinicio servico busca indice agora"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].docId").value(docId.toString()))
                .andExpect(jsonPath("$[0].score").value(greaterThan(0.70)));
    }

    // ------------------------------------------------------------------ helpers

    private MockHttpServletRequestBuilder search(UUID kbId, String token, String query) {
        return post("/v1/knowledge-bases/" + kbId + "/search")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"query\":\"" + query + "\",\"topK\":5}");
    }

    private String searchBody(UUID kbId, String token, String query) throws Exception {
        return mockMvc.perform(search(kbId, token, query))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private String tokenFor(UUID tenantId, String username) {
        seedTenantAndUser(tenantId, username, PASSWORD);
        return jwtTestHelper.generateToken(username, tenantId);
    }

    private UUID createKb(String token, String name, String model) throws Exception {
        String body = mockMvc.perform(post("/v1/knowledge-bases")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\",\"embeddingModel\":\"" + model + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(body.split("\"id\":\"")[1].split("\"")[0]);
    }

    /**
     * Seeds the row the prefixed ingestion would have written, into the table for its dimension.
     *
     * <p>{@code convention} is written honestly per model: it is the marker the mixed-space detector
     * groups by, so stamping {@code nomic-task-prefix} on an OpenAI row would teach the wrong thing
     * in the one column whose whole purpose is saying which space a vector lives in.
     */
    private UUID seedDocumentAndChunk(UUID tenantId, UUID kbId, String text, int dimension) {
        String convention = dimension == NOMIC_DIMENSION ? "nomic-task-prefix" : "plain";
        UUID docId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO kb_document (id, tenant_id, kb_id, source_type, file_name, status)"
                        + " VALUES (?, ?, ?, ?, ?, ?)",
                docId, tenantId, kbId, "UPLOAD", "manual.txt", "READY");
        jdbcTemplate.update(
                "INSERT INTO kb_embeddings_" + dimension
                        + " (kb_id, tenant_id, doc_id, chunk_index, embedding, text, metadata)"
                        + " VALUES (?, ?, ?, ?, CAST(? AS vector), ?, CAST(? AS jsonb))",
                kbId, tenantId, docId, 0,
                TaskPrefixEmbeddingModel.vectorLiteral(
                        TaskPrefixEmbeddingModel.documentVector(dimension, text)),
                text,
                "{\"tenant_id\":\"" + tenantId + "\",\"kb_id\":\"" + kbId + "\",\"doc_id\":\"" + docId
                        + "\",\"chunk_index\":0,\"convention\":\"" + convention + "\"}");
        return docId;
    }
}
