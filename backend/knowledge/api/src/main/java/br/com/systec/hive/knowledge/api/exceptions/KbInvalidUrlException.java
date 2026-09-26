package br.com.systec.hive.knowledge.api.exceptions;

import br.com.systec.hive.commons.api.exceptions.BaseException;
import org.springframework.http.HttpStatus;

import java.io.Serial;

public class KbInvalidUrlException extends BaseException {
    @Serial
    private static final long serialVersionUID = 1L;

    public KbInvalidUrlException(String reason) {
        super("Invalid URL: " + reason, HttpStatus.BAD_REQUEST);
    }
}
