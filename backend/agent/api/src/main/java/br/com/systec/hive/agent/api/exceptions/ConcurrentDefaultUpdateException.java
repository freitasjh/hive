package br.com.systec.hive.agent.api.exceptions;

import br.com.systec.hive.commons.api.exceptions.BaseException;
import org.springframework.http.HttpStatus;

import java.io.Serial;

public class ConcurrentDefaultUpdateException extends BaseException {
    @Serial
    private static final long serialVersionUID = 1L;

    public ConcurrentDefaultUpdateException(Throwable cause) {
        super("Conflito ao definir padrão concorrente", cause, HttpStatus.CONFLICT);
    }

    public ConcurrentDefaultUpdateException() {
        super("Conflito ao definir padrão concorrente", HttpStatus.CONFLICT);
    }
}
