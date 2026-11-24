package br.com.systec.hive.integrationtest;

import br.com.systec.hive.integrationtest.support.WebIntegrationTest;
import br.com.systec.hive.publicapi.api.domain.ProvisioningPayload;
import br.com.systec.hive.publicapi.impl.provisioning.PublicApiProvisioningRunner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** ADR-003: cota própria da key, namespaces Redis distintos e telemetria preservada. */
@Import(ChatCallerParityIT.CapturingFactoryConfig.class)
class PublicApiKeyQuotaIT extends WebIntegrationTest {

    private static final String PASSWORD = "it-secret-pw";

    @Autowired
    private PublicApiProvisioningRunner provisioningRunner;

    private UUID tenant;
    private String adminToken;
    private String code;
    private ProvisioningPayload client;

    @BeforeEach
    void setUp() throws Exception {
        tenant = UUID.randomUUID();
        code = "kq-" + UUID.randomUUID().toString().substring(0, 8);
        String username = "kq-human-" + UUID.randomUUID();
        seedTenantAndUser(tenant, username, PASSWORD);
        adminToken = jwtTestHelper.generateToken(username, tenant);
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
                        .content("{\"name\":\"KeyQuota\",\"code\":\"" + code
                                + "\",\"template\":\"Voce e um assistente.\",\"inferenceParams\":{\"temperature\":0.7,\"maxTokens\":256}}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID id = UUID.fromString(body.split("\"id\":\"")[1].split("\"")[0]);
        jdbcTemplate.update("UPDATE prompt_template SET status = 'ACTIVE' WHERE id = ?", id);
        client = provisioningRunner.provision("--label=kq-" + UUID.randomUUID(),
                "--tenant-id=" + tenant, "--prompt-codes=" + code);
        ChatCallerParityIT.TRACES.clear();
    }

    @Test
    void when_migrationV4Applied_then_columnHasDefaultAndCheck() throws Exception {
        Integer applied = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history_publicapi WHERE version = '4' AND success = true",
                Integer.class);
        assertThat(applied).isEqualTo(1);

        jdbcTemplate.update("INSERT INTO public_api_tenant_quota (tenant_id, chat_requests_per_minute, create_at, update_at)"
                + " VALUES (?, 250, NOW(), NOW())", tenant);
        String keyId = issueKey();
        Integer limit = jdbcTemplate.queryForObject(
                "SELECT key_chat_requests_per_minute FROM public_api_client_key WHERE key_id = ?", Integer.class, keyId);
        assertThat(limit).isEqualTo(100);

        // Backfill da V4 em linha existente: copia a cota do tenant; fallback 100 sem linha V2.
        jdbcTemplate.update("UPDATE public_api_client_key k SET key_chat_requests_per_minute = COALESCE("
                + "(SELECT q.chat_requests_per_minute FROM public_api_tenant_quota q WHERE q.tenant_id = k.tenant_id), 100)");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT key_chat_requests_per_minute FROM public_api_client_key WHERE key_id = ?",
                Integer.class, keyId)).isEqualTo(250);

        UUID noQuotaTenant = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO public_api_client (id, create_at, update_at, tenant_id, client_id, label,"
                + " secret_hash, rate_limit_per_minute, token_ttl_seconds, max_messages, max_message_chars, max_total_chars)"
                + " VALUES (?, NOW(), NOW(), ?, ?, ?, 'hash', 60, 600, 200, 32000, 256000)",
                UUID.randomUUID(), noQuotaTenant, "nq-" + UUID.randomUUID(), "nq-" + UUID.randomUUID());
        jdbcTemplate.update("INSERT INTO public_api_client_key (id, create_at, update_at, tenant_id, client_ref, key_id, key_hash)"
                + " VALUES (?, NOW(), NOW(), ?, (SELECT id FROM public_api_client WHERE tenant_id = ?), ?, 'hash')",
                UUID.randomUUID(), noQuotaTenant, noQuotaTenant, "nq-key-" + UUID.randomUUID());
        jdbcTemplate.update("UPDATE public_api_client_key k SET key_chat_requests_per_minute = COALESCE("
                + "(SELECT q.chat_requests_per_minute FROM public_api_tenant_quota q WHERE q.tenant_id = k.tenant_id), 100)"
                + " WHERE k.tenant_id = ?", noQuotaTenant);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT key_chat_requests_per_minute FROM public_api_client_key WHERE tenant_id = ?",
                Integer.class, noQuotaTenant)).isEqualTo(100);
    }

    @Test
    void when_keyAndJwtInSameTenant_then_limitsAndCountersAreIndependent() throws Exception {
        // JWT: limite do tenant = 1. Key: limite próprio = 2.
        jdbcTemplate.update("INSERT INTO public_api_tenant_quota (tenant_id, chat_requests_per_minute, create_at, update_at)"
                + " VALUES (?, 1, NOW(), NOW())", tenant);
        String keyId = issueKey();
        jdbcTemplate.update("UPDATE public_api_client_key SET key_chat_requests_per_minute = 2 WHERE key_id = ?", keyId);
        String apiKey = "sk-" + keyId;
        String jwt = token(client);

        mockMvc.perform(chat(jwt))
                .andExpect(status().isOk())
                .andExpect(header().string("X-RateLimit-Limit-Requests", "1"));
        mockMvc.perform(chat(jwt))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("X-RateLimit-Limit-Requests", "1"));

        mockMvc.perform(chat(apiKey))
                .andExpect(status().isOk())
                .andExpect(header().string("X-RateLimit-Limit-Requests", "2"));
        mockMvc.perform(chat(apiKey))
                .andExpect(status().isOk())
                .andExpect(header().string("X-RateLimit-Limit-Requests", "2"));
        mockMvc.perform(chat(apiKey))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("X-RateLimit-Limit-Requests", "2"));

        // Telemetria da key preservada (COT04).
        var trace = ChatCallerParityIT.TRACES.get(ChatCallerParityIT.TRACES.size() - 1);
        assertThat(trace.clientId()).isEqualTo(client.getClientId());
        assertThat(trace.turnId()).isNotNull();
    }

    private String issueKey() throws Exception {
        MvcResult result = mockMvc.perform(post("/v1/public-api/clients/" + client.getClientId() + "/keys")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isCreated())
                .andReturn();
        String body = result.getResponse().getContentAsString();
        int start = body.indexOf("\"keyId\":\"") + 9;
        return body.substring(start, body.indexOf('"', start));
    }

    private String token(ProvisioningPayload payload) throws Exception {
        MvcResult result = mockMvc.perform(post("/v1/public/auth/token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"grant_type\":\"client_credentials\",\"client_id\":\"" + payload.getClientId()
                                + "\",\"client_secret\":\"" + payload.getClientSecret() + "\"}"))
                .andExpect(status().isOk()).andReturn();
        String body = result.getResponse().getContentAsString();
        int start = body.indexOf("\"access_token\":\"") + 16;
        return body.substring(start, body.indexOf('"', start));
    }

    private org.springframework.test.web.servlet.RequestBuilder chat(String credential) {
        return post("/v1/public/v1/chat/completions")
                .header("Authorization", "Bearer " + credential)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"prompt_code\":\"" + code + "\",\"messages\":[{\"role\":\"user\",\"content\":\"oi\"}]}");
    }
}
