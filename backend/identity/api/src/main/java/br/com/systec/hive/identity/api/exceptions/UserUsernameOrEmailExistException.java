package br.com.systec.hive.identity.api.exceptions;

import org.springframework.http.HttpStatus;

import java.io.Serial;

public class UserUsernameOrEmailExistException extends UserException {
    @Serial
    private static final long serialVersionUID = 9016518893718040362L;

    public UserUsernameOrEmailExistException() {
        super("Email ou usuario já existe", HttpStatus.CONFLICT);
    }

    public UserUsernameOrEmailExistException(String message) {
        super(message, HttpStatus.CONFLICT);
    }
}
