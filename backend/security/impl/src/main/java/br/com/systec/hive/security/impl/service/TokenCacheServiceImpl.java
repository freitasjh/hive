package br.com.systec.hive.security.impl.service;

import br.com.systec.hive.security.api.domain.RefreshTokenData;
import br.com.systec.hive.security.api.service.TokenCacheService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Implementação do cache de tokens utilizando Redis.
 * Segue o padrão de Monolito Modular e Clean Architecture.
 */
@Service
public class TokenCacheServiceImpl implements TokenCacheService {

    private static final Logger log = LoggerFactory.getLogger(TokenCacheServiceImpl.class);

    // Prefixos de chaves para garantir isolamento e organização no Redis
    private static final String REFRESH_PREFIX = "hive:auth:refresh:";
    private static final String BLACKLIST_PREFIX = "hive:auth:blacklist:";

    private final RedisTemplate<String, Object> redisTemplate;

    public TokenCacheServiceImpl(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void saveRefreshToken(String refreshToken, RefreshTokenData data, Duration ttl) {
        String key = buildRefreshKey(data.tenantId(), refreshToken);
        log.debug("Salvando Refresh Token para o tenant: {} e usuário: {}", data.tenantId(), data.userId());
        redisTemplate.opsForValue().set(key, data, ttl);
    }

    @Override
    public Optional<RefreshTokenData> getRefreshTokenData(UUID tenantId, String refreshToken) {
        String key = buildRefreshKey(tenantId, refreshToken);
        Object data = redisTemplate.opsForValue().get(key);

        if (data != null) {
            RefreshTokenData refreshTokenData = new ObjectMapper().convertValue(data, RefreshTokenData.class);
            return Optional.of(refreshTokenData);
        }
        return Optional.empty();
    }

    @Override
    public void revokeRefreshToken(UUID tenantId, String refreshToken) {
        String key = buildRefreshKey(tenantId, refreshToken);
        redisTemplate.delete(key);
    }

    @Override
    public void addToBlacklist(UUID tenantId, String jti, Duration ttl) {
        String key = buildBlacklistKey(tenantId, jti);
        // O valor na blacklist é apenas um marcador (podemos usar o timestamp de revogação)
        redisTemplate.opsForValue().set(key, "revoked", ttl);
    }

    @Override
    public boolean isBlacklisted(UUID tenantId, String jti) {
        String key = buildBlacklistKey(tenantId, jti);
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    @Override
    public void revokeAllUserTokens(UUID tenantId, UUID userId) {
        // Busca todas as chaves de refresh tokens desse tenant
        String pattern = REFRESH_PREFIX + tenantId + ":*";
        Set<String> keys = redisTemplate.keys(pattern);

        if (keys != null && !keys.isEmpty()) {
            keys.forEach(key -> {
                Object data = redisTemplate.opsForValue().get(key);
                if (data instanceof RefreshTokenData tokenData && tokenData.userId().equals(userId)) {
                    redisTemplate.delete(key);
                    log.info("Revogando token de usuário {} no tenant {}", userId, tenantId);
                }
            });
        }
    }

    private String buildRefreshKey(UUID tenantId, String token) {
        return REFRESH_PREFIX + tenantId + ":" + token;
    }

    private String buildBlacklistKey(UUID tenantId, String jti) {
        return BLACKLIST_PREFIX + tenantId + ":" + jti;
    }
}
