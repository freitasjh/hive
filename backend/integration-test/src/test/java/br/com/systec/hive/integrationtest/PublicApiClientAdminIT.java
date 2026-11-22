package br.com.systec.hive.integrationtest;

import br.com.systec.hive.integrationtest.support.WebIntegrationTest;
import br.com.systec.hive.publicapi.impl.service.PublicClientTokenIssuer;
import com.auth0.jwt.interfaces.DecodedJWT;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ADR-003: superfície administrativa em /v1/public-api/clients, na chain humana com AGENT_MANAGE.
 * Nenhum secret ou hash em leitura; plaintext apenas em create/rotate.
 */
class PublicApiClientAdminIT extends WebIntegrationTest {

    private static final String BASE = "/v1/public-api/clients";
    private static final String PASSWORD = "it-secret-pw";

    @Autowired
    private PublicClientTokenIssuer tokenIssuer;

    @Test
    void when_anonymousRequestsAdminList_then_returnsUnauthorized() throws Exception {
        mockMvc.perform(get(BASE)).andExpect(status().isUnauthorized());
    }

    @Test
    void when_humanLacksAgentManage_then_returnsForbidden() throws Exception {
        UUID tenantId = UUID.randomUUID();
        String username = "no-perm-" + UUID.randomUUID();
        seedTenantAndUser(tenantId, username, PASSWORD);
        String token = jwtTestHelper.generateToken(username, tenantId, List.of("CHAT_SEND"));

        mockMvc.perform(get(BASE).header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void when_create_then_returns201WithPlaintextOnlyOnce() throws Exception {
        Stub s = stub();
        MvcResult result = mockMvc.perform(post(BASE)
                        .header("Authorization", "Bearer " + s.token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("it-client-one", "p1", "FOLLOW_ACTIVE", null)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.clientId").isNotEmpty())
                .andExpect(jsonPath("$.client_secret").isNotEmpty())
                .andExpect(jsonPath("$.grants[0]").value("PUBLIC_CHAT_COMPLETE"))
                .andExpect(jsonPath("$.prompts[0].promptCode").value("p1"))
                .andExpect(jsonPath("$.secretHash").doesNotExist())
                .andExpect(jsonPath("$.rateLimitPerMinute").doesNotExist())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).doesNotContain("secret_hash").doesNotContain("previous_secret_hash");
    }

    @Test
    void when_listAndGet_then_noSecretAndNoHash() throws Exception {
        Stub s = stub();
        create(s, "it-client-two", "p1", "FOLLOW_ACTIVE", null);

        mockMvc.perform(get(BASE).param("page", "0").param("size", "20")
                        .header("Authorization", "Bearer " + s.token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.content[0].clientId").isNotEmpty())
                .andExpect(jsonPath("$.content[0].client_secret").doesNotExist())
                .andExpect(jsonPath("$.content[0].secretHash").doesNotExist())
                .andExpect(jsonPath("$.content[0].previousSecretHash").doesNotExist())
                .andExpect(jsonPath("$.content[0].previous_secret_hash").doesNotExist())
                .andExpect(jsonPath("$.content[0].rate_limit_per_minute").doesNotExist());

        String clientId = createdClientId(s, "it-client-two");
        mockMvc.perform(get(BASE + "/" + clientId).header("Authorization", "Bearer " + s.token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clientId").value(clientId))
                .andExpect(jsonPath("$.client_secret").doesNotExist())
                .andExpect(jsonPath("$.secretHash").doesNotExist());
    }

    @Test
    void when_labelDuplicatedInTenant_then_409() throws Exception {
        Stub s = stub();
        create(s, "it-client-three", "p1", "FOLLOW_ACTIVE", null);

        mockMvc.perform(post(BASE)
                        .header("Authorization", "Bearer " + s.token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("it-client-three", "p1", "FOLLOW_ACTIVE", null)))
                .andExpect(status().isConflict());
    }

    @Test
    void when_payloadCarriesServerOwnedFields_then_400() throws Exception {
        Stub s = stub();
        String body = "{\"label\":\"x\",\"client_id\":\"invented\",\"tenantId\":\"" + s.tenantId + "\"}";

        mockMvc.perform(post(BASE)
                        .header("Authorization", "Bearer " + s.token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void when_crossTenantAccess_then_404() throws Exception {
        Stub s = stub();
        create(s, "it-client-four", "p1", "FOLLOW_ACTIVE", null);
        String clientId = createdClientId(s, "it-client-four");

        UUID otherTenant = UUID.randomUUID();
        String otherUser = "other-" + UUID.randomUUID();
        seedTenantAndUser(otherTenant, otherUser, PASSWORD);
        String otherToken = jwtTestHelper.generateToken(otherUser, otherTenant);

        mockMvc.perform(get(BASE + "/" + clientId).header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void when_rotate_then_plaintextReturnedAndPreviousSecretWindowHonored() throws Exception {
        Stub s = stub();
        MvcResult created = create(s, "it-client-five", "p1", "FOLLOW_ACTIVE", null);
        String oldSecret = json(created, "$.client_secret");
        String clientId = json(created, "$.clientId");

        MvcResult rotated = mockMvc.perform(post(BASE + "/" + clientId + "/rotate")
                        .header("Authorization", "Bearer " + s.token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.client_secret").isNotEmpty())
                .andExpect(jsonPath("$.previous_secret_expires_at").isNotEmpty())
                .andReturn();
        String newSecret = json(rotated, "$.client_secret");
        assertThat(newSecret).isNotEqualTo(oldSecret);

        assertThat(token(s.token, clientId, newSecret)).isNotBlank();
        // Secret anterior continua válido dentro da janela de 10 min (issuer, ADR-001).
        assertThat(token(s.token, clientId, oldSecret)).isNotBlank();

        jdbcTemplate.update("UPDATE public_api_client SET previous_secret_expires_at = ? WHERE client_id = ?",
                LocalDateTime.now().minusMinutes(1), clientId);
        tokenEndpoint(clientId, oldSecret).andExpect(status().isUnauthorized());
    }

    @Test
    void when_revoke_then_immediateUnauthorized() throws Exception {
        Stub s = stub();
        MvcResult created = create(s, "it-client-six", "p1", "FOLLOW_ACTIVE", null);
        String clientId = json(created, "$.clientId");
        String secret = json(created, "$.client_secret");
        String jwt = token(s.token, clientId, secret);

        mockMvc.perform(post(BASE + "/" + clientId + "/revoke")
                        .header("Authorization", "Bearer " + s.token))
                .andExpect(status().isNoContent());

        tokenEndpoint(clientId, secret).andExpect(status().isUnauthorized());
        // JWT já emitido é rejeitado na chain M2M (issuer consulta Redis + DB); a chain humana usa outra chave.
        mockMvc.perform(post("/v1/public/v1/chat/completions")
                        .header("Authorization", "Bearer " + jwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"prompt_code\":\"p1\",\"messages\":[{\"role\":\"user\",\"content\":\"oi\"}]}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void when_updateGrantsAndPrompts_then_jwtClaimsReflectThem() throws Exception {
        Stub s = stub();
        MvcResult created = create(s, "it-client-seven", "p1", "FOLLOW_ACTIVE", null);
        String clientId = json(created, "$.clientId");
        String secret = json(created, "$.client_secret");

        mockMvc.perform(put(BASE + "/" + clientId + "/grants")
                        .header("Authorization", "Bearer " + s.token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"grants\":[\"PUBLIC_CHAT_COMPLETE\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.grants[0]").value("PUBLIC_CHAT_COMPLETE"));

        mockMvc.perform(put(BASE + "/" + clientId + "/prompts")
                        .header("Authorization", "Bearer " + s.token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"prompts\":[{\"promptCode\":\"p2\",\"pinMode\":\"PINNED\",\"pinnedVersion\":3}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.prompts[0].promptCode").value("p2"))
                .andExpect(jsonPath("$.prompts[0].pinMode").value("PINNED"))
                .andExpect(jsonPath("$.prompts[0].pinnedVersion").value(3));

        DecodedJWT decoded = tokenIssuer.decodeAndVerify(token(s.token, clientId, secret));
        assertThat(decoded.getClaim("permissions").asList(String.class)).containsExactly("PUBLIC_CHAT_COMPLETE");
        assertThat(decoded.getClaim("pcode").asList(String.class)).containsExactly("p2");
        assertThat(decoded.getClaim("plock").asMap()).containsEntry("p2", 3);
    }

    private Stub stub() {
        UUID tenantId = UUID.randomUUID();
        String username = "admin-" + UUID.randomUUID();
        seedTenantAndUser(tenantId, username, PASSWORD);
        return new Stub(tenantId, jwtTestHelper.generateToken(username, tenantId));
    }

    private record Stub(UUID tenantId, String token) {
    }

    private MvcResult create(Stub s, String label, String promptCode, String pinMode, Integer version) throws Exception {
        return mockMvc.perform(post(BASE)
                        .header("Authorization", "Bearer " + s.token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(label, promptCode, pinMode, version)))
                .andExpect(status().isCreated())
                .andReturn();
    }

    private static String createBody(String label, String promptCode, String pinMode, Integer version) {
        String pin = version == null ? "null" : String.valueOf(version);
        return "{\"label\":\"" + label + "\",\"grants\":[\"PUBLIC_CHAT_COMPLETE\"],"
                + "\"prompts\":[{\"promptCode\":\"" + promptCode + "\",\"pinMode\":\"" + pinMode
                + "\",\"pinnedVersion\":" + pin + "}]}";
    }

    private String createdClientId(Stub s, String label) throws Exception {
        MvcResult result = mockMvc.perform(get(BASE).header("Authorization", "Bearer " + s.token))
                .andExpect(status().isOk())
                .andReturn();
        String body = result.getResponse().getContentAsString();
        int idx = body.indexOf("\"" + label + "\"");
        assertThat(idx).isPositive();
        int start = body.lastIndexOf("\"clientId\":\"", idx);
        return body.substring(start + 12, body.indexOf('"', start + 12));
    }

    private org.springframework.test.web.servlet.ResultActions tokenEndpoint(String clientId, String secret) throws Exception {
        String body = "{\"grant_type\":\"client_credentials\",\"client_id\":\"" + clientId
                + "\",\"client_secret\":\"" + secret + "\"}";
        return mockMvc.perform(post("/v1/public/auth/token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private String token(String adminToken, String clientId, String secret) throws Exception {
        MvcResult result = tokenEndpoint(clientId, secret)
                .andExpect(status().isOk())
                .andReturn();
        return json(result, "$.access_token");
    }

    private static String json(MvcResult result, String path) throws Exception {
        String body = result.getResponse().getContentAsString();
        String key = path.substring(path.lastIndexOf('.') + 1);
        int idx = body.indexOf("\"" + key + "\":\"");
        assertThat(idx).isPositive();
        int start = idx + key.length() + 4;
        return body.substring(start, body.indexOf('"', start));
    }
}
