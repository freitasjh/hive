package br.com.systec.hive.agent.web.internal.v1.controller;

import br.com.systec.hive.agent.api.domain.PromptStatus;
import br.com.systec.hive.agent.api.domain.PromptTemplate;
import br.com.systec.hive.agent.api.service.PromptTemplateService;
import br.com.systec.hive.agent.web.internal.v1.dto.PromptTemplateCreateDTO;
import br.com.systec.hive.agent.web.internal.v1.dto.PromptTemplateDTO;
import br.com.systec.hive.agent.web.internal.v1.dto.PromptTemplateUpdateDTO;
import br.com.systec.hive.agent.web.internal.v1.mapper.PromptTemplateDTOMapper;
import br.com.systec.hive.commons.api.tools.controller.RestPath;
import br.com.systec.hive.commons.api.tools.security.HasPermission;
import br.com.systec.hive.commons.api.tools.security.SecurityConstants;
import br.com.systec.hive.commons.impl.security.TenantContext;
import br.com.systec.hive.identity.api.domain.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping(RestPath.V1 + "/prompt-templates")
@Tag(name = "PromptTemplates", description = "Gestão de templates de prompts")
public class PromptTemplateControllerV1 {

    private final PromptTemplateService service;
    private final PromptTemplateDTOMapper mapper;

    public PromptTemplateControllerV1(PromptTemplateService service, PromptTemplateDTOMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(description = "Cria um novo template de prompt")
    @HasPermission(SecurityConstants.PROMPT_MANAGE)
    public ResponseEntity<PromptTemplateDTO> create(@RequestBody @Valid PromptTemplateCreateDTO payload) {
        PromptTemplate domain = mapper.toDomain(payload);
        domain.setTenantId(currentTenantId());
        PromptTemplate saved = service.create(domain);
        PromptTemplateDTO dto = mapper.toDTO(saved);
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(description = "Lista templates paginados")
    @HasPermission(SecurityConstants.PROMPT_MANAGE)
    public ResponseEntity<Page<PromptTemplateDTO>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) PromptStatus status) {
        if (size > 50) {
            throw new IllegalArgumentException("size must be <= 50");
        }
        if (size <= 0) size = 20;
        UUID tenantId = currentTenantId();
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createAt"));
        Page<PromptTemplate> result = service.findByTenantId(tenantId, pageable, q, status);
        Page<PromptTemplateDTO> dtoPage = result.map(mapper::toDTO);
        return ResponseEntity.ok(dtoPage);
    }

    @GetMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(description = "Busca um template de prompt pelo ID")
    @HasPermission(SecurityConstants.PROMPT_MANAGE)
    public ResponseEntity<PromptTemplateDTO> findById(@PathVariable UUID id) {
        PromptTemplate domain = service.findById(id);
        return ResponseEntity.ok(mapper.toDTO(domain));
    }

    @PutMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(description = "Atualiza um template de prompt existente")
    @HasPermission(SecurityConstants.PROMPT_MANAGE)
    public ResponseEntity<PromptTemplateDTO> update(@PathVariable UUID id, @RequestBody @Valid PromptTemplateUpdateDTO payload) {
        PromptTemplate domain = mapper.toDomain(payload);
        domain.setId(id);
        domain.setTenantId(currentTenantId());
        PromptTemplate updated = service.update(domain);
        return ResponseEntity.ok(mapper.toDTO(updated));
    }

    @DeleteMapping(value = "/{id}")
    @Operation(description = "Remove um template de prompt")
    @HasPermission(SecurityConstants.PROMPT_MANAGE)
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        PromptTemplate domain = new PromptTemplate();
        domain.setId(id);
        domain.setTenantId(currentTenantId());
        // service expects object with id; but service delete uses findByIdAndTenantId via TenantContext, so we need to set TenantContext correctly
        // Alternative: call deleteById which uses TenantContext internally
        service.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    private UUID currentTenantId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof User user) {
            if (user.getTenantId() != null) return user.getTenantId();
        }
        try {
            return TenantContext.getCurrentTenant();
        } catch (Exception e) {
            throw new org.springframework.security.access.AccessDeniedException("Tenant not found");
        }
    }
}
