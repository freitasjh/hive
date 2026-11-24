package br.com.systec.hive.publicapi.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;

/** Novo plaintext devolvido uma vez; janela do hash anterior como timestamp. */
public class ApiKeyRotatedDTO {

    private String clientId;
    private String keyId;

    @JsonProperty("api_key")
    private String apiKey;

    @JsonProperty("previous_key_expires_at")
    private LocalDateTime previousKeyExpiresAt;

    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = clientId; }
    public String getKeyId() { return keyId; }
    public void setKeyId(String keyId) { this.keyId = keyId; }
    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }
    public LocalDateTime getPreviousKeyExpiresAt() { return previousKeyExpiresAt; }
    public void setPreviousKeyExpiresAt(LocalDateTime previousKeyExpiresAt) { this.previousKeyExpiresAt = previousKeyExpiresAt; }
}
