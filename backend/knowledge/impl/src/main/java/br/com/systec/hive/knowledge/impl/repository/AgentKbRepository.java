package br.com.systec.hive.knowledge.impl.repository;

import br.com.systec.hive.knowledge.impl.entity.AgentKbEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AgentKbRepository extends JpaRepository<AgentKbEntity, UUID> {

    List<AgentKbEntity> findByAgentIdAndTenantId(UUID agentId, UUID tenantId);

    List<AgentKbEntity> findByKbIdAndTenantId(UUID kbId, UUID tenantId);

    boolean existsByAgentIdAndKbIdAndTenantId(UUID agentId, UUID kbId, UUID tenantId);

    long countByAgentIdAndTenantId(UUID agentId, UUID tenantId);

    void deleteByAgentIdAndKbIdAndTenantId(UUID agentId, UUID kbId, UUID tenantId);

    void deleteByKbIdAndTenantId(UUID kbId, UUID tenantId);

    @Query("SELECT l.kbId FROM AgentKbEntity l WHERE l.agentId = :agentId AND l.tenantId = :tenantId")
    List<UUID> findKbIdsByAgent(@Param("agentId") UUID agentId, @Param("tenantId") UUID tenantId);
}
