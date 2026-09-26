package br.com.systec.hive.agent.api.exceptions;

import br.com.systec.hive.commons.api.exceptions.ObjectNotFoundException;

import java.io.Serial;
import java.util.UUID;

public class ChatAgentNotFoundException extends ObjectNotFoundException {

    @Serial
    private static final long serialVersionUID = 6618827956479779659L;

    private final UUID agentId;

    public ChatAgentNotFoundException(UUID agentId) {
        super("Chat agent not found: " + agentId);
        this.agentId = agentId;
    }

    public UUID getAgentId() {
        return agentId;
    }
}
