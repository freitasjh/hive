package br.com.systec.hive.publicapi.impl.service;

import br.com.systec.hive.assistant.api.domain.PromptStatus;
import br.com.systec.hive.assistant.api.domain.PromptTemplate;
import br.com.systec.hive.assistant.api.exceptions.PromptTemplateNotFoundException;
import br.com.systec.hive.assistant.api.service.PromptTemplateService;
import br.com.systec.hive.publicapi.api.exceptions.PromptNotActiveException;
import br.com.systec.hive.publicapi.api.exceptions.PromptNotFoundException;
import br.com.systec.hive.publicapi.api.exceptions.PromptNotInAllowlistException;
import br.com.systec.hive.publicapi.api.exceptions.PromptVersionNotFoundException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class PromptGrantResolver {

    private final PromptTemplateService promptTemplateService;

    public PromptGrantResolver(PromptTemplateService promptTemplateService) {
        this.promptTemplateService = promptTemplateService;
    }

    public PromptTemplate resolve(String promptCode, List<String> allowlist, Map<String, Integer> pinnedVersions) {
        if (allowlist == null || !allowlist.contains(promptCode)) {
            throw new PromptNotInAllowlistException();
        }
        Integer pinnedVersion = (pinnedVersions == null) ? null : pinnedVersions.get(promptCode);
        if (pinnedVersion != null) {
            PromptTemplate pt;
            try {
                pt = promptTemplateService.findByCodeAndVersion(promptCode, pinnedVersion);
            } catch (PromptTemplateNotFoundException e) {
                throw new PromptVersionNotFoundException();
            }
            if (pt == null) {
                throw new PromptVersionNotFoundException();
            }
            if (pt.getStatus() != PromptStatus.ACTIVE) {
                throw new PromptNotActiveException();
            }
            return pt;
        }
        PromptTemplate pt;
        try {
            pt = promptTemplateService.findActiveByCode(promptCode);
        } catch (PromptTemplateNotFoundException e) {
            throw new PromptNotFoundException();
        }
        if (pt == null) {
            throw new PromptNotFoundException();
        }
        if (pt.getStatus() != PromptStatus.ACTIVE) {
            throw new PromptNotActiveException();
        }
        return pt;
    }
}