package br.com.systec.hive.integrationtest;

import br.com.systec.hive.integrationtest.support.WebIntegrationTest;
import br.com.systec.hive.publicapi.impl.service.PublicClientTokenIssuer;
import com.auth0.jwt.interfaces.DecodedJWT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Bugfix grants: catálogo fechado no ponto de escrita; somente PUBLIC_CHAT_COMPLETE é aceito. */
class PublicApiGrantsValidationIT extends WebIntegrationTest {

    private static final String BOGUS = "GRANT_INEXISTENTE";
    private static final String VALID = "PUBLIC_CHAT_COMPLETE";

    @Autowired
    private PublicClientTokenIssuer tokenIssuer;

    private String adminToken;
    private String label;

    @BeforeEach
    void setUp() {
        UUID tenant = UUID.randomUUID();
        String username = "grants-val-" + UUID.randomUUID();
        seedTenantAndUser(tenant, username, "it-pw");
        adminToken = jwtTestHelper.generateToken(username, tenant);
        label = "grants-" + UUID.randomUUID();
    }

    @Test
    void when_bogusGrantOnCreate_then_400AndClearMessage() throws Exception {
        MvcResult result = mockMvc.perform(post("/v1/public-api/clients")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"label\":\"" + label + "\",\"grants\":[\"" + BOGUS + "\"]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("permission desconhecida")))
                .andReturn();
        System.out.println("GRANTS_FIX create_invalid status=400 detail=" + result.getResponse().getContentAsString());
    }

    @Test
    void when_validGrantOnCreate_then_201AndJwtClaimIsCorrect() throws Exception {
        MvcResult created = mockMvc.perform(post("/v1/public-api/clients")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"label\":\"" + label + "\",\"grants\":[\"" + VALID + "\"]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.grants[0]").value(VALID))
                .andReturn();
        String body = created.getResponse().getContentAsString();
        String clientId = extract(body, "clientId");
        String secret = extract(body, "client_secret");
        DecodedJWT jwt = tokenIssuer.decodeAndVerify(token(clientId, secret));
        assertThat(jwt.getClaim("permissions").asList(String.class)).containsExactly(VALID);
        System.out.println("GRANTS_FIX create_valid status=201 permissions=" + jwt.getClaim("permissions").asList(String.class));
    }

    @Test
    void when_bogusGrantOnUpdate_then_400AndExistingGrantIsPreserved() throws Exception {
        MvcResult created = mockMvc.perform(post("/v1/public-api/clients")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"label\":\"" + label + "\",\"grants\":[\"" + VALID + "\"]}"))
                .andExpect(status().isCreated())
                .andReturn();
        String clientId = extract(created.getResponse().getContentAsString(), "clientId");

        mockMvc.perform(put("/v1/public-api/clients/" + clientId + "/grants")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"grants\":[\"" + BOGUS + "\"]}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/v1/public-api/clients/" + clientId + "/keys")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
        MvcResult after = mockMvc.perform(get("/v1/public-api/clients/" + clientId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.grants[0]").value(VALID))
                .andReturn();
        System.out.println("GRANTS_FIX update_invalid status=400 preserved=" + after.getResponse().getContentAsString());
    }

    private String token(String clientId, String secret) throws Exception {
        MvcResult result = mockMvc.perform(post("/v1/public/auth/token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"grant_type\":\"client_credentials\",\"client_id\":\"" + clientId
                                + "\",\"client_secret\":\"" + secret + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return extract(result.getResponse().getContentAsString(), "access_token");
    }

    private static String extract(String body, String key) {
        String needle = "\"" + key + "\":\"";
        int start = body.indexOf(needle);
        assertThat(start).isPositive();
        int from = start + needle.length();
        return body.substring(from, body.indexOf('"', from));
    }
}
