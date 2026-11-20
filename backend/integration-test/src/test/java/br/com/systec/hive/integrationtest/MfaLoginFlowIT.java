package br.com.systec.hive.integrationtest;

import br.com.systec.hive.identity.api.domain.MfaRecoveryCodeData;
import br.com.systec.hive.identity.api.domain.MfaSecretData;
import br.com.systec.hive.identity.api.service.MfaRepositoryPort;
import br.com.systec.hive.integrationtest.support.WebIntegrationTest;
import br.com.systec.hive.security.api.service.MfaCryptoPort;
import com.jayway.jsonpath.JsonPath;
import dev.samstevens.totp.code.DefaultCodeGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * IT decisivo da Fase 3 MFA: Postgres real (user_mfa/recovery) + Redis real (challenge CAS e
 * bloqueio), provando que nenhum token nasce antes do código e que o desafio não é reutilizável.
 */
class MfaLoginFlowIT extends WebIntegrationTest {

    private static final String PASSWORD = "it-secret-pw";
    private static final String SECRET = "JBSWY3DPEHPK3PXP";
    private static final String RECOVERY_CODE = "A1B2C3D4E5F60718";

    @Autowired
    private MfaRepositoryPort mfaRepository;

    @Autowired
    private MfaCryptoPort cryptoPort;

    @Test
    void when_userWithoutMfa_then_loginKeepsOriginalTokenContract() throws Exception {
        UUID tenantId = UUID.randomUUID();
        String username = "it-mfa-off-" + UUID.randomUUID();
        seedTenantAndUser(tenantId, username, PASSWORD);

        mockMvc.perform(loginRequest(username, PASSWORD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").isNotEmpty())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.type").value("Bearer"))
                .andExpect(jsonPath("$.mfaRequired").doesNotExist())
                .andExpect(header().string(HttpHeaders.SET_COOKIE,
                        org.hamcrest.Matchers.containsString("refreshToken=")));
    }

