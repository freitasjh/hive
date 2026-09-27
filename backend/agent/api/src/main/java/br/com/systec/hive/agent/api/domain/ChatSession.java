package br.com.systec.hive.agent.api.domain;

import br.com.systec.hive.commons.api.domain.AbstractDomain;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.UUID;

public class ChatSession extends AbstractDomain implements Serializable {

    @Serial
    private static final long serialVersionUID = 8758504739293149130L;

    private UUID userId;
    private UUID promptTemplateId;
    private String title;
    private String summary;
    private LocalDateTime updateAt;

    public ChatSession() {
    }

    public ChatSession(UUID userId, UUID promptTemplateId, UUID tenantId, String title) {
        this.userId = userId;
        this.promptTemplateId = promptTemplateId;
        this.tenantId = tenantId;
        this.title = title;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public UUID getPromptTemplateId() {
        return promptTemplateId;
    }

    public void setPromptTemplateId(UUID promptTemplateId) {
        this.promptTemplateId = promptTemplateId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public LocalDateTime getUpdateAt() {
        return updateAt;
    }

    public void setUpdateAt(LocalDateTime updateAt) {
        this.updateAt = updateAt;
    }
}
