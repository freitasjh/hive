package br.com.systec.hive.agent.impl.repository;

import br.com.systec.hive.agent.impl.entity.ChatSessionEntity;
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

    Page<ChatSessionEntity> findByTenantIdAndUserId(UUID tenantId, UUID userId, Pageable pageable);

    void deleteByIdAndTenantId(UUID id, UUID tenantId);
}
