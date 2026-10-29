package br.com.systec.hive.publicapi.api.exceptions;

import br.com.systec.hive.commons.api.exceptions.BaseException;

import java.io.Serial;

public class InvalidTenantIdException extends BaseException {

    @Serial
    private static final long serialVersionUID = 1L;

    public InvalidTenantIdException(String value) {
        super("Invalid tenant id: " + value);
    }

    public InvalidTenantIdException(String value, Throwable cause) {
        super("Invalid tenant id: " + value, cause);
    }
}