package br.com.systec.hive.security.api.exceptions;

import br.com.systec.hive.commons.api.exceptions.BaseException;

import java.io.Serial;

public class TokenGenerationFailedException extends BaseException {
    @Serial
    private static final long serialVersionUID = 576350448189099852L;

    public TokenGenerationFailedException() {
        super("token.generation.failed");
    }

    public TokenGenerationFailedException(Throwable e) {
        super("token.generation.failed", e);
    }

    public TokenGenerationFailedException(String message, Throwable e) {
        super(message, e);
    }
}