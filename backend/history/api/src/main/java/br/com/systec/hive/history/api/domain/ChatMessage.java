package br.com.systec.hive.history.api.domain;

import br.com.systec.hive.provider.api.ChatRole;
import br.com.systec.hive.commons.api.domain.AbstractDomain;

import java.io.Serial;
import java.io.Serializable;
import java.util.Map;
import java.util.UUID;

public class ChatMessage extends AbstractDomain implements Serializable {

    @Serial
    private static final long serialVersionUID = -1696933374616779500L;
    private UUID id;
    private UUID sessionId;
    private ChatRole role;
    private String message;
    private String content;
    private Map<String, Object> metadata;

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getSessionId() {
        return sessionId;
    }

    public void setSessionId(UUID sessionId) {
        this.sessionId = sessionId;
    }

    public ChatRole getRole() {
        return role;
    }

    public void setRole(ChatRole role) {
        this.role = role;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }
}
