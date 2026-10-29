package br.com.systec.hive.publicapi.api.exceptions;

import br.com.systec.hive.commons.api.exceptions.BaseException;
import org.springframework.http.HttpStatus;

public class PromptNotFoundException extends BaseException {
    public PromptNotFoundException() {
        super("prompt_not_found", HttpStatus.NOT_FOUND);
    }
}