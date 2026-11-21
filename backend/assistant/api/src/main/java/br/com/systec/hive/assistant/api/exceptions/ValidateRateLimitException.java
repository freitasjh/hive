package br.com.systec.hive.assistant.api.exceptions;

import br.com.systec.hive.commons.api.exceptions.BaseException;
import org.springframework.http.HttpStatus;

import java.io.Serial;

public class ValidateRateLimitException extends BaseException {

    @Serial
    private static final long serialVersionUID = 1L;

    public ValidateRateLimitException() {
        super("Limite de validações excedido. Tente novamente em instantes.",
                HttpStatus.TOO_MANY_REQUESTS);
    }
}
