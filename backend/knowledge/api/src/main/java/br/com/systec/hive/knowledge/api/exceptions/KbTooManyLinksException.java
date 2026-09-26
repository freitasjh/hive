package br.com.systec.hive.knowledge.api.exceptions;

import br.com.systec.hive.commons.api.exceptions.BaseException;
import org.springframework.http.HttpStatus;

import java.io.Serial;

/**
 * RN-KB4 — no máximo 5 bases de conhecimento por agente.
 *
 * <p>A mensagem é pt-BR porque a UI a exibe diretamente: o operador precisa saber qual regra
 *_adjustar_ (remover um vínculo), não apenas que um limite existe. O frontend tem a mensagem
 * equivalente em {@code KB_MAX_LINKS_MESSAGE} e desabilita a seleção no mesmo número.
 */
public class KbTooManyLinksException extends BaseException {
    @Serial
    private static final long serialVersionUID = 1L;

    public KbTooManyLinksException() {
        this(5);
    }

    public KbTooManyLinksException(int max) {
        super("Máximo de " + max + " bases de conhecimento por agente. "
                + "Remova um vínculo antes de adicionar outro.", HttpStatus.BAD_REQUEST);
    }
}
