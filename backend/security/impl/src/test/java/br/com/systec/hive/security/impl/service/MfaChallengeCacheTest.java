package br.com.systec.hive.security.impl.service;

import br.com.systec.hive.security.api.domain.MfaChallenge;
import br.com.systec.hive.security.api.service.MfaChallengeCachePort;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.JacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Cache de challenge MFA contra Redis real (Testcontainers): TTL de 120 s, contador de
 * tentativas e isolamento por tenant. Nenhum segredo/código é escrito em log.
 */
class MfaChallengeCacheTest {

    private static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

    private static RedisTemplate<String, Object> redisTemplate;
    private static MfaChallengeCache cache;

    @BeforeAll
    static void startRedis() {
        REDIS.start();
        LettuceConnectionFactory factory =
                new LettuceConnectionFactory(REDIS.getHost(), REDIS.getMappedPort(6379));
        factory.afterPropertiesSet();
        redisTemplate = new RedisTemplate<>();
        redisTemplate.setConnectionFactory(factory);
        redisTemplate.setKeySerializer(new StringRedisSerializer());
        redisTemplate.setValueSerializer(new JacksonJsonRedisSerializer<>(Object.class));
        redisTemplate.afterPropertiesSet();
        cache = new MfaChallengeCache(redisTemplate);
    }

    @AfterAll
    static void stopRedis() {
        REDIS.stop();
    }

    private static MfaChallenge challenge(UUID tenantId, UUID id) {
        return new MfaChallenge(id, UUID.randomUUID(), tenantId, "127.0.0.1", "it-agent", 0,
                Instant.now().plus(120, ChronoUnit.SECONDS));
    }

    @Test
    void when_saveThenGet_then_roundTripPreservesMetadata() {
        UUID tenantId = UUID.randomUUID();
        MfaChallenge challenge = challenge(tenantId, UUID.randomUUID());

        cache.save(challenge);
        Optional<MfaChallenge> found = cache.get(tenantId, challenge.id());

        assertTrue(found.isPresent());
        assertEquals(challenge.id(), found.get().id());
        assertEquals(challenge.userId(), found.get().userId());
        assertEquals(challenge.tenantId(), found.get().tenantId());
        assertEquals(challenge.remoteIp(), found.get().remoteIp());
        assertEquals(challenge.userAgent(), found.get().userAgent());
        assertEquals(challenge.expiresAt().getEpochSecond(), found.get().expiresAt().getEpochSecond());
        assertEquals(0, found.get().attempts());
    }

    @Test
    void when_getWithOtherTenant_then_empty() {
        UUID tenantId = UUID.randomUUID();
        MfaChallenge challenge = challenge(tenantId, UUID.randomUUID());
        cache.save(challenge);

        assertTrue(cache.get(UUID.randomUUID(), challenge.id()).isEmpty());
    }

    @Test
    void when_saved_then_ttlIs120Seconds() {
        UUID tenantId = UUID.randomUUID();
        MfaChallenge challenge = challenge(tenantId, UUID.randomUUID());

        cache.save(challenge);
        String key = "hive:auth:mfa:" + tenantId + ":" + challenge.id();

        Long ttl = redisTemplate.getExpire(key, TimeUnit.SECONDS);
        Long attemptsTtl = redisTemplate.getExpire(key + ":attempts", TimeUnit.SECONDS);
        assertTrue(ttl != null && ttl > 0 && ttl <= MfaChallengeCachePort.TTL_SECONDS, "ttl=" + ttl);
        assertTrue(attemptsTtl != null && attemptsTtl > 0 && attemptsTtl <= MfaChallengeCachePort.TTL_SECONDS);
    }

    @Test
    void when_incrementAttempts_then_countsUpToFiveAndReflectsInGet() {
        UUID tenantId = UUID.randomUUID();
        MfaChallenge challenge = challenge(tenantId, UUID.randomUUID());
        cache.save(challenge);

        for (int expected = 1; expected <= MfaChallengeCachePort.MAX_ATTEMPTS; expected++) {
            assertEquals(expected, cache.incrementAttempts(tenantId, challenge.id()));
        }
        assertEquals(MfaChallengeCachePort.MAX_ATTEMPTS,
                cache.get(tenantId, challenge.id()).orElseThrow().attempts());
    }

    @Test
    void when_delete_then_challengeAndCounterAreGone() {
        UUID tenantId = UUID.randomUUID();
        MfaChallenge challenge = challenge(tenantId, UUID.randomUUID());
        cache.save(challenge);
        cache.incrementAttempts(tenantId, challenge.id());

        cache.delete(tenantId, challenge.id());

        assertTrue(cache.get(tenantId, challenge.id()).isEmpty());
        assertEquals(-1, cache.incrementAttempts(tenantId, challenge.id()));
        assertFalse(Boolean.TRUE.equals(redisTemplate.hasKey(
                "hive:auth:mfa:" + tenantId + ":" + challenge.id() + ":attempts")));
    }
}
