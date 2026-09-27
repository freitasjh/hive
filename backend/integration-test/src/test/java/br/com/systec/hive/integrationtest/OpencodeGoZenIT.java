package br.com.systec.hive.integrationtest;

import br.com.systec.hive.agent.impl.converter.AesGcmAttributeConverter;
import br.com.systec.hive.integrationtest.support.WebIntegrationTest;
import br.com.systec.hive.provider.api.AiProvider;
import br.com.systec.hive.provider.api.DiscoveredModels;
import br.com.systec.hive.provider.api.FilteredModel;
import br.com.systec.hive.provider.api.LlmClientFactory;
import br.com.systec.hive.provider.api.OpencodeDiscoveryService;
import br.com.systec.hive.provider.api.exception.DiscoveryFailedException;
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
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(OpencodeGoZenIT.StubConfig.class)
class OpencodeGoZenIT extends WebIntegrationTest {

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
    static class StubConfig {

        static final AtomicReference<Function<AiProvider, DiscoveredModels>> discoveryBehavior =
                new AtomicReference<>(provider -> new DiscoveredModels(
                        List.of("mimo-v2.5"), List.of(new FilteredModel("grok-4.5", "DENYLISTED")),
                        Instant.now()));
        static final AtomicBoolean evicted = new AtomicBoolean(false);
        static final AtomicReference<Function<LlmClientFactoryParams, ChatModel>> clientBehavior =
                new AtomicReference<>(params -> {
                    throw new IllegalStateException("unexpected LLM call");
                });

        record LlmClientFactoryParams(AiProvider provider, String model) {
        }

        @Bean
        @Primary
        OpencodeDiscoveryService stubDiscovery() {
            return new OpencodeDiscoveryService() {
                @Override
                public DiscoveredModels discover(AiProvider provider) {
                    return discoveryBehavior.get().apply(provider);
                }

                @Override
                public void evict(AiProvider provider) {
                    evicted.set(true);
                }
            };
        }

        @Bean
        @Primary
        @SuppressWarnings("unchecked")
        LlmClientFactory<ChatModel> stubClientFactory() {
            return (provider, model, temperature, maxTokens, topP, apiKey, baseUrl) ->
                    clientBehavior.get().apply(new LlmClientFactoryParams(provider, model));
        }
    }

    @BeforeEach
    void resetStubs() {
        StubConfig.discoveryBehavior.set(provider -> new DiscoveredModels(
                List.of("mimo-v2.5"), List.of(new FilteredModel("grok-4.5", "DENYLISTED")),
                Instant.now()));
        StubConfig.evicted.set(false);
        StubConfig.clientBehavior.set(params -> {
            throw new IllegalStateException("unexpected LLM call");
        });
    }

    private String tokenFor(UUID tenantId, String username) {
        seedTenantAndUser(tenantId, username, PASSWORD);
        return jwtTestHelper.generateToken(username, tenantId);
    }

