package br.com.systec.hive.integrationtest;

import br.com.systec.hive.integrationtest.support.WebIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ADR-001/key: sem bean PublicApiKeyAuthenticator (estado real até a ADR-002), qualquer `sk-`
 * devolve 401 fail-closed. Formato desconhecido segue o caminho JWT e também falha.
 */
class PublicApiKeyNoBeanIT extends WebIntegrationTest {

    @Test
    void when_noAuthenticatorBean_then_skKeyIsRejected() throws Exception {
        mockMvc.perform(post("/v1/public/v1/chat/completions")
                        .header("Authorization", "Bearer sk-any-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"prompt_code\":\"p1\",\"messages\":[{\"role\":\"user\",\"content\":\"oi\"}]}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void when_unknownTokenFormat_then_jwtPathRejectsWithoutFallback() throws Exception {
        mockMvc.perform(post("/v1/public/v1/chat/completions")
                        .header("Authorization", "Bearer not-a-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"prompt_code\":\"p1\",\"messages\":[{\"role\":\"user\",\"content\":\"oi\"}]}"))
                .andExpect(status().isUnauthorized());
    }
}
