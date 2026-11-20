package br.com.systec.hive.security.web.internal.v1.controller;

import br.com.systec.hive.security.api.domain.Login;
import br.com.systec.hive.security.api.domain.LoginAuthenticate;
import br.com.systec.hive.security.api.domain.LoginResult;
import br.com.systec.hive.security.api.service.LoginService;
import br.com.systec.hive.security.web.internal.v1.dto.LoginDTO;
import br.com.systec.hive.security.web.internal.v1.dto.LoginResponseDTO;
import br.com.systec.hive.security.web.internal.v1.mapper.LoginMapperDTO;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.UUID;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class LoginControllerV1Test {

    private static final String TENANT_HEADER = "X-Tenant-ID";

    @Mock
    LoginService service;
    @Mock
    LoginMapperDTO mapper;

    @InjectMocks
    LoginControllerV1 controller;

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void when_loginWithoutMfa_then200WithUnchangedContractAndRefreshCookie() throws Exception {
        UUID userId = UUID.randomUUID();
        LoginDTO payload = new LoginDTO("user", "pass");
        Login domain = new Login("user", "pass");
        LoginAuthenticate authenticate = new LoginAuthenticate(userId, "access-token", "Bearer", "", "refresh-value");
        when(mapper.toDomain(payload)).thenReturn(domain);
        when(service.login(domain)).thenReturn(new LoginResult.Success(authenticate));
        when(mapper.toLoginResponse(authenticate)).thenReturn(new LoginResponseDTO(userId, "access-token", "Bearer"));

        mockMvc.perform(post("/v1/auth/login")
                        .contentType("application/json")
                        .content("{\"username\":\"user\",\"password\":\"pass\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(userId.toString()))
                .andExpect(jsonPath("$.accessToken").value("access-token"))
                .andExpect(jsonPath("$.type").value("Bearer"))
                .andExpect(jsonPath("$.mfaRequired").doesNotExist())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, allOf(
                        containsString("refreshToken=refresh-value"), containsString("HttpOnly"))));
    }

    @Test
    void when_loginWithMfa_then200WithChallengeAndNoTokenOrCookie() throws Exception {
        UUID challengeId = UUID.randomUUID();
        LoginDTO payload = new LoginDTO("user", "pass");
        Login domain = new Login("user", "pass");
        when(mapper.toDomain(payload)).thenReturn(domain);
        when(service.login(domain)).thenReturn(new LoginResult.MfaChallengeRequired(challengeId, 120));

        mockMvc.perform(post("/v1/auth/login")
                        .contentType("application/json")
                        .content("{\"username\":\"user\",\"password\":\"pass\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mfaRequired").value(true))
                .andExpect(jsonPath("$.challengeId").value(challengeId.toString()))
                .andExpect(jsonPath("$.expiresIn").value(120))
                .andExpect(jsonPath("$.accessToken").doesNotExist())
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
    }

    @Test
    void when_logout_with_refreshTokenCookie_then_revokes_fromHeaderTenant_expires_cookie_and_204() throws Exception {
        UUID tenantId = UUID.randomUUID();

        mockMvc.perform(post("/v1/auth/logout")
                        .header(TENANT_HEADER, tenantId.toString())
                        .cookie(new Cookie("refreshToken", "abc")))
                .andExpect(status().isNoContent())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, allOf(
                        containsString("refreshToken="),
                        containsString("Max-Age=0"),
                        containsString("HttpOnly"))));

        verify(service).logout(tenantId, "abc");
    }

    @Test
    void when_logout_without_refreshTokenCookie_then_204_idempotent_and_cookie_expired() throws Exception {
        UUID tenantId = UUID.randomUUID();

        mockMvc.perform(post("/v1/auth/logout")
                        .header(TENANT_HEADER, tenantId.toString()))
                .andExpect(status().isNoContent())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, allOf(
                        containsString("refreshToken="),
                        containsString("Max-Age=0"),
                        containsString("HttpOnly"))));

        verify(service, never()).logout(any(), any());
    }

    @Test
    void when_logout_with_blankRefreshTokenCookie_then_204_without_revoke() throws Exception {
        UUID tenantId = UUID.randomUUID();

        mockMvc.perform(post("/v1/auth/logout")
                        .header(TENANT_HEADER, tenantId.toString())
                        .cookie(new Cookie("refreshToken", "   ")))
                .andExpect(status().isNoContent())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")));

        verify(service, never()).logout(any(), any());
    }

    @Test
    void when_logout_without_header_and_without_cookie_then_204_idempotent_and_cookie_expired() throws Exception {
        mockMvc.perform(post("/v1/auth/logout"))
                .andExpect(status().isNoContent())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, allOf(
                        containsString("refreshToken="),
                        containsString("Max-Age=0"),
                        containsString("HttpOnly"))));

        verify(service, never()).logout(any(), any());
    }

    @Test
    void when_logout_with_cookie_but_without_header_then_204_without_revoke() throws Exception {
        mockMvc.perform(post("/v1/auth/logout")
                        .cookie(new Cookie("refreshToken", "abc")))
                .andExpect(status().isNoContent())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")));

        verify(service, never()).logout(any(), any());
    }

    @Test
    void when_logout_with_malformedTenantHeader_then_204_without_revoke() throws Exception {
        mockMvc.perform(post("/v1/auth/logout")
                        .header(TENANT_HEADER, "not-a-uuid")
                        .cookie(new Cookie("refreshToken", "abc")))
                .andExpect(status().isNoContent())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, allOf(
                        containsString("refreshToken="),
                        containsString("Max-Age=0"),
                        containsString("HttpOnly"))));

        verify(service, never()).logout(any(), any());
    }
}
