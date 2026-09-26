package br.com.systec.hive.knowledge.impl.service;

import br.com.systec.hive.knowledge.api.exceptions.KbRateLimitException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class KbIngestRateLimiter {

    public static final int MAX_PER_MINUTE = 10;
    static final int MAX_TENANTS = 10_000;
    private static final long WINDOW_MS = 60_000L;
    private static final long IDLE_EVICT_MS = 2L * WINDOW_MS;
    private static final long SWEEP_INTERVAL = 64L;

    private final Map<UUID, TenantHits> hits = new ConcurrentHashMap<>();
    private final AtomicLong calls = new AtomicLong();

    public void check(UUID tenantId) {
        long now = Instant.now().toEpochMilli();
        if (calls.incrementAndGet() % SWEEP_INTERVAL == 0) {
            sweepIdle(now);
        }
        TenantHits entry = hits.computeIfAbsent(tenantId, k -> new TenantHits());
        synchronized (entry.deque()) {
            entry.touchedAt().set(now);
            while (!entry.deque().isEmpty() && now - entry.deque().peekFirst() > WINDOW_MS) {
                entry.deque().pollFirst();
            }
            if (entry.deque().size() >= MAX_PER_MINUTE) {
                throw new KbRateLimitException();
            }
            entry.deque().addLast(now);
        }
        evictOverflow(now);
    }

    private void sweepIdle(long now) {
        hits.entrySet().removeIf(e -> {
            TenantHits entry = e.getValue();
            synchronized (entry.deque()) {
                while (!entry.deque().isEmpty() && now - entry.deque().peekFirst() > WINDOW_MS) {
                    entry.deque().pollFirst();
                }
                return entry.deque().isEmpty() && now - entry.touchedAt().get() > IDLE_EVICT_MS;
            }
        });
    }

    private void evictOverflow(long now) {
        if (hits.size() < MAX_TENANTS) {
            return;
        }
        sweepIdle(now);
        while (hits.size() >= MAX_TENANTS) {
            UUID oldest = null;
            long oldestTouch = Long.MAX_VALUE;
            for (Map.Entry<UUID, TenantHits> e : hits.entrySet()) {
                long touched = e.getValue().touchedAt().get();
                if (touched < oldestTouch) {
                    oldestTouch = touched;
                    oldest = e.getKey();
                }
            }
            if (oldest == null || hits.remove(oldest) == null) {
                break;
            }
        }
    }

    int trackedTenants() {
        return hits.size();
    }

    record TenantHits(Deque<Long> deque, AtomicLong touchedAt) {
        TenantHits() {
            this(new ArrayDeque<>(), new AtomicLong(Instant.now().toEpochMilli()));
        }
    }
}
