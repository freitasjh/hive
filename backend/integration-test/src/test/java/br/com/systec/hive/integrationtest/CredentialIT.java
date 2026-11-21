package br.com.systec.hive.integrationtest;

import br.com.systec.hive.assistant.impl.converter.AesGcmAttributeConverter;
import br.com.systec.hive.assistant.impl.repository.ProviderCredentialRepository;
import br.com.systec.hive.assistant.impl.startup.CredentialMasterKeyStartupCheck;
import br.com.systec.hive.integrationtest.support.WebIntegrationTest;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.security.SecureRandom;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CredentialIT extends WebIntegrationTest {

    private static final String PASSWORD = "it-secret-pw";
    private static final String OPENAI_KEY = "sk-test-fake-key-1234";
    private static final String GEMINI_KEY = "AIzaTestFakeKey-5678";

    private static String savedProfiles;

    @Autowired
    private ProviderCredentialRepository providerCredentialRepository;

    @Autowired
    private CredentialMasterKeyStartupCheck startupCheck;

    private ListAppender<ILoggingEvent> startupLogs;
    private Logger startupLogger;

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

    @BeforeEach
    void attachStartupLogAppender() {
        startupLogger = (Logger) LoggerFactory.getLogger(CredentialMasterKeyStartupCheck.class);
        startupLogs = new ListAppender<>();
        startupLogs.start();
        startupLogger.addAppender(startupLogs);
    }

    @AfterEach
    void detachStartupLogAppender() {
        startupLogger.detachAppender(startupLogs);
        startupLogs.stop();
    }

    private String tokenFor(UUID tenantId, String username) {
        seedTenantAndUser(tenantId, username, PASSWORD);
        return jwtTestHelper.generateToken(username, tenantId);
    }

    @Test
    void when_put_with_validKey_then_returnsConfiguredMasked() throws Exception {
        UUID tenantId = UUID.randomUUID();
        String token = tokenFor(tenantId, "cred-user-" + UUID.randomUUID());

        String body = mockMvc.perform(put("/v1/provider-credentials/OPENAI")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"apiKey\":\"" + OPENAI_KEY + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.provider").value("OPENAI"))
                .andExpect(jsonPath("$.configured").value(true))
                .andExpect(jsonPath("$.last4").value("1234"))
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain(OPENAI_KEY);
        assertThat(body).doesNotContain("ciphertext");
    }

    @Test
    void when_get_with_storedCredential_then_returnsMaskedWithoutSecret() throws Exception {
        UUID tenantId = UUID.randomUUID();
        String token = tokenFor(tenantId, "cred-user-" + UUID.randomUUID());
        mockMvc.perform(put("/v1/provider-credentials/GEMINI")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"apiKey\":\"" + GEMINI_KEY + "\"}"))
                .andExpect(status().isOk());

        String body = mockMvc.perform(get("/v1/provider-credentials")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.provider == 'GEMINI')].configured").value(true))
                .andExpect(jsonPath("$[?(@.provider == 'GEMINI')].last4").value("5678"))
                .andExpect(jsonPath("$[?(@.provider == 'OLLAMA')].configured").value(false))
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain(GEMINI_KEY);
        assertThat(body).doesNotContain("ciphertext");
    }

    @Test
    void when_crossTenantDelete_then_notFoundAndOwnerIntact() throws Exception {
        UUID tenantA = UUID.randomUUID();
        String tokenA = tokenFor(tenantA, "cred-a-" + UUID.randomUUID());
        UUID tenantB = UUID.randomUUID();
        String tokenB = tokenFor(tenantB, "cred-b-" + UUID.randomUUID());

        mockMvc.perform(put("/v1/provider-credentials/OPENAI")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"apiKey\":\"" + OPENAI_KEY + "\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/v1/provider-credentials/OPENAI")
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/v1/provider-credentials")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.provider == 'OPENAI')].configured").value(true));
    }

    @Test
    void when_crossTenantGet_then_doesNotLeakOtherTenant() throws Exception {
        UUID tenantA = UUID.randomUUID();
        String tokenA = tokenFor(tenantA, "cred-a-" + UUID.randomUUID());
        UUID tenantB = UUID.randomUUID();
        String tokenB = tokenFor(tenantB, "cred-b-" + UUID.randomUUID());

        mockMvc.perform(put("/v1/provider-credentials/OPENAI")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"apiKey\":\"" + OPENAI_KEY + "\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/v1/provider-credentials")
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.provider == 'OPENAI')].configured").value(false))
                .andExpect(jsonPath("$[?(@.provider == 'OPENAI')].last4")
                        .value(org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.nullValue())));
    }

    @Test
    void when_put_with_wrongPrefix_then_returns400() throws Exception {
        UUID tenantId = UUID.randomUUID();
        String token = tokenFor(tenantId, "cred-user-" + UUID.randomUUID());

        mockMvc.perform(put("/v1/provider-credentials/OPENAI")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"apiKey\":\"AIzaWrongPrefix\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void when_put_with_privateBaseUrl_then_returns400() throws Exception {
        UUID tenantId = UUID.randomUUID();
        String token = tokenFor(tenantId, "cred-user-" + UUID.randomUUID());

        mockMvc.perform(put("/v1/provider-credentials/OLLAMA")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"baseUrl\":\"http://192.168.1.10:11434\"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(put("/v1/provider-credentials/OPENAI_COMPATIBLE")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"apiKey\":\"custom-fake\",\"baseUrl\":\"http://169.254.169.254/\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void when_request_withoutToken_then_returns401() throws Exception {
        mockMvc.perform(get("/v1/provider-credentials"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void when_request_withoutPermission_then_returns403() throws Exception {
        UUID tenantId = UUID.randomUUID();
        String username = "cred-user-" + UUID.randomUUID();
        seedTenantAndUser(tenantId, username, PASSWORD);
        String limited = jwtTestHelper.generateToken(username, tenantId, List.of("CHAT_SEND"));

        mockMvc.perform(get("/v1/provider-credentials")
                        .header("Authorization", "Bearer " + limited))
                .andExpect(status().isForbidden());
    }

    @Test
    void when_delete_with_existing_then_returns204AndUnconfigured() throws Exception {
        UUID tenantId = UUID.randomUUID();
        String token = tokenFor(tenantId, "cred-user-" + UUID.randomUUID());
        mockMvc.perform(put("/v1/provider-credentials/OPENAI")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"apiKey\":\"" + OPENAI_KEY + "\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/v1/provider-credentials/OPENAI")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        mockMvc.perform(get("/v1/provider-credentials")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.provider == 'OPENAI')].configured").value(false));
    }

    @Test
    void when_startupCheck_readsRawCiphertext_then_queryWorksAndMatchesStoredCredential() throws Exception {
        // A checagem de startup engole falha de infra com WARN. Se esta native query quebrasse,
        // o banner de PROVIDER_MASTER_KEY nunca apareceria e ninguém perceberia. Valida o SQL real.
        assertThat(providerCredentialRepository.findFirstRawCiphertext()).isEmpty();

        UUID tenantId = UUID.randomUUID();
        String token = tokenFor(tenantId, "cred-startup-" + UUID.randomUUID());
        mockMvc.perform(put("/v1/provider-credentials/OPENAI")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"apiKey\":\"" + OPENAI_KEY + "\"}"))
                .andExpect(status().isOk());

        Optional<String> raw = providerCredentialRepository.findFirstRawCiphertext();
        assertThat(raw).isPresent();
        // É o envelope cifrado (Base64 do IV+ciphertext), nunca o plaintext.
        assertThat(raw.get()).doesNotContain(OPENAI_KEY);
        assertThat(new AesGcmAttributeConverter().convertToEntityAttribute(raw.get())).isEqualTo(OPENAI_KEY);

        // Com a chave certa, o startup check não deve logar nada.
        startupCheck.run(null);
        assertThat(startupErrorMessages()).isEmpty();
    }

    @Test
    void when_startupCheck_withDivergentMasterKey_then_logsSingleBannerWithoutLeakingSecret() throws Exception {
        UUID tenantId = UUID.randomUUID();
        String token = tokenFor(tenantId, "cred-divergent-" + UUID.randomUUID());
        mockMvc.perform(put("/v1/provider-credentials/OPENAI")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"apiKey\":\"" + OPENAI_KEY + "\"}"))
                .andExpect(status().isOk());

        // Chave que NÃO cifrou as credenciais acima.
        byte[] divergent = new byte[32];
        new SecureRandom().nextBytes(divergent);
        AesGcmAttributeConverter.setTestKey(divergent);
        try {
            startupCheck.run(null);
        } finally {
            AesGcmAttributeConverter.clearTestKey();
            byte[] restored = new byte[32];
            new SecureRandom().nextBytes(restored);
            AesGcmAttributeConverter.setTestKey(restored);
        }

        List<String> errors = startupErrorMessages();
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0)).contains("PROVIDER_MASTER_KEY").contains("não corresponde");
        assertThat(errors.get(0)).doesNotContain(OPENAI_KEY);
    }

    private List<String> startupErrorMessages() {
        return startupLogs.list.stream()
                .filter(e -> e.getLevel() == Level.ERROR)
                .map(ILoggingEvent::getFormattedMessage)
                .toList();
    }
}
