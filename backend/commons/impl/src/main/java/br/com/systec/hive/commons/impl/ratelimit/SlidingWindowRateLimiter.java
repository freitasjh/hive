package br.com.systec.hive.commons.impl.ratelimit;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Generic sliding-window throttle keyed by tenant.
 *
 * <p>Domain limiters ({@code ChatRateLimiter}, {@code ValidateRateLimiter}) delegate to
 * this class, parameterizing only window size and the exception factory — the
 * windowing algorithm lives here, once.
 */
public class SlidingWindowRateLimiter {

    private final int maxCalls;
    private final long windowSeconds;
    private final Supplier<? extends RuntimeException> exceptionFactory;
    private final ConcurrentHashMap<UUID, Deque<Instant>> calls = new ConcurrentHashMap<>();

    public SlidingWindowRateLimiter(int maxCalls, long windowSeconds,
            Supplier<? extends RuntimeException> exceptionFactory) {
        if (maxCalls <= 0) {
            throw new IllegalArgumentException("maxCalls must be positive");
        }
        if (windowSeconds <= 0) {
            throw new IllegalArgumentException("windowSeconds must be positive");
        }
        if (exceptionFactory == null) {
            throw new IllegalArgumentException("exceptionFactory must not be null");
        }
        this.maxCalls = maxCalls;
        this.windowSeconds = windowSeconds;
        this.exceptionFactory = exceptionFactory;
    }

    public void check(UUID tenantId) {
        Instant now = Instant.now();
        Instant cutoff = now.minusSeconds(windowSeconds);
        Deque<Instant> deque = calls.computeIfAbsent(tenantId, k -> new ArrayDeque<>());
        synchronized (deque) {
            while (!deque.isEmpty() && deque.peekFirst().isBefore(cutoff)) {
                deque.pollFirst();
            }
            if (deque.size() >= maxCalls) {
                throw exceptionFactory.get();
            }
            deque.addLast(now);
        }
    }

    public void clear() {
        calls.clear();
    }
}
