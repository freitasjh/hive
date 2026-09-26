package br.com.systec.hive.integrationtest;

import br.com.systec.hive.integrationtest.support.WebIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class NoResourceIT extends WebIntegrationTest {

    @Test
    void when_getUnknownRoute_with_authenticatedUser_then_returns404ProblemJson() throws Exception {
        UUID tenantId = UUID.randomUUID();
        String username = "it-user-" + UUID.randomUUID();
        seedTenantAndUser(tenantId, username, "it-secret-pw");
        String token = jwtTestHelper.generateToken(username, tenantId);

        mockMvc.perform(get("/v1/route-that-does-not-exist-xyz")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.detail").value("Recurso não encontrado"));
    }

    @Test
    void when_getUnknownRoute_without_token_then_returns401() throws Exception {
        mockMvc.perform(get("/v1/route-that-does-not-exist-xyz"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void when_getAuthMe_with_authenticatedUser_then_returns404ProblemJson() throws Exception {
        UUID tenantId = UUID.randomUUID();
        String username = "it-user-" + UUID.randomUUID();
        seedTenantAndUser(tenantId, username, "it-secret-pw");
        String token = jwtTestHelper.generateToken(username, tenantId);

        mockMvc.perform(get("/v1/auth/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }
}
