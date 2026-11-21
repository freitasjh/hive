package br.com.systec.hive.assistant.api.exceptions;

import br.com.systec.hive.commons.api.exceptions.BaseException;
import org.springframework.http.HttpStatus;

import java.io.Serial;

public class InvalidTemplateVariableException extends BaseException {
    @Serial
    private static final long serialVersionUID = 1L;

    public InvalidTemplateVariableException(String token) {
        super("INVALID_TEMPLATE_VARIABLE: token '" + token + "' não casa com regex \\{\\{\\s*[a-zA-Z_][a-zA-Z0-9_]*\\s*\\}\\}", HttpStatus.BAD_REQUEST);
    }

    public InvalidTemplateVariableException(String token, int position) {
        super("INVALID_TEMPLATE_VARIABLE: token '" + token + "' na posição " + position + " não casa com regex \\{\\{\\s*[a-zA-Z_][a-zA-Z0-9_]*\\s*\\}\\}", HttpStatus.BAD_REQUEST);
    }
}
