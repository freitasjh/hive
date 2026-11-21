package br.com.systec.hive.history.impl.repository;

import br.com.systec.hive.history.impl.entity.ChatSessionEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ChatSessionRepository extends JpaRepository<ChatSessionEntity, UUID> {

    Optional<ChatSessionEntity> findByIdAndTenantId(UUID id, UUID tenantId);

    Optional<ChatSessionEntity> findByIdAndTenantIdAndUserId(UUID id, UUID tenantId, UUID userId);

    boolean existsByPromptTemplateIdAndTenantId(UUID promptTemplateId, UUID tenantId);

    Page<ChatSessionEntity> findByTenantIdAndUserId(UUID tenantId, UUID userId, Pageable pageable);

    void deleteByIdAndTenantId(UUID id, UUID tenantId);
}
