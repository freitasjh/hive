package br.com.systec.hive.agent.impl.service;

import br.com.systec.hive.agent.api.exceptions.ValidateRateLimitException;
import br.com.systec.hive.agent.api.service.ValidateRateLimitPolicy;
import br.com.systec.hive.commons.impl.ratelimit.SlidingWindowRateLimiter;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Sliding-window throttle for POST /v1/agents/validate and GET /v1/providers/{provider}/models:
 * 10 calls/minute per tenant. Windowing algorithm lives in commons
 * {@link SlidingWindowRateLimiter}; only the limit and the exception type are
 * domain-specific. Exposed to the web layer via {@link ValidateRateLimitPolicy}
 * so web depends only on api.
 */
@Component
public class ValidateRateLimiter implements ValidateRateLimitPolicy {

    static final int MAX_CALLS = 10;
    static final long WINDOW_SECONDS = 60L;

    private final SlidingWindowRateLimiter delegate =
            new SlidingWindowRateLimiter(MAX_CALLS, WINDOW_SECONDS, ValidateRateLimitException::new);

    @Override
    public void check(UUID tenantId) {
        delegate.check(tenantId);
    }

    void clear() {
        delegate.clear();
    }
}
