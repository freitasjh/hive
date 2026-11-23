package br.com.systec.hive.publicapi.impl.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * ADR-004/QT02: limite de chat por tenant, lido no instante da emissão do token.
 *
 * <p>Chave primária é o próprio tenant_id; esta entidade não estende BaseEntity porque a tabela
 * V2 não tem coluna {@code id}.
 */
@Entity
@Table(name = "public_api_tenant_quota")
public class PublicApiTenantQuota implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "chat_requests_per_minute", nullable = false)
    private Integer chatRequestsPerMinute = 100;

    @Column(name = "create_at", updatable = false)
    private LocalDateTime createAt;

    @Column(name = "update_at")
    private LocalDateTime updateAt;

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public Integer getChatRequestsPerMinute() { return chatRequestsPerMinute; }
    public void setChatRequestsPerMinute(Integer chatRequestsPerMinute) { this.chatRequestsPerMinute = chatRequestsPerMinute; }
    public LocalDateTime getCreateAt() { return createAt; }
    public void setCreateAt(LocalDateTime createAt) { this.createAt = createAt; }
    public LocalDateTime getUpdateAt() { return updateAt; }
    public void setUpdateAt(LocalDateTime updateAt) { this.updateAt = updateAt; }
}
