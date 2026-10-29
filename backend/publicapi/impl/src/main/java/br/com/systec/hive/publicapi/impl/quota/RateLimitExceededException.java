package br.com.systec.hive.publicapi.impl.quota;

import java.io.Serial;

public class RateLimitExceededException extends QuotaExceededException {

    @Serial
    private static final long serialVersionUID = 1L;

    public RateLimitExceededException(String message, long retryAfterSeconds) {
        super(message, retryAfterSeconds);
    }
}