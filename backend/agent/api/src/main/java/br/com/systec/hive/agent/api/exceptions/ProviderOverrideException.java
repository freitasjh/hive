package br.com.systec.hive.agent.api.exceptions;

import br.com.systec.hive.commons.api.exceptions.BaseException;
import org.springframework.http.HttpStatus;

import java.io.Serial;

/**
 * Override de provider/modelo incompleto: os dois campos andam juntos.
 *
 * <p>Um par pela metade não pode ser persistido na sessão — o próximo turno herdaria uma escolha
 * inválida para sempre. Por isso a recusa é 400 e acontece antes de qualquer escrita.
 */
public class ProviderOverrideException extends BaseException {

    @Serial
    private static final long serialVersionUID = 1L;

    public ProviderOverrideException() {
        super("Provider e modelo devem ser informados juntos; envie os dois ou nenhum "
                + "para usar o default do tenant.", HttpStatus.BAD_REQUEST);
    }
}
