package br.com.systec.hive.provider.api.exception;

import br.com.systec.hive.commons.api.exceptions.BaseException;
import org.springframework.http.HttpStatus;

import java.io.Serial;

public class DiscoveryFailedException extends BaseException {

    @Serial
    private static final long serialVersionUID = 1L;

    public DiscoveryFailedException(String provider) {
        super("Falha ao descobrir modelos do provedor " + provider + ". Tente atualizar novamente.",
                HttpStatus.UNPROCESSABLE_ENTITY);
    }

    public DiscoveryFailedException(String provider, Throwable cause) {
        super("Falha ao descobrir modelos do provedor " + provider + ". Tente atualizar novamente.",
                cause, HttpStatus.UNPROCESSABLE_ENTITY);
    }
}
