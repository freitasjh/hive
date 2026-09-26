package br.com.systec.hive.agent.api.exceptions;

import br.com.systec.hive.commons.api.exceptions.ObjectNotFoundException;

import java.io.Serial;

public class PromptTemplateNotFoundException extends ObjectNotFoundException {
    @Serial
    private static final long serialVersionUID = 6618827956479779657L;

    public PromptTemplateNotFoundException() {
        super("PromptTemplate not found");
    }
}
