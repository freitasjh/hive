package br.com.systec.hive.provider.api.exception;

import br.com.systec.hive.commons.api.exceptions.BaseException;
import org.springframework.http.HttpStatus;

import java.io.Serial;

public class ModelNotSupportedForProviderException extends BaseException {

    @Serial
    private static final long serialVersionUID = 1L;

    public ModelNotSupportedForProviderException(String model, String provider) {
        super("Modelo '" + model + "' não suportado para o provedor " + provider + ".",
                HttpStatus.BAD_REQUEST);
    }
}
