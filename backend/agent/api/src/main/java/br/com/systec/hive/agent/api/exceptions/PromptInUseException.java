package br.com.systec.hive.agent.api.exceptions;

import br.com.systec.hive.commons.api.exceptions.BaseException;
import org.springframework.http.HttpStatus;

import java.io.Serial;

public class PromptInUseException extends BaseException {
    @Serial
    private static final long serialVersionUID = 1L;

    public PromptInUseException(String promptId) {
        super("Prompt template " + promptId + " em uso por uma sessão de chat, não pode ser removido.", HttpStatus.CONFLICT);
    }

    public PromptInUseException() {
        super("Prompt em uso por uma sessão de chat, não pode ser removido.", HttpStatus.CONFLICT);
    }
}
