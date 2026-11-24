package br.com.systec.hive.integrationtest;

import br.com.systec.hive.integrationtest.support.WebIntegrationTest;
import br.com.systec.hive.publicapi.api.domain.ProvisioningPayload;
import br.com.systec.hive.publicapi.impl.provisioning.PublicApiProvisioningRunner;
import br.com.systec.hive.publicapi.impl.service.PublicClientTokenIssuer;
import com.auth0.jwt.interfaces.DecodedJWT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** ADR-002/KEY2-02..04: migration V3, emissão one-shot, janela de 10 min e revogação. */
@Import(ChatCallerParityIT.CapturingFactoryConfig.class)
class PublicApiKeyLifecycleIT extends WebIntegrationTest {

    private static final String PASSWORD = "it-secret-pw";

    @Autowired
    private PublicApiProvisioningRunner provisioningRunner;

    @Autowired
    private PublicClientTokenIssuer tokenIssuer;

    private UUID tenant;
    private String adminToken;
    private String code;
    private ProvisioningPayload client;

    @BeforeEach
    void setUp() throws Exception {
        tenant = UUID.randomUUID();
        code = "keylife-" + UUID.randomUUID().toString().substring(0, 8);
        String username = "keylife-human-" + UUID.randomUUID();
        seedTenantAndUser(tenant, username, PASSWORD);
        adminToken = jwtTestHelper.generateToken(username, tenant);
        mockMvc.perform(put("/v1/provider-credentials/OPENCODE_GO")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"apiKey\":\"sk-test-fake-key\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(put("/v1/tenant/llm-config")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"provider\":\"OPENCODE_GO\",\"model\":\"mimo-v2.5\"}"))
                .andExpect(status().isOk());
        String body = mockMvc.perform(post("/v1/prompt-templates")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"KeyLife\",\"code\":\"" + code
                                + "\",\"template\":\"Voce e um assistente.\",\"inferenceParams\":{\"temperature\":0.7,\"maxTokens\":256}}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID id = UUID.fromString(body.split("\"id\":\"")[1].split("\"")[0]);
        jdbcTemplate.update("UPDATE prompt_template SET status = 'ACTIVE' WHERE id = ?", id);
        client = provisioningRunner.provision("--label=keylife-" + UUID.randomUUID(),
                "--tenant-id=" + tenant, "--prompt-codes=" + code);
    }

    @Test
    void when_migrationV3Applied_then_tableAndBackfillColumnsExist() {
        Integer applied = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history_publicapi WHERE version = '3' AND success = true",
                Integer.class);
        assertThat(applied).isEqualTo(1);
        Integer cols = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns WHERE table_name = 'public_api_client_key'"
                        + " AND column_name IN ('key_id','key_hash','previous_key_hash','previous_key_expires_at',"
                        + "'revoked_at','expires_at','last_used_at','tenant_id','client_ref')", Integer.class);
        assertThat(cols).isEqualTo(9);
    }

    @Test
    void when_keyIssued_then_plaintextOnlyOnceAndChatAuthenticates() throws Exception {
        String apiKey = issue();
        mockMvc.perform(chat(apiKey)).andExpect(status().isOk());

        mockMvc.perform(get("/v1/public-api/clients/" + client.getClientId() + "/keys")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].keyId").isNotEmpty())
                .andExpect(jsonPath("$[0].api_key").doesNotExist())
                .andExpect(jsonPath("$[0].key_hash").doesNotExist())
                .andExpect(jsonPath("$[0].previous_key_hash").doesNotExist());
        assertThat(ChatCallerParityIT.TRACES.get(ChatCallerParityIT.TRACES.size() - 1).clientId())
                .isEqualTo(client.getClientId());
    }

    @Test
    void when_rotated_then_oldKeyWorksInsideWindowAndFailsOutside() throws Exception {
        MvcResult issued = issueResult();
        String oldKey = extract(issued, "api_key");
        String keyId = extract(issued, "keyId");
        MvcResult rotated = mockMvc.perform(post("/v1/public-api/clients/" + client.getClientId()
                        + "/keys/" + keyId + "/rotate")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.api_key").isNotEmpty())
                .andExpect(jsonPath("$.previous_key_expires_at").isNotEmpty())
                .andReturn();
        String newKey = extract(rotated, "api_key");
        assertThat(newKey).isNotEqualTo(oldKey);

        mockMvc.perform(chat(oldKey)).andExpect(status().isOk());
        mockMvc.perform(chat(newKey)).andExpect(status().isOk());

        jdbcTemplate.update("UPDATE public_api_client_key SET previous_key_expires_at = ? WHERE key_id = ?",
                LocalDateTime.now().minusMinutes(1), extract(rotated, "keyId"));
        mockMvc.perform(chat(oldKey)).andExpect(status().isUnauthorized());
    }

    @Test
    void when_revoked_then_nextRequestIsUnauthorized() throws Exception {
        MvcResult issued = issueResult();
        String apiKey = extract(issued, "api_key");
        String keyId = extract(issued, "keyId");
        mockMvc.perform(post("/v1/public-api/clients/" + client.getClientId() + "/keys/" + keyId + "/revoke")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());
        mockMvc.perform(chat(apiKey)).andExpect(status().isUnauthorized());
        Boolean inRedis = redisTemplate.opsForSet().isMember("hive:public:revoked-keys", keyId);
        assertThat(inRedis).isTrue();
    }

    @Test
    void when_crossTenantOrNoPermission_then_404Or403() throws Exception {
        issue();
        UUID other = UUID.randomUUID();
        String otherUser = "other-" + UUID.randomUUID();
        seedTenantAndUser(other, otherUser, PASSWORD);
        String otherToken = jwtTestHelper.generateToken(otherUser, other, java.util.List.of("CHAT_SEND"));

        String crossTenant = jwtTestHelper.generateToken(otherUser, other);
        mockMvc.perform(get("/v1/public-api/clients/" + client.getClientId() + "/keys")
                        .header("Authorization", "Bearer " + crossTenant))
                .andExpect(status().isNotFound());

        UUID third = UUID.randomUUID();
        String noPermUser = "np-" + UUID.randomUUID();
        seedTenantAndUser(third, noPermUser, PASSWORD);
        String noPerm = jwtTestHelper.generateToken(noPermUser, third, java.util.List.of("CHAT_SEND"));
        mockMvc.perform(get("/v1/public-api/clients/" + client.getClientId() + "/keys")
                        .header("Authorization", "Bearer " + noPerm))
                .andExpect(status().isForbidden());
    }

    @Test
    void when_keyAndJwtForSameClient_then_claimsAndPlimAreIdentical() throws Exception {
        issue();
        DecodedJWT jwt = tokenIssuer.decodeAndVerify(token(client));
        @SuppressWarnings("unchecked")
        Map<String, Object> jwtPlim = jwt.getClaim("plim").asMap();
        assertThat(jwtPlim).containsEntry("m", 200).containsEntry("c", 32000)
                .containsEntry("t", 256000).containsEntry("r", 100);

        var policy = tokenIssuer.policyFor(clientRepository().findByClientId(client.getClientId()).orElseThrow());
        assertThat(policy.permissions()).isEqualTo(jwt.getClaim("permissions").asList(String.class));
        assertThat(policy.pcode()).isEqualTo(jwt.getClaim("pcode").asList(String.class));
        assertThat(policy.plim()).containsEntry("m", 200).containsEntry("c", 32000)
                .containsEntry("t", 256000).containsEntry("r", 100);
    }

    private MvcResult issueResult() throws Exception {
        return mockMvc.perform(post("/v1/public-api/clients/" + client.getClientId() + "/keys")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.api_key").isNotEmpty())
                .andReturn();
    }

    private String issue() throws Exception {
        return extract(issueResult(), "api_key");
    }

    private org.springframework.test.web.servlet.RequestBuilder chat(String apiKey) {
        return post("/v1/public/v1/chat/completions")
                .header("Authorization", "Bearer " + apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"prompt_code\":\"" + code + "\",\"messages\":[{\"role\":\"user\",\"content\":\"oi\"}]}");
    }

    private static String extract(MvcResult result, String key) throws Exception {
        String body = result.getResponse().getContentAsString();
        String needle = "\"" + key + "\":\"";
        int start = body.indexOf(needle);
        assertThat(start).isPositive();
        int from = start + needle.length();
        return body.substring(from, body.indexOf('"', from));
    }

    private String token(ProvisioningPayload payload) throws Exception {
        MvcResult result = mockMvc.perform(post("/v1/public/auth/token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"grant_type\":\"client_credentials\",\"client_id\":\"" + payload.getClientId()
                                + "\",\"client_secret\":\"" + payload.getClientSecret() + "\"}"))
                .andExpect(status().isOk()).andReturn();
        return extract(result, "access_token");
    }

    private br.com.systec.hive.publicapi.impl.repository.PublicApiClientRepository clientRepository() {
        return context.getBean(br.com.systec.hive.publicapi.impl.repository.PublicApiClientRepository.class);
    }

    @Autowired
    private org.springframework.context.ApplicationContext context;

    @Autowired
    private org.springframework.data.redis.core.RedisTemplate<String, Object> redisTemplate;
}
