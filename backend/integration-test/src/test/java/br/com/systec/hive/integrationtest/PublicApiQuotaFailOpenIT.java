package br.com.systec.hive.integrationtest;

import br.com.systec.hive.integrationtest.support.WebIntegrationTest;
import br.com.systec.hive.publicapi.api.domain.ProvisioningPayload;
import br.com.systec.hive.publicapi.impl.provisioning.PublicApiProvisioningRunner;
import br.com.systec.hive.publicapi.impl.quota.QuotaDecision;
import br.com.systec.hive.publicapi.impl.quota.QuotaExceededException;
import br.com.systec.hive.publicapi.impl.quota.RedisQuotaGuard;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * R1 (débito 2): captura os headers HTTP reais quando o guard devolve decisão não medida
 * (o mesmo estado que o fail-open do Redis produz, já provado em RedisQuotaGuardTest).
 */
@Import(ChatCallerParityIT.CapturingFactoryConfig.class)
class PublicApiQuotaFailOpenIT extends WebIntegrationTest {

    private static final String PASSWORD = "it-secret-pw";

    @MockitoBean
    private RedisQuotaGuard quotaGuard;

    @Autowired
    private PublicApiProvisioningRunner provisioningRunner;

    @Test
    void when_guardIsNotMeasured_then_successOmitsRateLimitHeaders() throws Exception {
        Fixture fixture = fixture();
        when(quotaGuard.acquireTenant(any(), anyInt()))
                .thenReturn(new QuotaDecision(100, 99, 0, 0, false));

        MvcResult result = mockMvc.perform(chat(fixture.token, fixture.code))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("X-RateLimit-Limit-Requests"))
                .andExpect(header().doesNotExist("X-RateLimit-Remaining-Requests"))
                .andExpect(header().doesNotExist("X-RateLimit-Reset-Requests"))
                .andReturn();

        System.out.println("N2_MEASURE success_headers=" + headerNames(result));
        assertThat(headerNames(result)).doesNotContain("X-RateLimit-Limit-Requests");
    }

    @Test
    void when_guardDeniesWithoutMeasurement_then_retryAfterSurvivesAndHeadersAreOmitted() throws Exception {
        Fixture fixture = fixture();
        when(quotaGuard.acquireTenant(any(), anyInt()))
                .thenThrow(new QuotaExceededException("quota", 7));

        MvcResult result = mockMvc.perform(chat(fixture.token, fixture.code))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error.code").value("quota_exceeded"))
                .andExpect(header().string("Retry-After", "7"))
                .andExpect(header().doesNotExist("X-RateLimit-Limit-Requests"))
                .andExpect(header().doesNotExist("X-RateLimit-Remaining-Requests"))
                .andExpect(header().doesNotExist("X-RateLimit-Reset-Requests"))
                .andReturn();

        System.out.println("N2_MEASURE denied_headers=" + headerNames(result));
        assertThat(headerNames(result)).contains("retry-after");
    }

    private Fixture fixture() throws Exception {
        UUID tenant = UUID.randomUUID();
        String code = "failopen-" + UUID.randomUUID().toString().substring(0, 8);
        String username = "failopen-human-" + UUID.randomUUID();
        seedTenantAndUser(tenant, username, PASSWORD);
        String adminToken = jwtTestHelper.generateToken(username, tenant);
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
                        .content("{\"name\":\"FailOpen\",\"code\":\"" + code
                                + "\",\"template\":\"Voce e um assistente.\",\"inferenceParams\":{\"temperature\":0.7,\"maxTokens\":256}}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID templateId = UUID.fromString(body.split("\"id\":\"")[1].split("\"")[0]);
        jdbcTemplate.update("UPDATE prompt_template SET status = 'ACTIVE' WHERE id = ?", templateId);

        ProvisioningPayload payload = provisioningRunner.provision(
                "--label=failopen-" + UUID.randomUUID(), "--tenant-id=" + tenant, "--prompt-codes=" + code);
        String tokenBody = "{\"grant_type\":\"client_credentials\",\"client_id\":\"" + payload.getClientId()
                + "\",\"client_secret\":\"" + payload.getClientSecret() + "\"}";
        MvcResult tokenResult = mockMvc.perform(post("/v1/public/auth/token")
                        .contentType(MediaType.APPLICATION_JSON).content(tokenBody))
                .andExpect(status().isOk()).andReturn();
        String json = tokenResult.getResponse().getContentAsString();
        int start = json.indexOf("\"access_token\":\"") + 16;
        return new Fixture(code, json.substring(start, json.indexOf('"', start)));
    }

    private record Fixture(String code, String token) {
    }

    private static org.springframework.test.web.servlet.RequestBuilder chat(String token, String code) {
        return post("/v1/public/v1/chat/completions")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"prompt_code\":\"" + code + "\",\"messages\":[{\"role\":\"user\",\"content\":\"oi\"}]}");
    }

    private static java.util.List<String> headerNames(MvcResult result) {
        return result.getResponse().getHeaderNames().stream().map(String::toLowerCase).toList();
    }
}
