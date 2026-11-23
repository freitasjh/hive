package br.com.systec.hive.integrationtest;

import br.com.systec.hive.integrationtest.support.WebIntegrationTest;
import br.com.systec.hive.publicapi.api.domain.ProvisioningPayload;
import br.com.systec.hive.publicapi.impl.provisioning.PublicApiProvisioningRunner;
import br.com.systec.hive.publicapi.impl.service.PublicClientTokenIssuer;
import com.auth0.jwt.interfaces.DecodedJWT;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** ADR-004: V2 em banco real, snapshot plim.r, cota por tenant e headers de rate limit. */
@Import(ChatCallerParityIT.CapturingFactoryConfig.class)
class PublicApiTenantQuotaIT extends WebIntegrationTest {

    private static final String PASSWORD = "it-secret-pw";

    @Autowired
    private PublicApiProvisioningRunner provisioningRunner;

    @Autowired
    private PublicClientTokenIssuer tokenIssuer;

    @Test
    void when_migrationV2Applied_then_tableHasDefaultAndCheckConstraint() {
        Integer applied = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history_publicapi WHERE version = '2' AND success = true",
                Integer.class);
        assertThat(applied).isEqualTo(1);

        UUID tenant = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO public_api_tenant_quota (tenant_id, create_at, update_at) VALUES (?, NOW(), NOW())",
                tenant);
        Integer limit = jdbcTemplate.queryForObject(
                "SELECT chat_requests_per_minute FROM public_api_tenant_quota WHERE tenant_id = ?",
                Integer.class, tenant);
        assertThat(limit).isEqualTo(100);

        assertThatThrownBy(() -> jdbcTemplate.update(
                "UPDATE public_api_tenant_quota SET chat_requests_per_minute = 0 WHERE tenant_id = ?", tenant))
                .isInstanceOf(Exception.class);
    }

    @Test
    void when_tokenIssued_then_plimRIsSnapshotWithFallback100() throws Exception {
        UUID tenant = UUID.randomUUID();
        ProvisioningPayload payload = provision(tenant, "quota-plim-" + UUID.randomUUID(), "p1");

        jdbcTemplate.update("INSERT INTO public_api_tenant_quota (tenant_id, chat_requests_per_minute, create_at, update_at)"
                + " VALUES (?, 250, NOW(), NOW())", tenant);

        DecodedJWT decoded = tokenIssuer.decodeAndVerify(token(payload));
        @SuppressWarnings("unchecked")
        Map<String, Object> plim = decoded.getClaim("plim").asMap();
        assertThat(plim).containsEntry("r", 250);
        assertThat(plim).containsKeys("m", "c", "t");

        jdbcTemplate.update("DELETE FROM public_api_tenant_quota WHERE tenant_id = ?", tenant);
        DecodedJWT withoutRow = tokenIssuer.decodeAndVerify(token(payload));
        @SuppressWarnings("unchecked")
        Map<String, Object> fallback = withoutRow.getClaim("plim").asMap();
        assertThat(fallback).containsEntry("r", 100);
        assertThat(decoded.getAudience()).containsExactly("hive.public");
        assertThat(withoutRow.getClaim("tenant-id").asString()).isEqualTo(tenant.toString());
    }

    @Test
    void when_twoClientsShareTenant_then_secondIsDeniedAndHeadersAreMeasured() throws Exception {
        UUID tenant = UUID.randomUUID();
        String code = "quota-" + UUID.randomUUID().toString().substring(0, 8);
        seedHumanLlmChain(tenant, code);
        ProvisioningPayload first = provision(tenant, "cota-a-" + UUID.randomUUID(), code);
        ProvisioningPayload second = provision(tenant, "cota-b-" + UUID.randomUUID(), code);
        jdbcTemplate.update("INSERT INTO public_api_tenant_quota (tenant_id, chat_requests_per_minute, create_at, update_at)"
                + " VALUES (?, 1, NOW(), NOW())", tenant);

        String tokenFirst = token(first);
        String tokenSecond = token(second);
        String body = "{\"prompt_code\":\"" + code + "\",\"messages\":[{\"role\":\"user\",\"content\":\"oi\"}]}";

        mockMvc.perform(chat(tokenFirst, body))
                .andExpect(status().isOk())
                .andExpect(header().string("X-RateLimit-Limit-Requests", "1"))
                .andExpect(header().string("X-RateLimit-Remaining-Requests", "0"))
                .andExpect(header().exists("X-RateLimit-Reset-Requests"))
                .andExpect(header().doesNotExist("Retry-After"));

        mockMvc.perform(chat(tokenSecond, body))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error.code").value("quota_exceeded"))
                .andExpect(header().exists("Retry-After"))
                .andExpect(header().string("X-RateLimit-Limit-Requests", "1"))
                .andExpect(header().string("X-RateLimit-Remaining-Requests", "0"))
                .andExpect(header().exists("X-RateLimit-Reset-Requests"));
    }

    @Test
    void when_differentTenants_then_quotasAreIsolated() throws Exception {
        UUID tenantA = UUID.randomUUID();
        UUID tenantB = UUID.randomUUID();
        String code = "iso-" + UUID.randomUUID().toString().substring(0, 8);
        seedHumanLlmChain(tenantA, code);
        seedHumanLlmChain(tenantB, code);
        ProvisioningPayload clientA = provision(tenantA, "iso-a-" + UUID.randomUUID(), code);
        ProvisioningPayload clientB = provision(tenantB, "iso-b-" + UUID.randomUUID(), code);
        jdbcTemplate.update("INSERT INTO public_api_tenant_quota (tenant_id, chat_requests_per_minute, create_at, update_at)"
                + " VALUES (?, 1, NOW(), NOW())", tenantA);
        jdbcTemplate.update("INSERT INTO public_api_tenant_quota (tenant_id, chat_requests_per_minute, create_at, update_at)"
                + " VALUES (?, 1, NOW(), NOW())", tenantB);

        String body = "{\"prompt_code\":\"" + code + "\",\"messages\":[{\"role\":\"user\",\"content\":\"oi\"}]}";
        mockMvc.perform(chat(token(clientA), body)).andExpect(status().isOk());
        // tenant A já consumiu seu 1º request; tenant B tem contador próprio.
        mockMvc.perform(chat(token(clientB), body)).andExpect(status().isOk());
        mockMvc.perform(chat(token(clientA), body)).andExpect(status().isTooManyRequests());
    }

    private ProvisioningPayload provision(UUID tenant, String label, String promptCode) {
        return provisioningRunner.provision("--label=" + label, "--tenant-id=" + tenant,
                "--prompt-codes=" + promptCode);
    }

    private String token(ProvisioningPayload payload) throws Exception {
        String body = "{\"grant_type\":\"client_credentials\",\"client_id\":\"" + payload.getClientId()
                + "\",\"client_secret\":\"" + payload.getClientSecret() + "\"}";
        MvcResult result = mockMvc.perform(post("/v1/public/auth/token")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andReturn();
        String json = result.getResponse().getContentAsString();
        int start = json.indexOf("\"access_token\":\"") + 16;
        return json.substring(start, json.indexOf('"', start));
    }

    private org.springframework.test.web.servlet.RequestBuilder chat(String token, String body) {
        return post("/v1/public/v1/chat/completions")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
    }

    private void seedHumanLlmChain(UUID tenant, String code) throws Exception {
        String username = "quota-human-" + UUID.randomUUID();
        seedTenantAndUser(tenant, username, PASSWORD);
        String token = jwtTestHelper.generateToken(username, tenant);
        mockMvc.perform(put("/v1/provider-credentials/OPENCODE_GO")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"apiKey\":\"sk-test-fake-key\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(put("/v1/tenant/llm-config")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"provider\":\"OPENCODE_GO\",\"model\":\"mimo-v2.5\"}"))
                .andExpect(status().isOk());
        String body = mockMvc.perform(post("/v1/prompt-templates")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Cota\",\"code\":\"" + code
                                + "\",\"template\":\"Voce e um assistente.\",\"inferenceParams\":{\"temperature\":0.7,\"maxTokens\":256}}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID id = UUID.fromString(body.split("\"id\":\"")[1].split("\"")[0]);
        jdbcTemplate.update("UPDATE prompt_template SET status = 'ACTIVE' WHERE id = ?", id);
    }
}
