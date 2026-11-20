package br.com.systec.hive.security.impl.service;

import br.com.systec.hive.security.api.domain.MfaChallenge;
import br.com.systec.hive.security.api.service.MfaChallengeCachePort;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Cache de desafios MFA no Redis, no padrão de {@link TokenCacheServiceImpl}.
 *
 * <p>Chave por tenant: {@code hive:auth:mfa:{tenantId}:{challengeId}} — o contador de tentativas
 * vive numa chave irmã {@code ...:attempts}, incrementada com {@code INCR} (atômico) e com o
 * mesmo TTL. Ambas expiram em 120 s. O challenge não contém segredo nem código TOTP.
 */
@Service
public class MfaChallengeCache implements MfaChallengeCachePort {

    private static final String MFA_PREFIX = "hive:auth:mfa:";
    private static final String ATTEMPTS_SUFFIX = ":attempts";
    private static final Duration TTL = Duration.ofSeconds(TTL_SECONDS);

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    public MfaChallengeCache(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    }

    @Override
    public void save(MfaChallenge challenge) {
        String key = buildKey(challenge.tenantId(), challenge.id());
        CachedChallenge cached = new CachedChallenge(
                challenge.id(), challenge.userId(), challenge.tenantId(),
                challenge.remoteIp(), challenge.userAgent(),
                challenge.expiresAt().getEpochSecond());
        redisTemplate.opsForValue().set(key, cached, TTL);
        redisTemplate.opsForValue().set(key + ATTEMPTS_SUFFIX, challenge.attempts(), TTL);
    }

    @Override
    public Optional<MfaChallenge> get(UUID tenantId, UUID challengeId) {
        String key = buildKey(tenantId, challengeId);
        Object raw = redisTemplate.opsForValue().get(key);
        if (raw == null) {
            return Optional.empty();
        }
        CachedChallenge cached = objectMapper.convertValue(raw, CachedChallenge.class);
        Object attemptsRaw = redisTemplate.opsForValue().get(key + ATTEMPTS_SUFFIX);
        int attempts = attemptsRaw instanceof Number number ? number.intValue() : 0;
        return Optional.of(new MfaChallenge(
                cached.id(), cached.userId(), cached.tenantId(),
                cached.remoteIp(), cached.userAgent(), attempts,
                Instant.ofEpochSecond(cached.expiresAtEpochSecond())));
    }

    @Override
    public int incrementAttempts(UUID tenantId, UUID challengeId) {
        String key = buildKey(tenantId, challengeId);
        if (!Boolean.TRUE.equals(redisTemplate.hasKey(key))) {
            return -1;
        }
        String attemptsKey = key + ATTEMPTS_SUFFIX;
        Long value = redisTemplate.opsForValue().increment(attemptsKey);
        if (value != null && value == 1L) {
            redisTemplate.expire(attemptsKey, TTL);
        }
        return value == null ? 0 : value.intValue();
    }

    @Override
    public void delete(UUID tenantId, UUID challengeId) {
        String key = buildKey(tenantId, challengeId);
        redisTemplate.delete(java.util.List.of(key, key + ATTEMPTS_SUFFIX));
    }

    private String buildKey(UUID tenantId, UUID challengeId) {
        return MFA_PREFIX + tenantId + ":" + challengeId;
    }

    /** Formato simples (sem Instant) para sobreviver ao serializer JSON do Redis. */
    private record CachedChallenge(
            UUID id,
            UUID userId,
            UUID tenantId,
            String remoteIp,
            String userAgent,
            long expiresAtEpochSecond
    ) {
    }
}
