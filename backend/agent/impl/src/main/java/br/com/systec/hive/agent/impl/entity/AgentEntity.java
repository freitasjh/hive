package br.com.systec.hive.agent.impl.entity;

import br.com.systec.hive.provider.api.AiProvider;
import br.com.systec.hive.commons.impl.entity.AbstractEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.io.Serial;
import java.util.Map;

@Entity
@Table(name = "agent")
public class AgentEntity extends AbstractEntity {
    @Serial
    private static final long serialVersionUID = -228186208618897674L;

    @Column(name = "name")
    private String name;
    @Column(name = "description")
    private String description;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "prompt_template_id")
    private PromptTemplateEntity promptTemplate;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "prompt_template_metadata", columnDefinition = "jsonb")
    private Map<String, Object> promptTemplateMetadata;
    @Column(name = "llm_model")
    private String model;
    @Enumerated(EnumType.STRING)
    @Column(name = "provider_type")
    private AiProvider provider;
    @Column(name = "temperature")
    private Double temperature;
    @Column(name = "top_p")
    private Double topP;
    @Column(name = "max_tokens")
    private Integer maxTokens;
    @Column(name = "avatar_url", length = 2048)
    private String avatarUrl;
    @Column(name = "system_prompt", columnDefinition = "TEXT")
    private String systemPrompt;
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "tools", columnDefinition = "text[]")
    private java.util.List<String> tools;
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "knowledge_bases", columnDefinition = "text[]")
    private java.util.List<String> knowledgeBases;
    @Column(name = "is_default", nullable = false)
    private boolean isDefault = false;
    @Column(name = "active")
    private boolean active = true;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public PromptTemplateEntity getPromptTemplate() {
        return promptTemplate;
    }

    public void setPromptTemplate(PromptTemplateEntity promptTemplate) {
        this.promptTemplate = promptTemplate;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public AiProvider getProvider() {
        return provider;
    }

    public void setProvider(AiProvider provider) {
        this.provider = provider;
    }

    public Double getTemperature() {
        return temperature;
    }

    public void setTemperature(Double temperature) {
        this.temperature = temperature;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public Double getTopP() {
        return topP;
    }

    public void setTopP(Double topP) {
        this.topP = topP;
    }

    public Integer getMaxTokens() {
        return maxTokens;
    }

    public void setMaxTokens(Integer maxTokens) {
        this.maxTokens = maxTokens;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }

    public String getSystemPrompt() {
        return systemPrompt;
    }

    public void setSystemPrompt(String systemPrompt) {
        this.systemPrompt = systemPrompt;
    }

    public java.util.List<String> getTools() {
        return tools;
    }

    public void setTools(java.util.List<String> tools) {
        this.tools = tools;
    }

    public java.util.List<String> getKnowledgeBases() {
        return knowledgeBases;
    }

    public void setKnowledgeBases(java.util.List<String> knowledgeBases) {
        this.knowledgeBases = knowledgeBases;
    }

    public boolean isDefault() {
        return isDefault;
    }

    public void setDefault(boolean isDefault) {
        this.isDefault = isDefault;
    }

    public boolean getIsDefault() {
        return isDefault;
    }

    public void setIsDefault(boolean isDefault) {
        this.isDefault = isDefault;
    }
}
