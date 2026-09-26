package br.com.systec.hive.identity.api.exceptions;

import br.com.systec.hive.commons.api.exceptions.ObjectNotFoundException;

import java.io.Serial;

public class UserNotFoundException extends ObjectNotFoundException {
    @Serial
    private static final long serialVersionUID = 8565663991235934392L;

    public UserNotFoundException() {
        super("Usuario não encontrado");
    }
}
