package br.com.systec.hive.agent.impl.repository;

import br.com.systec.hive.agent.impl.entity.ChatMessageEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessageEntity, UUID> {

    @Query("SELECT m FROM ChatMessageEntity m WHERE m.session.id = :sessionId AND m.tenantId = :tenantId ORDER BY m.createAt ASC")
    Page<ChatMessageEntity> findBySessionIdAndTenantIdOrderByCreateAtAsc(@Param("sessionId") UUID sessionId,
                                                                          @Param("tenantId") UUID tenantId,
                                                                          Pageable pageable);

    @Query("SELECT m FROM ChatMessageEntity m WHERE m.session.id = :sessionId AND m.tenantId = :tenantId ORDER BY m.createAt DESC")
    List<ChatMessageEntity> findBySessionIdAndTenantIdOrderByCreateAtDesc(@Param("sessionId") UUID sessionId,
                                                                           @Param("tenantId") UUID tenantId,
                                                                           Pageable pageable);

    @Query("SELECT COUNT(m) FROM ChatMessageEntity m WHERE m.session.id = :sessionId AND m.tenantId = :tenantId")
    long countBySessionIdAndTenantId(@Param("sessionId") UUID sessionId,
                                     @Param("tenantId") UUID tenantId);
}
