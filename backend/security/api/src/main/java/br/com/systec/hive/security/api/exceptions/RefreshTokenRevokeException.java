package br.com.systec.hive.security.api.exceptions;

import br.com.systec.hive.commons.api.exceptions.BaseException;

import java.io.Serial;

public class RefreshTokenRevokeException extends BaseException {
    @Serial
    private static final long serialVersionUID = 3817737026062430766L;

    public RefreshTokenRevokeException() {
        super("refresh.token.revoke");
    }
}