package br.com.systec.hive.security.impl.filter;

import br.com.systec.hive.commons.impl.security.TenantContext;
import br.com.systec.hive.identity.api.domain.User;
import br.com.systec.hive.identity.api.service.UserService;
import br.com.systec.hive.security.api.domain.RefreshTokenData;
import br.com.systec.hive.security.api.service.AuthenticationService;
import br.com.systec.hive.security.api.service.SecurityTokenService;
import br.com.systec.hive.security.api.service.TokenCacheService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SecurityFilterTest {

    private static final String TOKEN = "a".repeat(64);

    @Mock
    private SecurityTokenService securityTokenService;
    @Mock
    private AuthenticationService authenticationService;
    @Mock
    private TokenCacheService tokenCacheService;
    @Mock
    private UserService userService;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private SecurityFilter filter() {
        return new SecurityFilter(securityTokenService, authenticationService, tokenCacheService, userService);
    }

    private static MockHttpServletRequest request(String path) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        request.setRemoteAddr("127.0.0.1");
        return request;
    }

    private static RefreshTokenData refreshData(UUID tenantId, UUID userId, String remoteIp, Instant expiresAt) {
        return new RefreshTokenData(userId, tenantId, "jti", Instant.now(), expiresAt, remoteIp, "it-agent");
    }

    private static User user(UUID userId, UUID tenantId) {
        User user = new User();
        user.setId(userId);
        user.setTenantId(tenantId);
        return user;
    }

    private static FilterChain capturing(AtomicReference<UUID> tenantSeen, AtomicReference<Authentication> authSeen) throws Exception {
        FilterChain chain = mock(FilterChain.class);
        doAnswer(invocation -> {
            try {
                tenantSeen.set(TenantContext.getCurrentTenant());
            } catch (RuntimeException noTenant) {
                // esperado quando o cookie não autenticou
            }
            authSeen.set(SecurityContextHolder.getContext().getAuthentication());
            return null;
        }).when(chain).doFilter(any(), any());
        return chain;
    }

    @Test
    void when_docsPathWithValidRefreshCookie_then_authenticateAndRunChainInTenantContext() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        User user = user(userId, tenantId);
        MockHttpServletRequest req = request("/v3/api-docs/public");
        req.setCookies(new Cookie("refreshToken", TOKEN));
        when(tokenCacheService.findRefreshTokenDataByToken(TOKEN))
                .thenReturn(Optional.of(refreshData(tenantId, userId, "127.0.0.1", Instant.now().plusSeconds(3600))));
        when(userService.findById(userId)).thenReturn(user);
        AtomicReference<UUID> tenantSeen = new AtomicReference<>();
        AtomicReference<Authentication> authSeen = new AtomicReference<>();
        FilterChain chain = capturing(tenantSeen, authSeen);

        filter().doFilter(req, new MockHttpServletResponse(), chain);

        assertThat(tenantSeen.get()).isEqualTo(tenantId);
        assertThat(authSeen.get()).isNotNull();
        assertThat(authSeen.get().getPrincipal()).isSameAs(user);
        verify(chain).doFilter(any(), any());
    }

    @Test
    void when_scalarPathWithContextPathAndValidCookie_then_authenticate() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        MockHttpServletRequest req = request("/hive/scalar");
        req.setContextPath("/hive");
        req.setCookies(new Cookie("refreshToken", TOKEN));
        when(tokenCacheService.findRefreshTokenDataByToken(TOKEN))
                .thenReturn(Optional.of(refreshData(tenantId, userId, "127.0.0.1", Instant.now().plusSeconds(3600))));
        when(userService.findById(userId)).thenReturn(user(userId, tenantId));
        AtomicReference<UUID> tenantSeen = new AtomicReference<>();
        AtomicReference<Authentication> authSeen = new AtomicReference<>();

        filter().doFilter(req, new MockHttpServletResponse(), capturing(tenantSeen, authSeen));

        assertThat(tenantSeen.get()).isEqualTo(tenantId);
        assertThat(authSeen.get()).isNotNull();
    }

    @Test
    void when_docsPathWithoutCookie_then_chainRunsUnauthenticated() throws Exception {
        MockHttpServletRequest req = request("/v3/api-docs");
        req.setParameter("group", "public");
        AtomicReference<UUID> tenantSeen = new AtomicReference<>();
        AtomicReference<Authentication> authSeen = new AtomicReference<>();
        FilterChain chain = capturing(tenantSeen, authSeen);

        filter().doFilter(req, new MockHttpServletResponse(), chain);

        verify(chain).doFilter(any(), any());
        assertThat(authSeen.get()).isNull();
        assertThat(tenantSeen.get()).isNull();
        verifyNoInteractions(tokenCacheService);
    }

    @Test
    void when_docsPathWithUnknownCookie_then_chainRunsUnauthenticated() throws Exception {
        MockHttpServletRequest req = request("/v3/api-docs/public");
        req.setCookies(new Cookie("refreshToken", TOKEN));
        when(tokenCacheService.findRefreshTokenDataByToken(TOKEN)).thenReturn(Optional.empty());
        AtomicReference<Authentication> authSeen = new AtomicReference<>();

        filter().doFilter(req, new MockHttpServletResponse(), capturing(new AtomicReference<>(), authSeen));

        assertThat(authSeen.get()).isNull();
    }

    @Test
    void when_docsPathWithCookieFromDifferentIp_then_chainRunsUnauthenticated() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        MockHttpServletRequest req = request("/v3/api-docs/public");
        req.setCookies(new Cookie("refreshToken", TOKEN));
        when(tokenCacheService.findRefreshTokenDataByToken(TOKEN))
                .thenReturn(Optional.of(refreshData(tenantId, userId, "10.0.0.9", Instant.now().plusSeconds(3600))));
        AtomicReference<Authentication> authSeen = new AtomicReference<>();

        filter().doFilter(req, new MockHttpServletResponse(), capturing(new AtomicReference<>(), authSeen));

        assertThat(authSeen.get()).isNull();
        verify(userService, never()).findById(any());
    }

    @Test
    void when_docsPathWithExpiredCookie_then_chainRunsUnauthenticated() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        MockHttpServletRequest req = request("/v3/api-docs/public");
        req.setCookies(new Cookie("refreshToken", TOKEN));
        when(tokenCacheService.findRefreshTokenDataByToken(TOKEN))
                .thenReturn(Optional.of(refreshData(tenantId, userId, "127.0.0.1", Instant.now().minusSeconds(1))));
        AtomicReference<Authentication> authSeen = new AtomicReference<>();

        filter().doFilter(req, new MockHttpServletResponse(), capturing(new AtomicReference<>(), authSeen));

        assertThat(authSeen.get()).isNull();
        verify(userService, never()).findById(any());
    }

    @Test
    void when_nonDocsPathWithRefreshCookie_then_cookieIsIgnored() throws Exception {
        MockHttpServletRequest req = request("/v1/chat");
        req.setCookies(new Cookie("refreshToken", TOKEN));
        AtomicReference<Authentication> authSeen = new AtomicReference<>();

        filter().doFilter(req, new MockHttpServletResponse(), capturing(new AtomicReference<>(), authSeen));

        verifyNoInteractions(tokenCacheService);
        assertThat(authSeen.get()).isNull();
    }

    @Test
    void when_bearerHeaderOnDocsPath_then_existingFlowIsPreserved() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        User user = user(userId, tenantId);
        MockHttpServletRequest req = request("/v3/api-docs/public");
        req.addHeader("Authorization", "Bearer jwt-value");
        when(securityTokenService.getSubject("jwt-value")).thenReturn("it-user");
        when(securityTokenService.getTenantId("jwt-value")).thenReturn(tenantId);
        when(authenticationService.loadUserByUsername("it-user")).thenReturn(user);
        AtomicReference<UUID> tenantSeen = new AtomicReference<>();
        AtomicReference<Authentication> authSeen = new AtomicReference<>();

        filter().doFilter(req, new MockHttpServletResponse(), capturing(tenantSeen, authSeen));

        verify(securityTokenService).validateTokenRevoke("jwt-value");
        assertThat(tenantSeen.get()).isEqualTo(tenantId);
        assertThat(authSeen.get().getPrincipal()).isSameAs(user);
        verifyNoInteractions(tokenCacheService);
    }
}
