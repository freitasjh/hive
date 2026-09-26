package br.com.systec.hive.knowledge.api.exceptions;

import br.com.systec.hive.commons.api.exceptions.BaseException;
import org.springframework.http.HttpStatus;

import java.io.Serial;

/**
 * Linked knowledge bases do not share an embedding dimension, so one search cannot serve them.
 *
 * <p>Carries {@link #foundKbs()} because this is the one retrieval outcome that is <b>thrown</b>
 * instead of returned: the retrieval service records its line and then throws, so the result object
 * never reaches the caller. A caller that catches this has to build its own line, and it cannot know
 * how many bases were found unless the exception says — which is how a turn came to report
 * {@code foundKbs=0} for a mismatch between two bases that were both found, sending the reader to
 * the link table instead of to the dimension.
 */
public class KbDimensionMismatchException extends BaseException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final int foundKbs;

    public KbDimensionMismatchException(int foundKbs) {
        super("Linked knowledge bases use different embedding dimensions", HttpStatus.BAD_REQUEST);
        this.foundKbs = foundKbs;
    }

    /** How many of the requested bases exist for the tenant, and therefore were checked. */
    public int foundKbs() {
        return foundKbs;
    }
}
