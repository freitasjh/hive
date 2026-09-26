package br.com.systec.hive.provider.langchain4j;

import dev.langchain4j.exception.AuthenticationException;
import dev.langchain4j.exception.HttpException;
import dev.langchain4j.exception.InternalServerException;
import dev.langchain4j.exception.ModelNotFoundException;
import dev.langchain4j.exception.RateLimitException;
import dev.langchain4j.exception.TimeoutException;
import org.junit.jupiter.api.Test;

import java.net.http.HttpTimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LlmRetryPolicyTest {

    private final LlmRetryPolicy policy = LlmRetryPolicy.withoutDelay();

    // ------------------------------------------------- A3: read timeout must not be retried

    @Test
    void when_call_withLangChain4jReadTimeout_then_failsWithoutRetry() {
        AtomicInteger attempts = new AtomicInteger();

        assertThatThrownBy(() -> policy.call(() -> {
            attempts.incrementAndGet();
            throw new TimeoutException(new HttpTimeoutException("request timed out"));
        })).isInstanceOf(TimeoutException.class);

        assertThat(attempts).as("a read timeout must not be retried: the user already waited").hasValue(1);
    }

    @Test
    void when_call_withRawHttpTimeoutWrapped_then_failsWithoutRetry() {
        AtomicInteger attempts = new AtomicInteger();

        assertThatThrownBy(() -> policy.call(() -> {
            attempts.incrementAndGet();
            throw new TimeoutException(new RuntimeException(new HttpTimeoutException("read timed out")));
        })).isInstanceOf(TimeoutException.class);

        assertThat(attempts).hasValue(1);
    }

    @Test
    void when_call_withUncheckedRuntimeWrappingTimeout_then_failsWithoutRetry() {
        AtomicInteger attempts = new AtomicInteger();

        assertThatThrownBy(() -> policy.call(() -> {
            attempts.incrementAndGet();
            throw new IllegalStateException("boom", new HttpTimeoutException("read timed out"));
        })).isInstanceOf(IllegalStateException.class);

        assertThat(attempts).hasValue(1);
    }

    // ------------------------------------------------- 429 / 5xx stay retriable

    @Test
    void when_call_with429_then_retriesThenSucceeds() throws Exception {
        AtomicInteger attempts = new AtomicInteger();

        String result = policy.call(() -> {
            if (attempts.incrementAndGet() < 3) {
                throw new HttpException(429, "rate limited");
            }
            return "ok";
        });

        assertThat(result).isEqualTo("ok");
        assertThat(attempts).hasValue(3);
    }

    @Test
    void when_call_with500_then_retries() {
        AtomicInteger attempts = new AtomicInteger();

        assertThatThrownBy(() -> policy.call(() -> {
            attempts.incrementAndGet();
            throw new HttpException(500, "server error");
        })).isInstanceOf(HttpException.class);

        assertThat(attempts).as("1 initial + 2 retries").hasValue(3);
    }

    @Test
    void when_call_with503_then_retries() {
        AtomicInteger attempts = new AtomicInteger();

        assertThatThrownBy(() -> policy.call(() -> {
            attempts.incrementAndGet();
            throw new HttpException(503, "unavailable");
        })).isInstanceOf(HttpException.class);

        assertThat(attempts).hasValue(3);
    }

    @Test
    void when_call_withRateLimitAndServerExceptions_then_retries() {
        AtomicInteger rateLimit = new AtomicInteger();
        AtomicInteger server = new AtomicInteger();

        assertThatThrownBy(() -> policy.call(() -> {
            rateLimit.incrementAndGet();
            throw new RateLimitException("slow down");
        })).isInstanceOf(RateLimitException.class);
        assertThatThrownBy(() -> policy.call(() -> {
            server.incrementAndGet();
            throw new InternalServerException("boom");
        })).isInstanceOf(InternalServerException.class);

        assertThat(rateLimit).hasValue(3);
        assertThat(server).hasValue(3);
    }

    // ------------------------------------------------- non-transient failures

    @Test
    void when_call_withNonTransientFailure_then_failsWithoutRetry() {
        for (RuntimeException failure : java.util.List.of(
                new HttpException(400, "bad request"),
                new HttpException(401, "unauthorized"),
                new HttpException(404, "not found"),
                new AuthenticationException("bad key"),
                new ModelNotFoundException("model_not_found"),
                new IllegalStateException("bug"))) {
            AtomicInteger attempts = new AtomicInteger();
            RuntimeException thrown = failure;

            assertThatThrownBy(() -> policy.call(() -> {
                attempts.incrementAndGet();
                throw thrown;
            })).isSameAs(thrown);

            assertThat(attempts).as("must not retry %s", failure.getClass().getSimpleName()).hasValue(1);
        }
    }

    @Test
    void when_call_withSuccessfulAction_then_runsOnce() throws Exception {
        AtomicInteger attempts = new AtomicInteger();

        assertThat(policy.call(() -> {
            attempts.incrementAndGet();
            return "done";
        })).isEqualTo("done");
        assertThat(attempts).hasValue(1);
    }

    // ------------------------------------------------- classification + backoff

    @Test
    void when_isTransient_then_matchesOnly429AndServerErrors() {
        assertThat(LlmRetryPolicy.isTransient(new HttpException(429, "x"))).isTrue();
        assertThat(LlmRetryPolicy.isTransient(new HttpException(500, "x"))).isTrue();
        assertThat(LlmRetryPolicy.isTransient(new HttpException(502, "x"))).isTrue();
        assertThat(LlmRetryPolicy.isTransient(new HttpException(408, "x"))).isTrue();

        assertThat(LlmRetryPolicy.isTransient(new HttpException(400, "x"))).isFalse();
        assertThat(LlmRetryPolicy.isTransient(new HttpException(401, "x"))).isFalse();
        assertThat(LlmRetryPolicy.isTransient(new HttpException(404, "x"))).isFalse();
        assertThat(LlmRetryPolicy.isTransient(new TimeoutException("x"))).isFalse();
        assertThat(LlmRetryPolicy.isTransient(new AuthenticationException("x"))).isFalse();
        assertThat(LlmRetryPolicy.isTransient(new ModelNotFoundException("x"))).isFalse();
        assertThat(LlmRetryPolicy.isTransient(new IllegalStateException("x"))).isFalse();
        assertThat(LlmRetryPolicy.isTransient(null)).isFalse();
    }

    @Test
    void when_isTransient_withWrapped429_then_matches() {
        assertThat(LlmRetryPolicy.isTransient(new IllegalStateException("wrapper", new HttpException(429, "x")))).isTrue();
    }

    @Test
    void when_isTransient_withSelfReferencingCause_then_terminates() {
        RuntimeException loop = new RuntimeException("loop") {
            @Override
            public synchronized Throwable getCause() {
                return this;
            }
        };

        assertThat(LlmRetryPolicy.isTransient(loop)).isFalse();
    }

    @Test
    void when_policyWithoutDelay_then_doesNotSleep() {
        assertThat(policy.backoffMillis(0)).isZero();
        assertThat(policy.backoffMillis(3)).isZero();
    }

    @Test
    void when_backoff_then_growsExponentially() {
        LlmRetryPolicy growing = new LlmRetryPolicy(3, 100L);

        assertThat(growing.backoffMillis(0)).isEqualTo(100L);
        assertThat(growing.backoffMillis(1)).isEqualTo(150L);
        assertThat(growing.backoffMillis(2)).isEqualTo(225L);
    }

    @Test
    void when_defaults_then_twoRetriesOver500ms() {
        LlmRetryPolicy defaults = LlmRetryPolicy.defaults();

        assertThat(defaults.maxRetries()).isEqualTo(2);
        assertThat(defaults.backoffMillis(0)).isBetween(500L, 600L);
        assertThat(defaults.backoffMillis(1)).isBetween(750L, 900L);
    }

    @Test
    void when_call_withZeroRetriesConfigured_then_singleAttempt() {
        LlmRetryPolicy noRetry = new LlmRetryPolicy(0, 0L);
        AtomicInteger attempts = new AtomicInteger();

        assertThatThrownBy(() -> noRetry.call(() -> {
            attempts.incrementAndGet();
            throw new HttpException(500, "server error");
        })).isInstanceOf(HttpException.class);

        assertThat(attempts).hasValue(1);
    }
}
