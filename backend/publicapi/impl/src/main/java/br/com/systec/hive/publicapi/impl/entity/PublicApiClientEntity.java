package br.com.systec.hive.publicapi.impl.entity;

import br.com.systec.hive.commons.impl.entity.AbstractEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.io.Serial;
import java.time.LocalDateTime;

@Entity
@Table(name = "public_api_client")
public class PublicApiClientEntity extends AbstractEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @Column(name = "client_id", nullable = false, length = 64)
    private String clientId;

    @Column(name = "label", nullable = false, length = 255)
    private String label;

    @Column(name = "secret_hash", nullable = false, length = 72, columnDefinition = "VARCHAR(72)")
    private String secretHash;

    @Column(name = "previous_secret_hash", length = 72, columnDefinition = "VARCHAR(72)")
    private String previousSecretHash;

    @Column(name = "previous_secret_expires_at")
    private LocalDateTime previousSecretExpiresAt;

    @Column(name = "rate_limit_per_minute", nullable = false)
    private Integer rateLimitPerMinute;

    @Column(name = "token_ttl_seconds", nullable = false)
    private Integer tokenTtlSeconds;

    @Column(name = "max_messages", nullable = false)
    private Integer maxMessages;

    @Column(name = "max_message_chars", nullable = false)
    private Integer maxMessageChars;

    @Column(name = "max_total_chars", nullable = false)
    private Integer maxTotalChars;

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    @Column(name = "last_used_at")
    private LocalDateTime lastUsedAt;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = clientId; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public String getSecretHash() { return secretHash; }
    public void setSecretHash(String secretHash) { this.secretHash = secretHash; }
    public String getPreviousSecretHash() { return previousSecretHash; }
    public void setPreviousSecretHash(String previousSecretHash) { this.previousSecretHash = previousSecretHash; }
    public LocalDateTime getPreviousSecretExpiresAt() { return previousSecretExpiresAt; }
    public void setPreviousSecretExpiresAt(LocalDateTime previousSecretExpiresAt) { this.previousSecretExpiresAt = previousSecretExpiresAt; }
    public Integer getRateLimitPerMinute() { return rateLimitPerMinute; }
    public void setRateLimitPerMinute(Integer rateLimitPerMinute) { this.rateLimitPerMinute = rateLimitPerMinute; }
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
    public LocalDateTime getLastUsedAt() { return lastUsedAt; }
    public void setLastUsedAt(LocalDateTime lastUsedAt) { this.lastUsedAt = lastUsedAt; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }
}
