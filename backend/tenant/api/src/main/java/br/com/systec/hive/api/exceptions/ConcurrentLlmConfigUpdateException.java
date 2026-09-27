package br.com.systec.hive.api.exceptions;

import br.com.systec.hive.commons.api.exceptions.BaseException;
import org.springframework.http.HttpStatus;

import java.io.Serial;

/**
 * Two upserts for the same tenant collided on the {@code UNIQUE} key.
 *
 * <p>409, and the same wording the old default-agent path used for its own unique index: the
 * request was valid, the state moved underneath it, and retrying resolves it. Naming the tenant
 * rather than the constraint keeps the schema out of the response (OWASP A05).
 */
public class ConcurrentLlmConfigUpdateException extends BaseException {

    @Serial
    private static final long serialVersionUID = 1L;

    public ConcurrentLlmConfigUpdateException(Throwable cause) {
        super("A configuração de LLM deste tenant foi alterada por outra requisição. Tente de novo.",
                HttpStatus.CONFLICT);
        initCause(cause);
    }
}
