package br.com.systec.hive.integrationtest;

import br.com.systec.hive.integrationtest.support.WebIntegrationTest;
import br.com.systec.hive.publicapi.impl.provisioning.PublicApiProvisioningRunner;
import br.com.systec.hive.publicapi.api.domain.ProvisioningPayload;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class PublicApiProvisioningIT extends WebIntegrationTest {

    @Autowired
    private PublicApiProvisioningRunner runner;

    @Test
    void when_runner_provisions_then_printed_secret_authenticates() throws Exception {
        ProvisioningPayload payload = runner.provision("--label=it-prov", "--prompt-codes=p1");
        assertThat(payload.getClientSecret()).isNotBlank();

        String body = "{\"grant_type\":\"client_credentials\",\"client_id\":\"" + payload.getClientId()
                + "\",\"client_secret\":\"" + payload.getClientSecret() + "\"}";
        mockMvc.perform(post("/v1/public/auth/token")
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isOk());
    }
}
