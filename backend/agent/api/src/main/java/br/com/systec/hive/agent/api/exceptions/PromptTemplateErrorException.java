package br.com.systec.hive.agent.api.exceptions;

import br.com.systec.hive.commons.api.exceptions.BaseException;

import java.io.Serial;

public class PromptTemplateErrorException extends BaseException {
    @Serial
    private static final long serialVersionUID = 4252047749885910060L;

    public PromptTemplateErrorException(String message) {
        super(message);
    }

    public PromptTemplateErrorException(String message, Throwable cause) {
        super(message, cause);
    }
}
