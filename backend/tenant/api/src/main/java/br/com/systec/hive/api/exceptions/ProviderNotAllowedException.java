package br.com.systec.hive.api.exceptions;

import br.com.systec.hive.commons.api.exceptions.BaseException;
import org.springframework.http.HttpStatus;

import java.io.Serial;

/**
 * The tenant named a provider that is not on the allowlist.
 *
 * <p>Reachable only by a client that skipped the allowlist the UI enforces, which is exactly why it
 * exists as its own error rather than as a field-validation failure: the answer is a 422 that names
 * the offending value, and a 400 that says "invalid enum" would not.
 */
public class ProviderNotAllowedException extends BaseException {

    @Serial
    private static final long serialVersionUID = 1L;

    public ProviderNotAllowedException(String provider) {
        super("Provider " + provider + " não aceito. Aceitos: OPENAI, GEMINI, ANTHROPIC, "
                + "OLLAMA, OPENAI_COMPATIBLE, OPENCODE_GO, OPENCODE_ZEN.", HttpStatus.UNPROCESSABLE_ENTITY);
    }
}
