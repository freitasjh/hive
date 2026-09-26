package br.com.systec.hive.security.impl.service;

import br.com.systec.hive.security.api.domain.RefreshTokenData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TokenCacheServiceImplTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    @InjectMocks
    private TokenCacheServiceImpl service;

    @Test
    void when_saveRefreshToken_then_success() {
        RefreshTokenData data = new RefreshTokenData(UUID.randomUUID(), UUID.randomUUID(), "jti", null, null, "ip", "ua");
        String refreshToken = "token";
        Duration ttl = Duration.ofDays(1);

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        service.saveRefreshToken(refreshToken, data, ttl);

        verify(valueOperations).set(anyString(), eq(data), eq(ttl));
    }

    @Test
    void when_getRefreshTokenData_withExistingToken_then_returnData() {
        UUID tenantId = UUID.randomUUID();
        String refreshToken = "token";
        RefreshTokenData data = new RefreshTokenData(UUID.randomUUID(), tenantId, "jti", null, null, "ip", "ua");

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(data);

        Optional<RefreshTokenData> result = service.getRefreshTokenData(tenantId, refreshToken);

        assertTrue(result.isPresent());
        assertEquals(data.userId(), result.get().userId());
    }

    @Test
    void when_revokeRefreshToken_then_success() {
        UUID tenantId = UUID.randomUUID();
        String refreshToken = "token";

        service.revokeRefreshToken(tenantId, refreshToken);

        verify(redisTemplate).delete(anyString());
    }

    @Test
    void when_addToBlacklist_then_success() {
        UUID tenantId = UUID.randomUUID();
        String jti = "jti";
        Duration ttl = Duration.ofMinutes(15);

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        service.addToBlacklist(tenantId, jti, ttl);

        verify(valueOperations).set(anyString(), eq("revoked"), eq(ttl));
    }

    @Test
    void when_isBlacklisted_then_returnBoolean() {
        UUID tenantId = UUID.randomUUID();
        String jti = "jti";

        when(redisTemplate.hasKey(anyString())).thenReturn(true);

        boolean result = service.isBlacklisted(tenantId, jti);

        assertTrue(result);
    }

    @Test
    void when_revokeAllUserTokens_then_success() {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        String key = "hive:auth:refresh:" + tenantId + ":token1";
        RefreshTokenData data = new RefreshTokenData(userId, tenantId, "jti", null, null, "ip", "ua");

        when(redisTemplate.keys(anyString())).thenReturn(Set.of(key));
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(key)).thenReturn(data);

        service.revokeAllUserTokens(tenantId, userId);

        verify(redisTemplate).delete(key);
    }
}
