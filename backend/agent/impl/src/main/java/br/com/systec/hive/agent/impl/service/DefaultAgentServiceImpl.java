package br.com.systec.hive.agent.impl.service;

import br.com.systec.hive.agent.api.domain.Agent;
import br.com.systec.hive.agent.api.exceptions.AgentNotFoundException;
import br.com.systec.hive.agent.api.exceptions.ConcurrentDefaultUpdateException;
import br.com.systec.hive.agent.api.exceptions.DefaultAgentNotFoundException;
import br.com.systec.hive.agent.api.exceptions.InactiveAgentCannotBeDefaultException;
import br.com.systec.hive.agent.api.service.DefaultAgentService;
import br.com.systec.hive.agent.impl.entity.AgentEntity;
import br.com.systec.hive.agent.impl.mapper.AgentMapperDomain;
import br.com.systec.hive.agent.impl.repository.AgentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
public class DefaultAgentServiceImpl implements DefaultAgentService {

    private static final Logger log = LoggerFactory.getLogger(DefaultAgentServiceImpl.class);

    private final AgentRepository agentRepository;
    private final AgentMapperDomain mapper;

    public DefaultAgentServiceImpl(AgentRepository agentRepository, AgentMapperDomain mapper) {
        this.agentRepository = agentRepository;
        this.mapper = mapper;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Agent> findDefault(UUID tenantId) {
        return agentRepository.findByTenantIdAndIsDefaultTrueAndActiveTrue(tenantId)
                .or(() -> agentRepository.findByTenantIdAndIsDefaultTrue(tenantId).filter(AgentEntity::isActive))
                .map(mapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Agent> getDefault(UUID tenantId) {
        return findDefault(tenantId);
    }

    @Override
    @Transactional(readOnly = true)
    public Agent requireDefault(UUID tenantId) {
        return agentRepository.findByTenantIdAndIsDefaultTrueAndActiveTrue(tenantId)
                .map(mapper::toDomain)
                .orElseThrow(() -> new DefaultAgentNotFoundException(true));
    }

    @Override
    @Transactional
    public Agent setDefault(UUID tenantId, UUID agentId) {
        AgentEntity entity = agentRepository.findByIdAndTenantId(agentId, tenantId)
                .orElseThrow(AgentNotFoundException::new);
        if (!entity.isActive()) {
            throw new InactiveAgentCannotBeDefaultException();
        }
        try {
            agentRepository.clearDefaultByTenantId(tenantId);
            int updated = agentRepository.setDefaultById(tenantId, agentId);
            if (updated == 0) {
                log.warn("setDefaultById returned 0 tenantId={} agentId={}", tenantId, agentId);
            }
        } catch (DataIntegrityViolationException e) {
            log.warn("Concurrent default update violated unique index tenantId={} agentId={}", tenantId, agentId, e);
            throw new ConcurrentDefaultUpdateException(e);
        }
        AgentEntity refreshed = agentRepository.findByIdAndTenantId(agentId, tenantId)
                .orElseThrow(AgentNotFoundException::new);
        return mapper.toDomain(refreshed);
    }
}
