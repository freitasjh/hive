package br.com.systec.hive.agent.impl.service;

import br.com.systec.hive.agent.api.domain.Agent;
import br.com.systec.hive.agent.api.domain.PromptTemplate;
import br.com.systec.hive.agent.api.exceptions.AgentNotFoundException;
import br.com.systec.hive.agent.api.exceptions.InactiveAgentCannotBeDefaultException;
import br.com.systec.hive.agent.api.exceptions.PromptTemplateNotFoundException;
import br.com.systec.hive.agent.api.exceptions.SoleDefaultAgentException;
import br.com.systec.hive.agent.impl.entity.AgentEntity;
import br.com.systec.hive.agent.impl.entity.PromptTemplateEntity;
import br.com.systec.hive.agent.impl.mapper.AgentMapperDomain;
import br.com.systec.hive.agent.impl.mapper.PromptTemplateDomainMapper;
import br.com.systec.hive.agent.impl.repository.AgentRepository;
import br.com.systec.hive.agent.impl.repository.PromptTemplateRepository;
import br.com.systec.hive.commons.impl.security.TenantContext;
import br.com.systec.hive.knowledge.api.port.KbAgentLinkPort;
import br.com.systec.hive.provider.api.AiProvider;
import br.com.systec.hive.provider.api.VendorCatalog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class AgentServiceImpl implements br.com.systec.hive.agent.api.service.AgentService {
    private static final Logger log = LoggerFactory.getLogger(AgentServiceImpl.class);

    private final AgentRepository repository;
    private final PromptTemplateRepository promptTemplateRepository;
    private final AgentMapperDomain mapper;
    private final PromptTemplateDomainMapper promptMapper;
    private KbAgentLinkPort kbLinkPort;
    private CredentialResolver credentialResolver;
    private boolean kbMissingWarned;

    public AgentServiceImpl(AgentRepository repository,
                            PromptTemplateRepository promptTemplateRepository,
                            AgentMapperDomain mapper,
                            PromptTemplateDomainMapper promptMapper) {
        this.repository = repository;
        this.promptTemplateRepository = promptTemplateRepository;
        this.mapper = mapper;
        this.promptMapper = promptMapper;
    }

    @Autowired(required = false)
    public void setKbLinkPort(KbAgentLinkPort kbLinkPort) {
        this.kbLinkPort = kbLinkPort;
    }

    @Autowired(required = false)
    public void setCredentialResolver(CredentialResolver credentialResolver) {
        this.credentialResolver = credentialResolver;
    }

    private void requireVendorCredential(AiProvider provider, UUID tenantId, boolean active) {
        if (!active || credentialResolver == null || tenantId == null) {
            return;
        }
        if (provider == AiProvider.OPENCODE_GO
                || provider == AiProvider.OPENCODE_ZEN
                || provider == AiProvider.OPENAI_COMPATIBLE) {
            credentialResolver.resolve(tenantId, provider);
        }
    }

    private void ignoreLegacyKnowledgeBasesWrite(Agent agent) {
        if (agent.getKnowledgeBases() != null && !agent.getKnowledgeBases().isEmpty()) {
            // O campo continua sendo ignorado no create/update por compatibilidade com clientes
            // antigos, mas a rota que ele indica agora existe de fato: é o caminho de escrita
            // canônico, e é ele que valida o limite de 5 e a posse do agente.
            log.warn("agent legacy knowledgeBases write ignored agentId={} (use PUT /v1/agents/{{id}}/knowledge-bases)",
                    agent.getId());
            agent.setKnowledgeBases(null);
        }
    }

    private void enrichLegacyKnowledgeBasesRead(Agent agent, UUID tenantId) {
        if (kbLinkPort == null) {
            if (!kbMissingWarned) {
                kbMissingWarned = true;
                log.warn("KB module not available, RAG disabled");
            }
            return;
        }
        if (tenantId == null || agent == null || agent.getId() == null) {
            return;
        }
        try {
            agent.setKnowledgeBases(kbLinkPort.linkedKbNames(agent.getId(), tenantId));
        } catch (Exception e) {
            log.warn("agent kb link read failed agentId={}", agent.getId(), e);
        }
    }

    private void validateVendorChange(Agent agent) {
        if (agent.getProvider() != null && agent.getModel() != null) {
            VendorCatalog.validate(agent.getProvider(), agent.getModel());
            return;
        }
        Agent current = null;
        try {
            UUID lookupTenant = resolveTenant(agent);
            if (lookupTenant == null) {
                current = repository.findById(agent.getId()).map(mapper::toDomain).orElse(null);
            } else {
                current = repository.findByIdAndTenantId(agent.getId(), lookupTenant).map(mapper::toDomain).orElse(null);
            }
        } catch (Exception e) {
            current = null;
        }
        AiProvider effectiveProvider = agent.getProvider() != null ? agent.getProvider()
                : (current != null ? current.getProvider() : null);
        String effectiveModel = agent.getModel() != null ? agent.getModel()
                : (current != null ? current.getModel() : null);
        VendorCatalog.validate(effectiveProvider, effectiveModel);
    }

    private UUID resolveTenant(Agent agent) {
        try {
            return TenantContext.getCurrentTenant();
        } catch (Exception e) {
            if (agent != null && agent.getTenantId() != null) {
                return agent.getTenantId();
            }
            return null;
        }
    }

    private UUID resolveTenantForId(UUID tenantId) {
        if (tenantId != null) return tenantId;
        try {
            return TenantContext.getCurrentTenant();
        } catch (Exception e) {
            return null;
        }
    }

    private void applyInheritance(Agent agent, PromptTemplate prompt) {
        if (prompt == null || prompt.getInferenceParams() == null) {
            // still handle systemPrompt inheritance
            if ((agent.getSystemPrompt() == null || agent.getSystemPrompt().isBlank())
                    && prompt != null && prompt.getTemplate() != null) {
                agent.setSystemPrompt(prompt.getTemplate());
            }
            return;
        }
        if (agent.getTemperature() == null && prompt.getInferenceParams().getTemperature() != null) {
            agent.setTemperature(prompt.getInferenceParams().getTemperature());
        }
        if (agent.getTopP() == null && prompt.getInferenceParams().getTopP() != null) {
            agent.setTopP(prompt.getInferenceParams().getTopP());
        }
        if (agent.getMaxTokens() == null && prompt.getInferenceParams().getMaxTokens() != null) {
            agent.setMaxTokens(prompt.getInferenceParams().getMaxTokens());
        }
        if ((agent.getSystemPrompt() == null || agent.getSystemPrompt().isBlank())
                && prompt.getTemplate() != null) {
            agent.setSystemPrompt(prompt.getTemplate());
        }
    }

    private PromptTemplate loadPromptIfLinked(Agent agent, UUID tenantId) {
        if (agent.getPromptTemplate() != null && agent.getPromptTemplate().getId() != null) {
            UUID promptId = agent.getPromptTemplate().getId();
            PromptTemplateEntity entity = promptTemplateRepository.findByIdAndTenantId(promptId, tenantId)
                    .orElseThrow(PromptTemplateNotFoundException::new);
            return promptMapper.toDomain(entity);
        }
        return null;
    }

    @Override
    @Transactional
    public Agent create(Agent agent) {
        VendorCatalog.validate(agent.getProvider(), agent.getModel());
        ignoreLegacyKnowledgeBasesWrite(agent);
        UUID tenantId = resolveTenant(agent);
        requireVendorCredential(agent.getProvider(), tenantId, agent.isActive());
        PromptTemplate prompt = null;
        if (agent.getPromptTemplate() != null && agent.getPromptTemplate().getId() != null) {
            UUID promptId = agent.getPromptTemplate().getId();
            PromptTemplateEntity ptEntity;
            if (tenantId == null) {
                ptEntity = promptTemplateRepository.findById(promptId).orElseThrow(PromptTemplateNotFoundException::new);
            } else {
                ptEntity = promptTemplateRepository.findByIdAndTenantId(promptId, tenantId)
                        .orElseThrow(PromptTemplateNotFoundException::new);
            }
            prompt = promptMapper.toDomain(ptEntity);
        }
        applyInheritance(agent, prompt);

        if (agent.isDefault() && !agent.isActive()) {
            // active defaults false(java) would trigger; but if agent explicitly wants isDefault true while inactive -> 422
            // Check if agent was set active false explicitly; domain primitive default false would be inactive.
            // To distinguish, check if caller intended inactive==false vs default false. If isDefault true and active false -> 422
            throw new InactiveAgentCannotBeDefaultException();
        }

        agent.setTenantId(tenantId);
        AgentEntity toSave = mapper.toEntity(agent);
        toSave.setTenantId(tenantId);
        // C1: never INSERT with isDefault=true to avoid uq_agent_default_per_tenant violation when default already exists
        toSave.setDefault(false);
        AgentEntity saved = repository.save(toSave);

        if (agent.isDefault() && tenantId != null) {
            try {
                repository.clearDefaultByTenantId(tenantId);
                repository.setDefaultById(tenantId, saved.getId());
                saved.setDefault(true);
            } catch (DataIntegrityViolationException e) {
                throw new br.com.systec.hive.agent.api.exceptions.ConcurrentDefaultUpdateException(e);
            }
        } else if (agent.isDefault()) {
            saved.setDefault(true);
        }

        Agent result = mapper.toDomain(saved);
        enrichLegacyKnowledgeBasesRead(result, tenantId);
        return result;
    }

    @Override
    @Transactional
    public Agent update(Agent agent) {
        if (agent.getProvider() != null || agent.getModel() != null) {
            validateVendorChange(agent);
        }
        ignoreLegacyKnowledgeBasesWrite(agent);
        UUID tenantId = resolveTenant(agent);
        AgentEntity existing;
        if (tenantId == null) {
            existing = repository.findById(agent.getId()).orElseThrow(AgentNotFoundException::new);
        } else {
            existing = repository.findByIdAndTenantId(agent.getId(), tenantId)
                    .orElseThrow(AgentNotFoundException::new);
        }

        // C1 fix: treat Boolean null as "do not change" — preserve existing state
        Boolean incomingIsDefault = agent.getIsDefault();
        Boolean incomingActive = agent.getActive();
        boolean effectiveIsDefault = incomingIsDefault != null ? incomingIsDefault : existing.isDefault();
        boolean effectiveActive = incomingActive != null ? incomingActive : existing.isActive();

        // sole default check for deactivation — use effectiveActive
        boolean willBeInactive = !effectiveActive;
        boolean currentlyDefault = existing.isDefault();
        if (currentlyDefault && willBeInactive && tenantId != null) {
            long count = repository.countByTenantIdAndIsDefaultTrue(tenantId);
            if (count == 1) {
                throw new SoleDefaultAgentException();
            }
        }
        // if effective wants to become default but inactive
        if (effectiveIsDefault && !effectiveActive) {
            throw new InactiveAgentCannotBeDefaultException();
        }
        AiProvider effectiveProvider = agent.getProvider() != null ? agent.getProvider() : existing.getProvider();
        requireVendorCredential(effectiveProvider, tenantId, effectiveActive);

        // prompt validation and inheritance
        PromptTemplate prompt = null;
        if (agent.getPromptTemplate() != null && agent.getPromptTemplate().getId() != null) {
            UUID promptId = agent.getPromptTemplate().getId();
            if (tenantId == null) {
                PromptTemplateEntity ptEntity = promptTemplateRepository.findById(promptId).orElseThrow(PromptTemplateNotFoundException::new);
                prompt = promptMapper.toDomain(ptEntity);
            } else {
                PromptTemplateEntity ptEntity = promptTemplateRepository.findByIdAndTenantId(promptId, tenantId)
                        .orElseThrow(PromptTemplateNotFoundException::new);
                prompt = promptMapper.toDomain(ptEntity);
            }
        } else if (agent.getPromptTemplate() == null) {
            // keep existing prompt? if agent does not carry promptTemplate, preserve? But update DTO will have promptId nullable -> if null means unlink.
            // mapper will set null, which is intended.
        }
        applyInheritance(agent, prompt);

        mapper.updateEntity(existing, agent);
        existing.setTenantId(tenantId);
        // C1: preserve flags when incoming is null, otherwise apply effective value
        if (incomingActive != null) {
            existing.setActive(effectiveActive);
        }
        // C2: avoid INSERT-violation on promotion: force false before flush, then single-statement swap
        if (incomingIsDefault != null) {
            if (effectiveIsDefault) {
                existing.setDefault(false);
            } else {
                existing.setDefault(false);
            }
        }

        AgentEntity saved = repository.save(existing);

        if (incomingIsDefault != null && effectiveIsDefault && tenantId != null) {
            try {
                repository.clearDefaultByTenantId(tenantId);
                repository.setDefaultById(tenantId, saved.getId());
                saved.setDefault(true);
            } catch (DataIntegrityViolationException e) {
                throw new br.com.systec.hive.agent.api.exceptions.ConcurrentDefaultUpdateException(e);
            }
        } else if (incomingIsDefault != null && !effectiveIsDefault) {
            saved.setDefault(false);
        }

        Agent result = mapper.toDomain(saved);
        enrichLegacyKnowledgeBasesRead(result, tenantId);
        return result;
    }

    @Override
    @Transactional
    public Agent delete(Agent entity) {
        UUID tenantId;
        try {
            tenantId = TenantContext.getCurrentTenant();
        } catch (Exception e) {
            tenantId = entity.getTenantId() != null ? entity.getTenantId() : null;
        }
        AgentEntity existing;
        if (tenantId == null) {
            existing = repository.findById(entity.getId()).orElseThrow(AgentNotFoundException::new);
        } else {
            existing = repository.findByIdAndTenantId(entity.getId(), tenantId)
                    .orElseThrow(AgentNotFoundException::new);
        }
        if (tenantId != null && existing.isDefault()) {
            long count = repository.countByTenantIdAndIsDefaultTrue(tenantId);
            if (count == 1) {
                throw new SoleDefaultAgentException();
            }
        }
        repository.delete(existing);
        return mapper.toDomain(existing);
    }

    @Override
    @Transactional(readOnly = true)
    public Agent findById(UUID agentId) {
        try {
            UUID tenantId = TenantContext.getCurrentTenant();
            AgentEntity entity = repository.findByIdAndTenantId(agentId, tenantId)
                    .orElseThrow(AgentNotFoundException::new);
            Agent agent = mapper.toDomain(entity);
            enrichLegacyKnowledgeBasesRead(agent, tenantId);
            return agent;
        } catch (br.com.systec.hive.commons.api.exceptions.TenantNotFountException e) {
            // fallback for non-tenant context (unit tests)
            return repository.findById(agentId)
                    .map(mapper::toDomain)
                    .orElseThrow(AgentNotFoundException::new);
        } catch (AgentNotFoundException e) {
            throw e;
        }
    }

    @Override
    public List<Agent> findAll() {
        return List.of();
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Agent> listAgents(UUID tenantId, Pageable pageable) {
        return listAgents(tenantId, pageable, null);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Agent> listAgents(UUID tenantId, Pageable pageable, String q) {
        UUID tid = resolveTenantForId(tenantId);
        Page<AgentEntity> page;
        if (q != null && !q.isBlank()) {
            page = repository.findByTenantIdWithSearch(tid, q, pageable);
        } else {
            page = repository.findByTenantId(tid, pageable);
        }
        return page.map(entity -> {
            Agent agent = mapper.toDomain(entity);
            enrichLegacyKnowledgeBasesRead(agent, tid);
            return agent;
        });
    }
}
