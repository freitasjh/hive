package br.com.systec.hive.knowledge.web.internal.v1.dto;

import jakarta.validation.constraints.NotBlank;

public class KbSearchDTO {

    @NotBlank
    private String query;

    private Integer topK;

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    public Integer getTopK() {
        return topK;
    }

    public void setTopK(Integer topK) {
        this.topK = topK;
    }
}
