package br.com.systec.hive.integrationtest;

import br.com.systec.hive.integrationtest.support.WebIntegrationTest;
import br.com.systec.hive.provider.api.AiProvider;
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

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Override de provider/modelo por conversa, em Postgres real (Testcontainers).
 *
 * <p>O seletor da UI manda o modelo no body da request — fonte oficial consultada em
 * 2026-11-20: https://ui.shadcn.com/docs/ai-elements/base/model-selector — e o backend precisa
 * aceitar esse par sem trocar o default do tenant. O default continua em {@code tenant_llm_config};
 * o override é uma escolha da CONVERSA, persistida em {@code chat_session} e autoritativa nos
 * turnos seguintes (um {@code sessionId} ignoram o que o payload voltar a mandar).
 *
 * <p>Aqui o factory é substituído por um stub que CAPTURA o par que o turno realmente usou. Sem
 * essa captura, um 200 provaria apenas que algum modelo respondeu — não qual.
 */
@Import(ChatProviderOverrideIT.CapturingFactoryConfig.class)
class ChatProviderOverrideIT extends WebIntegrationTest {

    private static final String PASSWORD = "it-secret-pw";

    static final AtomicReference<AiProvider> USED_PROVIDER = new AtomicReference<>();
    static final AtomicReference<String> USED_MODEL = new AtomicReference<>();
    static final AtomicInteger CLIENT_CALLS = new AtomicInteger();

    @TestConfiguration
    static class CapturingFactoryConfig {
        @Bean
        @Primary
        @SuppressWarnings("unchecked")
        LlmClientFactory<ChatModel> capturingFactory() {
            return new LlmClientFactory<>() {
                @Override
                public ChatModel createClient(AiProvider provider, String model, Double temperature,
                                              Integer maxTokens, Double topP, String apiKey, String baseUrl) {
                    return capture(provider, model);
                }

                @Override
                public ChatModel createClientWithSession(AiProvider provider, String model, Double temperature,
                                                         Integer maxTokens, Double topP, String apiKey,
                                                         String baseUrl, UUID sessionId) {
                    return capture(provider, model);
                }
            };
        }

        private static ChatModel capture(AiProvider provider, String model) {
            USED_PROVIDER.set(provider);
            USED_MODEL.set(model);
            CLIENT_CALLS.incrementAndGet();
            return new ChatModel() {
                @Override
                public dev.langchain4j.model.chat.response.ChatResponse doChat(
                                dev.langchain4j.model.chat.request.ChatRequest chatRequest) {
                    return dev.langchain4j.model.chat.response.ChatResponse.builder()
                            .aiMessage(AiMessage.from("resposta do stub"))
                            .build();
                }
            };
        }
    }

    @BeforeEach
    void resetCapture() {
        USED_PROVIDER.set(null);
        USED_MODEL.set(null);
        CLIENT_CALLS.set(0);
    }

    @Test
    void when_noOverride_then_tenantDefaultIsUsedAndStaysUnchanged() throws Exception {
        UUID tenantId = UUID.randomUUID();
        String token = tokenFor(tenantId, "override-sem-override");
        configureTenant(token, "OPENAI", "gpt-4o-mini");
        seedCredential(token, "OPENAI");
        UUID promptTemplateId = createPromptTemplate(token, "Prompt default");

        String response = sendChat(token, "{\"promptTemplateId\":\"" + promptTemplateId
                + "\",\"message\":\"pergunta\"}", true);

        assertThat(USED_PROVIDER.get()).isEqualTo(AiProvider.OPENAI);
        assertThat(USED_MODEL.get()).isEqualTo("gpt-4o-mini");
        assertThat(response).contains("\"provider\":\"OPENAI\"").contains("\"model\":\"gpt-4o-mini\"");
        // NULL na sessão = usar o default do tenant. Nenhuma coluna é preenchida "por conveniência".
        assertThat(sessionSelection(promptTemplateId)).containsExactly(null, null);
        assertTenantDefault(tenantId, "OPENAI", "gpt-4o-mini");
    }

