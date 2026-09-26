package br.com.systec.hive.agent.api.service;

import br.com.systec.hive.agent.api.domain.Agent;

import java.util.Optional;
import java.util.UUID;

public interface DefaultAgentService {

    Optional<Agent> findDefault(UUID tenantId);

    Agent requireDefault(UUID tenantId);

    Agent setDefault(UUID tenantId, UUID agentId);

    Optional<Agent> getDefault(UUID tenantId);
}
