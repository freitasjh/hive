package br.com.systec.hive.ai.impl;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Streaming counters (security F6): names and numbers only, never content. The name set is closed so
 * a future call cannot smuggle a thinking snippet into a metric label.
 */
public final class StreamingMetrics {

    public static final String CANCEL_UNSUPPORTED = "stream.cancel.unsupported";
    public static final String FAILED = "stream.failed";
    public static final String TIMEOUT = "stream.timeout";
    public static final String CLIENT_ABORT = "stream.client_abort";

    private static final Set<String> ALLOWED = Set.of(
            CANCEL_UNSUPPORTED, FAILED, TIMEOUT, CLIENT_ABORT);

    private final Map<String, AtomicLong> counters = new ConcurrentHashMap<>();

    public void increment(String name) {
        if (!ALLOWED.contains(name)) {
            throw new IllegalArgumentException("unknown streaming counter");
        }
        counters.computeIfAbsent(name, ignored -> new AtomicLong()).incrementAndGet();
    }

    public long get(String name) {
        AtomicLong counter = counters.get(name);
        return counter == null ? 0L : counter.get();
    }
}
