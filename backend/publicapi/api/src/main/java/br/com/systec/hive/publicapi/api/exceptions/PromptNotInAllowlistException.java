package br.com.systec.hive.publicapi.api.exceptions;

import br.com.systec.hive.commons.api.exceptions.BaseException;
import org.springframework.http.HttpStatus;

public class PromptNotInAllowlistException extends BaseException {
    public PromptNotInAllowlistException() {
        super("prompt_not_in_allowlist", HttpStatus.FORBIDDEN);
    }
}