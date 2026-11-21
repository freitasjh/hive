package br.com.systec.hive.integrationtest;

import br.com.systec.hive.assistant.impl.converter.AesGcmAttributeConverter;
import br.com.systec.hive.assistant.impl.service.ValidateProbe;
import br.com.systec.hive.integrationtest.support.WebIntegrationTest;
import dev.langchain4j.model.chat.ChatModel;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;

import java.security.SecureRandom;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(ValidateIT.StubProbeConfig.class)
class ValidateIT extends WebIntegrationTest {

    private static final String PASSWORD = "it-secret-pw";
    private static String savedProfiles;

    @BeforeAll
    static void setUpMasterKey() {
        savedProfiles = System.getProperty("spring.profiles.active");
        System.setProperty("spring.profiles.active", "test");
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        AesGcmAttributeConverter.setTestKey(key);
    }

    @AfterAll
    static void clearMasterKey() {
        System.setProperty("spring.profiles.active", "test");
        AesGcmAttributeConverter.clearTestKey();
        if (savedProfiles == null) {
            System.clearProperty("spring.profiles.active");
        } else {
            System.setProperty("spring.profiles.active", savedProfiles);
        }
    }

    @TestConfiguration
    static class StubProbeConfig {

        static final AtomicReference<Function<ChatModel, String>> behavior =
                new AtomicReference<>(client -> "pong");

        @Bean
        @Primary
        ValidateProbe stubProbe() {
            return client -> behavior.get().apply(client);
        }
    }

    @BeforeEach
    void resetProbe() {
        StubProbeConfig.behavior.set(client -> "pong");
    }

    private String tokenFor(UUID tenantId, String username) {
        seedTenantAndUser(tenantId, username, PASSWORD);
        return jwtTestHelper.generateToken(username, tenantId);
    }

    private void putCredential(String token, String provider, String body) throws Exception {
        mockMvc.perform(put("/v1/provider-credentials/" + provider)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
    }

    private long count(String table) {
        Long value = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
        return value == null ? 0 : value;
    }

    @Test
    void when_validate_with_healthyVendor_then_returnsOkAndPersistsNothing() throws Exception {
        UUID tenantId = UUID.randomUUID();
        String token = tokenFor(tenantId, "val-user-" + UUID.randomUUID());
        putCredential(token, "OPENAI", "{\"apiKey\":\"sk-test-fake-key-1234\"}");

        long sessionsBefore = count("chat_session");
        long messagesBefore = count("chat_message");

        mockMvc.perform(post("/v1/providers/validate")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"provider\":\"OPENAI\",\"model\":\"gpt-4o-mini\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.provider").value("OPENAI"))
                .andExpect(jsonPath("$.model").value("gpt-4o-mini"))
                .andExpect(jsonPath("$.latencyMs").exists())
                .andExpect(jsonPath("$.reason").doesNotExist());

        assertThat(count("chat_session")).isEqualTo(sessionsBefore);
        assertThat(count("chat_message")).isEqualTo(messagesBefore);
    }

    @Test
    void when_validate_with_vendorDown_then_returnsOkFalseAndPersistsNothing() throws Exception {
        UUID tenantId = UUID.randomUUID();
        String token = tokenFor(tenantId, "val-user-" + UUID.randomUUID());
        putCredential(token, "OPENAI", "{\"apiKey\":\"sk-test-fake-key-1234\"}");
        StubProbeConfig.behavior.set(client -> {
            throw new RuntimeException("vendor down");
        });

        long sessionsBefore = count("chat_session");

        mockMvc.perform(post("/v1/providers/validate")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"provider\":\"OPENAI\",\"model\":\"gpt-4o-mini\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(false))
                .andExpect(jsonPath("$.reason").value("connection_failed"));

        assertThat(count("chat_session")).isEqualTo(sessionsBefore);
    }

    @Test
    void when_validate_with_eleventhCall_then_returns429() throws Exception {
        UUID tenantId = UUID.randomUUID();
        String token = tokenFor(tenantId, "val-user-" + UUID.randomUUID());
        putCredential(token, "OPENAI", "{\"apiKey\":\"sk-test-fake-key-1234\"}");

        String body = "{\"provider\":\"OPENAI\",\"model\":\"gpt-4o-mini\"}";
        for (int i = 0; i < 10; i++) {
            mockMvc.perform(post("/v1/providers/validate")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk());
        }

        mockMvc.perform(post("/v1/providers/validate")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void when_validate_with_metadataBaseUrl_then_returns400() throws Exception {
        UUID tenantId = UUID.randomUUID();
        String token = tokenFor(tenantId, "val-user-" + UUID.randomUUID());
        putCredential(token, "OPENAI", "{\"apiKey\":\"sk-test-fake-key-1234\"}");

        mockMvc.perform(post("/v1/providers/validate")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"provider\":\"OPENAI_COMPATIBLE\",\"model\":\"mimo-v2.5\",\"baseUrl\":\"http://169.254.169.254/\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void when_validate_with_modelOutsideCatalog_then_returns400() throws Exception {
        UUID tenantId = UUID.randomUUID();
        String token = tokenFor(tenantId, "val-user-" + UUID.randomUUID());
        putCredential(token, "OPENAI", "{\"apiKey\":\"sk-test-fake-key-1234\"}");

        mockMvc.perform(post("/v1/providers/validate")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"provider\":\"OPENAI\",\"model\":\"llama3.2\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void when_validate_withoutCredential_then_returns422() throws Exception {
        UUID tenantId = UUID.randomUUID();
        String token = tokenFor(tenantId, "val-user-" + UUID.randomUUID());

        mockMvc.perform(post("/v1/providers/validate")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"provider\":\"ANTHROPIC\",\"model\":\"claude-3-5-haiku-latest\"}"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void when_validate_withoutToken_then_returns401() throws Exception {
        mockMvc.perform(post("/v1/providers/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"provider\":\"OPENAI\",\"model\":\"gpt-4o-mini\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void when_validate_withoutPermission_then_returns403() throws Exception {
        UUID tenantId = UUID.randomUUID();
        String username = "val-user-" + UUID.randomUUID();
        seedTenantAndUser(tenantId, username, PASSWORD);
        String limited = jwtTestHelper.generateToken(username, tenantId, List.of("CHAT_SEND"));

        mockMvc.perform(post("/v1/providers/validate")
                        .header("Authorization", "Bearer " + limited)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"provider\":\"OPENAI\",\"model\":\"gpt-4o-mini\"}"))
                .andExpect(status().isForbidden());
    }

}
