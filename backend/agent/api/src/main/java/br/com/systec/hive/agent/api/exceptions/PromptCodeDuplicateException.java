package br.com.systec.hive.agent.api.exceptions;

import br.com.systec.hive.commons.api.exceptions.BaseException;
import org.springframework.http.HttpStatus;

import java.io.Serial;

public class PromptCodeDuplicateException extends BaseException {
    @Serial
    private static final long serialVersionUID = 1L;

    public PromptCodeDuplicateException(String code) {
        super("Prompt code '" + code + "' já existe no tenant", HttpStatus.CONFLICT);
    }
}
