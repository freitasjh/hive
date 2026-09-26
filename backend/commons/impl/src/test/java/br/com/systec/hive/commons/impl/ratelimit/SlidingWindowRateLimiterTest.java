package br.com.systec.hive.commons.impl.ratelimit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SlidingWindowRateLimiterTest {

    private SlidingWindowRateLimiter limiter;
    private UUID tenantId;

    @BeforeEach
    void setUp() {
        limiter = new SlidingWindowRateLimiter(2, 60L, IllegalStateException::new);
        tenantId = UUID.randomUUID();
    }

    @Test
    void when_check_with_callsBelowLimit_then_noException() {
        limiter.check(tenantId);

        assertThatNoException().isThrownBy(() -> limiter.check(tenantId));
    }

    @Test
    void when_check_with_callAboveLimit_then_throwsConfiguredException() {
        limiter.check(tenantId);
        limiter.check(tenantId);

        assertThatThrownBy(() -> limiter.check(tenantId))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void when_check_with_otherTenant_then_isolated() {
        limiter.check(tenantId);
        limiter.check(tenantId);

        assertThatNoException().isThrownBy(() -> limiter.check(UUID.randomUUID()));
    }

    @Test
    void when_check_after_clear_then_noException() {
        limiter.check(tenantId);
        limiter.check(tenantId);
        limiter.clear();

        assertThatNoException().isThrownBy(() -> limiter.check(tenantId));
    }
}
