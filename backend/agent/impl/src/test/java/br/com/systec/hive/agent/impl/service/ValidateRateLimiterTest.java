package br.com.systec.hive.agent.impl.service;

import br.com.systec.hive.agent.api.exceptions.ValidateRateLimitException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ValidateRateLimiterTest {

    private ValidateRateLimiter limiter;
    private UUID tenantId;

    @BeforeEach
    void setUp() {
        limiter = new ValidateRateLimiter();
        tenantId = UUID.randomUUID();
    }

    @Test
    void when_check_with_tenCalls_then_allows() {
        for (int i = 0; i < 10; i++) {
            assertThatNoException().isThrownBy(() -> limiter.check(tenantId));
        }
    }

    @Test
    void when_check_with_eleventhCall_then_throws429() {
        for (int i = 0; i < 10; i++) {
            limiter.check(tenantId);
        }

        assertThatThrownBy(() -> limiter.check(tenantId))
                .isInstanceOf(ValidateRateLimitException.class);
    }

    @Test
    void when_check_with_differentTenant_then_countsSeparately() {
        for (int i = 0; i < 10; i++) {
            limiter.check(tenantId);
        }

        assertThatNoException().isThrownBy(() -> limiter.check(UUID.randomUUID()));
    }
}
