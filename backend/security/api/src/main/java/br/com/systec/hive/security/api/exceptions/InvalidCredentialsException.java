package br.com.systec.hive.security.api.exceptions;

import org.springframework.http.HttpStatus;

import java.io.Serial;

public class InvalidCredentialsException extends SecurityException {
    @Serial
    private static final long serialVersionUID = 8421753096642017731L;

    public InvalidCredentialsException() {
        super("Credenciais inválidas", HttpStatus.UNAUTHORIZED);
    }

    public InvalidCredentialsException(Throwable cause) {
        super("Credenciais inválidas", cause, HttpStatus.UNAUTHORIZED);
    }
}
