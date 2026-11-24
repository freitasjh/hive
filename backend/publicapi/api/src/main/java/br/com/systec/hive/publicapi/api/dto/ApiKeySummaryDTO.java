package br.com.systec.hive.publicapi.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;

/** Metadados seguros de uma key — sem plaintext e sem hash. */
public class ApiKeySummaryDTO {

    private String keyId;

    @JsonProperty("created_at")
    private LocalDateTime createdAt;

    @JsonProperty("revoked_at")
    private LocalDateTime revokedAt;

    @JsonProperty("expires_at")
    private LocalDateTime expiresAt;

    @JsonProperty("previous_key_expires_at")
    private LocalDateTime previousKeyExpiresAt;

    @JsonProperty("last_used_at")
    private LocalDateTime lastUsedAt;

    public String getKeyId() { return keyId; }
    public void setKeyId(String keyId) { this.keyId = keyId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getRevokedAt() { return revokedAt; }
    public void setRevokedAt(LocalDateTime revokedAt) { this.revokedAt = revokedAt; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }
    public LocalDateTime getPreviousKeyExpiresAt() { return previousKeyExpiresAt; }
    public void setPreviousKeyExpiresAt(LocalDateTime previousKeyExpiresAt) { this.previousKeyExpiresAt = previousKeyExpiresAt; }
    public LocalDateTime getLastUsedAt() { return lastUsedAt; }
    public void setLastUsedAt(LocalDateTime lastUsedAt) { this.lastUsedAt = lastUsedAt; }
}
