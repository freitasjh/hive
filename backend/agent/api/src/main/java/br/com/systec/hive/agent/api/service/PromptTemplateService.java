package br.com.systec.hive.agent.api.service;

import br.com.systec.hive.agent.api.domain.PromptStatus;
import br.com.systec.hive.agent.api.domain.PromptTemplate;
import br.com.systec.hive.commons.api.service.CrudService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface PromptTemplateService extends CrudService<PromptTemplate> {

    PromptTemplate findById(UUID promptTemplateId);

    List<PromptTemplate> findAll();

    PromptTemplate createDraft(String code);

    void activateVersion(UUID id);

    List<PromptTemplate> listVersions(String code);

    PromptTemplate findActiveByCode(String code);

    Page<PromptTemplate> findByTenantId(UUID tenantId, Pageable pageable, String q, PromptStatus status);

    void deleteById(UUID id);
}
