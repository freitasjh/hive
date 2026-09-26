package br.com.systec.hive.agent.api.exceptions;

import br.com.systec.hive.commons.api.exceptions.BaseException;
import org.springframework.http.HttpStatus;

import java.io.Serial;

public class ModelUnavailableException extends BaseException {

    @Serial
    private static final long serialVersionUID = 1L;

    public ModelUnavailableException(String model, String provider) {
        super("Modelo " + model + " indisponível no provedor " + provider
                + ". Escolha outro modelo ou atualize a lista.",
                HttpStatus.UNPROCESSABLE_ENTITY);
    }
}
