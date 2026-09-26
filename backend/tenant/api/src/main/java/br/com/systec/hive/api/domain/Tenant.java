package br.com.systec.hive.api.domain;

import br.com.systec.hive.commons.api.domain.BaseDomain;

import java.io.Serial;
import java.util.UUID;

public class Tenant extends BaseDomain {
    @Serial
    private static final long serialVersionUID = -1953938846009436206L;

    private UUID id;
    private String name;
    private String description;
    private boolean active;
    private UUID ownerId;

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public void setId(UUID id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(UUID ownerId) {
        this.ownerId = ownerId;
    }
}
