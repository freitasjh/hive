package br.com.systec.hive.knowledge.impl.repository;

import br.com.systec.hive.knowledge.impl.entity.KbDocumentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface KbDocumentRepository extends JpaRepository<KbDocumentEntity, UUID> {

    Optional<KbDocumentEntity> findByIdAndTenantId(UUID id, UUID tenantId);

    List<KbDocumentEntity> findByTenantIdAndIdIn(UUID tenantId, Collection<UUID> ids);

    List<KbDocumentEntity> findByKbIdAndTenantId(UUID kbId, UUID tenantId);

    Optional<KbDocumentEntity> findByTenantIdAndSha256(UUID tenantId, String sha256);

    void deleteByKbIdAndTenantId(UUID kbId, UUID tenantId);
}
