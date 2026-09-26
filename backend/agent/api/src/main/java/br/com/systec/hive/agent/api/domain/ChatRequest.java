package br.com.systec.hive.agent.api.domain;

import java.io.Serial;
import java.io.Serializable;
import java.util.UUID;

public class ChatRequest implements Serializable {
    @Serial
    private static final long serialVersionUID = -4350606251618187866L;
    private UUID sessionId;
    private Agent agent;
    private String model;
    private String message;
    private boolean stream;

    public UUID getSessionId() {
        return sessionId;
    }

    public void setSessionId(UUID sessionId) {
        this.sessionId = sessionId;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public Agent getAgent() {
        return agent;
    }

    public void setAgent(Agent agent) {
        this.agent = agent;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public boolean isStream() {
        return stream;
    }

    public void setStream(boolean stream) {
        this.stream = stream;
    }
}
