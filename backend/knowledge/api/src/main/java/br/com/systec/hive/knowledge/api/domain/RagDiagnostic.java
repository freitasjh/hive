package br.com.systec.hive.knowledge.api.domain;

import java.util.List;
import java.util.UUID;

/**
 * One observation of a RAG attempt, produced at every level that can end in "no context".
 *
 * <p>Purpose: an operator must be able to grep the backend and see <em>where</em> the flow stopped.
 * A bare {@code return List.of()} is invisible; this record carries the reason, the tenant, the
 * linked bases, the counts and the latency.
 *
 * <p><b>Sensitive data rule:</b> {@code query} and chunk text must never be captured here. The
 * {@code cause} field holds an exception class name plus its message (transport/protocol failures
 * only), never user content.
 *
 * @param outcome         terminal state of the attempt
 * @param agentId         agent the retrieval serves, {@code null} for direct KB search
 * @param tenantId        tenant the retrieval was scoped to
 * @param kbIds           knowledge-base ids requested (not the query, not the content)
 * @param foundKbs        how many of those bases exist for the tenant
 * @param chunks          how many chunks cleared the similarity threshold
 * @param threshold       similarity threshold applied, {@code null} when not reached
 * @param elapsedMs       wall time of <b>the level that produced this observation</b>: the vector
 *                        search on a line from the retrieval service, the whole RAG resolution (the
 *                        link lookup included) on a line from a chat turn. The two are different
 *                        measurements of different scopes, not a disagreement about the same one —
 *                        everything that describes the retrieval itself ({@code outcome},
 *                        {@code kbIds}, {@code foundKbs}, {@code chunks}, {@code threshold},
 *                        {@code reason}, {@code cause}) must be identical across both.
 * @param systemBlockChars size of the context block injected in the prompt, {@code -1} when absent
 * @param cause           {@code SimpleName: message} of the failure, {@code null} when none
 * @param reason          human-readable reason, safe to log
 */
