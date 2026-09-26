package br.com.systec.hive.knowledge.api.exceptions;

import br.com.systec.hive.commons.api.exceptions.BaseException;
import org.springframework.http.HttpStatus;

import java.io.Serial;

public class KbFileTooLargeException extends BaseException {
    @Serial
    private static final long serialVersionUID = 1L;

    public KbFileTooLargeException() {
        super("file exceeds 10MB", HttpStatus.PAYLOAD_TOO_LARGE);
    }
}
