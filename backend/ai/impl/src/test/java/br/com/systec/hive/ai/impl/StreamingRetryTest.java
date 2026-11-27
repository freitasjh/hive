package br.com.systec.hive.ai.impl;

import dev.langchain4j.exception.HttpException;
import dev.langchain4j.exception.TimeoutException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RT-04 — retry only before the first token; context captured on the caller thread survives the
 * provider's callback thread (ADR-001 D4/C1).
 */
class StreamingRetryTest {

    private final StreamingRetryPolicy policy = StreamingRetryPolicy.withoutDelay();

    @Test
    void when_errorBeforeFirstToken_then_retriesUpToPolicyLimit() {
        StreamingRetryPolicy.Context context = policy.capture(UUID.randomUUID(), UUID.randomUUID());
        AtomicInteger attempts = new AtomicInteger();
        int retries = 0;

        while (attempts.incrementAndGet() <= policy.maxAttempts()) {
            if (policy.shouldRetry(new HttpException(503, "unavailable"), false, attempts.get())) {
                retries++;
                continue;
            }
            break;
        }

        assertThat(retries).isEqualTo(policy.maxAttempts() - 1);
        assertThat(attempts).hasValue(policy.maxAttempts());
        assertThat(context.turnId()).isNotNull();
    }

    @Test
    void when_errorAfterFirstToken_then_noRetry() {
        boolean emittedAnyToken = true;

        assertThat(policy.shouldRetry(new HttpException(503, "unavailable"), emittedAnyToken, 1)).isFalse();
        assertThat(policy.shouldRetry(new HttpException(503, "unavailable"), false, policy.maxAttempts())).isFalse();
    }

    @Test
    void when_timeoutOccurs_then_noRetry() {
        assertThat(policy.shouldRetry(new TimeoutException("waited"), false, 1)).isFalse();
    }

    /** AT-03: paridade com LlmRetryPolicy.java:137 — 408 é transitório, como 429 e 5xx. */
    @ParameterizedTest
    @ValueSource(ints = {408, 429, 500, 503})
    void when_httpStatusIsTransient_then_retry(int status) {
        assertThat(StreamingRetryPolicy.isTransient(new HttpException(status, "retry"))).isTrue();
        assertThat(policy.shouldRetry(new HttpException(status, "retry"), false, 1)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 401, 404})
    void when_httpStatusIsNotTransient_then_noRetry(int status) {
        assertThat(StreamingRetryPolicy.isTransient(new HttpException(status, "no retry"))).isFalse();
    }

    @Test
    void when_callbackRunsOnProviderThread_then_capturedContextIsStillAvailable() throws Exception {
        UUID sessionId = UUID.randomUUID();
        UUID turnId = UUID.randomUUID();
        StreamingRetryPolicy.Context captured = policy.capture(sessionId, turnId);
        AtomicReference<StreamingRetryPolicy.Context> seen = new AtomicReference<>();

        Thread providerThread = new Thread(() -> seen.set(captured)); // no ScopedValue binding here
        providerThread.start();
        providerThread.join(1_000);

        assertThat(seen.get()).isEqualTo(captured);
        assertThat(seen.get().sessionId()).isEqualTo(sessionId);
        assertThat(seen.get().turnId()).isEqualTo(turnId);
    }

    @Test
    void when_policyDefaults_then_threeAttemptsAnd120sTimeout() {
        assertThat(StreamingRetryPolicy.defaults().maxAttempts()).isEqualTo(3);
        assertThat(StreamingRetryPolicy.TURN_TIMEOUT.toSeconds()).isEqualTo(120L);
    }
}
