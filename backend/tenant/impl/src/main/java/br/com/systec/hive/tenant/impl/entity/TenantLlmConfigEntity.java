package br.com.systec.hive.tenant.impl.entity;

import br.com.systec.hive.commons.impl.entity.AbstractEntity;
import br.com.systec.hive.provider.api.AiProvider;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.io.Serial;

/**
 * One row per tenant: the vendor and the model every conversation of that tenant is answered by.
 *
 * <p>Extends {@link AbstractEntity} rather than {@code BaseEntity} even though the table's
 * {@code tenant_id} is its own business key, and that is the point: {@code AbstractEntity} carries
 * the {@code tenantFilter} and the {@code TenantListener}, so this row is isolated by the same
 * mechanism as every other tenant-scoped entity instead of by remembering to add a {@code tenantId}
 * to a query. {@code tenant_id} is the filter column <i>and</i> the {@code UNIQUE} key, which is
 * what makes "exactly one default per tenant" a database fact rather than an application promise.
 *
 * <p>{@code EnumType.STRING}, not an ordinal: the stored value is read by operators, dumped by
 * {@code pg_dump} and compared by hand against {@code AiProvider}, and an ordinal turns a future
 * {@code Enum} reorder into silent data corruption.
 */
@Entity
@Table(name = "tenant_llm_config")
public class TenantLlmConfigEntity extends AbstractEntity {

    @Serial
    private static final long serialVersionUID = -3120266441987339201L;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, length = 32)
    private AiProvider provider;

    @Column(name = "model", nullable = false, length = 255)
    private String model;

    public AiProvider getProvider() {
        return provider;
    }

    public void setProvider(AiProvider provider) {
        this.provider = provider;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }
}