    @Test
    void when_validOverrideWithCredential_then_selectedProviderIsUsedAndDefaultRemains() throws Exception {
        UUID tenantId = UUID.randomUUID();
        String token = tokenFor(tenantId, "override-valido");
        configureTenant(token, "OPENAI", "gpt-4o-mini");
        seedCredential(token, "OPENAI");
        seedCredential(token, "OPENCODE_GO");
        UUID promptTemplateId = createPromptTemplate(token, "Prompt override");

        String response = sendChat(token, "{\"promptTemplateId\":\"" + promptTemplateId
                + "\",\"message\":\"pergunta\",\"provider\":\"OPENCODE_GO\",\"model\":\"mimo-v2.5\"}", true);

        assertThat(USED_PROVIDER.get()).isEqualTo(AiProvider.OPENCODE_GO);
        assertThat(USED_MODEL.get()).isEqualTo("mimo-v2.5");
        assertThat(response).contains("\"provider\":\"OPENCODE_GO\"").contains("\"model\":\"mimo-v2.5\"");
        assertThat(sessionSelection(promptTemplateId)).containsExactly("OPENCODE_GO", "mimo-v2.5");
        // O default do tenant não é sobrescrito pela escolha de uma conversa.
        assertTenantDefault(tenantId, "OPENAI", "gpt-4o-mini");
    }

