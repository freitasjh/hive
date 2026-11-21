package br.com.systec.hive.assistant.api.service;

import br.com.systec.hive.assistant.api.domain.PromptStatus;
import br.com.systec.hive.assistant.api.domain.PromptTemplate;
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

    /**
     * One exact version of a template, addressed by code and version.
     *
     * <p>Exists beside {@link #findActiveByCode} because the two questions are different and only one
     * of them has a default: following the active version is the usual case, and pinning an exact one
     * is the deliberate exception — the caller is saying "this conversation must keep answering with
     * the text I chose", which stops being true the moment the template is edited. Resolving a pin
     * by falling back to the active version would silently honour the wrong text.
     *
     * <p>No query is added for it: the repository already carries the tenant-scoped lookup.
     */
    PromptTemplate findByCodeAndVersion(String code, Integer version);

    Page<PromptTemplate> findByTenantId(UUID tenantId, Pageable pageable, String q, PromptStatus status);

    void deleteById(UUID id);
}
