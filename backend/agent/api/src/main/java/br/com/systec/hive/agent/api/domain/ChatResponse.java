package br.com.systec.hive.agent.api.domain;

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

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }
}
