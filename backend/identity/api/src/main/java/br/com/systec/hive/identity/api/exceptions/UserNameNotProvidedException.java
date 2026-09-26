package br.com.systec.hive.identity.api.exceptions;

import org.springframework.http.HttpStatus;

import java.io.Serial;

public class UserNameNotProvidedException extends UserException {
    @Serial
    private static final long serialVersionUID = 1L;

    public UserNameNotProvidedException() {
        super("Nome não informado", HttpStatus.BAD_REQUEST);
    }

    public UserNameNotProvidedException(String message) {
        super(message, HttpStatus.BAD_REQUEST);
    }
}
