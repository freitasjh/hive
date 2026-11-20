package br.com.systec.hive.security.impl.service;

import br.com.systec.hive.security.api.domain.MfaChallenge;
import br.com.systec.hive.security.api.service.MfaChallengeCachePort;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Cache de desafios MFA no Redis, no padrão de {@link TokenCacheServiceImpl}.
 *
 * <p>Chave por tenant: {@code hive:auth:mfa:{tenantId}:{challengeId}} — o contador de tentativas
 * vive numa chave irmã {@code ...:attempts}, incrementada com {@code INCR} (atômico) e com o
 * mesmo TTL. O nonce de consumo vive em {@code ...:consume}; um script Lua atômico
 * (GET + DEL condicionais) garante que apenas um verify concorrente o remova — compatível
 * com Redis 7, que não tem o comando DELEX usado por {@code compareAndDelete}.
 * Todas expiram em 120 s. O challenge não contém segredo nem código TOTP.
 */
@Service
public class MfaChallengeCache implements MfaChallengeCachePort {

    private static final String MFA_PREFIX = "hive:auth:mfa:";
    private static final String BLOCK_PREFIX = "hive:auth:mfa:block:";
    private static final String ATTEMPTS_SUFFIX = ":attempts";
    private static final String CONSUME_SUFFIX = ":consume";
    private static final Duration TTL = Duration.ofSeconds(TTL_SECONDS);
    private static final Duration BLOCK_TTL = Duration.ofSeconds(BLOCK_TTL_SECONDS);

    /** Compare-and-delete em Redis 7: DELEX (Spring Data Redis 4.1) não existe no servidor. */
    private static final String CONSUME_SCRIPT =
            "local token = redis.call('GET', KEYS[1])\n" +
            "if token == ARGV[1] then\n" +
            "  redis.call('DEL', KEYS[1], KEYS[2], KEYS[3])\n" +
            "  return 1\n" +
            "end\n" +
            "return 0\n";

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;
    private final RedisScript<Long> consumeScript = new DefaultRedisScript<>(CONSUME_SCRIPT, Long.class);

    public MfaChallengeCache(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    }

    @Override
    public void save(MfaChallenge challenge, String consumeToken) {
        String key = buildKey(challenge.tenantId(), challenge.id());
        CachedChallenge cached = new CachedChallenge(
                challenge.id(), challenge.userId(), challenge.tenantId(),
                challenge.remoteIp(), challenge.userAgent(),
                challenge.expiresAt().getEpochSecond(),
                consumeToken, challenge.jti());
        redisTemplate.opsForValue().set(key, cached, TTL);
        redisTemplate.opsForValue().set(key + ATTEMPTS_SUFFIX, challenge.attempts(), TTL);
        if (consumeToken != null && !consumeToken.isBlank()) {
            redisTemplate.opsForValue().set(key + CONSUME_SUFFIX, consumeToken, TTL);
        }
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
                Instant.ofEpochSecond(cached.expiresAtEpochSecond()),
                cached.consumeToken(), cached.jti()));
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
    public boolean deleteIfTokenMatches(UUID tenantId, UUID challengeId, String expectedToken) {
        if (expectedToken == null || expectedToken.isBlank()) {
            return false;
        }
        String key = buildKey(tenantId, challengeId);
        // Lua atômica: só o chamador que remover o nonce pode seguir para emissão.
        Long removed = redisTemplate.execute(consumeScript,
                List.of(key + CONSUME_SUFFIX, key, key + ATTEMPTS_SUFFIX), expectedToken);
        return Long.valueOf(1L).equals(removed);
    }

    @Override
    public void delete(UUID tenantId, UUID challengeId) {
        String key = buildKey(tenantId, challengeId);
        redisTemplate.delete(List.of(key, key + ATTEMPTS_SUFFIX, key + CONSUME_SUFFIX));
    }

    @Override
    public void blockUser(UUID tenantId, UUID userId) {
        redisTemplate.opsForValue().set(buildBlockKey(tenantId, userId), "blocked", BLOCK_TTL);
    }

    @Override
    public boolean isUserBlocked(UUID tenantId, UUID userId) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(buildBlockKey(tenantId, userId)));
    }

    private String buildKey(UUID tenantId, UUID challengeId) {
        return MFA_PREFIX + tenantId + ":" + challengeId;
    }

    private String buildBlockKey(UUID tenantId, UUID userId) {
        return BLOCK_PREFIX + tenantId + ":" + userId;
    }

    /** Formato simples (sem Instant) para sobreviver ao serializer JSON do Redis. */
    private record CachedChallenge(
            UUID id,
            UUID userId,
            UUID tenantId,
            String remoteIp,
            String userAgent,
            long expiresAtEpochSecond,
            String consumeToken,
            String jti
    ) {
    }
}
