package br.com.systec.hive.identity.api.exceptions;

import org.springframework.http.HttpStatus;

import java.io.Serial;

public class UserDocumentNotProvidedException extends UserException {
    @Serial
    private static final long serialVersionUID = 1L;

    public UserDocumentNotProvidedException() {
        super("Documento não informado", HttpStatus.BAD_REQUEST);
    }

    public UserDocumentNotProvidedException(String message) {
        super(message, HttpStatus.BAD_REQUEST);
    }
}
