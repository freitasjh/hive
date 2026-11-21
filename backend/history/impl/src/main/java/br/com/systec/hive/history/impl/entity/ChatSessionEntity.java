package br.com.systec.hive.history.impl.entity;

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
    // V7: nullable de propósito. NULL = usar o default do tenant; o par só é gravado quando o
    // chamador escolheu explicitamente um provider/modelo para ESTA conversa.
    @Column(name = "provider", length = 32)
    private String provider;
    @Column(name = "model", length = 255)
    private String model;
    @Column(name = "title", nullable = false, length = 50)
    private String title;
    // length = 2000 reflete o DDL (VARCHAR(2000)). A anotação anterior dizia
    // columnDefinition = "TEXT", que não é o que o banco tem — e como `ddl-auto=none` o Hibernate
    // não valida, a divergência não produzia erro, produzia mentira. Se o produto precisar de
    // resumo maior, quem muda é o DDL (V4), não esta linha.
    @Column(name = "summary", length = 2000)
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

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
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
