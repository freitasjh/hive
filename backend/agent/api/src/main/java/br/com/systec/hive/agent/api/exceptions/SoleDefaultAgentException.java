package br.com.systec.hive.agent.api.exceptions;

import br.com.systec.hive.commons.api.exceptions.BaseException;
import org.springframework.http.HttpStatus;

import java.io.Serial;

public class SoleDefaultAgentException extends BaseException {
    @Serial
    private static final long serialVersionUID = 1L;

    public SoleDefaultAgentException() {
        super("Defina outro agente como padrão antes de desativar/excluir o único padrão.", HttpStatus.CONFLICT);
    }
}
