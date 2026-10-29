package br.com.systec.hive.publicapi.api.exceptions;

import br.com.systec.hive.commons.api.exceptions.BaseException;
import org.springframework.http.HttpStatus;

public class PromptNotActiveException extends BaseException {
    public PromptNotActiveException() {
        super("prompt_not_active", HttpStatus.CONFLICT);
    }
}