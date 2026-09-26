package br.com.systec.hive.agent.api.exceptions;

import br.com.systec.hive.commons.api.exceptions.BaseException;
import org.springframework.http.HttpStatus;

import java.io.Serial;

public class ChatRateLimitException extends BaseException {

    @Serial
    private static final long serialVersionUID = 1L;

    public ChatRateLimitException() {
        super("Limite de mensagens excedido. Tente novamente em instantes.",
                HttpStatus.TOO_MANY_REQUESTS);
    }
}
