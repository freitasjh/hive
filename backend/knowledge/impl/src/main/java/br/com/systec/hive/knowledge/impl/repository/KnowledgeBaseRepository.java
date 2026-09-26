package br.com.systec.hive.knowledge.impl.repository;

import br.com.systec.hive.knowledge.impl.entity.KnowledgeBaseEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface KnowledgeBaseRepository extends JpaRepository<KnowledgeBaseEntity, UUID> {

    Optional<KnowledgeBaseEntity> findByIdAndTenantId(UUID id, UUID tenantId);

    Page<KnowledgeBaseEntity> findByTenantId(UUID tenantId, Pageable pageable);

    @Query("SELECT k FROM KnowledgeBaseEntity k WHERE k.tenantId = :tenantId AND (lower(k.name) LIKE lower(concat('%', :q, '%')) OR lower(k.slug) LIKE lower(concat('%', :q, '%')))")
    Page<KnowledgeBaseEntity> findByTenantIdWithSearch(@Param("tenantId") UUID tenantId, @Param("q") String q, Pageable pageable);

    boolean existsByTenantIdAndSlug(UUID tenantId, String slug);

    List<KnowledgeBaseEntity> findByTenantIdAndIdIn(UUID tenantId, List<UUID> ids);
}
