package br.com.systec.hive.agent.api.exceptions;

import br.com.systec.hive.commons.api.exceptions.BaseException;
import org.springframework.http.HttpStatus;

import java.io.Serial;

public class DefaultAgentNotFoundException extends BaseException {
    @Serial
    private static final long serialVersionUID = 1L;

    private final boolean isCommand;

    public DefaultAgentNotFoundException(boolean isCommand) {
        super("Nenhum agente padrão configurado", isCommand ? HttpStatus.UNPROCESSABLE_ENTITY : HttpStatus.NOT_FOUND);
        this.isCommand = isCommand;
    }

    public DefaultAgentNotFoundException() {
        this(true);
    }

    public boolean isCommand() {
        return isCommand;
    }
}
