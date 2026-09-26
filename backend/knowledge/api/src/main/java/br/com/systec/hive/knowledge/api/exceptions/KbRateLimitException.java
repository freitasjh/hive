package br.com.systec.hive.knowledge.api.exceptions;

import br.com.systec.hive.commons.api.exceptions.BaseException;
import org.springframework.http.HttpStatus;

import java.io.Serial;

public class KbRateLimitException extends BaseException {
    @Serial
    private static final long serialVersionUID = 1L;

    public KbRateLimitException() {
        super("ingestion rate limit exceeded for tenant", HttpStatus.TOO_MANY_REQUESTS);
    }
}
