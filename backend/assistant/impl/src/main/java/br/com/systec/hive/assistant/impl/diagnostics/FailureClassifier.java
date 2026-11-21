package br.com.systec.hive.assistant.impl.diagnostics;

import java.sql.SQLException;
import java.util.concurrent.CancellationException;

/**
 * Turns a failed call into a log-safe label: {@code RootSimpleName:code}.
 *
 * <p>It lives in {@code impl} because the chat turn is its only caller: {@code TurnOutcomeMapper}
 * hands a provider failure to {@link #causeOf} to label the {@code failure} field of the turn's trace, and
 * nothing else in the product asks this question. It was born as a method on a knowledge-base
 * diagnostic record, because that was the only caller that needed to say "why did this retrieval
 * stop"; the chat turn's own trace needs the same sentence about a completely different failure. It
 * is a collaborator of the turn, so it sits in the module that owns the turn.
 *
 * <h3>Why it classifies instead of quoting</h3>
 *
 * <p><b>The raw {@code getMessage()} never travels.</b> An exception message is written by whoever
 * threw it, and application and driver exceptions routinely interpolate the value that failed. This
 * project shipped that bug once: an
 * {@code IllegalStateException: embed failed for query=[PERGUNTA SIGILOSA] using
 * chunk=[salary of the CEO is 1M]} — straight into the log line. A log file cannot be un-leaked, so
 * the message is dropped.
 *
 * <p>What is kept is deliberately enough to act on: the root exception class plus one code from the
 * closed set of {@link #causeCode} — {@code timeout}, {@code connection_refused}, {@code unknown_host},
 * {@code socket_error}, {@code rate_limited}, {@code unauthorized}, {@code model_not_found},
 * {@code not_found}, {@code server_error}, {@code http_<status>}, {@code sql_error}, {@code cancelled},
 * {@code unexpected}.
 *
 * <p>Accepted cost: the server-side detail of a driver error (e.g. {@code relation "agent" does not
 * exist}) is no longer on this line. It stays reachable on the server-side error log, which carries
 * the stack trace and is not the same artefact.
 */
public final class FailureClassifier {

    private static final int MAX_CAUSE_DEPTH = 10;

    private FailureClassifier() {
    }

    public static String causeOf(Throwable t) {
        if (t == null) {
            return null;
        }
        return rootOf(t).getClass().getSimpleName() + ":" + causeCode(t);
    }

    /**
     * Classifies the failure from the whole cause chain, returning a value from a closed set. The
     * chain is walked because a timeout arrives wrapped twice by the time the caller sees it, and
     * bounded because a cycle is a shape a throwable can actually have.
     */
    static String causeCode(Throwable t) {
        Throwable c = t;
        for (int depth = 0; c != null && depth < MAX_CAUSE_DEPTH; depth++, c = nextCause(c)) {
            if (isTimeout(c) || c instanceof dev.langchain4j.exception.TimeoutException) {
                return "timeout";
            }
            if (c instanceof CancellationException) {
                return "cancelled";
            }
            if (c instanceof dev.langchain4j.exception.RateLimitException) {
                return "rate_limited";
            }
            if (c instanceof dev.langchain4j.exception.AuthenticationException) {
                return "unauthorized";
            }
            if (c instanceof dev.langchain4j.exception.ModelNotFoundException) {
                return "model_not_found";
            }
            if (c instanceof dev.langchain4j.exception.InternalServerException) {
                return "server_error";
            }
            if (c instanceof dev.langchain4j.exception.HttpException http) {
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
            if (c instanceof java.net.ConnectException) {
                return "connection_refused";
            }
            if (c instanceof java.net.UnknownHostException) {
                return "unknown_host";
            }
            if (c instanceof java.net.SocketException) {
                return "socket_error";
            }
            if (c instanceof SQLException) {
                return "sql_error";
            }
        }
        return "unexpected";
    }

    private static boolean isTimeout(Throwable t) {
        return t instanceof java.net.http.HttpTimeoutException
                || t instanceof java.net.SocketTimeoutException
                || t instanceof java.util.concurrent.TimeoutException;
    }

    /** Deepest cause, cycle-safe. The class name is the only part of the throwable that is kept. */
    private static Throwable rootOf(Throwable t) {
        Throwable root = t;
        int depth = 0;
        while (depth < MAX_CAUSE_DEPTH) {
            Throwable cause = root.getCause();
            if (cause == null || cause == root) {
                break;
            }
            root = cause;
            depth++;
        }
        return root;
    }

    private static Throwable nextCause(Throwable t) {
        Throwable cause = t.getCause();
        return cause == t ? null : cause;
    }
}
