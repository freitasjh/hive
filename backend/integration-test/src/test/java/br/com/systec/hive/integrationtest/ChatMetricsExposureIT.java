package br.com.systec.hive.integrationtest;

import br.com.systec.hive.ai.impl.StreamingMetrics;
import br.com.systec.hive.integrationtest.support.WebIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** MET-08 (Decisão 7): scrape só por loopback; probes seguem públicos; actuator sensível não é exposto. */
class ChatMetricsExposureIT extends WebIntegrationTest {

    private static final String[] EXPOSED = {
            "chat_turns_total", "chat_provider_calls_total", "chat_provider_failures_total",
            "chat_provider_latency_seconds", "chat_telemetry_export_failures_total",
            "chat_telemetry_redaction_failures_total", "chat_provider_tokens_sum"};

    private static RequestPostProcessor from(String ip) {
        return request -> {
            request.setRemoteAddr(ip);
            return request;
        };
    }

    @Test
    void when_streamingCountersAreIncremented_then_onlyFixedKeysAreAccepted() {
        StreamingMetrics metrics = new StreamingMetrics();
        metrics.increment(StreamingMetrics.FAILED);
        metrics.increment(StreamingMetrics.TIMEOUT);
        metrics.increment(StreamingMetrics.CLIENT_ABORT);
        assertThat(metrics.get(StreamingMetrics.FAILED)).isEqualTo(1L);
        assertThat(metrics.get(StreamingMetrics.TIMEOUT)).isEqualTo(1L);
        assertThat(metrics.get(StreamingMetrics.CLIENT_ABORT)).isEqualTo(1L);
        assertThatThrownBy(() -> metrics.increment("thinking-CANARIO")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void when_scrapeComesFromLoopback_then_allSevenSeriesAreExposed() throws Exception {
        String body = mockMvc.perform(get("/actuator/prometheus").with(from("127.0.0.1")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).contains(EXPOSED);
        assertThat(body).doesNotContain("chat_telemetry_export_failures_total_total", "gen_ai_client_token_usage");
    }

    @Test
    void when_scrapeComesFromIpv6Loopback_then_itIsAllowed() throws Exception {
        mockMvc.perform(get("/actuator/prometheus").with(from("::1")))
                .andExpect(status().isOk());
    }

    @Test
    void when_scrapeComesFromExternalAddress_then_403WithExistingBody() throws Exception {
        mockMvc.perform(get("/actuator/prometheus").with(from("203.0.113.10")))
                .andExpect(status().isForbidden())
                .andExpect(content().json("{\"error\":\"Acesso negado\"}"));
    }

    @Test
    void when_contextPathIsUsedFromExternalAddress_then_403WithExistingBody() throws Exception {
        mockMvc.perform(get("/hive/actuator/prometheus").contextPath("/hive").with(from("203.0.113.10")))
                .andExpect(status().isForbidden())
                .andExpect(content().json("{\"error\":\"Acesso negado\"}"));
    }

    @Test
    void when_probesAreRequestedFromExternalAddress_then_noCredentialIsNeeded() throws Exception {
        mockMvc.perform(get("/actuator/health").with(from("203.0.113.10")))
                .andExpect(status().isOk());
        mockMvc.perform(get("/actuator/info").with(from("203.0.113.10")))
                .andExpect(status().isOk());
    }

    @Test
    void when_sensitiveActuatorIsRequested_then_authenticatedCallerGets404AndAnonymousGets401Or403() throws Exception {
        UUID tenantId = UUID.randomUUID();
        String username = "it-user-" + UUID.randomUUID();
        seedTenantAndUser(tenantId, username, "it-secret-pw");
        String token = jwtTestHelper.generateToken(username, tenantId);

        int anonymous = mockMvc.perform(get("/actuator/env").with(from("127.0.0.1")))
                .andReturn().getResponse().getStatus();
        assertThat(anonymous).isIn(401, 403);

        mockMvc.perform(get("/actuator/env").with(from("127.0.0.1"))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }
}
