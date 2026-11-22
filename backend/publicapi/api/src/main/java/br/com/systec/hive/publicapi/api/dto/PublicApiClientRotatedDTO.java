package br.com.systec.hive.publicapi.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;

/** Resposta one-shot de rotação: novo plaintext e o timestamp do secret anterior. */
public class PublicApiClientRotatedDTO {

    private String clientId;

    @JsonProperty("client_secret")
    private String clientSecret;

    @JsonProperty("previous_secret_expires_at")
    private LocalDateTime previousSecretExpiresAt;

    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = clientId; }
    public String getClientSecret() { return clientSecret; }
    public void setClientSecret(String clientSecret) { this.clientSecret = clientSecret; }
    public LocalDateTime getPreviousSecretExpiresAt() { return previousSecretExpiresAt; }
    public void setPreviousSecretExpiresAt(LocalDateTime previousSecretExpiresAt) { this.previousSecretExpiresAt = previousSecretExpiresAt; }
}
