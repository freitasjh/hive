package br.com.systec.hive.commons.api.exceptions;

import java.io.Serial;

public class TenantNotFountException extends BaseException {
    @Serial
    private static final long serialVersionUID = -5917077530278513193L;

    public TenantNotFountException() {
        super("O tenant não foi informado", org.springframework.http.HttpStatus.BAD_REQUEST);
    }
}
