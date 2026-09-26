package br.com.systec.hive.knowledge.web.internal.v1.dto;

import jakarta.validation.constraints.NotBlank;

public class KbUrlDTO {

    @NotBlank
    private String url;

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }
}
