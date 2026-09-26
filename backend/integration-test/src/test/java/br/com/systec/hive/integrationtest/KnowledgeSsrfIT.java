package br.com.systec.hive.integrationtest;

import br.com.systec.hive.integrationtest.support.WebIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class KnowledgeSsrfIT extends WebIntegrationTest {

    private static final String PASSWORD = "it-secret-pw";

    private String tokenFor(UUID tenantId, String username) {
        seedTenantAndUser(tenantId, username, PASSWORD);
        return jwtTestHelper.generateToken(username, tenantId);
    }

    @Test
    void when_urlDocs_with_internalTargets_then_badRequest() throws Exception {
        UUID tenantId = UUID.randomUUID();
        String token = tokenFor(tenantId, "ssrf");

        String response = mockMvc.perform(post("/v1/knowledge-bases")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Web\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String kbId = response.split("\"id\":\"")[1].split("\"")[0];

        List<String> evil = List.of(
                "http://127.0.0.1/docs",
                "http://localhost/docs",
                "http://169.254.169.254/latest/meta-data",
                "http://192.168.1.10/internal",
                "http://10.0.0.5/internal",
                "http://100.64.0.1/internal",
                "http://203.0.113.10/docs",
                "http://example.com:8080/docs",
                "ftp://example.com/docs");
        for (String url : evil) {
            mockMvc.perform(post("/v1/knowledge-bases/" + kbId + "/documents:url")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"url\":\"" + url + "\"}"))
                    .andExpect(status().isBadRequest());
        }
    }
}
