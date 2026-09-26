package br.com.systec.hive.security.api.exceptions;

import br.com.systec.hive.commons.api.exceptions.BaseException;

import java.io.Serial;

public class RefreshTokenUsedException extends BaseException {
    @Serial
    private static final long serialVersionUID = 8068516555815886436L;

    public RefreshTokenUsedException() {
        super("refresh.token.used");
    }
}