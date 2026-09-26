package br.com.systec.hive.commons.impl.security;

import br.com.systec.hive.commons.api.exceptions.TenantNotFountException;
import br.com.systec.hive.commons.impl.entity.AbstractEntity;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

public class TenantListener {
    private static final Logger log = LoggerFactory.getLogger(TenantListener.class);

    @PrePersist
    @PreUpdate
    public void setTenant(AbstractEntity entity) {
        log.warn("@@@ Setando tenant na entidade {} @@@", entity.getClass().getName());

        if(entity.getTenantId() != null) {
            log.warn("@@@ Tenant já foi informado @@@");
            return;
        }

        try {
            UUID tenantId = TenantContext.getCurrentTenant();
            if(tenantId == null) {
                log.warn("@@ Tenant null, allowing null tenantId for {} @@@", entity.getClass().getName());
                return;
            }
            entity.setTenantId(tenantId);
        } catch (TenantNotFountException e) {
            log.warn("@@ Tenant not in context, skipping tenant assignment for {} (registration flow) @@@", entity.getClass().getName());
        }
    }
}
