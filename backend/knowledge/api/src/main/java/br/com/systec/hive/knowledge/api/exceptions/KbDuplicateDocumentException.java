package br.com.systec.hive.knowledge.api.exceptions;

import br.com.systec.hive.commons.api.exceptions.BaseException;
import org.springframework.http.HttpStatus;

import java.io.Serial;
import java.util.UUID;

public class KbDuplicateDocumentException extends BaseException {
    @Serial
    private static final long serialVersionUID = 1L;

    public KbDuplicateDocumentException(UUID kbId) {
        super("Identical content already ingested in another knowledge base (kbId=" + kbId + ")", HttpStatus.CONFLICT);
    }
}
