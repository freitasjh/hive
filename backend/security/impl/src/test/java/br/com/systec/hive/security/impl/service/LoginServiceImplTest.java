package br.com.systec.hive.security.impl.service;

import br.com.systec.hive.identity.api.domain.MfaSecretData;
import br.com.systec.hive.identity.api.domain.User;
import br.com.systec.hive.identity.api.service.MfaRepositoryPort;
import br.com.systec.hive.security.api.domain.Login;
import br.com.systec.hive.security.api.domain.LoginAuthenticate;
import br.com.systec.hive.security.api.domain.LoginResult;
import br.com.systec.hive.security.api.domain.MfaChallenge;
import br.com.systec.hive.security.api.exceptions.InvalidCredentialsException;
import br.com.systec.hive.security.api.exceptions.SecurityException;
import br.com.systec.hive.security.api.service.MfaChallengeCachePort;
import br.com.systec.hive.security.api.service.SecurityTokenService;
import br.com.systec.hive.security.api.service.TokenCacheService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoginServiceImplTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID TENANT_ID = UUID.randomUUID();

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private SecurityTokenService securityTokenService;

    @Mock
    private TokenCacheService tokenCacheService;

    @Mock
    private MfaRepositoryPort mfaRepository;

    @Mock
    private MfaChallengeCachePort challengeCache;

    @Mock
    private HttpServletRequest request;

    @InjectMocks
    private LoginServiceImpl service;

    @Test
    void when_loginWithValidCredentialsAndMfaDisabled_thenReturnSuccessWithTokenAndNoChallenge() {
        Login login = new Login("user", "pass");
        Authentication authentication = authenticatedUser();
        LoginAuthenticate expected = new LoginAuthenticate(null, "token", "Bearer", "", "refreshToken");
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class))).thenReturn(authentication);
        when(mfaRepository.findByUserIdAndTenantId(USER_ID, TENANT_ID))
                .thenReturn(Optional.of(new MfaSecretData(USER_ID, TENANT_ID, "cipher", false, null)));
        when(securityTokenService.generateTokenAndRefreshToken(authentication)).thenReturn(expected);

        LoginResult result = service.login(login);

        assertNotNull(result);
        assertEquals(expected, assertInstanceOf(LoginResult.Success.class, result).authenticate());
        verify(authenticationManager).authenticate(any(UsernamePasswordAuthenticationToken.class));
        verify(securityTokenService).generateTokenAndRefreshToken(authentication);
        verify(challengeCache, never()).save(any(), any());
    }

    @Test
    void when_loginWithoutMfaRecord_thenReturnSuccessWithToken() {
        Login login = new Login("user", "pass");
        Authentication authentication = authenticatedUser();
        LoginAuthenticate expected = new LoginAuthenticate(USER_ID, "token", "Bearer", "", "refreshToken");
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class))).thenReturn(authentication);
        when(mfaRepository.findByUserIdAndTenantId(USER_ID, TENANT_ID)).thenReturn(Optional.empty());
        when(securityTokenService.generateTokenAndRefreshToken(authentication)).thenReturn(expected);

        LoginResult result = service.login(login);

        assertEquals(expected, assertInstanceOf(LoginResult.Success.class, result).authenticate());
        verify(challengeCache, never()).save(any(), any());
    }

    @Test
    void when_loginWithMfaEnabled_thenReturnsChallengeWithoutIssuingAnyToken() {
        Login login = new Login("user", "pass");
        Authentication authentication = authenticatedUser();
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class))).thenReturn(authentication);
        when(mfaRepository.findByUserIdAndTenantId(USER_ID, TENANT_ID))
                .thenReturn(Optional.of(new MfaSecretData(USER_ID, TENANT_ID, "cipher", true, Instant.now())));
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        when(request.getRemoteAddr()).thenReturn("192.168.1.20");
        when(request.getHeader("User-Agent")).thenReturn("test-agent");

        LoginResult result = service.login(login);

        LoginResult.MfaChallengeRequired challengeResult =
                assertInstanceOf(LoginResult.MfaChallengeRequired.class, result);
        assertEquals(MfaChallengeCachePort.TTL_SECONDS, challengeResult.expiresIn());
        assertNotNull(challengeResult.challengeId());

        ArgumentCaptor<MfaChallenge> saved = ArgumentCaptor.forClass(MfaChallenge.class);
        ArgumentCaptor<String> nonce = ArgumentCaptor.forClass(String.class);
        verify(challengeCache).save(saved.capture(), nonce.capture());
        MfaChallenge challenge = saved.getValue();
        assertEquals(challengeResult.challengeId(), challenge.id());
        assertEquals(USER_ID, challenge.userId());
        assertEquals(TENANT_ID, challenge.tenantId());
        assertEquals("192.168.1.20", challenge.remoteIp());
        assertEquals("test-agent", challenge.userAgent());
        assertEquals(0, challenge.attempts());
        assertEquals(nonce.getValue(), challenge.consumeToken());
        assertThat(challenge.consumeToken()).isNotBlank();
        assertThat(challenge.jti()).isNull();
        verify(securityTokenService, never()).generateTokenAndRefreshToken(any());
    }

    @Test
    void when_loginWithMfaEnabledAndOldBearer_thenChallengeCarriesJtiForBlacklist() {
        Login login = new Login("user", "pass");
        Authentication authentication = authenticatedUser();
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class))).thenReturn(authentication);
        when(mfaRepository.findByUserIdAndTenantId(USER_ID, TENANT_ID))
                .thenReturn(Optional.of(new MfaSecretData(USER_ID, TENANT_ID, "cipher", true, Instant.now())));
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        when(request.getHeader("User-Agent")).thenReturn(null);
        when(request.getHeader("Authorization")).thenReturn("Bearer old-access-token");
        when(securityTokenService.extractJti("old-access-token")).thenReturn("old-jti");

        service.login(login);

        ArgumentCaptor<MfaChallenge> saved = ArgumentCaptor.forClass(MfaChallenge.class);
        verify(challengeCache).save(saved.capture(), any());
        assertEquals("old-jti", saved.getValue().jti());
        verify(securityTokenService, never()).generateTokenAndRefreshToken(any());
    }

    @Test
    void when_loginWithInvalidCredentials_thenThrow401() {
        Login login = new Login("user", "wrong-pass");

        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(new BadCredentialsException("Bad credentials"));

        assertThatThrownBy(() -> service.login(login))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Credenciais inválidas")
                .satisfies(ex -> assertThat(((InvalidCredentialsException) ex).getHttpStatus()).isEqualTo(HttpStatus.UNAUTHORIZED));
        verify(securityTokenService, never()).generateTokenAndRefreshToken(any());
        verify(challengeCache, never()).save(any(), any());
    }

    @Test
    void when_loginWithUnknownUser_thenThrow401() {
        Login login = new Login("noexiste_xyz", "errada123");

        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(new InternalAuthenticationServiceException("User not found"));

        assertThatThrownBy(() -> service.login(login))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Credenciais inválidas");
        verify(securityTokenService, never()).generateTokenAndRefreshToken(any());
    }

    @Test
    void when_refreshToken_then_returnNewToken() {
        LoginAuthenticate expected = new LoginAuthenticate(null, "new-token", "Bearer", "", "new-refreshToken");
        when(securityTokenService.generateNewAccessTokenByRefreshToken()).thenReturn(expected);

        LoginAuthenticate result = service.refreshToken();

        assertNotNull(result);
        assertEquals(expected, result);
        verify(securityTokenService).generateNewAccessTokenByRefreshToken();
    }

    @Test
    void when_logout_with_tenantAndToken_then_revokeRefreshToken() {
        UUID tenantId = UUID.randomUUID();

        service.logout(tenantId, "refresh-abc");

        verify(tokenCacheService).revokeRefreshToken(tenantId, "refresh-abc");
    }

    @Test
    void when_logout_with_nullTenant_then_throwSecurityException_withoutRevoke() {
        assertThatThrownBy(() -> service.logout(null, "refresh-abc"))
                .isInstanceOf(SecurityException.class);

        verify(tokenCacheService, never()).revokeRefreshToken(any(), any());
    }

    @Test
    void when_logout_with_blankToken_then_throwSecurityException_withoutRevoke() {
        assertThatThrownBy(() -> service.logout(UUID.randomUUID(), "   "))
                .isInstanceOf(SecurityException.class);

        verify(tokenCacheService, never()).revokeRefreshToken(any(), any());
    }

    private static Authentication authenticatedUser() {
        User user = new User();
        user.setId(USER_ID);
        user.setTenantId(TENANT_ID);
        user.setUsername("user");
        Authentication authentication = org.mockito.Mockito.mock(Authentication.class);
        when(authentication.getPrincipal()).thenReturn(user);
        return authentication;
    }
}
