package br.com.systec.hive.integrationtest;

import br.com.systec.hive.agent.api.diagnostics.ChatTrace;
import br.com.systec.hive.agent.api.diagnostics.ChatTraceRecorder;
import br.com.systec.hive.integrationtest.support.StubEmbeddingProviderConfig;
import br.com.systec.hive.integrationtest.support.TaskPrefixEmbeddingModel;
import br.com.systec.hive.integrationtest.support.WebIntegrationTest;
import br.com.systec.hive.knowledge.api.domain.RagDiagnostic;
import br.com.systec.hive.knowledge.api.domain.RagOutcome;
import br.com.systec.hive.knowledge.api.port.RagDiagnosticRecorder;
import br.com.systec.hive.provider.api.LlmClientFactory;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.model.chat.ChatModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.lessThan;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Prova de aceitação da rodada 2: KB vinculada por endpoint → chat → RAG de verdade.
 *
 * <p>Este é o primeiro teste que exercita o RAG ponta a ponta. Antes dele ninguém tinha visto
 * {@code ragUsed=true} com citações: o caminho de escrita do vínculo não existia, então nenhuma KB
 * chegava ao prompt e toda busca retornava {@code no_link}.
 *
 * <h3>Por que o modelo e o embedding são substituídos</h3>
 *
 * A instância real depende de duas coisas que não existem em CI: a API do {@code mimo-v2.5} e um
 * Ollama com {@code nomic-embed-text}. Testar contra elas mediria a disponibilidade da rede, não o
 * que esta rodada mudou. As duas bordas externas são substituídas — a mesma estratégia já usada por
 * {@code KnowledgeSearchIT} para o embedding — e todo o miolo fica real: controller, autorização,
 * anti-IDOR, transação, validação de tenant, busca por similaridade no pgvector, montagem do
 * {@code systemBlock}, diagnóstico e resposta HTTP.
 *
 * <p>O agente continua declarado com {@code provider=OPENCODE_GO} e {@code model=mimo-v2.5}, o
 * modelo de referência da rodada, para que o catálogo, as permissões e o trace sejam exercitados
 * com a configuração real.
 */
@Import({RagChatE2EIT.StubProviderConfig.class, StubEmbeddingProviderConfig.class})
class RagChatE2EIT extends WebIntegrationTest {

    private static final String PASSWORD = "it-secret-pw";
    private static final String MODEL = "mimo-v2.5";
    private static final int DIMENSION = 768;

    /** Replies captured so the stub can answer differently per test. */
    static volatile String nextReply = "resposta com contexto";

    /** System messages seen by the model; this is where the retrieved chunk has to appear. */
    static volatile List<String> lastSystemPrompts = List.of();

    @TestConfiguration
    static class StubProviderConfig {

        @Bean
        @Primary
        @SuppressWarnings("unchecked")
        LlmClientFactory<ChatModel> stubFactory() {
            return new LlmClientFactory<>() {
                @Override
                public ChatModel createClient(br.com.systec.hive.provider.api.AiProvider provider, String model,
                                              Double temperature, Integer maxTokens, Double topP,
                                              String apiKey, String baseUrl) {
                    return new ChatModel() {
                        @Override
                        public dev.langchain4j.model.chat.response.ChatResponse chat(
                                List<dev.langchain4j.data.message.ChatMessage> messages) {
                            lastSystemPrompts = messages.stream()
                                    .filter(m -> m instanceof SystemMessage)
                                    .map(m -> ((SystemMessage) m).text())
                                    .toList();
                            return dev.langchain4j.model.chat.response.ChatResponse.builder()
                                    .aiMessage(AiMessage.from(nextReply))
                                    .build();
                        }
                    };
                }
            };
        }

        @Bean
        @Primary
        ChatTraceRecorder capturingTraceRecorder(List<ChatTrace> sink) {
            return sink::add;
        }

        @Bean
        List<ChatTrace> chatTraceSink() {
            return new ArrayList<>();
        }

        @Bean
        @Primary
        RagDiagnosticRecorder capturingRagRecorder(List<RagDiagnostic> sink) {
            return sink::add;
        }

        @Bean
        List<RagDiagnostic> ragDiagnosticSink() {
            return new ArrayList<>();
        }
    }

    @Autowired
    private List<ChatTrace> chatTraceSink;

    @Autowired
    private List<RagDiagnostic> ragDiagnosticSink;

    private String token;

