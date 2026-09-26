package br.com.systec.hive.agent.api.service;

import br.com.systec.hive.agent.api.domain.Agent;
import br.com.systec.hive.commons.api.service.CrudService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface AgentService extends CrudService<Agent> {

    Agent findById(UUID agentId);

    List<Agent> findAll();

    Page<Agent> listAgents(UUID tenantId, Pageable pageable);

    Page<Agent> listAgents(UUID tenantId, Pageable pageable, String q);
}
