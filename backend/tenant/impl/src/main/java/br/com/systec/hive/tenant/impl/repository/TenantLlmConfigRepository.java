package br.com.systec.hive.tenant.impl.repository;

import br.com.systec.hive.tenant.impl.entity.TenantLlmConfigEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Every method takes the tenant explicitly, even though {@code AbstractEntity}'s Hibernate filter
 * would already scope the read.
 *
 * <p>That filter is a defence in depth, not the contract: it is enabled per request by an aspect, so
 * a call from outside a tenant-scoped context would read every tenant's row. A repository whose
 * only finders demand a {@code tenantId} cannot be called that way at all.
 */
@Repository
public interface TenantLlmConfigRepository extends JpaRepository<TenantLlmConfigEntity, UUID> {

    Optional<TenantLlmConfigEntity> findByTenantId(UUID tenantId);
}
