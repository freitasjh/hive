package br.com.systec.hive.agent.api.exceptions;

import br.com.systec.hive.commons.api.exceptions.BaseException;
import org.springframework.http.HttpStatus;

import java.io.Serial;

/**
 * The {@code knowledge} module is not on the classpath, so the agent↔knowledge-base link cannot be
 * read or written.
 *
 * <p>Answering 200 here would be a lie the client acts on: the editor would show "vínculos salvos"
 * while nothing was persisted. 503 tells the operator the deployment is incomplete, which is the
 * situation that actually needs fixing.
 */
public class KnowledgeModuleUnavailableException extends BaseException {

    @Serial
    private static final long serialVersionUID = 1L;

    public KnowledgeModuleUnavailableException() {
        super("Módulo de bases de conhecimento indisponível nesta instalação.", HttpStatus.SERVICE_UNAVAILABLE);
    }
}
