package br.com.systec.hive.assistant.api.exceptions;

import br.com.systec.hive.commons.api.exceptions.ObjectNotFoundException;

import java.io.Serial;
import java.util.UUID;

/**
 * The prompt template the session was asked to use does not exist for this tenant.
 *
 * <p>The id-carrying constructor exists because the no-arg one could not answer the question the
 * caller actually has, which is "which one did you look for?". A 404 that names the id the client
 * sent is still uniform — it reveals nothing about other tenants' rows, which is what
 * {@code ObjectNotFoundException} exists to guarantee.
 */
public class PromptTemplateNotFoundException extends ObjectNotFoundException {

    @Serial
    private static final long serialVersionUID = 6618827956479779657L;

    private final UUID promptTemplateId;

    public PromptTemplateNotFoundException() {
        super("PromptTemplate not found");
        this.promptTemplateId = null;
    }

    public PromptTemplateNotFoundException(UUID promptTemplateId) {
        super("Prompt template not found: " + promptTemplateId);
        this.promptTemplateId = promptTemplateId;
    }

    public UUID getPromptTemplateId() {
        return promptTemplateId;
    }
}
