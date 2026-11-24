package br.com.systec.hive.integrationtest;

import br.com.systec.hive.integrationtest.support.WebIntegrationTest;
import br.com.systec.hive.publicapi.api.domain.ProvisioningPayload;
import br.com.systec.hive.publicapi.impl.provisioning.PublicApiProvisioningRunner;
import br.com.systec.hive.publicapi.impl.service.PublicClientTokenIssuer;
import com.auth0.jwt.interfaces.DecodedJWT;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** R1 (débito 3): capturar o limite efetivo de token antigo após mudança da cota do tenant. */
class PublicApiPlimStalenessIT extends WebIntegrationTest {

    @Autowired
    private PublicApiProvisioningRunner provisioningRunner;

    @Autowired
    private PublicClientTokenIssuer tokenIssuer;

    @Test
    void when_tenantLimitChanges_then_oldTokenKeepsXAndNewTokenUsesY() throws Exception {
        UUID tenant = UUID.randomUUID();
        ProvisioningPayload payload = provisioningRunner.provision("--label=stale-" + UUID.randomUUID(),
                "--tenant-id=" + tenant, "--prompt-codes=p1");
        jdbcTemplate.update("INSERT INTO public_api_tenant_quota (tenant_id, chat_requests_per_minute, create_at, update_at)"
                + " VALUES (?, 250, NOW(), NOW())", tenant);

        String oldToken = token(payload);
        int oldLimit = effectiveLimit(oldToken);
        System.out.println("N3_MEASURE oldToken_plim_r=" + oldLimit);

        jdbcTemplate.update("UPDATE public_api_tenant_quota SET chat_requests_per_minute = 1 WHERE tenant_id = ?", tenant);
        int oldAfterChange = effectiveLimit(oldToken);
        String newToken = token(payload);
        int newLimit = effectiveLimit(newToken);
        System.out.println("N3_MEASURE oldToken_afterChange_plim_r=" + oldAfterChange
                + " newToken_plim_r=" + newLimit);

        assertThat(oldLimit).isEqualTo(250);
        assertThat(oldAfterChange).isEqualTo(250);
        assertThat(newLimit).isEqualTo(1);
    }

    private int effectiveLimit(String jwt) {
        DecodedJWT decoded = tokenIssuer.decodeAndVerify(jwt);
        @SuppressWarnings("unchecked")
        Map<String, Object> plim = decoded.getClaim("plim").asMap();
        return ((Number) plim.get("r")).intValue();
    }

    private String token(ProvisioningPayload payload) throws Exception {
        String body = "{\"grant_type\":\"client_credentials\",\"client_id\":\"" + payload.getClientId()
                + "\",\"client_secret\":\"" + payload.getClientSecret() + "\"}";
        MvcResult result = mockMvc.perform(post("/v1/public/auth/token")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn();
        String json = result.getResponse().getContentAsString();
        int start = json.indexOf("\"access_token\":\"") + 16;
        return json.substring(start, json.indexOf('"', start));
    }
}
