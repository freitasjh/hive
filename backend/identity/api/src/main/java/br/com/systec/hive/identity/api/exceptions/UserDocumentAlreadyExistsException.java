package br.com.systec.hive.identity.api.exceptions;

import org.springframework.http.HttpStatus;

import java.io.Serial;

public class UserDocumentAlreadyExistsException extends UserException {
    @Serial
    private static final long serialVersionUID = 1L;

    public UserDocumentAlreadyExistsException() {
        super("Documento já cadastrado", HttpStatus.CONFLICT);
    }

    public UserDocumentAlreadyExistsException(String message) {
        super(message, HttpStatus.CONFLICT);
    }
}
