package br.com.systec.hive.commons.impl.entity;

import br.com.systec.hive.commons.impl.security.TenantListener;
import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.ParamDef;

import java.io.Serial;
import java.util.UUID;

@MappedSuperclass
@EntityListeners(TenantListener.class)
@FilterDef(name = "tenantFilter", parameters = {@ParamDef(name = "tenantId", type = UUID.class)})
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
public abstract class AbstractEntity extends BaseEntity{
    @Serial
    private static final long serialVersionUID = -861118043152753468L;

    @Column(name = "tenant_id", nullable = true, updatable = false)
    protected UUID tenantId;

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }
}
