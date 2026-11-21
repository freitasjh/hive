package br.com.systec.hive.assistant.api.domain;

import br.com.systec.hive.provider.api.AiProvider;
import br.com.systec.hive.provider.api.ChatRole;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

public class ChatResponse implements Serializable {

    @Serial
    private static final long serialVersionUID = 1340246067280529587L;
    private UUID sessionId;
    private String message;
    private ChatRole role;
    private Instant timestamp;
    /** Par efetivo que respondeu o turno — pode vir do tenant ou da escolha da sessão. */
    private AiProvider provider;
    private String model;

    public UUID getSessionId() {
        return sessionId;
    }

    public void setSessionId(UUID sessionId) {
        this.sessionId = sessionId;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public ChatRole getRole() {
        return role;
    }

    public void setRole(ChatRole role) {
        this.role = role;
    }

    public AiProvider getProvider() {
        return provider;
    }

    public void setProvider(AiProvider provider) {
        this.provider = provider;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }
}
