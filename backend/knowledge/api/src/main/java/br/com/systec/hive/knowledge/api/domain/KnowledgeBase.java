package br.com.systec.hive.knowledge.api.domain;

import br.com.systec.hive.commons.api.domain.AbstractDomain;

import java.io.Serial;
import java.io.Serializable;

public class KnowledgeBase extends AbstractDomain implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private String name;
    private String slug;
    private String description;
    private String embeddingModel;
    private int dimension;
    private KbStatus status = KbStatus.ACTIVE;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSlug() {
        return slug;
    }

    public void setSlug(String slug) {
        this.slug = slug;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getEmbeddingModel() {
        return embeddingModel;
    }

    public void setEmbeddingModel(String embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    public int getDimension() {
        return dimension;
    }

    public void setDimension(int dimension) {
        this.dimension = dimension;
    }

    public KbStatus getStatus() {
        return status;
    }

    public void setStatus(KbStatus status) {
        this.status = status;
    }
}
