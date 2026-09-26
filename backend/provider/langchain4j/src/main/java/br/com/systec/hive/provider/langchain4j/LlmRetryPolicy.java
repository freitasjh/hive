package br.com.systec.hive.provider.langchain4j;

import dev.langchain4j.exception.AuthenticationException;
import dev.langchain4j.exception.HttpException;
import dev.langchain4j.exception.InternalServerException;
import dev.langchain4j.exception.ModelNotFoundException;
import dev.langchain4j.exception.NonRetriableException;
import dev.langchain4j.exception.RateLimitException;
import dev.langchain4j.exception.TimeoutException;

import java.util.concurrent.Callable;

/**
 * Retry policy for LLM generation that distinguishes <em>transient</em> failures from
 * <em>expensive</em> ones.
 *
 * <p>Why this exists instead of the SDK default: langchain4j 1.19.0 retries <b>every</b>
 * {@link Exception} that is not a {@link NonRetriableException}
 * ({@code dev.langchain4j.internal.RetryUtils.RetryPolicy#withRetry}) — read timeouts included,
 * because {@link TimeoutException} is a {@code RetriableException}, not a non-retriable one. With a
 * 30 s read timeout and the SDK default of 2 retries, a long generation that never answers burned
 * 3 × 30 s ≈ 90 s before the user saw an error.
 *
 * <p>So the SDK retry is switched off at the builders ({@code maxRetries(0)}) and this policy takes
 * over: it retries only what is cheap and likely to succeed on a second attempt (429, 5xx) and
 * fails fast on timeouts, auth and configuration errors. Same backoff shape as the SDK default
 * (2 retries, 500 ms base, 1.5× growth, 20% jitter) so nothing regresses for 429/5xx.
 */
public final class LlmRetryPolicy {

    public static final int DEFAULT_MAX_RETRIES = 2;
    public static final long DEFAULT_BASE_DELAY_MILLIS = 500L;
    public static final double BACKOFF_EXPONENT = 1.5;
    public static final double JITTER_SCALE = 0.2;
    private static final int MAX_CAUSE_DEPTH = 10;

    private final int maxRetries;
    private final long baseDelayMillis;
    private final boolean jitterEnabled;
    private final java.util.Random random;

    public LlmRetryPolicy(int maxRetries, long baseDelayMillis) {
        this(maxRetries, baseDelayMillis, false, new java.util.Random());
    }

    private LlmRetryPolicy(int maxRetries, long baseDelayMillis, boolean jitterEnabled, java.util.Random random) {
        this.maxRetries = Math.max(0, maxRetries);
        this.baseDelayMillis = Math.max(0, baseDelayMillis);
        this.jitterEnabled = jitterEnabled;
        this.random = random;
    }

    /** Production default: 2 retries, 500 ms base delay, no jitter (single-node backend). */
    public static LlmRetryPolicy defaults() {
        return new LlmRetryPolicy(DEFAULT_MAX_RETRIES, DEFAULT_BASE_DELAY_MILLIS, true, new java.util.Random());
    }

    /** No waiting — used by tests and by the connection probe. */
    public static LlmRetryPolicy withoutDelay() {
        return new LlmRetryPolicy(DEFAULT_MAX_RETRIES, 0L, false, new java.util.Random());
    }

    public int maxRetries() {
        return maxRetries;
    }

    /**
     * Runs {@code action}, retrying only transient failures.
     *
     * @throws Exception the last failure, unwrapped, when it is not transient or retries run out
     */
    public <T> T call(Callable<T> action) throws Exception {
        int attempt = 0;
        while (true) {
            try {
                return action.call();
            } catch (Exception e) {
                if (!isTransient(e) || attempt >= maxRetries) {
                    throw e;
                }
                sleep(backoffMillis(attempt));
                attempt++;
            }
        }
    }

    long backoffMillis(int attempt) {
        double raw = baseDelayMillis * Math.pow(BACKOFF_EXPONENT, attempt);
        if (!jitterEnabled || raw <= 0) {
            return (long) raw;
        }
        int jitterBound = (int) (raw * JITTER_SCALE);
        if (jitterBound <= 0) {
            return (long) raw;
        }
        return (long) (raw + random.nextInt(jitterBound));
    }

    private void sleep(long millis) {
        if (millis <= 0) {
            return;
        }
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while backing off before an LLM retry", e);
        }
    }

    /**
     * Transient = the provider is overloaded or briefly broken, so a second attempt right away
     * has a real chance of succeeding.
     *
     * <p>Never transient: any timeout (the user already waited, and repeating multiplies the wait),
     * {@link NonRetriableException} and its subclasses (auth, unsupported feature, filtered
     * content), and {@link ModelNotFoundException} (repeating will not conjure the model).
     */
    public static boolean isTransient(Throwable t) {
        Throwable current = t;
        int depth = 0;
        while (current != null && depth < MAX_CAUSE_DEPTH) {
            if (current instanceof TimeoutException
                    || current instanceof java.net.http.HttpTimeoutException
                    || current instanceof java.net.SocketTimeoutException
                    || current instanceof java.util.concurrent.TimeoutException
                    || current instanceof NonRetriableException
                    || current instanceof ModelNotFoundException
                    || current instanceof AuthenticationException) {
                return false;
            }
            if (current instanceof RateLimitException || current instanceof InternalServerException) {
                return true;
            }
            if (current instanceof HttpException http) {
                int status = http.statusCode();
                if (status == 429 || status == 408 || status >= 500) {
                    return true;
                }
            }
            Throwable cause = current.getCause();
            current = cause == current ? null : cause;
            depth++;
        }
        return false;
    }
}
