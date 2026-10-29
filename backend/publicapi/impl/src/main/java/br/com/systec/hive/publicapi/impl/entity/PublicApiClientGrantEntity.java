package br.com.systec.hive.publicapi.impl.entity;

import br.com.systec.hive.commons.impl.entity.AbstractEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.io.Serial;
import java.util.UUID;

@Entity
@Table(name = "public_api_client_grant")
public class PublicApiClientGrantEntity extends AbstractEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @Column(name = "client_ref", nullable = false)
    private UUID clientRef;

    @Column(name = "permission", nullable = false, length = 64)
    private String permission;

    public UUID getClientRef() { return clientRef; }
    public void setClientRef(UUID clientRef) { this.clientRef = clientRef; }
    public String getPermission() { return permission; }
    public void setPermission(String permission) { this.permission = permission; }
}
