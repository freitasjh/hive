package br.com.systec.hive.publicapi.api.exceptions;

import br.com.systec.hive.commons.api.exceptions.BaseException;
import org.springframework.http.HttpStatus;

/** Client inexistente ou fora do tenant da sessão — nunca revela qual dos dois casos ocorreu. */
public class PublicApiClientNotFoundException extends BaseException {

    public PublicApiClientNotFoundException() {
        super("Public API client not found", HttpStatus.NOT_FOUND);
    }
}
