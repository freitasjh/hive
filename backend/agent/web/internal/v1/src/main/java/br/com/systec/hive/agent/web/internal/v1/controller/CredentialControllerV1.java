package br.com.systec.hive.agent.web.internal.v1.controller;

import br.com.systec.hive.agent.api.domain.ProviderCredential;
import br.com.systec.hive.agent.api.service.ProviderCredentialService;
import br.com.systec.hive.agent.web.internal.v1.dto.CredentialStatusDTO;
import br.com.systec.hive.agent.web.internal.v1.dto.CredentialUpsertDTO;
import br.com.systec.hive.agent.web.internal.v1.mapper.CredentialDTOMapper;
import br.com.systec.hive.commons.api.tools.controller.RestPath;
import br.com.systec.hive.commons.api.tools.security.HasPermission;
import br.com.systec.hive.commons.api.tools.security.SecurityConstants;
import br.com.systec.hive.commons.impl.security.TenantContext;
import br.com.systec.hive.identity.api.domain.User;
import br.com.systec.hive.provider.api.AiProvider;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping(RestPath.V1 + "/provider-credentials")
@Tag(name = "Hive Agent - Provider Credentials", description = "Gestão mascarada de credenciais de provider por tenant")
public class CredentialControllerV1 {

    private final ProviderCredentialService credentialService;
    private final CredentialDTOMapper mapper;

    public CredentialControllerV1(ProviderCredentialService credentialService, CredentialDTOMapper mapper) {
        this.credentialService = credentialService;
        this.mapper = mapper;
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(description = "Lista status mascarado das credenciais do tenant")
    @HasPermission(SecurityConstants.AGENT_MANAGE)
    public ResponseEntity<List<CredentialStatusDTO>> list() {
        List<CredentialStatusDTO> result = credentialService.listStatuses(currentTenantId()).stream()
                .map(mapper::toDto)
                .toList();
        return ResponseEntity.ok(result);
    }

    @PutMapping(value = "/{provider}",
            consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(description = "Cria ou rotaciona a credencial do provider no tenant")
    @HasPermission(SecurityConstants.AGENT_MANAGE)
    public ResponseEntity<CredentialStatusDTO> upsert(@PathVariable String provider,
                                                      @RequestBody @Valid CredentialUpsertDTO payload) {
        AiProvider vendor = parseProvider(provider);
        String apiKey = payload.getApiKey();
        ProviderCredential saved = credentialService.upsert(currentTenantId(), vendor, apiKey, payload.getBaseUrl());
        return ResponseEntity.ok(mapper.toDto(saved));
    }

    @DeleteMapping(value = "/{provider}")
    @Operation(description = "Remove a credencial do provider no tenant")
    @HasPermission(SecurityConstants.AGENT_MANAGE)
    public ResponseEntity<Void> delete(@PathVariable String provider) {
        credentialService.delete(currentTenantId(), parseProvider(provider));
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    private AiProvider parseProvider(String provider) {
        try {
            return AiProvider.valueOf(provider.trim().toUpperCase());
        } catch (Exception e) {
            throw new IllegalArgumentException("provider inválido: " + provider + ".");
        }
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
