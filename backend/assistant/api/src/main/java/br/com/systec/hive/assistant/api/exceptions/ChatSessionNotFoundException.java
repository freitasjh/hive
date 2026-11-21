package br.com.systec.hive.assistant.api.exceptions;

import br.com.systec.hive.commons.api.exceptions.ObjectNotFoundException;

import java.io.Serial;
import java.util.UUID;

public class ChatSessionNotFoundException extends ObjectNotFoundException {

    @Serial
    private static final long serialVersionUID = 6618827956479779658L;

    private final UUID sessionId;

    public ChatSessionNotFoundException(UUID sessionId) {
        super("ChatSession not found: " + sessionId);
        this.sessionId = sessionId;
    }

    public UUID getSessionId() {
        return sessionId;
    }
}
