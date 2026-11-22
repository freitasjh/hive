package br.com.systec.hive.publicapi.api.exceptions;

import br.com.systec.hive.commons.api.exceptions.BaseException;
import org.springframework.http.HttpStatus;

/** Violação de UNIQUE(tenant_id,label) — ADR-003/ADM02. */
public class DuplicateClientLabelException extends BaseException {

    public DuplicateClientLabelException(String label) {
        super("Client label already exists in tenant: " + label, HttpStatus.CONFLICT);
    }
}
