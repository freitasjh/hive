package br.com.systec.hive.ai.impl;

import java.sql.SQLException;
import java.util.concurrent.CancellationException;

/**
 * Closed-set labels for a failed streaming turn (security F1). Mirrors the labels of
 * {@code assistant/impl FailureClassifier} without importing it — {@code ai/**} is a leaf.
 *
 * <p>Only exception types and numeric HTTP statuses are inspected. The raw exception message and
 * stack trace are deliberately never read: a driver's message can interpolate the very thinking text
 * or credential this path must not leak.
 */
final class StreamFailureCodes {

    private static final int MAX_CAUSE_DEPTH = 10;

    private StreamFailureCodes() {
    }

    static String of(Throwable failure) {
        Throwable current = failure;
        for (int depth = 0; current != null && depth < MAX_CAUSE_DEPTH; depth++) {
            if (isTimeout(current) || current instanceof dev.langchain4j.exception.TimeoutException) {
                return "timeout";
            }
            if (current instanceof CancellationException) {
                return "cancelled";
            }
            if (current instanceof dev.langchain4j.exception.RateLimitException) {
                return "rate_limited";
            }
            if (current instanceof dev.langchain4j.exception.AuthenticationException) {
                return "unauthorized";
            }
            if (current instanceof dev.langchain4j.exception.ModelNotFoundException) {
                return "model_not_found";
            }
            if (current instanceof dev.langchain4j.exception.InternalServerException) {
                return "server_error";
            }
            if (current instanceof dev.langchain4j.exception.HttpException http) {
                int status = http.statusCode();
                if (status == 429) {
                    return "rate_limited";
                }
                if (status == 401 || status == 403) {
                    return "unauthorized";
                }
                if (status == 404) {
                    return "not_found";
                }
                if (status >= 500) {
                    return "server_error";
                }
                return "http_" + status;
            }
            if (current instanceof java.net.ConnectException) {
                return "connection_refused";
            }
            if (current instanceof java.net.UnknownHostException) {
                return "unknown_host";
            }
            if (current instanceof java.net.SocketException) {
                return "socket_error";
            }
            if (current instanceof SQLException) {
                return "sql_error";
            }
            Throwable cause = current.getCause();
            current = cause == current ? null : cause;
        }
        return "unexpected";
    }

    private static boolean isTimeout(Throwable t) {
        return t instanceof java.net.http.HttpTimeoutException
                || t instanceof java.net.SocketTimeoutException
                || t instanceof java.util.concurrent.TimeoutException;
    }
}
