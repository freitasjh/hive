package br.com.systec.hive.publicapi.impl.repository;

import br.com.systec.hive.publicapi.impl.entity.PublicApiTenantQuota;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/** ADR-004/QT02: repositório no mesmo módulo/datasource do issuer — sem aresta de módulo nova. */
@Repository
public interface PublicApiTenantQuotaRepository extends JpaRepository<PublicApiTenantQuota, UUID> {
}
