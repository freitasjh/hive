package br.com.systec.hive.agent.api.exceptions;

import br.com.systec.hive.commons.api.exceptions.BaseException;
import org.springframework.http.HttpStatus;

import java.io.Serial;

public class ConfigMissingException extends BaseException {

    @Serial
    private static final long serialVersionUID = 1L;

    public ConfigMissingException(String provider) {
        super("Credencial do provedor " + provider + " não configurada para este tenant.",
                HttpStatus.UNPROCESSABLE_ENTITY);
    }
}
