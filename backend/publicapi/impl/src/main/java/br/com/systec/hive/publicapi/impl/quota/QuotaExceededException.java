package br.com.systec.hive.publicapi.impl.quota;

import br.com.systec.hive.commons.api.exceptions.BaseException;
import org.springframework.http.HttpStatus;

import java.io.Serial;

public class QuotaExceededException extends BaseException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final long retryAfterSeconds;
    private final QuotaDecision decision;

    /** Construtor legado (token endpoint, sem agregação de limit/remaining). */
    public QuotaExceededException(String message, long retryAfterSeconds) {
        this(message, new QuotaDecision(0, 0, retryAfterSeconds <= 0 ? 1 : retryAfterSeconds, 0, false));
    }

    /** Construtor com a decisão medida pelo guard — usado para os headers do chat. */
    public QuotaExceededException(String message, QuotaDecision decision) {
        super(message, HttpStatus.TOO_MANY_REQUESTS);
        this.decision = decision;
        this.retryAfterSeconds = decision.retryAfterSeconds() <= 0 ? 1 : decision.retryAfterSeconds();
    }

    public QuotaDecision getDecision() { return decision; }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }

    public long getRetryAfter() {
        return retryAfterSeconds;
    }
}