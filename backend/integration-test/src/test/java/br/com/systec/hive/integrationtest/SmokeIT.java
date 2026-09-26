package br.com.systec.hive.integrationtest;

import br.com.systec.hive.integrationtest.support.WebIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SmokeIT extends WebIntegrationTest {

    private static final String PASSWORD = "it-secret-pw";

    @Test
    void when_contextStarts_then_flywayMigrationsApplied() {
        assertThat(countAppliedMigrations("flyway_schema_history_tenant")).isPositive();
        assertThat(countAppliedMigrations("flyway_schema_history_identity")).isPositive();
        assertThat(countAppliedMigrations("flyway_schema_history_agent")).isPositive();
    }

    @Test
    void when_accessProtectedResourceWithoutToken_then_returnsUnauthorized() throws Exception {
        mockMvc.perform(get("/v1/agents"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void when_loginWithSeededUser_then_returnsAccessToken() throws Exception {
        UUID tenantId = UUID.randomUUID();
        String username = "it-user-" + UUID.randomUUID();
        seedTenantAndUser(tenantId, username, PASSWORD);

        mockMvc.perform(post("/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty());
    }

    private int countAppliedMigrations(String historyTable) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + historyTable + " WHERE success = true", Integer.class);
        return count == null ? 0 : count;
    }
}
