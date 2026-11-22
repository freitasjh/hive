package br.com.systec.hive.publicapi.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Representação segura de um client público: sem {@code secret}, {@code secret_hash},
 * {@code previous_secret_hash} e sem {@code rate_limit_per_minute} (cota é da ADR-004).
 */
public class PublicApiClientResponseDTO {

    private String clientId;
    private String label;
    private UUID tenantId;
    private List<String> grants = new ArrayList<>();
    private List<PublicApiClientPromptBindingDTO> prompts = new ArrayList<>();

    @JsonProperty("token_ttl_seconds")
    private Integer tokenTtlSeconds;

    @JsonProperty("max_messages")
    private Integer maxMessages;

    @JsonProperty("max_message_chars")
    private Integer maxMessageChars;

    @JsonProperty("max_total_chars")
    private Integer maxTotalChars;

    @JsonProperty("revoked_at")
    private LocalDateTime revokedAt;

    @JsonProperty("expires_at")
    private LocalDateTime expiresAt;

    @JsonProperty("previous_secret_expires_at")
    private LocalDateTime previousSecretExpiresAt;

    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = clientId; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public List<String> getGrants() { return grants; }
    public void setGrants(List<String> grants) { this.grants = grants; }
    public List<PublicApiClientPromptBindingDTO> getPrompts() { return prompts; }
    public void setPrompts(List<PublicApiClientPromptBindingDTO> prompts) { this.prompts = prompts; }
    public Integer getTokenTtlSeconds() { return tokenTtlSeconds; }
    public void setTokenTtlSeconds(Integer tokenTtlSeconds) { this.tokenTtlSeconds = tokenTtlSeconds; }
    public Integer getMaxMessages() { return maxMessages; }
    public void setMaxMessages(Integer maxMessages) { this.maxMessages = maxMessages; }
    public Integer getMaxMessageChars() { return maxMessageChars; }
    public void setMaxMessageChars(Integer maxMessageChars) { this.maxMessageChars = maxMessageChars; }
    public Integer getMaxTotalChars() { return maxTotalChars; }
    public void setMaxTotalChars(Integer maxTotalChars) { this.maxTotalChars = maxTotalChars; }
    public LocalDateTime getRevokedAt() { return revokedAt; }
    public void setRevokedAt(LocalDateTime revokedAt) { this.revokedAt = revokedAt; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }
    public LocalDateTime getPreviousSecretExpiresAt() { return previousSecretExpiresAt; }
    public void setPreviousSecretExpiresAt(LocalDateTime previousSecretExpiresAt) { this.previousSecretExpiresAt = previousSecretExpiresAt; }
}
