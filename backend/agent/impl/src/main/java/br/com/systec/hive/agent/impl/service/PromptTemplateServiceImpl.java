package br.com.systec.hive.agent.impl.service;

import br.com.systec.hive.agent.api.domain.PromptStatus;
import br.com.systec.hive.agent.api.domain.PromptTemplate;
import br.com.systec.hive.agent.api.exceptions.CodeImmutableException;
import br.com.systec.hive.agent.api.exceptions.PromptCodeDuplicateException;
import br.com.systec.hive.agent.api.exceptions.PromptInUseException;
import br.com.systec.hive.agent.api.exceptions.PromptTemplateNotFoundException;
import br.com.systec.hive.agent.api.service.PromptTemplateService;
import br.com.systec.hive.agent.impl.entity.PromptTemplateEntity;
import br.com.systec.hive.agent.impl.mapper.PromptTemplateDomainMapper;
import br.com.systec.hive.history.impl.repository.ChatSessionRepository;
import br.com.systec.hive.agent.impl.repository.PromptTemplateRepository;
import br.com.systec.hive.agent.impl.util.PromptVariableExtractor;
import br.com.systec.hive.commons.impl.security.TenantContext;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class PromptTemplateServiceImpl implements PromptTemplateService {
    private final PromptTemplateRepository repository;
    private final PromptTemplateDomainMapper mapper;
    private final ChatSessionRepository chatSessionRepository;

    public PromptTemplateServiceImpl(PromptTemplateRepository repository, PromptTemplateDomainMapper mapper, ChatSessionRepository chatSessionRepository) {
        this.repository = repository;
        this.mapper = mapper;
        this.chatSessionRepository = chatSessionRepository;
    }

    @Override
    @Transactional
    public PromptTemplate create(PromptTemplate promptTemplate) {
        if (promptTemplate.getCode() == null || promptTemplate.getCode().isBlank()) {
            throw new IllegalArgumentException("Code cannot be null or empty");
        }
        if (promptTemplate.getTemplate() == null || promptTemplate.getTemplate().isBlank()) {
            throw new IllegalArgumentException("Template cannot be null or empty");
        }
        UUID tenantId = TenantContext.getCurrentTenant();
        if (repository.existsByTenantIdAndCode(tenantId, promptTemplate.getCode())) {
            throw new PromptCodeDuplicateException(promptTemplate.getCode());
        }
        if (promptTemplate.getVersion() == null) {
            promptTemplate.setVersion(1);
        }
        if (promptTemplate.getStatus() == null) {
            promptTemplate.setStatus(PromptStatus.DRAFT);
        }
        Set<String> extracted = PromptVariableExtractor.extractParams(promptTemplate.getTemplate());
        promptTemplate.setParams(extracted);

        PromptTemplateEntity entity = mapper.toEntity(promptTemplate);
        PromptTemplateEntity saved = repository.save(entity);
        return mapper.toDomain(saved);
    }

    @Override
    @Transactional
    public PromptTemplate update(PromptTemplate promptTemplate) {
        UUID tenantId = TenantContext.getCurrentTenant();
        PromptTemplateEntity existing = repository.findByIdAndTenantId(promptTemplate.getId(), tenantId)
                .orElseThrow(PromptTemplateNotFoundException::new);

        if (promptTemplate.getCode() != null && !promptTemplate.getCode().equals(existing.getCode())) {
            throw new CodeImmutableException();
        }

        if (promptTemplate.getTemplate() != null) {
            Set<String> extracted = PromptVariableExtractor.extractParams(promptTemplate.getTemplate());
            promptTemplate.setParams(extracted);
        } else {
            promptTemplate.setParams(existing.getParams());
        }

        promptTemplate.setVersion(existing.getVersion() + 1);
        promptTemplate.setCode(existing.getCode());

        mapper.updateEntity(existing, promptTemplate);
        existing.setParams(promptTemplate.getParams());
        existing.setVersion(promptTemplate.getVersion());

        PromptTemplateEntity saved = repository.save(existing);
        return mapper.toDomain(saved);
    }

    @Override
    @Transactional
    public PromptTemplate createDraft(String code) {
        UUID tenantId = TenantContext.getCurrentTenant();
        PromptTemplateEntity latest = repository.findFirstByCodeAndTenantIdOrderByVersionDesc(code, tenantId)
                .orElseThrow(PromptTemplateNotFoundException::new);

        PromptTemplateEntity draft = new PromptTemplateEntity();
        draft.setCode(latest.getCode());
        draft.setName(latest.getName());
        draft.setTemplate(latest.getTemplate());
        draft.setParams(latest.getParams());
        draft.setVersion(latest.getVersion() + 1);
        draft.setStatus(PromptStatus.DRAFT);
        draft.setDescription(latest.getDescription());
        draft.setTags(latest.getTags());
        draft.setInferenceParams(latest.getInferenceParams());
        draft.setTenantId(tenantId);

        PromptTemplateEntity savedDraft = repository.save(draft);
        return mapper.toDomain(savedDraft);
    }

    @Override
    @Transactional
    public void activateVersion(UUID id) {
        UUID tenantId = TenantContext.getCurrentTenant();
        PromptTemplateEntity templateToActivate = repository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(PromptTemplateNotFoundException::new);
        deactivateVersion(templateToActivate);
        templateToActivate.setStatus(PromptStatus.ACTIVE);
        repository.save(templateToActivate);
    }

    private void deactivateVersion(PromptTemplateEntity promptTemplate) {
        UUID tenantId = TenantContext.getCurrentTenant();
        repository.findByCodeAndTenantIdAndStatus(promptTemplate.getCode(), tenantId, PromptStatus.ACTIVE)
                .ifPresent(toDeactivate -> {
                    toDeactivate.setStatus(PromptStatus.ARCHIVED);
                    repository.save(toDeactivate);
                });
    }

    @Override
    @Transactional(readOnly = true)
    public List<PromptTemplate> listVersions(String code) {
        UUID tenantId = TenantContext.getCurrentTenant();
        List<PromptTemplateEntity> versions = repository.findAllByCodeAndTenantIdOrderByVersionDesc(code, tenantId);
        return mapper.toDomain(versions);
    }

    @Override
    @Transactional(readOnly = true)
    public PromptTemplate findActiveByCode(String code) {
        UUID tenantId = TenantContext.getCurrentTenant();
        return repository.findByCodeAndTenantIdAndStatus(code, tenantId, PromptStatus.ACTIVE)
                .map(mapper::toDomain)
                .orElseThrow(PromptTemplateNotFoundException::new);
    }

    @Override
    @Transactional(readOnly = true)
    public PromptTemplate findByCodeAndVersion(String code, Integer version) {
        UUID tenantId = TenantContext.getCurrentTenant();
        return repository.findByTenantIdAndCodeAndVersion(tenantId, code, version)
                .map(mapper::toDomain)
                .orElseThrow(PromptTemplateNotFoundException::new);
    }

    @Override
    @Transactional
    public PromptTemplate delete(PromptTemplate obj) {
        UUID tenantId = TenantContext.getCurrentTenant();
        PromptTemplateEntity entity = repository.findByIdAndTenantId(obj.getId(), tenantId)
                .orElseThrow(PromptTemplateNotFoundException::new);
        boolean inUse = chatSessionRepository.existsByPromptTemplateIdAndTenantId(entity.getId(), tenantId);
        if (inUse) {
            throw new PromptInUseException(entity.getId().toString());
        }
        repository.delete(entity);
        return mapper.toDomain(entity);
    }

    @Transactional
    public void deleteById(UUID id) {
        UUID tenantId = TenantContext.getCurrentTenant();
        PromptTemplateEntity entity = repository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(PromptTemplateNotFoundException::new);
        boolean inUse = chatSessionRepository.existsByPromptTemplateIdAndTenantId(entity.getId(), tenantId);
        if (inUse) {
            throw new PromptInUseException(entity.getId().toString());
        }
        repository.delete(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public PromptTemplate findById(UUID promptTemplateId) {
        UUID tenantId = TenantContext.getCurrentTenant();
        PromptTemplateEntity entity = repository.findByIdAndTenantId(promptTemplateId, tenantId)
                .orElseThrow(PromptTemplateNotFoundException::new);
        return mapper.toDomain(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PromptTemplate> findAll() {
        UUID tenantId = TenantContext.getCurrentTenant();
        List<PromptTemplateEntity> list = repository.findByTenantId(tenantId, Pageable.unpaged()).getContent();
        return mapper.toDomain(list);
    }

    @Transactional(readOnly = true)
    public Page<PromptTemplate> findByTenantId(UUID tenantId, Pageable pageable, String q, PromptStatus status) {
        Page<PromptTemplateEntity> page;
        boolean hasQ = q != null && !q.isBlank();
        boolean hasStatus = status != null;
        if (hasStatus && hasQ) {
            page = repository.findByTenantIdAndStatusWithSearch(tenantId, status, q, pageable);
        } else if (hasStatus) {
            page = repository.findByTenantIdAndStatus(tenantId, status, pageable);
        } else if (hasQ) {
            page = repository.findByTenantIdWithSearch(tenantId, q, pageable);
        } else {
            page = repository.findByTenantId(tenantId, pageable);
        }
        return page.map(mapper::toDomain);
    }
}
