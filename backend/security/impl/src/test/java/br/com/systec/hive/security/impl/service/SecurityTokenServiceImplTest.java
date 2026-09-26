package br.com.systec.hive.security.impl.service;

import br.com.systec.hive.identity.api.domain.User;
import br.com.systec.hive.identity.api.service.UserService;
import br.com.systec.hive.security.api.domain.LoginAuthenticate;
import br.com.systec.hive.security.api.domain.RefreshTokenData;
import br.com.systec.hive.security.api.service.TokenCacheService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SecurityTokenServiceImplTest {

    @Mock
    private UserService userService;

    @Mock
    private TokenCacheService tokenCacheService;

    @Mock
    private HttpServletRequest request;

    @InjectMocks
    private SecurityTokenServiceImpl service;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "secret", "mySecretKeyForTestingPurposesOnly12345");
    }

    @Test
    void when_generateTokenAndRefreshToken_then_success() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setUsername("testuser");
        user.setTenantId(UUID.randomUUID());
        
        Authentication authentication = mock(Authentication.class);
        when(authentication.getPrincipal()).thenReturn(user);
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");

        LoginAuthenticate result = service.generateTokenAndRefreshToken(authentication);

        assertNotNull(result);
        assertNotNull(result.getAccessToken());
        assertNotNull(result.getRefreshToken());
        verify(tokenCacheService).saveRefreshToken(anyString(), any(RefreshTokenData.class), any(Duration.class));
    }

    @Test
    void when_generateNewAccessTokenByRefreshToken_then_success() {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        String refreshToken = "oldRefreshToken";
        Cookie cookie = new Cookie("refreshToken", refreshToken);
        
        RefreshTokenData oldData = new RefreshTokenData(userId, tenantId, "oldJti", Instant.now(), Instant.now().plus(Duration.ofDays(1)), "127.0.0.1", "UA");
        User user = new User();
        user.setId(userId);
        user.setUsername("testuser");
        user.setTenantId(tenantId);

        when(request.getCookies()).thenReturn(new Cookie[]{cookie});
        when(request.getHeader("X-Tenant-ID")).thenReturn(tenantId.toString());
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        when(tokenCacheService.getRefreshTokenData(tenantId, refreshToken)).thenReturn(Optional.of(oldData));
        when(userService.findById(userId)).thenReturn(user);

        LoginAuthenticate result = service.generateNewAccessTokenByRefreshToken();

        assertNotNull(result);
        verify(tokenCacheService).revokeRefreshToken(tenantId, refreshToken);
        verify(tokenCacheService).saveRefreshToken(anyString(), any(RefreshTokenData.class), any(Duration.class));
    }
}
