package br.com.systec.hive.assistant.web.internal.v1.dto;

import jakarta.validation.constraints.Size;

public class CredentialUpsertDTO {

    @Size(max = 4096, message = "apiKey deve ter no máximo 4096 caracteres.")
    private String apiKey;

    @Size(max = 2048, message = "baseUrl deve ter no máximo 2048 caracteres.")
    private String baseUrl;

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }
}
