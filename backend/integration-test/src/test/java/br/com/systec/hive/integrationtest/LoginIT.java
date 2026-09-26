package br.com.systec.hive.integrationtest;

import br.com.systec.hive.integrationtest.support.WebIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class LoginIT extends WebIntegrationTest {

    private static final String PASSWORD = "it-secret-pw";

    @Test
    void when_loginWithUnknownUser_then_returns401InvalidCredentials() throws Exception {
        mockMvc.perform(post("/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"noexiste_xyz\",\"password\":\"errada123\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.detail").value("Credenciais inválidas"));
    }

    @Test
    void when_loginWithWrongPassword_then_returns401WithoutLeakingExistence() throws Exception {
        UUID seededTenantId = UUID.randomUUID();
        String username = "it-user-" + UUID.randomUUID();
        seedTenantAndUser(seededTenantId, username, PASSWORD);

        mockMvc.perform(post("/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"senha_errada_123\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.detail").value("Credenciais inválidas"));
    }

    @Test
    void when_loginWithValidCredentials_then_returns200() throws Exception {
        UUID tenantId = UUID.randomUUID();
        String username = "it-user-" + UUID.randomUUID();
        seedTenantAndUser(tenantId, username, PASSWORD);

        mockMvc.perform(post("/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty());
    }
}
