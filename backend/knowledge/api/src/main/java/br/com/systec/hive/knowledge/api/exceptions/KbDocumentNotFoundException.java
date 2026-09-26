package br.com.systec.hive.knowledge.api.exceptions;

import br.com.systec.hive.commons.api.exceptions.ObjectNotFoundException;

import java.io.Serial;

public class KbDocumentNotFoundException extends ObjectNotFoundException {
    @Serial
    private static final long serialVersionUID = 1L;

    public KbDocumentNotFoundException() {
        super("Document not found");
    }
}
