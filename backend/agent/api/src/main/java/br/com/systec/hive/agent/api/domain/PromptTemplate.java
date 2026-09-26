package br.com.systec.hive.agent.api.domain;

import br.com.systec.hive.commons.api.domain.AbstractDomain;

import java.io.Serial;
import java.io.Serializable;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class PromptTemplate extends AbstractDomain implements Serializable {
    @Serial
    private static final long serialVersionUID = 7081168969573645790L;

    private UUID id;
    private String code;
    private String name;
    private String template;
    private Set<String> params;
    private Integer version;
    private PromptStatus status;
    private String description;
    private Set<String> tags;
    private InferenceParams inferenceParams;


    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public void setId(UUID id) {
        this.id = id;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getTemplate() {
        return template;
    }

    public void setTemplate(String template) {
        this.template = template;
    }

    public Set<String> getParams() {
        return params;
    }

    public void setParams(Set<String> params) {
        this.params = params;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    public PromptStatus getStatus() {
        return status;
    }

    public void setStatus(PromptStatus status) {
        this.status = status;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Set<String> getTags() {
        return tags;
    }

    public void setTags(Set<String> tags) {
        this.tags = tags;
    }

    public InferenceParams getInferenceParams() {
        return inferenceParams;
    }

    public void setInferenceParams(InferenceParams inferenceParams) {
        this.inferenceParams = inferenceParams;
    }
}
