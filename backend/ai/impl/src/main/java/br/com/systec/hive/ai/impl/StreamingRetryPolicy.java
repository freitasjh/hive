package br.com.systec.hive.ai.impl;

import dev.langchain4j.exception.AuthenticationException;
import dev.langchain4j.exception.HttpException;
import dev.langchain4j.exception.InternalServerException;
import dev.langchain4j.exception.ModelNotFoundException;
import dev.langchain4j.exception.NonRetriableException;
import dev.langchain4j.exception.RateLimitException;
import dev.langchain4j.exception.TimeoutException;

import java.time.Duration;
import java.util.UUID;

/**
 * Retry rule for a streaming turn (ADR-001 D4/C1). Mirrors {@code LlmRetryPolicy} in
 * {@code provider/langchain4j} without importing it: {@code ai/**} may depend on {@code provider/api}
 * and the langchain4j library only, never on another project module.
 *
 * <p>A retry is legal only when no {@code Reasoning}/{@code Text} has reached the sink: after the
 * first token a second attempt would duplicate bytes on the wire and re-bill the turn.
 */
public final class StreamingRetryPolicy {

    public static final int MAX_RETRIES = 2;
    public static final Duration TURN_TIMEOUT = Duration.ofSeconds(120);

    private final int maxAttempts;

    private StreamingRetryPolicy(int maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    public static StreamingRetryPolicy defaults() {
        return new StreamingRetryPolicy(MAX_RETRIES + 1);
    }

    /** Kept for symmetry with the synchronous policy; no waiting is needed in the tests. */
    public static StreamingRetryPolicy withoutDelay() {
        return defaults();
    }

    public int maxAttempts() {
        return maxAttempts;
    }

    /** Immutable turn identity captured on the caller thread before {@code start()}. */
    public record Context(UUID sessionId, UUID turnId) { }

    public Context capture(UUID sessionId, UUID turnId) {
        return new Context(sessionId, turnId);
    }

    public boolean shouldRetry(Throwable failure, boolean emittedAnyToken, int attempt) {
        return !emittedAnyToken && attempt < maxAttempts && isTransient(failure);
    }

    /** Same classification as LlmRetryPolicy: 429/5xx only; timeouts and config errors fail fast. */
    public static boolean isTransient(Throwable t) {
        Throwable current = t;
        int depth = 0;
        while (current != null && depth < 10) {
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
            // AT-03: parity with LlmRetryPolicy.java:137 — 408 is transient too.
            if (current instanceof HttpException http
                    && (http.statusCode() == 429 || http.statusCode() == 408 || http.statusCode() >= 500)) {
                return true;
            }
            Throwable cause = current.getCause();
            current = cause == current ? null : cause;
            depth++;
        }
        return false;
    }
}
