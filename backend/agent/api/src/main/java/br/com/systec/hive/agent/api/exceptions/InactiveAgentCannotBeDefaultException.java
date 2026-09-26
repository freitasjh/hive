package br.com.systec.hive.agent.api.exceptions;

import br.com.systec.hive.commons.api.exceptions.BaseException;
import org.springframework.http.HttpStatus;

import java.io.Serial;

public class InactiveAgentCannotBeDefaultException extends BaseException {
    @Serial
    private static final long serialVersionUID = 1L;

    public InactiveAgentCannotBeDefaultException() {
        super("Agente inativo não pode ser padrão", HttpStatus.UNPROCESSABLE_ENTITY);
    }
}
