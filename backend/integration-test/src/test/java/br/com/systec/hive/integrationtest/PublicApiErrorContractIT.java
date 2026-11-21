package br.com.systec.hive.integrationtest;

import br.com.systec.hive.integrationtest.support.WebIntegrationTest;
import br.com.systec.hive.publicapi.api.domain.ProvisioningPayload;
import br.com.systec.hive.publicapi.impl.provisioning.PublicApiProvisioningRunner;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ADR-001/CT04: congela o envelope de erro `{message,type,param,code}` e os códigos do
 * contrato público antes e depois dos DTOs tipados. Nenhum handler novo é introduzido.
 */
class PublicApiErrorContractIT extends WebIntegrationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private PublicApiProvisioningRunner provisioningRunner;

    @Test
    void when_tokenRequestHasInvalidGrant_then_errorEnvelopeIsPreserved() throws Exception {
        MvcResult result = mockMvc.perform(post("/v1/public/auth/token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"grant_type\":\"password\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.type").value("invalid_request_error"))
                .andExpect(jsonPath("$.error.code").value("invalid_grant"))
                .andExpect(jsonPath("$.error.message").value("grant_type must be client_credentials"))
                .andReturn();

        JsonNode error = objectMapper.readTree(result.getResponse().getContentAsString()).path("error");
        System.out.println("ERROR_ENVELOPE_BASELINE " + error);
        assertThat(error.has("message")).isTrue();
        assertThat(error.has("type")).isTrue();
        assertThat(error.has("param")).isTrue();
        assertThat(error.has("code")).isTrue();
    }

    @Test
    void when_chatHasSystemRole_then_validationLayerRejectsWithStableEnvelope() throws Exception {
        String token = provisionedToken();
        String body = "{\"prompt_code\":\"p1\",\"messages\":[{\"role\":\"system\",\"content\":\"hi\"}]}";

        MvcResult result = mockMvc.perform(post("/v1/public/v1/chat/completions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                // @Pattern(user|assistant) rejeita na validação antes do service; código real é invalid_request.
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.type").value("invalid_request_error"))
                .andExpect(jsonPath("$.error.code").value("invalid_request"))
                .andReturn();

        JsonNode error = objectMapper.readTree(result.getResponse().getContentAsString()).path("error");
        System.out.println("CHAT_ERROR_ENVELOPE_BASELINE " + error);
        assertThat(error.has("message")).isTrue();
        assertThat(error.has("type")).isTrue();
        assertThat(error.has("param")).isTrue();
        assertThat(error.has("code")).isTrue();
    }

    @Test
    void when_chatHasStreamTrue_then_streamingNotSupportedEnvelopeIsPreserved() throws Exception {
        String token = provisionedToken();
        String body = "{\"prompt_code\":\"p1\",\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}],\"stream\":true}";

        mockMvc.perform(post("/v1/public/v1/chat/completions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.type").value("invalid_request_error"))
                .andExpect(jsonPath("$.error.code").value("streaming_not_supported"));
    }

    private String provisionedToken() throws Exception {
        ProvisioningPayload payload = provisioningRunner.provision(
                "--label=it-contract-" + java.util.UUID.randomUUID(), "--prompt-codes=p1");
        String body = "{\"grant_type\":\"client_credentials\",\"client_id\":\"" + payload.getClientId()
                + "\",\"client_secret\":\"" + payload.getClientSecret() + "\"}";
        MvcResult result = mockMvc.perform(post("/v1/public/auth/token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("access_token").asText();
    }
}
