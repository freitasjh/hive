package br.com.systec.hive.knowledge.impl.service;

import br.com.systec.hive.knowledge.api.exceptions.KbRateLimitException;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KbIngestRateLimiterTest {

    @Test
    void when_check_withinLimit_then_ok() {
        KbIngestRateLimiter limiter = new KbIngestRateLimiter();
        UUID tenant = UUID.randomUUID();
        for (int i = 0; i < KbIngestRateLimiter.MAX_PER_MINUTE; i++) {
            limiter.check(tenant);
        }
        assertThat(true).isTrue();
    }

    @Test
    void when_check_overLimit_then_throws() {
        KbIngestRateLimiter limiter = new KbIngestRateLimiter();
        UUID tenant = UUID.randomUUID();
        for (int i = 0; i < KbIngestRateLimiter.MAX_PER_MINUTE; i++) {
            limiter.check(tenant);
        }
        assertThatThrownBy(() -> limiter.check(tenant)).isInstanceOf(KbRateLimitException.class);
    }
}
