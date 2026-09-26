package br.com.systec.hive.agent.web.internal.v1.controller;

import br.com.systec.hive.agent.api.service.ValidateRateLimitPolicy;
import br.com.systec.hive.agent.web.internal.v1.dto.ProviderModelsDTO;
import br.com.systec.hive.agent.web.internal.v1.mapper.ProviderModelsDTOMapper;
import br.com.systec.hive.commons.api.tools.controller.RestControllerTool;
import br.com.systec.hive.commons.api.tools.controller.RestPath;
import br.com.systec.hive.commons.api.tools.security.HasPermission;
import br.com.systec.hive.commons.api.tools.security.SecurityConstants;
import br.com.systec.hive.commons.impl.security.TenantContext;
import br.com.systec.hive.identity.api.domain.User;
import br.com.systec.hive.provider.api.AiProvider;
import br.com.systec.hive.provider.api.OpencodeDiscoveryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping(RestPath.V1 + "/providers")
@Tag(name = "Hive Agent - Provider Models", description = "Descoberta de modelos remotos OpenCode GO/Zen")
public class ProviderModelsControllerV1 {

    private final OpencodeDiscoveryService discoveryService;
    private final ValidateRateLimitPolicy rateLimiter;
    private final ProviderModelsDTOMapper mapper;

    public ProviderModelsControllerV1(OpencodeDiscoveryService discoveryService,
                                      ValidateRateLimitPolicy rateLimiter,
                                      ProviderModelsDTOMapper mapper) {
        this.discoveryService = discoveryService;
        this.rateLimiter = rateLimiter;
        this.mapper = mapper;
    }

    @GetMapping(value = "/{provider}/models", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(description = "Lista modelos remotos filtrados do provedor (go|zen) com cache de 10min")
    @HasPermission(SecurityConstants.AGENT_MANAGE)
    public ResponseEntity<ProviderModelsDTO> models(@PathVariable String provider,
                                                   @RequestParam(defaultValue = "false") boolean refresh) {
        AiProvider vendor = parseProvider(provider);
        rateLimiter.check(currentTenantId());
        if (refresh) {
            discoveryService.evict(vendor);
        }
        ProviderModelsDTO dto = mapper.toDto(discoveryService.discover(vendor));
        return RestControllerTool.of().buildSuccessResponse(dto);
    }

    private AiProvider parseProvider(String provider) {
        if (provider == null) {
            throw new IllegalArgumentException("provider inválido: null.");
        }
        String normalized = provider.trim().toUpperCase();
        if (normalized.equals("GO") || normalized.equals("OPENCODE_GO")) {
            return AiProvider.OPENCODE_GO;
        }
        if (normalized.equals("ZEN") || normalized.equals("OPENCODE_ZEN")) {
            return AiProvider.OPENCODE_ZEN;
        }
        throw new IllegalArgumentException("provider inválido: " + provider + ". Use go|zen.");
    }

    private UUID currentTenantId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof User user) {
            if (user.getTenantId() != null) {
                return user.getTenantId();
            }
        }
        try {
            return TenantContext.getCurrentTenant();
        } catch (Exception e) {
            throw new org.springframework.security.access.AccessDeniedException("Tenant not found");
        }
    }
}
