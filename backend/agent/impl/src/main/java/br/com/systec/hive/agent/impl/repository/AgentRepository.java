package br.com.systec.hive.agent.impl.repository;

import br.com.systec.hive.agent.impl.entity.AgentEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface AgentRepository extends JpaRepository<AgentEntity, UUID> {

    Optional<AgentEntity> findByIdAndTenantId(UUID id, UUID tenantId);

    Page<AgentEntity> findByTenantId(UUID tenantId, Pageable pageable);

    @Query("SELECT a FROM AgentEntity a WHERE a.tenantId = :tenantId AND (lower(a.name) LIKE lower(concat('%', :q, '%')) OR lower(a.description) LIKE lower(concat('%', :q, '%')))")
    Page<AgentEntity> findByTenantIdWithSearch(@Param("tenantId") UUID tenantId, @Param("q") String q, Pageable pageable);

    Optional<AgentEntity> findByTenantIdAndIsDefaultTrue(UUID tenantId);

    Optional<AgentEntity> findByTenantIdAndIsDefaultTrueAndActiveTrue(UUID tenantId);

    long countByTenantIdAndIsDefaultTrue(UUID tenantId);

    int countByTenantIdAndIsDefaultTrueAndActiveTrue(UUID tenantId);

    @Query("SELECT CASE WHEN COUNT(a) > 0 THEN true ELSE false END FROM AgentEntity a WHERE a.promptTemplate.id = :promptTemplateId AND a.tenantId = :tenantId")
    boolean existsByPromptTemplateIdAndTenantId(@Param("promptTemplateId") UUID promptTemplateId, @Param("tenantId") UUID tenantId);

    boolean existsByPromptTemplate_IdAndTenantId(UUID promptTemplateId, UUID tenantId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE AgentEntity a SET a.isDefault = false WHERE a.tenantId = :tenantId AND a.isDefault = true")
    int clearDefaultByTenantId(@Param("tenantId") UUID tenantId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE AgentEntity a SET a.isDefault = true WHERE a.id = :targetId AND a.tenantId = :tenantId")
    int setDefaultById(@Param("tenantId") UUID tenantId, @Param("targetId") UUID targetId);
}
