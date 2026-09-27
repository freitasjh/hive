package br.com.systec.hive.tenant.web.internal.v1.controller;

import br.com.systec.hive.api.domain.TenantLlmConfig;
import br.com.systec.hive.api.service.TenantLlmConfigService;
import br.com.systec.hive.commons.api.tools.controller.RestControllerTool;
import br.com.systec.hive.commons.api.tools.controller.RestPath;
import br.com.systec.hive.commons.api.tools.security.HasPermission;
import br.com.systec.hive.commons.api.tools.security.SecurityConstants;
import br.com.systec.hive.commons.impl.security.TenantContext;
import br.com.systec.hive.identity.api.domain.User;
import br.com.systec.hive.tenant.web.internal.v1.dto.TenantLlmConfigDTO;
import br.com.systec.hive.tenant.web.internal.v1.dto.TenantLlmConfigUpsertDTO;
import br.com.systec.hive.tenant.web.internal.v1.mapper.TenantLlmConfigMapperDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * The tenant's default LLM. One row, one tenant, no fallback.
 *
 * <p>It lives in the {@code tenant} module — and not next to the chat — because of a Flyway fact, not
 * a modelling preference: {@code tenant_llm_config} needs a foreign key to {@code tenant(id)}, and a
 * foreign key across two independent Flyway histories has no guaranteed creation order. The
 * {@code agent} module documents the same rule from the other side in its V9 and therefore carries
 * no FK to {@code tenant} at all.
 *
 * <p>The tenant is never read from the path or the body. It comes from the authenticated principal,
 * so a payload cannot address another tenant's configuration even if a client tries.
 */
@RestController
@RequestMapping(RestPath.V1 + "/tenant/llm-config")
@Tag(name = "Hive Tenant - LLM Config", description = "Provedor e modelo padrão do tenant")
public class TenantLlmConfigControllerV1 {

    private final TenantLlmConfigService service;
    private final TenantLlmConfigMapperDTO mapper;

    public TenantLlmConfigControllerV1(TenantLlmConfigService service, TenantLlmConfigMapperDTO mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(description = "Lê a configuração padrão do tenant; null quando ainda não configurada")
    @HasPermission(SecurityConstants.AGENT_MANAGE)
    public ResponseEntity<TenantLlmConfigDTO> get() {
        UUID tenantId = currentTenantId();
        TenantLlmConfig config = service.get(tenantId);
        return RestControllerTool.of().buildSuccessResponse(
                config == null ? null : mapper.toDto(config, tenantId));
    }

    @PutMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(description = "Cria ou substitui a configuração padrão do tenant")
    @HasPermission(SecurityConstants.AGENT_MANAGE)
    public ResponseEntity<TenantLlmConfigDTO> upsert(@RequestBody @Valid TenantLlmConfigUpsertDTO payload) {
        TenantLlmConfig config = new TenantLlmConfig();
        config.setProvider(payload.provider());
        config.setModel(payload.model());
        UUID tenantId = currentTenantId();
        TenantLlmConfig saved = service.upsert(tenantId, config);
        return RestControllerTool.of().buildSuccessResponse(mapper.toDto(saved, tenantId));
    }

    private UUID currentTenantId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof User user && user.getTenantId() != null) {
            return user.getTenantId();
        }
        try {
            return TenantContext.getCurrentTenant();
        } catch (Exception e) {
            throw new org.springframework.security.access.AccessDeniedException("Tenant not found");
        }
    }
}
