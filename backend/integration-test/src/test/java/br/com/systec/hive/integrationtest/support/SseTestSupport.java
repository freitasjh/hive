package br.com.systec.hive.integrationtest.support;

import br.com.systec.hive.publicapi.api.domain.ProvisioningPayload;
import br.com.systec.hive.publicapi.impl.provisioning.PublicApiProvisioningRunner;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Shared HTTP setup for the ADR-002 SSE integration tests. */
public final class SseTestSupport {

    private SseTestSupport() {
    }

    /** Seeds the full tenant chain (credential, LLM config, active prompt) then provisions a client. */
    public static String provisionedToken(MockMvc mockMvc, ObjectMapper mapper,
                                          PublicApiProvisioningRunner runner, JwtTestHelper jwt,
                                          JdbcTemplate jdbc, br.com.systec.hive.publicapi.impl.service.PublicClientTokenIssuer issuer,
                                          String label, UUID tenantId, String promptCode)
            throws Exception {
        String username = "sse-" + label + "-" + UUID.randomUUID().toString().substring(0, 8);
        UUID userId = UUID.randomUUID();
        String hash = new BCryptPasswordEncoder().encode("it-secret-pw");
        jdbc.update("INSERT INTO tenant (id, name, description, active, owner_id) VALUES (?, ?, ?, ?, ?)",
                tenantId, "SSE IT Tenant", "seeded by SSE IT", true, userId);
        jdbc.update("INSERT INTO users (id, username, email, first_name, last_name, password, is_enabled, tenant_id)"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                userId, username, username + "@example.com", "IT", "User", hash, true, tenantId);
        String humanToken = jwt.generateToken(username, tenantId);

        mockMvc.perform(put("/v1/provider-credentials/OPENCODE_GO")
                        .header("Authorization", "Bearer " + humanToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"apiKey\":\"sk-test-fake-key\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(put("/v1/tenant/llm-config")
                        .header("Authorization", "Bearer " + humanToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"provider\":\"OPENCODE_GO\",\"model\":\"mimo-v2.5\"}"))
                .andExpect(status().isOk());
        String created = mockMvc.perform(post("/v1/prompt-templates")
                        .header("Authorization", "Bearer " + humanToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"SSE public\",\"code\":\"" + promptCode
                                + "\",\"template\":\"Voce e um assistente.\","
                                + "\"inferenceParams\":{\"temperature\":0.7,\"maxTokens\":256}}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        UUID promptId = UUID.fromString(mapper.readTree(created).path("id").asText());
        jdbc.update("UPDATE prompt_template SET status = 'ACTIVE' WHERE id = ?", promptId);

        ProvisioningPayload payload = runner.provision("--label=" + label + "-" + UUID.randomUUID(),
                "--tenant-id=" + tenantId, "--prompt-codes=" + promptCode);
        // Emite via serviço: o endpoint /auth/token tem limite GLOBAL de 30/min e as SSE ITs
        // não devem consumir a cota compartilhada com os demais testes do pacote.
        return issuer.issueToken(payload.getClientId(), payload.getClientSecret(), null);
    }

    public record AssistantSetup(String token, UUID promptTemplateId) {
    }

    public static AssistantSetup assistantSetup(MockMvc mockMvc, JwtTestHelper jwt,
                                                 org.springframework.jdbc.core.JdbcTemplate jdbc, UUID tenantId, String username,
                                                 ObjectMapper mapper) throws Exception {
        String uname = username;
        UUID uid = UUID.randomUUID();
        String hash = new BCryptPasswordEncoder().encode("it-secret-pw");
        jdbc.update("INSERT INTO tenant (id, name, description, active, owner_id) VALUES (?, ?, ?, ?, ?)",
                tenantId, "SSE IT Tenant", "seeded by SSE IT", true, uid);
        jdbc.update("INSERT INTO users (id, username, email, first_name, last_name, password, is_enabled, tenant_id)"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                uid, uname, uname + "@example.com", "IT", "User", hash, true, tenantId);
        String token = jwt.generateToken(username, tenantId);
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
                        .content("{\"name\":\"SSE prompt\",\"code\":\"sse-" + UUID.randomUUID()
                                + "\",\"template\":\"Voce e um assistente.\","
                                + "\"inferenceParams\":{\"temperature\":0.7,\"maxTokens\":256}}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        JsonNode created = mapper.readTree(body);
        UUID promptId = UUID.fromString(created.path("id").asText());
        jdbc.update("UPDATE prompt_template SET status = 'ACTIVE' WHERE id = ?", promptId);
        return new AssistantSetup(token, promptId);
    }

}
