package br.com.systec.hive.publicapi.impl.quota;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RedisQuotaGuardTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    private RedisQuotaGuard guard;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        guard = new RedisQuotaGuard(redisTemplate, 60);
    }

    @Test
    void when_underLimit_then_allowed() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenReturn(List.of(1L, 5L));

        guard.check("hive:public:quota:{c1}", 60);
        // no exception
    }

    @Test
    void when_exactLimitReached_then_throwsWithRetryAfter() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenReturn(List.of(0L, 42L));

        assertThatThrownBy(() -> guard.check("hive:public:quota:{c1}", 60))
                .isInstanceOf(QuotaExceededException.class)
                .satisfies(e -> assertThat(((QuotaExceededException) e).getRetryAfterSeconds()).isEqualTo(42L));
    }

    @Test
    void when_twoClients_then_independentKeys() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenReturn(List.of(1L, 1L));

        guard.check(RedisQuotaGuard.clientKey("c1"), 60);
        guard.check(RedisQuotaGuard.clientKey("c2"), 60);
        // no exception for either
    }

    @Test
    void when_tenantCapExceeded_then_throws() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenReturn(List.of(0L, 10L));

        UUID tenant = UUID.randomUUID();
        assertThatThrownBy(() -> guard.checkTenant(tenant, 100))
                .isInstanceOf(QuotaExceededException.class);
    }

    @Test
    void when_redisDown_then_failOpen() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenThrow(new RuntimeException("redis down"));

        // should not throw
        guard.check("hive:public:quota:{c1}", 60);
    }

    @Test
    void when_underLimit_then_decisionIsMeasuredAndCarriesReset() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenReturn(List.of(1L, 5L));
        long before = System.currentTimeMillis() / 1000L + 60L;

        QuotaDecision decision = guard.acquireDecision(RedisQuotaGuard.tenantKey(UUID.randomUUID()), 100);

        assertThat(decision.limit()).isEqualTo(100);
        assertThat(decision.remaining()).isEqualTo(5L);
        assertThat(decision.retryAfterSeconds()).isZero();
        assertThat(decision.measured()).isTrue();
        assertThat(decision.resetEpochSeconds()).isBetween(before, before + 2L);
    }

    @Test
    void when_denied_then_exceptionCarriesMeasuredDecision() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenReturn(List.of(0L, 42L));

        assertThatThrownBy(() -> guard.acquireDecision(RedisQuotaGuard.tenantKey(UUID.randomUUID()), 100))
                .isInstanceOf(QuotaExceededException.class)
                .satisfies(e -> {
                    QuotaDecision decision = ((QuotaExceededException) e).getDecision();
                    assertThat(decision.limit()).isEqualTo(100);
                    assertThat(decision.remaining()).isZero();
                    assertThat(decision.retryAfterSeconds()).isEqualTo(42L);
                    assertThat(decision.measured()).isTrue();
                });
    }

    @Test
    void when_redisDown_then_decisionIsNotMeasuredAndNoValuesInvented() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenThrow(new RuntimeException("redis down"));

        QuotaDecision decision = guard.acquireDecision(RedisQuotaGuard.tenantKey(UUID.randomUUID()), 100);

        assertThat(decision.measured()).isFalse();
        assertThat(decision.remaining()).isEqualTo(100L);
        assertThat(decision.retryAfterSeconds()).isZero();
    }

    @Test
    void when_tokenEndpoint_then_threeChecks() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenReturn(List.of(1L, 1L));

        guard.checkTokenEndpoint("c1", "10.0.0.1", 5, 100, 20);
        // no exception
    }

    @Test
    void when_checkClientAndTenant_secondExceeds_then_throws() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenReturn(List.of(1L, 1L), List.of(0L, 5L));

        UUID tenant = UUID.randomUUID();
        assertThatThrownBy(() -> guard.checkClientAndTenant("c1", tenant, 60, 100))
                .isInstanceOf(QuotaExceededException.class);
    }
}