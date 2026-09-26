package br.com.systec.hive.knowledge.impl.service;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class KbIngestRateLimiterEvictionTest {

    @Test
    @SuppressWarnings("unchecked")
    void when_idleTenantsAccumulate_then_sweepEvictsThem() throws Exception {
        KbIngestRateLimiter limiter = new KbIngestRateLimiter();
        Field hitsField = KbIngestRateLimiter.class.getDeclaredField("hits");
        hitsField.setAccessible(true);

        UUID staleTenant = UUID.randomUUID();
        limiter.check(staleTenant);
        Map<UUID, Object> hits = (Map<UUID, Object>) hitsField.get(limiter);
        Object entry = hits.get(staleTenant);
        Field dequeField = entry.getClass().getDeclaredField("deque");
        Field touchedField = entry.getClass().getDeclaredField("touchedAt");
        dequeField.setAccessible(true);
        touchedField.setAccessible(true);
        ((java.util.Deque<Long>) dequeField.get(entry)).clear();
        ((java.util.concurrent.atomic.AtomicLong) touchedField.get(entry)).set(1L);

        for (int i = 0; i < 64; i++) {
            limiter.check(UUID.randomUUID());
        }

        assertThat(hits).doesNotContainKey(staleTenant);
    }
}
