package br.com.systec.hive.integrationtest;

import br.com.systec.hive.integrationtest.support.WebIntegrationTest;
import br.com.systec.hive.publicapi.api.service.PublicApiKeyAuthenticator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** ADR-001/key: `sk-` exato, sem fallback, com stub da porta (emissão é da ADR-002). */
@Import({ChatCallerParityIT.CapturingFactoryConfig.class, PublicApiKeyAuthIT.StubAuthConfig.class})
class PublicApiKeyAuthIT extends WebIntegrationTest {

    private static final String VALID_KEY = "sk-test-key";
    private static String promptCode;
    private static UUID tenantId;

    @Autowired
    private org.springframework.context.ApplicationContext context;

    @TestConfiguration
    static class StubAuthConfig {
        @Bean
        PublicApiKeyAuthenticator stubAuthenticator() {
            return rawKey -> {
                if (!VALID_KEY.equals(rawKey)) {
                    throw new IllegalArgumentException("unknown key");
                }
                return new PublicApiKeyAuthenticator.AuthenticatedKey(
                        tenantId, "key-client", List.of("PUBLIC_CHAT_COMPLETE"),
                        List.of(promptCode), Map.of(),
                        Map.of("m", 200, "c", 32000, "t", 256000, "r", 100));
            };
        }
    }

    @BeforeEach
    void setUp() throws Exception {
        tenantId = UUID.randomUUID();
        promptCode = "key-" + UUID.randomUUID().toString().substring(0, 8);
        String username = "key-human-" + UUID.randomUUID();
        seedTenantAndUser(tenantId, username, "it-pw");
        String adminToken = jwtTestHelper.generateToken(username, tenantId);
        mockMvc.perform(put("/v1/provider-credentials/OPENCODE_GO")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"apiKey\":\"sk-test-fake-key\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(put("/v1/tenant/llm-config")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"provider\":\"OPENCODE_GO\",\"model\":\"mimo-v2.5\"}"))
                .andExpect(status().isOk());
        String body = mockMvc.perform(post("/v1/prompt-templates")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Key\",\"code\":\"" + promptCode
                                + "\",\"template\":\"Voce e um assistente.\",\"inferenceParams\":{\"temperature\":0.7,\"maxTokens\":256}}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID id = UUID.fromString(body.split("\"id\":\"")[1].split("\"")[0]);
        jdbcTemplate.update("UPDATE prompt_template SET status = 'ACTIVE' WHERE id = ?", id);
        ChatCallerParityIT.CAPTURED.clear();
        ChatCallerParityIT.TRACES.clear();
    }

    @Test
    void when_validSkKey_then_chatReturns200WithSamePrincipal() throws Exception {
        MvcResult result = mockMvc.perform(post("/v1/public/v1/chat/completions")
                        .header("Authorization", "Bearer " + VALID_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(chatBody()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.object").value("chat.completion"))
                .andReturn();

        var trace = ChatCallerParityIT.TRACES.get(ChatCallerParityIT.TRACES.size() - 1);
        assertThat(trace.clientId()).isEqualTo("key-client");
        assertThat(trace.tenantId()).isEqualTo(tenantId);
        assertThat(trace.turnId()).isNotNull();
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void when_keyIsEmptyOrUnknown_then_401WithoutFallback() throws Exception {
        mockMvc.perform(post("/v1/public/v1/chat/completions")
                        .header("Authorization", "Bearer sk-")
                        .contentType(MediaType.APPLICATION_JSON).content(chatBody()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/v1/public/v1/chat/completions")
                        .header("Authorization", "Bearer sk-unknown")
                        .contentType(MediaType.APPLICATION_JSON).content(chatBody()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/v1/public/v1/chat/completions")
                        .header("Authorization", "Bearer sk-eyJhbGciOiJIUzI1NiJ9.x.y")
                        .contentType(MediaType.APPLICATION_JSON).content(chatBody()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/v1/public/v1/chat/completions")
                        .header("Authorization", "Bearer SK-test-key")
                        .contentType(MediaType.APPLICATION_JSON).content(chatBody()))
                .andExpect(status().isUnauthorized());
        assertThat(ChatCallerParityIT.CAPTURED).isEmpty();
    }

    @Test
    void when_xGoogApiKeyIsPresent_then_itIsIgnored() throws Exception {
        mockMvc.perform(post("/v1/public/v1/chat/completions")
                        .header("Authorization", "Bearer " + VALID_KEY)
                        .header("x-goog-api-key", VALID_KEY)
                        .contentType(MediaType.APPLICATION_JSON).content(chatBody()))
                .andExpect(status().isOk());
        mockMvc.perform(post("/v1/public/v1/chat/completions")
                        .header("x-goog-api-key", VALID_KEY)
                        .contentType(MediaType.APPLICATION_JSON).content(chatBody()))
                .andExpect(status().isUnauthorized());
    }

    private static String chatBody() {
        return "{\"prompt_code\":\"" + promptCode + "\",\"messages\":[{\"role\":\"user\",\"content\":\"oi\"}]}";
    }
}
