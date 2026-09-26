package br.com.systec.hive.identity.api.exceptions;

import br.com.systec.hive.commons.api.exceptions.ObjectNotFoundException;

import java.io.Serial;

public class TenantNotFoundException extends ObjectNotFoundException {
    @Serial
    private static final long serialVersionUID = 6294442345379717043L;

    public TenantNotFoundException() {
        super("Tenant não encontrado");
    }
}