    @BeforeEach
    void setUp() {
        nextReply = "resposta com contexto";
        lastSystemPrompts = List.of();
        chatTraceSink.clear();
        ragDiagnosticSink.clear();
        StubEmbeddingProviderConfig.reset();
    }

    @Test
    void when_chatWithLinkedKnowledgeBase_then_ragUsedWithCitations() throws Exception {
        UUID tenantId = UUID.randomUUID();
        token = tokenFor(tenantId, "rag");
        seedOpencodeGoCredential(tenantId, token);

        UUID kbId = createKb(token, "Runbook");
        UUID docId = seedDocumentAndChunk(tenantId, kbId, "reinicio do servico de busca");
        UUID agentId = createAgent(token, "Agente RAG");

        // 1. O caminho de escrita que não existia: vincular a KB pelo endpoint.
        mockMvc.perform(put("/v1/agents/{id}/knowledge-bases", agentId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kbIds\":[\"" + kbId + "\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.agentId").value(agentId.toString()))
                .andExpect(jsonPath("$.kbIds[0]").value(kbId.toString()))
                .andExpect(jsonPath("$.kbNames[0]").value("Runbook"));

        assertThat(linkedKbCount(agentId, tenantId)).isEqualTo(1);

        // 2. Chat: o chunk tem de entrar no prompt e a resposta tem de declarar que o usou.
        mockMvc.perform(post("/v1/chat")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"agentId\":\"" + agentId + "\",\"message\":\"reinicio do servico de busca agora\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ragUsed").value(true))
                .andExpect(jsonPath("$.ragReason").value("ok"))
                .andExpect(jsonPath("$.citations").isNotEmpty())
                .andExpect(jsonPath("$.citations[0].docId").value(docId.toString()))
                .andExpect(jsonPath("$.citations[0].docName").value("manual.txt"))
                // similaridade real: acima do corte, e nada perto do 1.0 artificial do stub antigo
                .andExpect(jsonPath("$.citations[0].score").value(greaterThan(0.7)))
                .andExpect(jsonPath("$.citations[0].score").value(lessThan(0.99)));

        // 3. O chunk foi de fato injetado, e não apenas anotado na resposta.
        assertThat(systemPromptText()).contains("reinicio do servico de busca");

