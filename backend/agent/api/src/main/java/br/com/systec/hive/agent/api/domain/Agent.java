package br.com.systec.hive.agent.api.domain;

import br.com.systec.hive.provider.api.AiProvider;
import br.com.systec.hive.commons.api.domain.AbstractDomain;

import java.io.Serial;
import java.io.Serializable;
import java.util.UUID;

public class Agent extends AbstractDomain implements Serializable {
    @Serial
    private static final long serialVersionUID = -6381178262753528341L;

    private String name;
    private String description;
    private String prompt;
    private String systemPrompt;
    private String avatarUrl;
    private PromptTemplate promptTemplate;
    private AiProvider provider;
    private String model;
    private Double temperature;
    private Double topP;
    private Integer maxTokens;
    private java.util.List<String> tools;
    private java.util.List<String> knowledgeBases;
    private Boolean active = true;
    private Boolean isDefault = false;
    private Integer version;

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

    public String getPrompt() {
        return prompt;
    }

    public void setPrompt(String prompt) {
        this.prompt = prompt;
    }

    public PromptTemplate getPromptTemplate() {
        return promptTemplate;
    }

    public void setPromptTemplate(PromptTemplate promptTemplate) {
        this.promptTemplate = promptTemplate;
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

    public Double getTemperature() {
        return temperature;
    }

    public void setTemperature(Double temperature) {
        this.temperature = temperature;
    }

    public Boolean getActive() {
        return active;
    }

    public boolean isActive() {
        return Boolean.TRUE.equals(active);
    }

    public void setActive(Boolean active) {
        this.active = active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public String getSystemPrompt() {
        return systemPrompt;
    }

    public void setSystemPrompt(String systemPrompt) {
        this.systemPrompt = systemPrompt;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
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

    public Boolean getIsDefault() {
        return isDefault;
    }

    public boolean isDefault() {
        return Boolean.TRUE.equals(isDefault);
    }

    public void setDefault(Boolean isDefault) {
        this.isDefault = isDefault;
    }

    public void setDefault(boolean isDefault) {
        this.isDefault = isDefault;
    }

    public void setIsDefault(Boolean isDefault) {
        this.isDefault = isDefault;
    }

    public void setIsDefault(boolean isDefault) {
        this.isDefault = isDefault;
    }

    public Integer getVersion() {
        return version;
    }

    public Agent setVersion(Integer version) {
        this.version = version;
        return this;
    }
}
