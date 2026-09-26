package br.com.systec.hive.integrationtest;

import br.com.systec.hive.agent.api.exceptions.ModelReturnedEmptyException;
import br.com.systec.hive.integrationtest.support.WebIntegrationTest;
import br.com.systec.hive.provider.api.LlmClientFactory;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Resposta vazia do modelo contra o banco real.
 *
 * <p>O defeito só aparecia na escrita: {@code chat_message.content} é {@code NOT NULL}, então um
 * {@code null} do provider virava {@code DataIntegrityViolationException} e a API respondia 409
 * "DATA_CONFLICT" — um erro de integridade que não diz ao usuário nada. Aqui o provider é
 * substituído para devolver exatamente os dois vazios medidos no upstream e o teste afirma o
 * comportamento correto: 422 {@code MODEL_RETURNED_EMPTY}, <b>nenhuma</b> linha de assistente e a
 * mensagem do usuário preservada.
 */
@Import(ChatEmptyResponseIT.EmptyModelConfig.class)
class ChatEmptyResponseIT extends WebIntegrationTest {

    private static final String PASSWORD = "it-secret-pw";

    /**
     * Margem entre o turno que toca a sessão e o que não deve tocá-la. A coluna tem precisão de
     * microssegundo; 25ms é ordens de grandeza acima, então a comparação é estável.
     */
    private static final long TOUCH_RESOLUTION_MS = 25;

    /** The exact shape measured upstream with mimo-v2.5: content null or empty string. */
    static volatile String emptyReply = null;
    static volatile boolean replyIsNull = true;