    @Test
    void when_overrideModelOutsideCatalog_then_400BeforeTouchingTheCredential() throws Exception {
        UUID tenantId = UUID.randomUUID();
        String token = tokenFor(tenantId, "override-modelo-invalido");
        configureTenant(token, "OPENAI", "gpt-4o-mini");
        seedCredential(token, "OPENAI");
        // Sem credencial de GEMINI de propósito: se a credencial fosse resolvida antes do catálogo,
        // o erro seria 422 CONFIG_MISSING em vez do 400 do modelo. A ordem é load-bearing.
        UUID promptTemplateId = createPromptTemplate(token, "Prompt invalido");

        mockMvc.perform(post("/v1/chat")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"promptTemplateId\":\"" + promptTemplateId
                                + "\",\"message\":\"pergunta\",\"provider\":\"GEMINI\",\"model\":\"gpt-9-turbo\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MODEL_NOT_SUPPORTED_FOR_PROVIDER"))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("gpt-9-turbo")));

        assertThat(CLIENT_CALLS.get()).isZero();
        assertThat(count("SELECT count(*) FROM chat_message")).isZero();
    }

    @Test
    void when_secondTurnHasSessionId_then_persistedOverrideIsReusedAndPayloadOverrideIgnored() throws Exception {
        UUID tenantId = UUID.randomUUID();
        String token = tokenFor(tenantId, "override-reuso");
        configureTenant(token, "OPENAI", "gpt-4o-mini");
        seedCredential(token, "OPENAI");
        seedCredential(token, "GEMINI");
        UUID promptTemplateId = createPromptTemplate(token, "Prompt reuso");

        sendChat(token, "{\"promptTemplateId\":\"" + promptTemplateId
                + "\",\"message\":\"primeira\",\"provider\":\"GEMINI\",\"model\":\"gemini-2.0-flash\"}", true);

        UUID sessionId = sessionIdOf(promptTemplateId);
        assertThat(sessionId).isNotNull();
        assertThat(sessionSelection(promptTemplateId)).containsExactly("GEMINI", "gemini-2.0-flash");

        // O payload tenta trocar a conversa silenciosamente. A sessão é autoritativa.
        String response = sendChat(token, "{\"sessionId\":\"" + sessionId
                + "\",\"message\":\"segunda\",\"provider\":\"OPENAI\",\"model\":\"gpt-4o-mini\"}", true);

        assertThat(USED_PROVIDER.get()).isEqualTo(AiProvider.GEMINI);
        assertThat(USED_MODEL.get()).isEqualTo("gemini-2.0-flash");
        assertThat(response).contains("\"provider\":\"GEMINI\"").contains("\"model\":\"gemini-2.0-flash\"");
        assertThat(sessionSelection(promptTemplateId)).containsExactly("GEMINI", "gemini-2.0-flash");
    }

    @Test
    void when_onlyOneOverrideFieldIsSent_then_400WithClearMessage() throws Exception {
        UUID tenantId = UUID.randomUUID();
        String token = tokenFor(tenantId, "override-incompleto");
        configureTenant(token, "OPENAI", "gpt-4o-mini");
        seedCredential(token, "OPENAI");
        UUID promptTemplateId = createPromptTemplate(token, "Prompt incompleto");

        mockMvc.perform(post("/v1/chat")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"promptTemplateId\":\"" + promptTemplateId
                                + "\",\"message\":\"pergunta\",\"provider\":\"GEMINI\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("model")));

        assertThat(CLIENT_CALLS.get()).isZero();
    }

    @Test
    void when_createSessionWithOverride_then_dtoExposesTheSelection() throws Exception {
        UUID tenantId = UUID.randomUUID();
        String token = tokenFor(tenantId, "override-criacao");
        UUID promptTemplateId = createPromptTemplate(token, "Prompt criacao");

        String body = mockMvc.perform(post("/v1/chat/sessions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"promptTemplateId\":\"" + promptTemplateId
                                + "\",\"title\":\"Conversa\",\"provider\":\"ANTHROPIC\","
                                + "\"model\":\"claude-3-5-sonnet-latest\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.provider").value("ANTHROPIC"))
                .andExpect(jsonPath("$.model").value("claude-3-5-sonnet-latest"))
                .andReturn().getResponse().getContentAsString();

        UUID sessionId = UUID.fromString(body.split("\"id\":\"")[1].split("\"")[0]);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT provider FROM chat_session WHERE id = ?", String.class, sessionId))
                .isEqualTo("ANTHROPIC");

        // A GET da sessão devolve a mesma escolha — é o que a UI precisa para pintar o seletor.
        mockMvc.perform(get("/v1/chat/sessions/{id}", sessionId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.provider").value("ANTHROPIC"))
                .andExpect(jsonPath("$.model").value("claude-3-5-sonnet-latest"));
    }

    @Test
    void when_createSessionWithModelOutsideCatalog_then_400AndNoRowIsInserted() throws Exception {
        // O review encontrou este buraco: a criação de sessão validava só o half-pair. Um modelo
        // fora do catálogo entrava como 201 e o turno seguinte (que ignora override do payload)
        // ficava preso num 400 que o cliente não conseguia corrigir.
        UUID tenantId = UUID.randomUUID();
        String token = tokenFor(tenantId, "override-criacao-invalida");
        UUID promptTemplateId = createPromptTemplate(token, "Prompt invalido criacao");

        mockMvc.perform(post("/v1/chat/sessions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"promptTemplateId\":\"" + promptTemplateId
                                + "\",\"title\":\"Conversa\",\"provider\":\"GEMINI\","
                                + "\"model\":\"gpt-9-turbo\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MODEL_NOT_SUPPORTED_FOR_PROVIDER"));

        assertThat(count("SELECT count(*) FROM chat_session")).isZero();
    }

    // ------------------------------------------------------------------ helpers

    private String tokenFor(UUID tenantId, String username) {
        seedTenantAndUser(tenantId, username, PASSWORD);
        return jwtTestHelper.generateToken(username, tenantId);
    }

    private void configureTenant(String token, String provider, String model) throws Exception {
        mockMvc.perform(put("/v1/tenant/llm-config")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"provider\":\"" + provider + "\",\"model\":\"" + model + "\"}"))
                .andExpect(status().isOk());
    }

    private void seedCredential(String token, String provider) throws Exception {
        // CredentialPolicy valida o prefixo por vendor (sk-, AIza, sk-ant-). Chave fake, nunca real.
        String apiKey = switch (provider) {
            case "GEMINI" -> "AIzaFakeTestKey";
            case "ANTHROPIC" -> "sk-ant-fake-test-key";
            default -> "sk-test-fake-key";
        };
        mockMvc.perform(put("/v1/provider-credentials/" + provider)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"apiKey\":\"" + apiKey + "\"}"))
                .andExpect(status().isOk());
    }

    private UUID createPromptTemplate(String token, String name) throws Exception {
        String body = mockMvc.perform(post("/v1/prompt-templates")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\",\"code\":\"ovr-" + UUID.randomUUID()
                                + "\",\"template\":\"Voce e um assistente.\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(body.split("\"id\":\"")[1].split("\"")[0]);
    }

    private String sendChat(String token, String body, boolean expectOk) throws Exception {
        var result = mockMvc.perform(post("/v1/chat")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body));
        if (expectOk) {
            result.andExpect(status().isOk());
        }
        return result.andReturn().getResponse().getContentAsString();
    }

    private UUID sessionIdOf(UUID promptTemplateId) {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM chat_session WHERE prompt_template_id = ? ORDER BY create_at LIMIT 1",
                UUID.class, promptTemplateId);
    }

    /** Par (provider, model) gravado na sessão; {@code [null, null]} = usar o default do tenant. */
    private java.util.List<String> sessionSelection(UUID promptTemplateId) {
        java.util.List<java.util.Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT provider, model FROM chat_session WHERE prompt_template_id = ?", promptTemplateId);
        assertThat(rows).as("uma sessão por prompt neste teste").hasSize(1);
        return java.util.Arrays.asList((String) rows.get(0).get("provider"), (String) rows.get(0).get("model"));
    }

    private void assertTenantDefault(UUID tenantId, String provider, String model) {
        var row = jdbcTemplate.queryForMap(
                "SELECT provider, model FROM tenant_llm_config WHERE tenant_id = ?", tenantId);
        assertThat(row.get("provider")).isEqualTo(provider);
        assertThat(row.get("model")).isEqualTo(model);
    }

    private int count(String sql, Object... args) {
        Integer value = jdbcTemplate.queryForObject(sql, Integer.class, args);
        return value == null ? 0 : value;
    }
}