        // 4. A pergunta chegou ao modelo como pergunta: sem o prefixo de tarefa o par cai para
        //    outro espaço e a similarities sai do corte — era o bug, e este é o teste que o pega.
        assertThat(StubEmbeddingProviderConfig.seenTexts())
                .isNotEmpty()
                .allSatisfy(text -> assertThat(text).startsWith(TaskPrefixEmbeddingModel.QUERY_PREFIX));
    }

    @Test
    void when_chatWithLinkedKnowledgeBase_then_ragDiagnosticRecordsOkOutcome() throws Exception {
        UUID tenantId = UUID.randomUUID();
        token = tokenFor(tenantId, "ragdiag");
        seedOpencodeGoCredential(tenantId, token);
        UUID kbId = createKb(token, "Politicas");
        seedDocumentAndChunk(tenantId, kbId, "politica de acesso a dados");
        UUID agentId = createAgent(token, "Agente Politicas");
        linkKb(token, agentId, kbId);

        mockMvc.perform(post("/v1/chat")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"agentId\":\"" + agentId + "\",\"message\":\"qual a politica de acesso a dados\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ragOutcome").doesNotExist())
                .andExpect(jsonPath("$.ragUsed").value(true));

        // ragOutcome=ok é um sintoma de log, não de resposta: vem do recorder de diagnóstico.
        assertThat(lastRagOutcome()).isEqualTo(RagOutcome.OK);
        assertThat(lastChatTrace().ragOutcome()).isEqualTo("ok");
    }

    @Test
    void when_chatWithoutLink_then_reportsNoLinkSoTheScreenCanExplainIt() throws Exception {
        UUID tenantId = UUID.randomUUID();
        token = tokenFor(tenantId, "nolink");
        seedOpencodeGoCredential(tenantId, token);
        UUID agentId = createAgent(token, "Agente Sem KB");

        mockMvc.perform(post("/v1/chat")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"agentId\":\"" + agentId + "\",\"message\":\"oi\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ragUsed").value(false))
                .andExpect(jsonPath("$.ragReason").value("no_link"))
                .andExpect(jsonPath("$.citations").isEmpty());

        assertThat(lastRagOutcome()).isEqualTo(RagOutcome.NO_LINK);
    }

    @Test
    void when_chatWithForeignKnowledgeBase_then_rejectsTheLinkAndKeepsNoLink() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID otherTenant = UUID.randomUUID();
        token = tokenFor(tenantId, "idor");
        seedOpencodeGoCredential(tenantId, token);
        UUID foreignKb = createKbAsTenant(otherTenant, "KB de outro tenant");
        UUID agentId = createAgent(token, "Agente Isolar");

        mockMvc.perform(put("/v1/agents/{id}/knowledge-bases", agentId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kbIds\":[\"" + foreignKb + "\"]}"))
                .andExpect(status().isNotFound());

        assertThat(linkedKbCount(agentId, tenantId)).isZero();

        mockMvc.perform(post("/v1/chat")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"agentId\":\"" + agentId + "\",\"message\":\"oi\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ragReason").value("no_link"));
    }

    // ------------------------------------------------------------------ helpers

    private static String systemPromptText() {
        return String.join("\n", lastSystemPrompts);
    }

    private String tokenFor(UUID tenantId, String username) {
        seedTenantAndUser(tenantId, username, PASSWORD);
        return jwtTestHelper.generateToken(username, tenantId);
    }

    private void seedOpencodeGoCredential(UUID tenantId, String token) throws Exception {
        mockMvc.perform(put("/v1/provider-credentials/OPENCODE_GO")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"apiKey\":\"sk-test-fake-key\"}"))
                .andExpect(status().isOk());
    }

    private UUID createKb(String token, String name) throws Exception {
        String body = mockMvc.perform(post("/v1/knowledge-bases")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\",\"embeddingModel\":\"nomic-embed-text\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(body.split("\"id\":\"")[1].split("\"")[0]);
    }

    private UUID createKbAsTenant(UUID tenantId, String name) {
        UUID kbId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO knowledge_base (id, tenant_id, name, slug, embedding_model, dimension, status)"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?)",
                kbId, tenantId, name, "kb-" + kbId, "nomic-embed-text", DIMENSION, "ACTIVE");
        return kbId;
    }

    private UUID createAgent(String token, String name) throws Exception {
        String body = mockMvc.perform(post("/v1/agents")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\",\"provider\":\"OPENCODE_GO\",\"model\":\""
                                + MODEL + "\",\"active\":true,\"maxTokens\":2048}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(body.split("\"id\":\"")[1].split("\"")[0]);
    }

    private void linkKb(String token, UUID agentId, UUID kbId) throws Exception {
        mockMvc.perform(put("/v1/agents/{id}/knowledge-bases", agentId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kbIds\":[\"" + kbId + "\"]}"))
                .andExpect(status().isOk());
    }

    private UUID seedDocumentAndChunk(UUID tenantId, UUID kbId, String text) {
        UUID docId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO kb_document (id, tenant_id, kb_id, source_type, file_name, status)"
                        + " VALUES (?, ?, ?, ?, ?, ?)",
                docId, tenantId, kbId, "UPLOAD", "manual.txt", "READY");
        jdbcTemplate.update(
                "INSERT INTO kb_embeddings_768 (kb_id, tenant_id, doc_id, chunk_index, embedding, text, metadata)"
                        + " VALUES (?, ?, ?, ?, CAST(? AS vector), ?, CAST(? AS jsonb))",
                kbId, tenantId, docId, 0,
                TaskPrefixEmbeddingModel.vectorLiteral(TaskPrefixEmbeddingModel.documentVector(DIMENSION, text)), text,
                "{\"tenant_id\":\"" + tenantId + "\",\"kb_id\":\"" + kbId + "\",\"doc_id\":\"" + docId
                        + "\",\"chunk_index\":0}");
        return docId;
    }

    private int linkedKbCount(UUID agentId, UUID tenantId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM agent_knowledge_base WHERE agent_id = ? AND tenant_id = ?",
                Integer.class, agentId, tenantId);
        return count == null ? 0 : count;
    }

    private ChatTrace lastChatTrace() {
        assertThat(chatTraceSink).isNotEmpty();
        return chatTraceSink.get(chatTraceSink.size() - 1);
    }

    private RagOutcome lastRagOutcome() {
        assertThat(ragDiagnosticSink).isNotEmpty();
        return ragDiagnosticSink.get(ragDiagnosticSink.size() - 1).outcome();
    }
}