    @TestConfiguration
    static class EmptyModelConfig {
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
                                java.util.List<dev.langchain4j.data.message.ChatMessage> messages) {
                            return dev.langchain4j.model.chat.response.ChatResponse.builder()
                                    .aiMessage(replyIsNull
                                            ? AiMessage.builder().text(null).build()
                                            : AiMessage.from(emptyReply))
                                    .finishReason(dev.langchain4j.model.output.FinishReason.LENGTH)
                                    .tokenUsage(new dev.langchain4j.model.output.TokenUsage(120, 2048, 2168))
                                    .build();
                        }
                    };
                }
            };
        }
    }

    private String token;

    @BeforeEach
    void setUp() {
        replyIsNull = true;
        emptyReply = null;
    }

    @Test
    void when_modelReturnsNullContent_then_422AndNoAssistantRow() throws Exception {
        UUID tenantId = UUID.randomUUID();
        token = tokenFor(tenantId, "empty-null");
        seedCredential(tenantId, token);
        UUID agentId = createAgent(token, "Agente Vazio");
        replyIsNull = true;

        mockMvc.perform(post("/v1/chat")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"agentId\":\"" + agentId + "\",\"message\":\"pergunta que exige raciocinio\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("MODEL_RETURNED_EMPTY"))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("não devolveu texto")));

        assertThat(assistantMessageCount(agentId, tenantId)).isZero();
        // A mensagem do usuário foi salva em TX1 e é o contexto que ela precisa para reenviar.
        assertThat(userMessageCount(agentId, tenantId)).isEqualTo(1);
    }

    @Test
    void when_modelReturnsEmptyString_then_422AndNoAssistantRow() throws Exception {
        UUID tenantId = UUID.randomUUID();
        token = tokenFor(tenantId, "empty-blank");
        seedCredential(tenantId, token);
        UUID agentId = createAgent(token, "Agente Vazio Branco");
        replyIsNull = false;
        emptyReply = "";

        mockMvc.perform(post("/v1/chat")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"agentId\":\"" + agentId + "\",\"message\":\"outra pergunta\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("MODEL_RETURNED_EMPTY"));

        assertThat(assistantMessageCount(agentId, tenantId)).isZero();
        assertThat(userMessageCount(agentId, tenantId)).isEqualTo(1);
    }

    @Test
    void when_modelReturnsWhitespaceOnly_then_422AndNoAssistantRow() throws Exception {
        UUID tenantId = UUID.randomUUID();
        token = tokenFor(tenantId, "empty-ws");
        seedCredential(tenantId, token);
        UUID agentId = createAgent(token, "Agente Vazio Brancos");
        replyIsNull = false;
        emptyReply = "   \n\t ";

        mockMvc.perform(post("/v1/chat")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"agentId\":\"" + agentId + "\",\"message\":\"mais uma\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("MODEL_RETURNED_EMPTY"));

        assertThat(assistantMessageCount(agentId, tenantId)).isZero();
    }

    @Test
    void when_modelReturnsTextWithLengthFinishReason_then_stillSavesAndAnswers() throws Exception {
        // mimo-v2.5 com max_tokens=256 devolve finish_reason=length e 610 chars. Truncamento é
        // sucesso normal e não pode virar 422.
        UUID tenantId = UUID.randomUUID();
        token = tokenFor(tenantId, "length-ok");
        seedCredential(tenantId, token);
        UUID agentId = createAgent(token, "Agente Truncado");
        replyIsNull = false;
        emptyReply = "resposta truncada mas útil";

        mockMvc.perform(post("/v1/chat")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"agentId\":\"" + agentId + "\",\"message\":\"resuma\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("resposta truncada mas útil"));

        assertThat(assistantMessageCount(agentId, tenantId)).isEqualTo(1);
    }

    @Test
    void when_modelReturnsNull_then_doesNotTouchTheSessionTimestamp() throws Exception {
        // O touch em TX2 é o que marca a sessão como "atualizada" e a reposiciona no topo do
        // histórico. Um turno que não gerou resposta não pode alterá-la.
        // Precisa de um intervalo explícito: BaseEntity.prePersist chama LocalDateTime.now() duas
        // vezes, então create_at e update_at já nascem diferentes e não servem de referência.
        UUID tenantId = UUID.randomUUID();
        token = tokenFor(tenantId, "no-touch");
        seedCredential(tenantId, token);
        UUID agentId = createAgent(token, "Agente Sem Touch");
        replyIsNull = false;
        emptyReply = "primeira resposta";

        assertThat(send(token, agentId, "primeira")).isEqualTo(200);
        LocalDateTime baseline = sessionUpdateAt(agentId, tenantId);
        assertThat(baseline).isNotNull();

        Thread.sleep(TOUCH_RESOLUTION_MS);
        replyIsNull = true;
        assertThat(send(token, agentId, "segunda")).isEqualTo(422);

        assertThat(sessionUpdateAt(agentId, tenantId)).isEqualTo(baseline);
    }

    @Test
    void when_modelReturnsText_then_theSessionTimestampIsTouched() throws Exception {
        // Controle do teste anterior: sem um turno que realmente toque a sessão, "não mudou" não
        // distingue a guarda funcionando de a coluna nunca ser escrita.
        UUID tenantId = UUID.randomUUID();
        token = tokenFor(tenantId, "com-touch");
        seedCredential(tenantId, token);
        UUID agentId = createAgent(token, "Agente Com Touch");
        replyIsNull = false;
        emptyReply = "primeira resposta";

        assertThat(send(token, agentId, "primeira")).isEqualTo(200);
        LocalDateTime baseline = sessionUpdateAt(agentId, tenantId);

        Thread.sleep(TOUCH_RESOLUTION_MS);
        emptyReply = "segunda resposta";
        assertThat(send(token, agentId, "segunda")).isEqualTo(200);

        assertThat(sessionUpdateAt(agentId, tenantId)).isAfter(baseline);
    }

    /**
     * Sends one turn and returns its HTTP status. A {@code null} session id makes the service create
     * the session, which is how the first turn of each pair opens the session the second one reuses.
     * The status is returned instead of asserted because the second turn of the empty-reply pair is
     * expected to be 422.
     */
    private int send(String token, UUID agentId, String message) throws Exception {
        UUID sessionId = sessionIdOf(agentId);
        String body = sessionId == null
                ? "{\"agentId\":\"" + agentId + "\",\"message\":\"" + message + "\"}"
                : "{\"sessionId\":\"" + sessionId + "\",\"message\":\"" + message + "\"}";
        return mockMvc.perform(post("/v1/chat")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andReturn().getResponse().getStatus();
    }

    private UUID sessionIdOf(UUID agentId) {
        List<UUID> ids = jdbcTemplate.queryForList(
                "SELECT id FROM chat_session WHERE agent_id = ? ORDER BY create_at",
                UUID.class, agentId);
        return ids.isEmpty() ? null : ids.get(0);
    }

    @Test
    void when_emptyResponse_then_codeMatchesTheExceptionClassName() throws Exception {
        // O handler deriva o code do SimpleName em SNAKE_UPPER. Se alguém renomear a exceção, o
        // contrato com o frontend (que compara com MODEL_RETURNED_EMPTY) quebra em silêncio.
        assertThat(new ModelReturnedEmptyException("mimo-v2.5").getMessage())
                .contains("mimo-v2.5")
                .contains("Aumente o limite de tokens do agente");
    }

    // ------------------------------------------------------------------ helpers

    private String tokenFor(UUID tenantId, String username) {
        seedTenantAndUser(tenantId, username, PASSWORD);
        return jwtTestHelper.generateToken(username, tenantId);
    }

    private void seedCredential(UUID tenantId, String token) throws Exception {
        mockMvc.perform(put("/v1/provider-credentials/OPENCODE_GO")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"apiKey\":\"sk-test-fake-key\"}"))
                .andExpect(status().isOk());
    }

    private UUID createAgent(String token, String name) throws Exception {
        // maxTokens=256 é o menor valor aceito por AgentCreateDTO (@Min(256)). O caso upstream de
        // max_tokens=32 que produz content=null não é alcançável por esta API, mas 256 continua
        // vulnerável com pergunta que exige raciocínio — por isso a guarda é testada aqui.
        String body = mockMvc.perform(post("/v1/agents")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\",\"provider\":\"OPENCODE_GO\","
                                + "\"model\":\"mimo-v2.5\",\"active\":true,\"maxTokens\":256}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(body.split("\"id\":\"")[1].split("\"")[0]);
    }

    private int assistantMessageCount(UUID agentId, UUID tenantId) {
        return messageCount(agentId, tenantId, "ASSISTANT");
    }

    private int userMessageCount(UUID agentId, UUID tenantId) {
        return messageCount(agentId, tenantId, "USER");
    }

    private int messageCount(UUID agentId, UUID tenantId, String role) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM chat_message m"
                        + " JOIN chat_session s ON s.id = m.session_id"
                        + " WHERE s.agent_id = ? AND s.tenant_id = ? AND m.role = ?",
                Integer.class, agentId, tenantId, role);
        return count == null ? 0 : count;
    }

    /** {@code update_at} da sessão do agente, que é o que o touch em TX2 reescreve. */
    private LocalDateTime sessionUpdateAt(UUID agentId, UUID tenantId) {
        return jdbcTemplate.queryForObject(
                "SELECT update_at FROM chat_session WHERE agent_id = ? AND tenant_id = ?"
                        + " ORDER BY create_at LIMIT 1",
                LocalDateTime.class, agentId, tenantId);
    }
}
