package br.com.systec.hive.publicapi.impl.quota;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Sliding-window quota guard backed by Redis SORTED SET.
 *
 * <p>A single atomic {@code EVAL} runs {@code ZREMRANGEBYSCORE}/{@code ZCARD}/{@code ZADD}/{@code EXPIRE}.
 * The member carries a random suffix so two requests landing in the same microsecond do not collapse
 * into one entry (Redis treats a duplicate member as an update, which would under-count).
 *
 * <p>Redis unavailability degrades to fail-open with an error log (cost accepted: fail-closed would
 * make Redis a hard dependency of every chat turn).
 */
@Component
public class RedisQuotaGuard {

    private static final Logger log = LoggerFactory.getLogger(RedisQuotaGuard.class);

    public static final long DEFAULT_WINDOW_SECONDS = 60L;

    private static final String LUA_SCRIPT =
            "local key = KEYS[1]\n" +
            "local now = tonumber(ARGV[1])\n" +
            "local window = tonumber(ARGV[2])\n" +
            "local limit = tonumber(ARGV[3])\n" +
            "local suffix = ARGV[4]\n" +
            "local cutoff = now - (window * 1000)\n" +
            "redis.call('ZREMRANGEBYSCORE', key, 0, cutoff)\n" +
            "local count = redis.call('ZCARD', key)\n" +
            "if count >= limit then\n" +
            "  local oldest = redis.call('ZRANGE', key, 0, 0, 'WITHSCORES')\n" +
            "  local retryAfter = window\n" +
            "  if oldest and #oldest >= 2 then\n" +
            "    local oldestScore = tonumber(oldest[2])\n" +
            "    if oldestScore then\n" +
            "      retryAfter = math.ceil((oldestScore + (window * 1000) - now) / 1000)\n" +
            "      if retryAfter < 1 then retryAfter = 1 end\n" +
            "    end\n" +
            "  end\n" +
            "  return {0, retryAfter}\n" +
            "end\n" +
            "local member = tostring(now) .. '-' .. suffix\n" +
            "redis.call('ZADD', key, now, member)\n" +
            "redis.call('EXPIRE', key, window * 2)\n" +
            "return {1, limit - count - 1}\n";

    private final StringRedisTemplate redisTemplate;
    private final long windowSeconds;
    private final RedisScript<List> script;

    @org.springframework.beans.factory.annotation.Autowired
    public RedisQuotaGuard(RedisConnectionFactory connectionFactory) {
        this(connectionFactory, DEFAULT_WINDOW_SECONDS);
    }

    public RedisQuotaGuard(RedisConnectionFactory connectionFactory, long windowSeconds) {
        if (connectionFactory == null) {
            throw new IllegalArgumentException("connectionFactory must not be null");
        }
        StringRedisTemplate redisTemplate = new StringRedisTemplate(connectionFactory);
        if (windowSeconds <= 0) {
            throw new IllegalArgumentException("windowSeconds must be positive");
        }
        this.redisTemplate = redisTemplate;
        this.windowSeconds = windowSeconds;
        this.script = new DefaultRedisScript<>(LUA_SCRIPT, List.class);
    }

    public RedisQuotaGuard(StringRedisTemplate redisTemplate) {
        this(redisTemplate, DEFAULT_WINDOW_SECONDS);
    }

    public RedisQuotaGuard(StringRedisTemplate redisTemplate, long windowSeconds) {
        if (redisTemplate == null) {
            throw new IllegalArgumentException("redisTemplate must not be null");
        }
        if (windowSeconds <= 0) {
            throw new IllegalArgumentException("windowSeconds must be positive");
        }
        this.redisTemplate = redisTemplate;
        this.windowSeconds = windowSeconds;
        this.script = new DefaultRedisScript<>(LUA_SCRIPT, List.class);
    }

    public long getWindowSeconds() {
        return windowSeconds;
    }

    /** Generic single-key check. Throws {@link QuotaExceededException} when the limit is exceeded. */
    public void check(String key, int limitPerMinute) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("key must not be blank");
        }
        int limit = limitPerMinute <= 0 ? 1 : limitPerMinute;
        acquire(key, limit);
    }

    public void checkClient(String clientId, int clientLimitPerMinute) {
        check(clientKey(clientId), clientLimitPerMinute);
    }

    public void checkTenant(UUID tenantId, int tenantLimitPerMinute) {
        check(tenantKey(tenantId), tenantLimitPerMinute);
    }

    public void checkClientAndTenant(String clientId, UUID tenantId, int clientLimitPerMinute, int tenantLimitPerMinute) {
        checkClient(clientId, clientLimitPerMinute);
        checkTenant(tenantId, tenantLimitPerMinute);
    }

    public void checkTokenEndpoint(String clientId, String ip, int clientLimitPerMinute,
                                  int globalLimitPerMinute, int ipLimitPerMinute) {
        check(tokenClientKey(clientId), clientLimitPerMinute);
        check(tokenGlobalKey(), globalLimitPerMinute);
        check(tokenIpKey(ip), ipLimitPerMinute);
    }

    private void acquire(String key, int limit) {
        try {
            long now = System.currentTimeMillis();
            String suffix = Long.toHexString(ThreadLocalRandom.current().nextLong());
            List<?> result = redisTemplate.execute(script, Collections.singletonList(key),
                    String.valueOf(now), String.valueOf(windowSeconds), String.valueOf(limit), suffix);
            if (result == null || result.isEmpty()) {
                // No answer from Redis: treat as allowed (fail-open on ambiguous response).
                log.warn("Redis quota check returned no result for key {}; failing open", key);
                return;
            }
            int status = ((Number) result.get(0)).intValue();
            if (status == 0) {
                long retryAfter = result.size() > 1 && result.get(1) != null
                        ? ((Number) result.get(1)).longValue() : windowSeconds;
                throw new QuotaExceededException("rate limit exceeded for key " + key, retryAfter);
            }
        } catch (QuotaExceededException e) {
            throw e;
        } catch (Exception e) {
            log.error("Redis unavailable during quota check for key {}; failing open", key, e);
        }
    }

    public static String clientKey(String clientId) {
        return "hive:public:quota:{" + clientId + "}";
    }

    public static String tenantKey(UUID tenantId) {
        return "hive:public:quota-tenant:{" + tenantId + "}";
    }

    public static String tokenClientKey(String clientId) {
        return "hive:public:quota-token:{" + clientId + "}";
    }

    public static String tokenGlobalKey() {
        return "hive:public:quota-token:global";
    }

    public static String tokenIpKey(String ip) {
        return "hive:public:quota-token:ip:{" + (ip == null ? "unknown" : ip) + "}";
    }
}