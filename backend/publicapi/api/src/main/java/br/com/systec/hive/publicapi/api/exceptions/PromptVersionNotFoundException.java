package br.com.systec.hive.publicapi.api.exceptions;

import br.com.systec.hive.commons.api.exceptions.BaseException;
import org.springframework.http.HttpStatus;

public class PromptVersionNotFoundException extends BaseException {
    public PromptVersionNotFoundException() {
        super("prompt_version_not_found", HttpStatus.CONFLICT);
    }
}