    @Test
    void when_discovery_with_go_then_returnsFilteredList() throws Exception {
        UUID tenantId = UUID.randomUUID();
        String token = tokenFor(tenantId, "gozen-user-" + UUID.randomUUID());

        mockMvc.perform(get("/v1/providers/go/models")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.models[0]").value("mimo-v2.5"))
                .andExpect(jsonPath("$.filteredOut[0].id").value("grok-4.5"))
                .andExpect(jsonPath("$.filteredOut[0].reason").value("DENYLISTED"))
                .andExpect(jsonPath("$.cachedAt").exists());

        mockMvc.perform(get("/v1/providers/zen/models")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void when_discovery_with_refresh_then_evicts() throws Exception {
        UUID tenantId = UUID.randomUUID();
        String token = tokenFor(tenantId, "gozen-user-" + UUID.randomUUID());

        mockMvc.perform(get("/v1/providers/go/models")
                        .param("refresh", "true")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        assertThat(StubConfig.evicted.get()).isTrue();
    }

    @Test
    void when_discovery_with_remoteFailure_then_returns422() throws Exception {
        UUID tenantId = UUID.randomUUID();
        String token = tokenFor(tenantId, "gozen-user-" + UUID.randomUUID());
        StubConfig.discoveryBehavior.set(provider -> {
            throw new DiscoveryFailedException(String.valueOf(provider));
        });

        mockMvc.perform(get("/v1/providers/go/models")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("DISCOVERY_FAILED"));
    }

    @Test
    void when_discovery_with_unsupportedProvider_then_returns400() throws Exception {
        UUID tenantId = UUID.randomUUID();
        String token = tokenFor(tenantId, "gozen-user-" + UUID.randomUUID());

        mockMvc.perform(get("/v1/providers/openai/models")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    @Test
    void when_discovery_with_eleventhCall_then_returns429() throws Exception {
        UUID tenantId = UUID.randomUUID();
        String token = tokenFor(tenantId, "gozen-user-" + UUID.randomUUID());

        for (int i = 0; i < 10; i++) {
            mockMvc.perform(get("/v1/providers/go/models")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk());
        }
        mockMvc.perform(get("/v1/providers/go/models")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void when_discovery_withoutToken_then_returns401() throws Exception {
        mockMvc.perform(get("/v1/providers/go/models"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void when_discovery_withoutPermission_then_returns403() throws Exception {
        UUID tenantId = UUID.randomUUID();
        String username = "gozen-user-" + UUID.randomUUID();
        seedTenantAndUser(tenantId, username, PASSWORD);
        String limited = jwtTestHelper.generateToken(username, tenantId, List.of("CHAT_SEND"));

        mockMvc.perform(get("/v1/providers/go/models")
                        .header("Authorization", "Bearer " + limited))
                .andExpect(status().isForbidden());
    }

    @Test
    void when_credential_with_goZen_then_prefixSkippedAndTenantIsolated() throws Exception {
        UUID tenantA = UUID.randomUUID();
        String tokenA = tokenFor(tenantA, "gozen-a-" + UUID.randomUUID());
        UUID tenantB = UUID.randomUUID();
        String tokenB = tokenFor(tenantB, "gozen-b-" + UUID.randomUUID());

        mockMvc.perform(put("/v1/provider-credentials/OPENCODE_GO")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"apiKey\":\"go-key-without-any-prefix\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configured").value(true));

        mockMvc.perform(put("/v1/provider-credentials/OPENCODE_ZEN")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"apiKey\":\"zen-key-without-any-prefix\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/v1/provider-credentials")
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.provider == 'OPENCODE_GO')].configured").value(false))
                .andExpect(jsonPath("$[?(@.provider == 'OPENCODE_ZEN')].configured").value(false));

        mockMvc.perform(delete("/v1/provider-credentials/OPENCODE_GO")
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/v1/provider-credentials")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.provider == 'OPENCODE_GO')].configured").value(true));
    }

    @Test
    void when_credential_with_goBaseUrl_then_returns400() throws Exception {
        UUID tenantId = UUID.randomUUID();
        String token = tokenFor(tenantId, "gozen-user-" + UUID.randomUUID());

        mockMvc.perform(put("/v1/provider-credentials/OPENCODE_GO")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"apiKey\":\"go-key\",\"baseUrl\":\"https://custom.example/v1\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void when_credential_with_goZenEchoOfFixedVendorUrl_then_returns200AndDoesNotPersistBaseUrl() throws Exception {
        UUID tenantId = UUID.randomUUID();
        String token = tokenFor(tenantId, "gozen-user-" + UUID.randomUUID());

        // A UI exibe a URL fixa do vendor como readonly; ecoá-la não pode dar 400 (footgun de API).
        mockMvc.perform(put("/v1/provider-credentials/OPENCODE_GO")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"apiKey\":\"go-key\",\"baseUrl\":\"https://opencode.ai/zen/go/v1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configured").value(true));

        mockMvc.perform(put("/v1/provider-credentials/OPENCODE_ZEN")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"apiKey\":\"zen-key\",\"baseUrl\":\"https://opencode.ai/zen/v1\"}"))
                .andExpect(status().isOk());

        // A baseUrl fixa é constante do vendor: nunca vira estado do tenant.
        assertThat(storedBaseUrl(tenantId, "OPENCODE_GO")).isNull();
        assertThat(storedBaseUrl(tenantId, "OPENCODE_ZEN")).isNull();

        mockMvc.perform(get("/v1/provider-credentials")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.provider == 'OPENCODE_GO')].configured").value(true))
                .andExpect(jsonPath("$[?(@.provider == 'OPENCODE_GO')].baseUrl",
                        org.hamcrest.Matchers.hasItem(org.hamcrest.Matchers.nullValue())))
                .andExpect(jsonPath("$[?(@.provider == 'OPENCODE_ZEN')].baseUrl",
                        org.hamcrest.Matchers.hasItem(org.hamcrest.Matchers.nullValue())));
    }

    @Test
    void when_credential_with_goZenOverrideAttempt_then_returns400AndNeverPersists() throws Exception {
        UUID tenantId = UUID.randomUUID();
        String token = tokenFor(tenantId, "gozen-user-" + UUID.randomUUID());

        // Teste de SEGURANÇA: o override da baseUrl fixa continua bloqueado.
        mockMvc.perform(put("/v1/provider-credentials/OPENCODE_GO")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"apiKey\":\"go-key\",\"baseUrl\":\"https://evil.example.com\"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(put("/v1/provider-credentials/OPENCODE_ZEN")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"apiKey\":\"zen-key\",\"baseUrl\":\"https://evil.example.com\"}"))
                .andExpect(status().isBadRequest());

        // Nada foi persistido: nem apiKey, nem baseUrl.
        mockMvc.perform(get("/v1/provider-credentials")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.provider == 'OPENCODE_GO')].configured").value(false))
                .andExpect(jsonPath("$[?(@.provider == 'OPENCODE_ZEN')].configured").value(false));
    }

    private String storedBaseUrl(UUID tenantId, String provider) {
        return jdbcTemplate.queryForObject(
                "SELECT base_url FROM provider_credential WHERE tenant_id = ? AND provider = ?",
                String.class, tenantId, provider);
    }

    @Test
    void when_credential_with_customCgnatOrHttp_then_returns400() throws Exception {
        UUID tenantId = UUID.randomUUID();
        String token = tokenFor(tenantId, "gozen-user-" + UUID.randomUUID());

        mockMvc.perform(put("/v1/provider-credentials/OPENAI_COMPATIBLE")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"apiKey\":\"custom-key\",\"baseUrl\":\"https://100.64.0.5/v1\"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(put("/v1/provider-credentials/OPENAI_COMPATIBLE")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"apiKey\":\"custom-key\",\"baseUrl\":\"http://203.0.113.10/v1\"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(put("/v1/provider-credentials/OPENAI_COMPATIBLE")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"apiKey\":\"custom-key\",\"baseUrl\":\"https://203.0.113.10/v1\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void when_chat_with_staleGoModel_then_returns422AndPreservesUserMsg() throws Exception {
        UUID tenantId = UUID.randomUUID();
        String token = tokenFor(tenantId, "gozen-user-" + UUID.randomUUID());

        mockMvc.perform(put("/v1/provider-credentials/OPENCODE_GO")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"apiKey\":\"go-fake-key\"}"))
                .andExpect(status().isOk());

        // O provider e o modelo passaram a ser o default do tenant (option (a), tabela dedicada);
        // o prompt é escolhido por conversa.
        mockMvc.perform(put("/v1/tenant/llm-config")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"provider\":\"OPENCODE_GO\",\"model\":\"mimo-v2.5\"}"))
                .andExpect(status().isOk());

        String created = mockMvc.perform(post("/v1/prompt-templates")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Go Prompt\",\"code\":\"go-prompt\",\"template\":\"Voce e um assistente.\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String promptTemplateId = new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(created).get("id").asText();

        StubConfig.clientBehavior.set(params -> {
            throw new dev.langchain4j.exception.ModelNotFoundException("model_not_found: mimo-v2.5");
        });

        long messagesBefore = count("chat_message");

        mockMvc.perform(post("/v1/chat")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"promptTemplateId\":\"" + promptTemplateId + "\",\"message\":\"hello go\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("MODEL_UNAVAILABLE"))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("mimo-v2.5")));

        assertThat(count("chat_message")).isEqualTo(messagesBefore + 1);
        assertThat(StubConfig.evicted.get()).isTrue();
    }

    private long count(String table) {
        Long value = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
        return value == null ? 0 : value;
    }
}
