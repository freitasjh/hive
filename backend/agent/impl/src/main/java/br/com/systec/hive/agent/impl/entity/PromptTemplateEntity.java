package br.com.systec.hive.agent.impl.entity;

import br.com.systec.hive.agent.api.domain.InferenceParams;
import br.com.systec.hive.agent.api.domain.PromptStatus;
import br.com.systec.hive.commons.impl.entity.AbstractEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.io.Serial;
import java.util.Set;

@Entity
@Table(name = "prompt_template")
public class PromptTemplateEntity extends AbstractEntity {

    @Serial
    private static final long serialVersionUID = 7081168969573645790L;

    @Column(name = "code", nullable = false)
    private String code;
    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "template", columnDefinition = "TEXT", nullable = false)
    private String template;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "params", columnDefinition = "text[]")
    private Set<String> params;

    @Column(name = "version", nullable = false)
    private Integer version;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private PromptStatus status;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "tags", columnDefinition = "text[]")
    private Set<String> tags;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "inference_params", columnDefinition = "jsonb")
    private InferenceParams inferenceParams;

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
