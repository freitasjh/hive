package br.com.systec.hive.integrationtest;

import br.com.systec.hive.integrationtest.support.WebIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class KnowledgeIsolationIT extends WebIntegrationTest {

    private static final String PASSWORD = "it-secret-pw";

    private String tokenFor(UUID tenantId, String username) {
        seedTenantAndUser(tenantId, username, PASSWORD);
        return jwtTestHelper.generateToken(username, tenantId);
    }

    @Test
    void when_crossTenant_with_kbCrud_then_notFound() throws Exception {
        UUID tenantA = UUID.randomUUID();
        UUID tenantB = UUID.randomUUID();
        String tokenA = tokenFor(tenantA, "iso-a");
        String tokenB = tokenFor(tenantB, "iso-b");

        String response = mockMvc.perform(post("/v1/knowledge-bases")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Secret\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String kbId = response.split("\"id\":\"")[1].split("\"")[0];

        mockMvc.perform(get("/v1/knowledge-bases/" + kbId)
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/v1/knowledge-bases/" + kbId + "/documents")
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/v1/knowledge-bases/" + kbId + "/search")
                        .header("Authorization", "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"hello\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void when_crossTenant_with_link_then_notFound() throws Exception {
        UUID tenantA = UUID.randomUUID();
        UUID tenantB = UUID.randomUUID();
        String tokenA = tokenFor(tenantA, "iso-c");
        String tokenB = tokenFor(tenantB, "iso-d");

        String response = mockMvc.perform(post("/v1/knowledge-bases")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Secret\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String kbId = response.split("\"id\":\"")[1].split("\"")[0];

        UUID agentB = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO agent (id, tenant_id, name, active, is_default) VALUES (?, ?, ?, ?, ?)",
                agentB, tenantB, "Agent B", true, false);

        mockMvc.perform(put("/v1/agents/" + agentB + "/knowledge-bases")
                        .header("Authorization", "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kbIds\":[\"" + kbId + "\"]}"))
                .andExpect(status().isNotFound());
    }
}
