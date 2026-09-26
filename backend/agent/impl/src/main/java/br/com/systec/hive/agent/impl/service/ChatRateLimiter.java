package br.com.systec.hive.agent.impl.service;

import br.com.systec.hive.agent.api.exceptions.ChatRateLimitException;
import br.com.systec.hive.commons.impl.ratelimit.SlidingWindowRateLimiter;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Sliding-window throttle for POST /v1/chat: 10 messages/minute per tenant.
 * Windowing algorithm lives in commons {@link SlidingWindowRateLimiter}; only the
 * limit and the exception type are domain-specific.
 */
@Component
public class ChatRateLimiter {

    static final int MAX_CALLS = 10;
    static final long WINDOW_SECONDS = 60L;

    private final SlidingWindowRateLimiter delegate =
            new SlidingWindowRateLimiter(MAX_CALLS, WINDOW_SECONDS, ChatRateLimitException::new);

    public void check(UUID tenantId) {
        delegate.check(tenantId);
    }

    void clear() {
        delegate.clear();
    }
}
