package br.com.systec.hive.publicapi.api.exceptions;

import br.com.systec.hive.commons.api.exceptions.BaseException;
import org.springframework.http.HttpStatus;

public class UnknownModelException extends BaseException {
    public UnknownModelException() {
        super("unknown_model", HttpStatus.BAD_REQUEST);
    }
}