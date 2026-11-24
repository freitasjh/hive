package br.com.systec.hive.publicapi.impl.entity;

import br.com.systec.hive.commons.impl.entity.AbstractEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.io.Serial;
import java.time.LocalDateTime;
import java.util.UUID;

/** ADR-002: credencial Bearer sk- de um client público; somente hashes são persistidos. */
@Entity
@Table(name = "public_api_client_key")
public class PublicApiClientKeyEntity extends AbstractEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @Column(name = "client_ref", nullable = false)
    private UUID clientRef;

    @Column(name = "key_id", nullable = false, length = 64)
    private String keyId;

    @Column(name = "key_hash", nullable = false, length = 72, columnDefinition = "VARCHAR(72)")
    private String keyHash;

    @Column(name = "key_chat_requests_per_minute", nullable = false)
    private Integer keyChatRequestsPerMinute = 100;

    @Column(name = "previous_key_id", length = 64)
    private String previousKeyId;

    @Column(name = "previous_key_hash", length = 72, columnDefinition = "VARCHAR(72)")
    private String previousKeyHash;

    @Column(name = "previous_key_expires_at")
    private LocalDateTime previousKeyExpiresAt;

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "last_used_at")
    private LocalDateTime lastUsedAt;

    public UUID getClientRef() { return clientRef; }
    public void setClientRef(UUID clientRef) { this.clientRef = clientRef; }
    public String getKeyId() { return keyId; }
    public void setKeyId(String keyId) { this.keyId = keyId; }
    public String getKeyHash() { return keyHash; }
    public void setKeyHash(String keyHash) { this.keyHash = keyHash; }
    public Integer getKeyChatRequestsPerMinute() { return keyChatRequestsPerMinute; }
    public void setKeyChatRequestsPerMinute(Integer keyChatRequestsPerMinute) { this.keyChatRequestsPerMinute = keyChatRequestsPerMinute; }
    public String getPreviousKeyId() { return previousKeyId; }
    public void setPreviousKeyId(String previousKeyId) { this.previousKeyId = previousKeyId; }
    public String getPreviousKeyHash() { return previousKeyHash; }
    public void setPreviousKeyHash(String previousKeyHash) { this.previousKeyHash = previousKeyHash; }
    public LocalDateTime getPreviousKeyExpiresAt() { return previousKeyExpiresAt; }
    public void setPreviousKeyExpiresAt(LocalDateTime previousKeyExpiresAt) { this.previousKeyExpiresAt = previousKeyExpiresAt; }
    public LocalDateTime getRevokedAt() { return revokedAt; }
    public void setRevokedAt(LocalDateTime revokedAt) { this.revokedAt = revokedAt; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }
    public LocalDateTime getLastUsedAt() { return lastUsedAt; }
    public void setLastUsedAt(LocalDateTime lastUsedAt) { this.lastUsedAt = lastUsedAt; }
}
