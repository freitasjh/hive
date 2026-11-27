package br.com.systec.hive.assistant.api.service;

import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * One live stream per tenant+client pair (security F2). Admission happens before the emitter opens,
 * so a rejected request still gets the ordinary JSON 429 and leaves no stream behind.
 *
 * <p>The returned token is the release credential. {@link #release(UUID, String, Object)} removes the
 * entry only when the caller still owns it: a late callback from a finished stream cannot free the
 * slot a different stream has since taken.
 */
@Component
public final class StreamAdmission {

    private final ConcurrentHashMap<String, Object> active = new ConcurrentHashMap<>();

    /** Returns the ownership token, or {@code null} when this pair already has a live stream. */
    public Object tryAdmit(UUID tenantId, String clientId) {
        Object token = new Object();
        return active.putIfAbsent(key(tenantId, clientId), token) == null ? token : null;
    }

    /** Idempotent for the owner; a no-op for a stale token (CR-01/F2). */
    public void release(UUID tenantId, String clientId, Object token) {
        active.remove(key(tenantId, clientId), token);
    }

    private static String key(UUID tenantId, String clientId) {
        return String.valueOf(tenantId) + "|" + String.valueOf(clientId);
    }
}