public record RagDiagnostic(
        RagOutcome outcome,
        UUID agentId,
        UUID tenantId,
        List<UUID> kbIds,
        int foundKbs,
        int chunks,
        Double threshold,
        long elapsedMs,
        int systemBlockChars,
        String cause,
        String reason) {

    public static final int NO_SYSTEM_BLOCK = -1;
    private static final int MAX_CAUSE_DEPTH = 10;

    public RagDiagnostic {
        kbIds = kbIds == null ? List.of() : List.copyOf(kbIds);
    }

    public static Builder builder(RagOutcome outcome, UUID agentId, UUID tenantId) {
        return new Builder(outcome, agentId, tenantId);
    }

    public RagDiagnostic withOutcome(RagOutcome newOutcome) {
        return new RagDiagnostic(newOutcome, agentId, tenantId, kbIds, foundKbs, chunks, threshold,
                elapsedMs, systemBlockChars, cause, reason);
    }

    public RagDiagnostic withElapsedMs(long millis) {
        return new RagDiagnostic(outcome, agentId, tenantId, kbIds, foundKbs, chunks, threshold,
                millis, systemBlockChars, cause, reason);
    }

    public RagDiagnostic withSystemBlock(int chars) {
        return new RagDiagnostic(outcome, agentId, tenantId, kbIds, foundKbs, chunks, threshold,
                elapsedMs, chars, cause, reason);
    }

    /** Thrown away on purpose: no stack trace, only the class and the closed cause code. */
    public RagDiagnostic withCause(Throwable t) {
        return withCause(causeOf(t));
    }

    public RagDiagnostic withCause(String causeText) {
        return new RagDiagnostic(outcome, agentId, tenantId, kbIds, foundKbs, chunks, threshold,
                elapsedMs, systemBlockChars, causeText, reason);
    }

    /**
     * Log-safe description of a failure: {@code RootSimpleName:causeCode}.
     *
     * <p><b>The raw {@code getMessage()} is never included.</b> An exception message is written by
     * whoever threw it, and application and driver exceptions routinely interpolate the value that
     * failed. Reproduced here before this method was hardened:
     * {@code IllegalStateException: embed failed for query=[PERGUNTA SIGILOSA] using
     * chunk=[salary of the CEO is 1M]} — straight into the log line. A log file cannot be
     * un-leaked, so the message is dropped.
     *
     * <p>What is kept is deliberately enough to act on: the root exception class plus one code from
     * the closed set of {@link #causeCode} — {@code timeout}, {@code connection_refused},
     * {@code unknown_host}, {@code socket_error}, {@code rate_limited}, {@code unauthorized},
     * {@code model_not_found}, {@code not_found}, {@code server_error}, {@code http_<status>},
     * {@code sql_error}, {@code cancelled}, {@code dimension_mismatch}, {@code unexpected}.
     *
     * <p>Accepted cost: the server-side detail of a driver error (e.g. {@code relation
     * "kb_embeddings_768" does not exist}) is no longer on this line. It stays reachable on the
     * server-side error log, which carries the stack trace and is not the same artefact.
     */
    public static String causeOf(Throwable t) {
        if (t == null) {
            return null;
        }
        return rootOf(t).getClass().getSimpleName() + ":" + causeCode(t);
    }

    /**
     * Classifies the failure from the whole cause chain, returning a value from a closed set. The
     * chain is walked because a timeout arrives wrapped twice by the time the caller sees it.
     */
    static String causeCode(Throwable t) {
        Throwable c = t;
        for (int depth = 0; c != null && depth < MAX_CAUSE_DEPTH; depth++, c = nextCause(c)) {
            if (isTimeout(c) || c instanceof dev.langchain4j.exception.TimeoutException) {
                return "timeout";
            }
            if (c instanceof java.util.concurrent.CancellationException) {
                return "cancelled";
            }
            if (c instanceof br.com.systec.hive.knowledge.api.exceptions.KbDimensionMismatchException) {
                return "dimension_mismatch";
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
            if (c instanceof java.sql.SQLException) {
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

    public static final class Builder {

        private final RagOutcome outcome;
        private final UUID agentId;
        private final UUID tenantId;
        private List<UUID> kbIds = List.of();
        private int foundKbs;
        private int chunks;
        private Double threshold;
        private long elapsedMs;
        private int systemBlockChars = NO_SYSTEM_BLOCK;
        private String cause;
        private String reason;

        private Builder(RagOutcome outcome, UUID agentId, UUID tenantId) {
            this.outcome = outcome;
            this.agentId = agentId;
            this.tenantId = tenantId;
        }

        public Builder kbIds(List<UUID> value) {
            this.kbIds = value;
            return this;
        }

        public Builder foundKbs(int value) {
            this.foundKbs = value;
            return this;
        }

        public Builder chunks(int value) {
            this.chunks = value;
            return this;
        }

        public Builder threshold(Double value) {
            this.threshold = value;
            return this;
        }

        public Builder elapsedMs(long value) {
            this.elapsedMs = value;
            return this;
        }

        public Builder systemBlockChars(int value) {
            this.systemBlockChars = value;
            return this;
        }

        public Builder cause(Throwable value) {
            this.cause = causeOf(value);
            return this;
        }

        /**
         * An already-classified cause, for a level that received the label rather than the
         * throwable. Named apart from {@link #cause(Throwable)} on purpose: this is the only way to
         * put a cause on a diagnostic <b>without</b> handing over a throwable, and the whole point of
         * {@link #causeOf} is that {@code getMessage()} never travels. A caller with an exception in
         * hand should pass it and let the classification happen here, once.
         */
        public Builder classifiedCause(String value) {
            this.cause = value;
            return this;
        }

        public Builder reason(String value) {
            this.reason = value;
            return this;
        }

        public RagDiagnostic build() {
            return new RagDiagnostic(outcome, agentId, tenantId, kbIds, foundKbs, chunks, threshold,
                    elapsedMs, systemBlockChars, cause, reason);
        }
    }
}
