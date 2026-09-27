package br.com.systec.hive.agent.impl.entity;

import br.com.systec.hive.commons.impl.entity.AbstractEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.io.Serial;
import java.util.UUID;

@Entity
@Table(name = "chat_session")
public class ChatSessionEntity extends AbstractEntity {
    @Serial
    private static final long serialVersionUID = 8758504739293149129L;

    @Column(name = "user_id", nullable = false)
    private UUID userId;
    @Column(name = "prompt_template_id", nullable = false)
    private UUID promptTemplateId;
    @Column(name = "title", nullable = false, length = 50)
    private String title;
    @Column(name = "summary", columnDefinition = "TEXT")
    private String summary;

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
}
