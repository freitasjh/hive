package br.com.systec.hive.agent.impl.repository;

import br.com.systec.hive.agent.api.domain.PromptStatus;
import br.com.systec.hive.agent.impl.entity.PromptTemplateEntity;
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
public interface PromptTemplateRepository extends JpaRepository<PromptTemplateEntity, UUID> {

    Optional<PromptTemplateEntity> findByIdAndTenantId(UUID id, UUID tenantId);

    Page<PromptTemplateEntity> findByTenantId(UUID tenantId, Pageable pageable);

    boolean existsByTenantIdAndCode(UUID tenantId, String code);

    Page<PromptTemplateEntity> findByTenantIdAndStatus(UUID tenantId, PromptStatus status, Pageable pageable);

    Optional<PromptTemplateEntity> findFirstByCodeAndTenantIdOrderByVersionDesc(String code, UUID tenantId);

    Optional<PromptTemplateEntity> findByCodeAndTenantIdAndStatus(String code, UUID tenantId, PromptStatus status);

    List<PromptTemplateEntity> findAllByCodeAndTenantIdOrderByVersionDesc(String code, UUID tenantId);

    Optional<PromptTemplateEntity> findByTenantIdAndCodeAndVersion(UUID tenantId, String code, Integer version);

    List<PromptTemplateEntity> findByTenantIdAndCodeOrderByVersionDesc(UUID tenantId, String code);

    @Query("SELECT p FROM PromptTemplateEntity p WHERE p.tenantId = :tenantId AND " +
            "(:q IS NULL OR LOWER(p.name) LIKE LOWER(CONCAT('%', :q, '%')) " +
            "OR LOWER(p.code) LIKE LOWER(CONCAT('%', :q, '%')) " +
            "OR LOWER(p.description) LIKE LOWER(CONCAT('%', :q, '%')))")
    Page<PromptTemplateEntity> findByTenantIdWithSearch(@Param("tenantId") UUID tenantId,
                                                        @Param("q") String q,
                                                        Pageable pageable);

    @Query("SELECT p FROM PromptTemplateEntity p WHERE p.tenantId = :tenantId " +
            "AND p.status = :status AND " +
            "(:q IS NULL OR LOWER(p.name) LIKE LOWER(CONCAT('%', :q, '%')) " +
            "OR LOWER(p.code) LIKE LOWER(CONCAT('%', :q, '%')) " +
            "OR LOWER(p.description) LIKE LOWER(CONCAT('%', :q, '%')))")
    Page<PromptTemplateEntity> findByTenantIdAndStatusWithSearch(@Param("tenantId") UUID tenantId,
                                                                 @Param("status") PromptStatus status,
                                                                 @Param("q") String q,
                                                                 Pageable pageable);
}
