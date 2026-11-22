package br.com.systec.hive.publicapi.impl.repository;

import br.com.systec.hive.publicapi.impl.entity.PublicApiClientEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PublicApiClientRepository extends JpaRepository<PublicApiClientEntity, UUID> {
    Optional<PublicApiClientEntity> findByClientId(String clientId);

    /** ADR-003/ADM02: listagem paginada por tenant da sessão. */
    Page<PublicApiClientEntity> findByTenantId(UUID tenantId, Pageable pageable);

    /** ADR-003/ADM02: unicidade de label dentro do tenant. */
    Optional<PublicApiClientEntity> findByTenantIdAndLabel(UUID tenantId, String label);
}
