package br.com.systec.hive.agent.api.domain;

import br.com.systec.hive.provider.api.AiProvider;

import java.io.Serial;
import java.io.Serializable;
import java.util.UUID;

public class ChatRequest implements Serializable {
    @Serial
    private static final long serialVersionUID = -4350606251618187866L;
    private UUID sessionId;
    private UUID promptTemplateId;
    /** Override opcional da conversa; {@code null} = manter o default do tenant. */
    private AiProvider provider;
    private String model;
    private String message;
    private boolean stream;

    public UUID getSessionId() {
        return sessionId;
    }

    public void setSessionId(UUID sessionId) {
        this.sessionId = sessionId;
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

    public UUID getPromptTemplateId() {
        return promptTemplateId;
    }

    public void setPromptTemplateId(UUID promptTemplateId) {
        this.promptTemplateId = promptTemplateId;
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