    @Test
    void when_userWithMfa_then_challengeThenValidCodeIssuesTokenAndChallengeIsConsumed() throws Exception {
        UUID tenantId = UUID.randomUUID();
        String username = "it-mfa-on-" + UUID.randomUUID();
        seedEnabledMfa(tenantId, username);

        MvcResult challengeResult = mockMvc.perform(loginRequest(username, PASSWORD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mfaRequired").value(true))
                .andExpect(jsonPath("$.expiresIn").value(120))
                .andExpect(jsonPath("$.challengeId").isNotEmpty())
                .andExpect(jsonPath("$.accessToken").doesNotExist())
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE))
                .andReturn();
        String challengeId = JsonPath.read(challengeResult.getResponse().getContentAsString(), "$.challengeId");

        String code = totp(SECRET);
        mockMvc.perform(verifyRequest(tenantId, challengeId, code))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").isNotEmpty())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.type").value("Bearer"))
                .andExpect(header().string(HttpHeaders.SET_COOKIE,
                        org.hamcrest.Matchers.containsString("refreshToken=")));

        // Replay do mesmo challenge: nonce já removido do Redis → 401, sem segundo token.
        mockMvc.perform(verifyRequest(tenantId, challengeId, code))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("MFA_CHALLENGE_EXPIRED"));
    }

    @Test
    void when_fiveWrongCodes_then429AndNewChallengeIsAlsoBlockedFor15Minutes() throws Exception {
        UUID tenantId = UUID.randomUUID();
        String username = "it-mfa-block-" + UUID.randomUUID();
        seedEnabledMfa(tenantId, username);

        MvcResult challengeResult = mockMvc.perform(loginRequest(username, PASSWORD))
                .andExpect(status().isOk())
                .andReturn();
        String challengeId = JsonPath.read(challengeResult.getResponse().getContentAsString(), "$.challengeId");
        String wrongCode = wrongCode(totp(SECRET));

        for (int attempt = 1; attempt <= 5; attempt++) {
            mockMvc.perform(verifyRequest(tenantId, challengeId, wrongCode))
                    .andExpect(attempt < 5 ? status().isUnauthorized() : status().isTooManyRequests())
                    .andExpect(attempt < 5
                            ? jsonPath("$.code").value("MFA_INVALID_CODE")
                            : jsonPath("$.code").value("MFA_TOO_MANY_ATTEMPTS"));
        }

        // Mesmo com challenge novo e código correto, usuário segue bloqueado por 15 min.
        MvcResult fresh = mockMvc.perform(loginRequest(username, PASSWORD))
                .andExpect(status().isOk())
                .andReturn();
        String freshId = JsonPath.read(fresh.getResponse().getContentAsString(), "$.challengeId");
        mockMvc.perform(verifyRequest(tenantId, freshId, totp(SECRET)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("MFA_TOO_MANY_ATTEMPTS"));
    }

    @Test
    void when_recoveryCodeUsed_thenTokenIssuedAndCodeCannotBeReused() throws Exception {
        UUID tenantId = UUID.randomUUID();
        String username = "it-mfa-recovery-" + UUID.randomUUID();
        UUID userId = seedEnabledMfa(tenantId, username);
        mfaRepository.saveRecoveryCode(new MfaRecoveryCodeData(
                UUID.randomUUID(), userId, cryptoPort.hashRecoveryCode(RECOVERY_CODE), null, Instant.now()));

        MvcResult first = mockMvc.perform(loginRequest(username, PASSWORD))
                .andExpect(status().isOk())
                .andReturn();
        String firstId = JsonPath.read(first.getResponse().getContentAsString(), "$.challengeId");
        mockMvc.perform(post("/v1/auth/mfa/recovery")
                        .header("X-Tenant-ID", tenantId.toString())
                        .header("User-Agent", "it-agent")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"challengeId\":\"" + firstId + "\",\"recoveryCode\":\"" + RECOVERY_CODE + "\"}")
                        .with(request -> {
                            request.setRemoteAddr("127.0.0.1");
                            return request;
                        }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(header().string(HttpHeaders.SET_COOKIE,
                        org.hamcrest.Matchers.containsString("refreshToken=")));

        MvcResult second = mockMvc.perform(loginRequest(username, PASSWORD))
                .andExpect(status().isOk())
                .andReturn();
        String secondId = JsonPath.read(second.getResponse().getContentAsString(), "$.challengeId");
        mockMvc.perform(post("/v1/auth/mfa/recovery")
                        .header("X-Tenant-ID", tenantId.toString())
                        .header("User-Agent", "it-agent")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"challengeId\":\"" + secondId + "\",\"recoveryCode\":\"" + RECOVERY_CODE + "\"}")
                        .with(request -> {
                            request.setRemoteAddr("127.0.0.1");
                            return request;
                        }))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("MFA_INVALID_RECOVERY"));
    }

    private UUID seedEnabledMfa(UUID tenantId, String username) throws Exception {
        seedTenantAndUser(tenantId, username, PASSWORD);
        UUID userId = jdbcTemplate.queryForObject("SELECT id FROM users WHERE username = ?", UUID.class, username);
        assertThat(userId).isNotNull();
        mfaRepository.save(new MfaSecretData(userId, tenantId, cryptoPort.encrypt(SECRET), true, Instant.now()));
        return userId;
    }

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder loginRequest(
            String username, String password) {
        return post("/v1/auth/login")
                .header("User-Agent", "it-agent")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}")
                .with(request -> {
                    request.setRemoteAddr("127.0.0.1");
                    return request;
                });
    }

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder verifyRequest(
            UUID tenantId, String challengeId, String code) {
        return post("/v1/auth/mfa/verify")
                .header("X-Tenant-ID", tenantId.toString())
                .header("User-Agent", "it-agent")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"challengeId\":\"" + challengeId + "\",\"code\":\"" + code + "\"}")
                .with(request -> {
                    request.setRemoteAddr("127.0.0.1");
                    return request;
                });
    }

    /** Counter TOTP (RFC 6238) — a lib recebe o contador, não epoch segundos. */
    private static String totp(String secret) throws Exception {
        long counter = System.currentTimeMillis() / 1000L / 30L;
        return new DefaultCodeGenerator().generate(secret, counter);
    }

    private static String wrongCode(String valid) {
        char first = valid.charAt(0);
        char different = first == '0' ? '1' : '0';
        return different + valid.substring(1);
    }
}
