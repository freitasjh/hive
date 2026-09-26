package br.com.systec.hive.knowledge.api.exceptions;

import br.com.systec.hive.commons.api.exceptions.BaseException;
import org.springframework.http.HttpStatus;

import java.io.Serial;

public class KbInUseException extends BaseException {
    @Serial
    private static final long serialVersionUID = 1L;

    public KbInUseException(String agents) {
        super("Knowledge base in use by agents: " + agents, HttpStatus.CONFLICT);
    }
}
