package br.com.systec.hive.knowledge.web.internal.v1.dto;

import jakarta.validation.constraints.Size;

public class KnowledgeBaseUpdateDTO {

    @Size(max = 255)
    private String name;

    @Size(max = 4000)
    private String description;

    private String status;

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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
