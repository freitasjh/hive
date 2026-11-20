package br.com.systec.hive.security.web.internal.v1.controller;

import br.com.systec.hive.identity.api.domain.User;
import br.com.systec.hive.security.api.domain.LoginAuthenticate;
import br.com.systec.hive.security.api.domain.MfaErrorCode;
import br.com.systec.hive.security.api.domain.MfaSetupResult;
import br.com.systec.hive.security.api.exceptions.MfaException;
import br.com.systec.hive.security.api.service.MfaServicePort;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TDD do controller MFA (Fase 2). verify/recovery são chamados SEM Bearer
 * ({@code omitAuthorization} no cliente) — o SecurityFilter valida Bearer antes do controller.
 */
@ExtendWith(MockitoExtension.class)
class MfaControllerV1Test {

    private static final String TENANT_HEADER = "X-Tenant-ID";
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID CHALLENGE_ID = UUID.randomUUID();
    private static final String SECRET = "JBSWY3DPEHPK3PXP";
    private static final String QR = "data:image/png;base64,QUJD";
    private static final String CODE = "123456";
    private static final String ACCESS_TOKEN = "new-access-token";
    private static final String REFRESH_TOKEN = "new-refresh-token";

    @Mock
    MfaServicePort service;

    @InjectMocks
    MfaControllerV1 controller;

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        User user = new User();
        user.setId(USER_ID);
        user.setTenantId(TENANT_ID);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, List.of()));
        // Controller-local @ExceptionHandler resolve MfaException; no global advice needed.
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // ---------- setup ----------

    @Test
    void when_setupWithAuthenticatedUser_then200WithSecretQrAndEightRecoveryCodesOnce() throws Exception {
        List<String> codes = List.of("C1", "C2", "C3", "C4", "C5", "C6", "C7", "C8");
        when(service.setup(USER_ID, TENANT_ID)).thenReturn(new MfaSetupResult(SECRET, QR, codes));

        mockMvc.perform(post("/v1/auth/mfa/setup"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.secret").value(SECRET))
                .andExpect(jsonPath("$.qrDataUri").value(QR))
                .andExpect(jsonPath("$.recoveryCodes.length()").value(8));

        verify(service).setup(USER_ID, TENANT_ID);
    }

    @Test
    void when_setupWithoutAuthenticatedUser_then401MfaRequiredAndNoSecret() throws Exception {
        SecurityContextHolder.clearContext();

        mockMvc.perform(post("/v1/auth/mfa/setup"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("MFA_REQUIRED"))
                .andExpect(jsonPath("$.message", not(containsString(SECRET))));

        verify(service, never()).setup(any(), any());
    }

    // ---------- enable ----------

    @Test
    void when_enableWithValidCode_then204() throws Exception {
        mockMvc.perform(post("/v1/auth/mfa/enable")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + CODE + "\"}"))
                .andExpect(status().isNoContent());

        verify(service).enable(USER_ID, TENANT_ID, CODE);
    }

    // ---------- disable ----------

    @Test
    void when_disableWithPassword_then204() throws Exception {
        mockMvc.perform(post("/v1/auth/mfa/disable")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"raw-password\"}"))
                .andExpect(status().isNoContent());

        verify(service).disable(USER_ID, TENANT_ID, "raw-password");
    }

    // ---------- verify (sem Bearer) ----------

    @Test
    void when_verifyWithValidCode_then200WithLoginContractAndRefreshCookie() throws Exception {
        when(service.verify(CHALLENGE_ID, TENANT_ID, CODE)).thenReturn(issuedToken());

        mockMvc.perform(post("/v1/auth/mfa/verify")
                        .header(TENANT_HEADER, TENANT_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"challengeId\":\"" + CHALLENGE_ID + "\",\"code\":\"" + CODE + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(USER_ID.toString()))
                .andExpect(jsonPath("$.accessToken").value(ACCESS_TOKEN))
                .andExpect(jsonPath("$.type").value("Bearer"))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("refreshToken=" + REFRESH_TOKEN)));

        verify(service).verify(CHALLENGE_ID, TENANT_ID, CODE);
    }

    @Test
    void when_verifyWithMalformedTenantHeader_then200WithoutBadRequest() throws Exception {
        when(service.verify(CHALLENGE_ID, null, CODE)).thenReturn(issuedToken());

        mockMvc.perform(post("/v1/auth/mfa/verify")
                        .header(TENANT_HEADER, "not-a-uuid")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"challengeId\":\"" + CHALLENGE_ID + "\",\"code\":\"" + CODE + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value(ACCESS_TOKEN));

        verify(service).verify(CHALLENGE_ID, null, CODE);
    }

    @Test
    void when_verifyTooManyAttempts_then429SemanticError() throws Exception {
        doThrow(new MfaException(MfaErrorCode.MFA_TOO_MANY_ATTEMPTS))
                .when(service).verify(CHALLENGE_ID, TENANT_ID, CODE);

        mockMvc.perform(post("/v1/auth/mfa/verify")
                        .header(TENANT_HEADER, TENANT_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"challengeId\":\"" + CHALLENGE_ID + "\",\"code\":\"" + CODE + "\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("MFA_TOO_MANY_ATTEMPTS"));
    }

    @Test
    void when_verifyExpiredChallenge_then401SemanticError() throws Exception {
        doThrow(new MfaException(MfaErrorCode.MFA_CHALLENGE_EXPIRED))
                .when(service).verify(CHALLENGE_ID, TENANT_ID, CODE);

        mockMvc.perform(post("/v1/auth/mfa/verify")
                        .header(TENANT_HEADER, TENANT_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"challengeId\":\"" + CHALLENGE_ID + "\",\"code\":\"" + CODE + "\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("MFA_CHALLENGE_EXPIRED"));
    }

    @Test
    void when_verifyInvalidCode_then401SemanticError() throws Exception {
        doThrow(new MfaException(MfaErrorCode.MFA_INVALID_CODE))
                .when(service).verify(CHALLENGE_ID, TENANT_ID, CODE);

        mockMvc.perform(post("/v1/auth/mfa/verify")
                        .header(TENANT_HEADER, TENANT_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"challengeId\":\"" + CHALLENGE_ID + "\",\"code\":\"" + CODE + "\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("MFA_INVALID_CODE"));
    }

    @Test
    void when_verifyErrorResponse_thenNoSecretOrCodeLeaked() throws Exception {
        doThrow(new MfaException(MfaErrorCode.MFA_INVALID_CODE))
                .when(service).verify(CHALLENGE_ID, TENANT_ID, CODE);

        mockMvc.perform(post("/v1/auth/mfa/verify")
                        .header(TENANT_HEADER, TENANT_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"challengeId\":\"" + CHALLENGE_ID + "\",\"code\":\"" + CODE + "\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message", not(containsString(SECRET))))
                .andExpect(jsonPath("$.message", not(containsString(CODE))));
    }

    // ---------- recovery (sem Bearer) ----------

    @Test
    void when_recoveryWithValidCode_then200WithTokenAndRefreshCookie() throws Exception {
        String recoveryCode = "A1B2C3D4E5F60718";
        when(service.recovery(CHALLENGE_ID, TENANT_ID, recoveryCode)).thenReturn(issuedToken());

        mockMvc.perform(post("/v1/auth/mfa/recovery")
                        .header(TENANT_HEADER, TENANT_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"challengeId\":\"" + CHALLENGE_ID + "\",\"recoveryCode\":\"" + recoveryCode + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(USER_ID.toString()))
                .andExpect(jsonPath("$.accessToken").value(ACCESS_TOKEN))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("refreshToken=" + REFRESH_TOKEN)));

        verify(service).recovery(CHALLENGE_ID, TENANT_ID, recoveryCode);
    }

    @Test
    void when_recoveryWithInvalidCode_then401SemanticError() throws Exception {
        String recoveryCode = "A1B2C3D4E5F60718";
        doThrow(new MfaException(MfaErrorCode.MFA_INVALID_RECOVERY))
                .when(service).recovery(CHALLENGE_ID, TENANT_ID, recoveryCode);

        mockMvc.perform(post("/v1/auth/mfa/recovery")
                        .header(TENANT_HEADER, TENANT_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"challengeId\":\"" + CHALLENGE_ID + "\",\"recoveryCode\":\"" + recoveryCode + "\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("MFA_INVALID_RECOVERY"));
    }

    private static LoginAuthenticate issuedToken() {
        return new LoginAuthenticate(USER_ID, ACCESS_TOKEN, "Bearer", "", REFRESH_TOKEN);
    }
}
