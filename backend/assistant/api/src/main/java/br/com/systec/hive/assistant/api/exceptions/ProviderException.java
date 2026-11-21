package br.com.systec.hive.assistant.api.exceptions;

import br.com.systec.hive.commons.api.exceptions.BaseException;
import org.springframework.http.HttpStatus;

import java.io.Serial;

public class ProviderException extends BaseException {

    @Serial
    private static final long serialVersionUID = 1L;

    public ProviderException(String message) {
        super(message, HttpStatus.BAD_GATEWAY);
    }

    public ProviderException(String message, Throwable cause) {
        super(message, cause, HttpStatus.BAD_GATEWAY);
    }
}
