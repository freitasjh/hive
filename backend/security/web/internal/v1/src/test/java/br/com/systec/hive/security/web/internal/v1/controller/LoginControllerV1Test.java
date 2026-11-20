package br.com.systec.hive.security.web.internal.v1.controller;

import br.com.systec.hive.security.api.service.LoginService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
