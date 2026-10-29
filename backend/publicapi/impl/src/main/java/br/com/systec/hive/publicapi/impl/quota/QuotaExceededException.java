package br.com.systec.hive.publicapi.impl.quota;

import br.com.systec.hive.commons.api.exceptions.BaseException;
import org.springframework.http.HttpStatus;

import java.io.Serial;

public class QuotaExceededException extends BaseException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final long retryAfterSeconds;

    public QuotaExceededException(String message, long retryAfterSeconds) {
        super(message, HttpStatus.TOO_MANY_REQUESTS);
        this.retryAfterSeconds = retryAfterSeconds <= 0 ? 1 : retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }

    public long getRetryAfter() {
        return retryAfterSeconds;
    }
}