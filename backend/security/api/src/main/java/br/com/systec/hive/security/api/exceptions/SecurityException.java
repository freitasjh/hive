package br.com.systec.hive.security.api.exceptions;

import br.com.systec.hive.commons.api.exceptions.BaseException;
import org.springframework.http.HttpStatus;

import java.io.Serial;

public class SecurityException extends BaseException {
    @Serial
    private static final long serialVersionUID = -2035670618368637537L;

    public SecurityException(Throwable e) {
        super("Ocorreu um erro ao tentar aplicar as seguranças",e);
    }

    public SecurityException(String message) {
        super(message);
    }

    public SecurityException(String message, Throwable cause) {
        super(message, cause);
    }

    public SecurityException(String message, HttpStatus httpStatus) {
        super(message, httpStatus);
    }

    public SecurityException(String message, Throwable cause, HttpStatus httpStatus) {
        super(message, cause, httpStatus);
    }
}
